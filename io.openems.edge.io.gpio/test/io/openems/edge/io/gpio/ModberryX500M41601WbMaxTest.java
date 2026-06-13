package io.openems.edge.io.gpio;

import static io.openems.edge.io.gpio.hardware.HardwareType.MODBERRY_X500_M41601WB_MAX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.WriteChannel;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.io.api.DigitalInput;
import io.openems.edge.io.api.DigitalOutput;
import io.openems.edge.io.gpio.api.AbstractGpioChannel;
import io.openems.edge.io.gpio.api.ReadChannelId;
import io.openems.edge.io.gpio.api.WriteChannelId;


public class ModberryX500M41601WbMaxTest {

	private File root;

	// All GPIO channels of the ModberryX500M41601WbMax:
	// - 4x digital inputs (530-533)
	// - 4x digital outputs (534-537)
	// - 4x bidirectional output (578-581)
	// - 4x bidirectional input (582-585)
	private static final List<AbstractGpioChannel> CHANNEL_IDS = List.of(//
			new ReadChannelId(530, "DigitalInput1"), //
			new ReadChannelId(531, "DigitalInput2"), //
			new ReadChannelId(532, "DigitalInput3"), //
			new ReadChannelId(533, "DigitalInput4"), //
			new WriteChannelId(534, "DigitalOutput1"), //
			new WriteChannelId(535, "DigitalOutput2"), //
			new WriteChannelId(536, "DigitalOutput3"), //
			new WriteChannelId(537, "DigitalOutput4"), //
			new WriteChannelId(578, "DigitalInputOutput1Out"), //
			new WriteChannelId(579, "DigitalInputOutput2Out"), //
			new WriteChannelId(580, "DigitalInputOutput3Out"), //
			new WriteChannelId(581, "DigitalInputOutput4Out"), //
			new ReadChannelId(582, "DigitalInputOutput1In"), //
			new ReadChannelId(583, "DigitalInputOutput2In"), //
			new ReadChannelId(584, "DigitalInputOutput3In"), //
			new ReadChannelId(585, "DigitalInputOutput4In") //
	);

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Before
	public void setUp() throws IOException {
		try {
			this.folder.create();
			this.root = this.folder.getRoot();
			new File(this.root.getAbsolutePath() + "/gpio").mkdir();
			this.fileWithDirectoryAssurance(this.root.getAbsolutePath() + File.separator + "gpio", "export");
		} catch (IOException ioe) {
			throw new IOException("error creating temporary test file in " + this.getClass().getSimpleName());
		}
		for (var channelId : CHANNEL_IDS) {
			this.createDirectoryForGpio(this.folder, channelId.gpio);
		}
	}

	private File fileWithDirectoryAssurance(String directory, String filename) {
		File dir = new File(directory);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		return new File(directory + File.separatorChar + filename);
	}

	private void createDirectoryForGpio(TemporaryFolder root, int gpioNumber) throws IOException {
		var rootPath = root.getRoot().getAbsolutePath();
		var basePath = rootPath + File.separatorChar + "gpio" + File.separatorChar + "gpio" + gpioNumber;
		var valueFile = this.fileWithDirectoryAssurance(basePath, "value");
		Files.writeString(valueFile.toPath(), "0", Charset.defaultCharset());
		this.fileWithDirectoryAssurance(basePath, "direction");
	}

	private String readGpioFile(File root, int gpioNumber) throws IOException {
		var path = Path.of(String.join(File.separator, root.getAbsolutePath(), "gpio", "gpio" + gpioNumber, "value"));
		return Files.readString(path);
	}

	private void setGpioFile(File root, int gpioNumber, int value) throws IOException {
		var path = Path.of(String.join(File.separator, root.getAbsolutePath(), "gpio", "gpio" + gpioNumber, "value"));
		Files.writeString(path, String.valueOf(value));
	}

	private MyConfig buildConfig() {
		return MyConfig.create() //
				.setId("io0") //
				.setAlias("io0") //
				.setEnabled(true) //
				.setGpioPath(this.folder.getRoot().getAbsolutePath()) //
				.setHardwareType(MODBERRY_X500_M41601WB_MAX) //
				//.setHardwareType(MODBERRY_X500_M40804_WB)
				.build();
	}

	// -------------------------------------------------------------------------
	// Basic component tests
	// -------------------------------------------------------------------------

