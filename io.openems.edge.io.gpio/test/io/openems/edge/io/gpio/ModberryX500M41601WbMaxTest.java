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
	// - 4x digital inputs (586-589, OPTO DI)
	// - 4x digital outputs (578-581, DO)
	// - 4x configurable digital I/O used as outputs (582-585, DIO)
	private static final List<AbstractGpioChannel> CHANNEL_IDS = List.of(//
			new ReadChannelId(586, "DigitalInput1"), //
			new ReadChannelId(587, "DigitalInput2"), //
			new ReadChannelId(588, "DigitalInput3"), //
			new ReadChannelId(589, "DigitalInput4"), //
			new WriteChannelId(578, "DigitalOutput1"), //
			new WriteChannelId(579, "DigitalOutput2"), //
			new WriteChannelId(580, "DigitalOutput3"), //
			new WriteChannelId(581, "DigitalOutput4"), //
			new WriteChannelId(582, "DigitalInputOutput1"), //
			new WriteChannelId(583, "DigitalInputOutput2"), //
			new WriteChannelId(584, "DigitalInputOutput3"), //
			new WriteChannelId(585, "DigitalInputOutput4") //
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
		assertNotNull(component.channel("DigitalInputOutput1"));
	}

	// -------------------------------------------------------------------------
	// Digital inputs (586-589, OPTO DI)
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

		this.setGpioFile(this.root, 586, 1);
		this.setGpioFile(this.root, 587, 1);
		this.setGpioFile(this.root, 588, 1);
		this.setGpioFile(this.root, 589, 1);

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
	// Digital outputs (578-581, DO)
	// -------------------------------------------------------------------------

	@Test
	public void testDigitalOutputsWrittenToFs() throws Exception {
		assertEquals(this.readGpioFile(this.root, 578), "0");
		assertEquals(this.readGpioFile(this.root, 579), "0");
		assertEquals(this.readGpioFile(this.root, 580), "0");
		assertEquals(this.readGpioFile(this.root, 581), "0");

		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("Digital outputs written to filesystem") //
						.input(new ChannelAddress("io0", "DigitalOutput1"), true) //
						.input(new ChannelAddress("io0", "DigitalOutput2"), true) //
						.input(new ChannelAddress("io0", "DigitalOutput3"), true) //
						.input(new ChannelAddress("io0", "DigitalOutput4"), true) //
				);

		assertEquals(this.readGpioFile(this.root, 578), "1");
		assertEquals(this.readGpioFile(this.root, 579), "1");
		assertEquals(this.readGpioFile(this.root, 580), "1");
		assertEquals(this.readGpioFile(this.root, 581), "1");
	}

	// -------------------------------------------------------------------------
	// Configurable I/O used as outputs (582-585, DIO)
	// -------------------------------------------------------------------------

	@Test
	public void testDioOutputsWrittenToFs() throws Exception {
		assertEquals(this.readGpioFile(this.root, 582), "0");
		assertEquals(this.readGpioFile(this.root, 583), "0");
		assertEquals(this.readGpioFile(this.root, 584), "0");
		assertEquals(this.readGpioFile(this.root, 585), "0");

		new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig()) //
				.next(new TestCase("DIO outputs written to filesystem") //
						.input(new ChannelAddress("io0", "DigitalInputOutput1"), true) //
						.input(new ChannelAddress("io0", "DigitalInputOutput2"), true) //
						.input(new ChannelAddress("io0", "DigitalInputOutput3"), true) //
						.input(new ChannelAddress("io0", "DigitalInputOutput4"), true) //
				);

		assertEquals(this.readGpioFile(this.root, 582), "1");
		assertEquals(this.readGpioFile(this.root, 583), "1");
		assertEquals(this.readGpioFile(this.root, 584), "1");
		assertEquals(this.readGpioFile(this.root, 585), "1");
	}

	// -------------------------------------------------------------------------
	// Java API and interface tests
	// -------------------------------------------------------------------------

	@Test
	public void testJavaApi() throws Exception {
		this.setGpioFile(this.root, 578, 0);
		var componentManager = new DummyComponentManager();
		var componentTest = new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig());
		componentManager.addComponent(componentTest.getSut());

		WriteChannel<Boolean> writeChannel = componentManager //
				.getChannel(new ChannelAddress("io0", "DigitalOutput1"));
		assertFalse(writeChannel.value().isDefined());
		writeChannel.setNextValue(true);
	}

	@Test
	public void testDigitalOutputChannelCount() throws Exception {
		var componentTest = new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig());
		// 4x DO + 4x DIO = 8 output channels
		assertTrue(((DigitalOutput) componentTest.getSut()).digitalOutputChannels().length == 8);
	}

	@Test
	public void testDigitalInputChannelCount() throws Exception {
		var componentTest = new ComponentTest(new IoGpioImpl()) //
				.activate(this.buildConfig());
		// 4x OPTO DI
		assertTrue(((DigitalInput) componentTest.getSut()).digitalInputChannels().length == 4);
	}
}