	@Test
	public void testComponentLoadsSuccessfully() throws Exception {
		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig());
	}

	
	@Test
	public void testChannelIdsAreCorrect() throws Exception {
	    IoGpio component = new IoGpioImpl();
	    new ComponentTest(component).activate(this.buildConfig());
	    assertNotNull(component.channel("DigitalInput1"));
	    assertNotNull(component.channel("DigitalOutput1"));
	    assertNotNull(component.channel("DigitalInputOutput1Out"));
	    assertNotNull(component.channel("DigitalInputOutput1In"));
	}

	// -------------------------------------------------------------------------
	// Digital inputs (530-533)
	// -------------------------------------------------------------------------

	@Test
	public void testDigitalInputsDefaultFalse() throws Exception {
		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("Digital inputs default to false") //
						.output(new ChannelAddress("io0", "DigitalInput1"), false) //
						.output(new ChannelAddress("io0", "DigitalInput2"), false) //
						.output(new ChannelAddress("io0", "DigitalInput3"), false) //
						.output(new ChannelAddress("io0", "DigitalInput4"), false) //
				);
	}

	@Test
	public void testDigitalInputsDetected() throws Exception {
		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("Digital inputs initially false") //
						.output(new ChannelAddress("io0", "DigitalInput1"), false) //
						.output(new ChannelAddress("io0", "DigitalInput2"), false) //
						.output(new ChannelAddress("io0", "DigitalInput3"), false) //
						.output(new ChannelAddress("io0", "DigitalInput4"), false) //
				);

		this.setGpioFile(this.root, 530, 1);
		this.setGpioFile(this.root, 531, 1);
		this.setGpioFile(this.root, 532, 1);
		this.setGpioFile(this.root, 533, 1);

		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("Digital inputs detected as true after GPIO set") //
						.output(new ChannelAddress("io0", "DigitalInput1"), true) //
						.output(new ChannelAddress("io0", "DigitalInput2"), true) //
						.output(new ChannelAddress("io0", "DigitalInput3"), true) //
						.output(new ChannelAddress("io0", "DigitalInput4"), true) //
				);
	}

	// -------------------------------------------------------------------------
	// Digital outputs (534-537)
	// -------------------------------------------------------------------------

	@Test
	public void testDigitalOutputsWrittenToFs() throws Exception {
		assertEquals(this.readGpioFile(this.root, 534), "0");
		assertEquals(this.readGpioFile(this.root, 535), "0");
		assertEquals(this.readGpioFile(this.root, 536), "0");
		assertEquals(this.readGpioFile(this.root, 537), "0");

		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("Digital outputs written to filesystem") //
						.input(new ChannelAddress("io0", "DigitalOutput1"), true) //
						.input(new ChannelAddress("io0", "DigitalOutput2"), true) //
						.input(new ChannelAddress("io0", "DigitalOutput3"), true) //
						.input(new ChannelAddress("io0", "DigitalOutput4"), true) //
				);

		assertEquals(this.readGpioFile(this.root, 534), "1");
		assertEquals(this.readGpioFile(this.root, 535), "1");
		assertEquals(this.readGpioFile(this.root, 536), "1");
		assertEquals(this.readGpioFile(this.root, 537), "1");
	}

	// -------------------------------------------------------------------------
	// Bidirectional channels (578-585)
	// -------------------------------------------------------------------------

	@Test
	public void testBidirectionalOutputsWrittenToFs() throws Exception {
		assertEquals(this.readGpioFile(this.root, 578), "0");
		assertEquals(this.readGpioFile(this.root, 579), "0");
		assertEquals(this.readGpioFile(this.root, 580), "0");
		assertEquals(this.readGpioFile(this.root, 581), "0");

		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("Bidirectional outputs written to filesystem") //
						.input(new ChannelAddress("io0", "DigitalInputOutput1Out"), true) //
						.input(new ChannelAddress("io0", "DigitalInputOutput2Out"), true) //
						.input(new ChannelAddress("io0", "DigitalInputOutput3Out"), true) //
						.input(new ChannelAddress("io0", "DigitalInputOutput4Out"), true) //
				);

		assertEquals(this.readGpioFile(this.root, 578), "1");
		assertEquals(this.readGpioFile(this.root, 579), "1");
		assertEquals(this.readGpioFile(this.root, 580), "1");
		assertEquals(this.readGpioFile(this.root, 581), "1");
	}

	@Test
	public void testBidirectionalInputsDetected() throws Exception {
		this.setGpioFile(this.root, 582, 1);
		this.setGpioFile(this.root, 583, 1);
		this.setGpioFile(this.root, 584, 1);
		this.setGpioFile(this.root, 585, 1);

		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("Bidirectional inputs detected as true") //
						.output(new ChannelAddress("io0", "DigitalInputOutput1In"), true) //
						.output(new ChannelAddress("io0", "DigitalInputOutput2In"), true) //
						.output(new ChannelAddress("io0", "DigitalInputOutput3In"), true) //
						.output(new ChannelAddress("io0", "DigitalInputOutput4In"), true) //
				);
	}

	// -------------------------------------------------------------------------
	// Java API and interface tests
	// -------------------------------------------------------------------------

	@Test
	public void testJavaApi() throws Exception {
		this.setGpioFile(this.root, 534, 0);
		var componentManager = new DummyComponentManager();
		var componentTest = new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig());
		componentManager.addComponent(componentTest.getSut());

		WriteChannel<Boolean> writeChannel = componentManager
				.getChannel(new ChannelAddress("io0", "DigitalOutput1"));
		assertFalse(writeChannel.value().isDefined());
		writeChannel.setNextValue(true);
	}

	@Test
	public void testDigitalOutputChannelCount() throws Exception {
		var componentTest = new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig());
		// 4x DIGITAL_OUTPUT + 4x DIGITAL_INPUT_OUTPUT_OUT = 8 output channels
		assertTrue(((DigitalOutput) componentTest.getSut()).digitalOutputChannels().length == 8);
	}

	@Test
	public void testDigitalInputChannelCount() throws Exception {
		var componentTest = new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig());
		// 4x DIGITAL_INPUT + 4x DIGITAL_INPUT_OUTPUT_IN = 8 input channels
		assertTrue(((DigitalInput) componentTest.getSut()).digitalInputChannels().length >= 4);
	}
}
