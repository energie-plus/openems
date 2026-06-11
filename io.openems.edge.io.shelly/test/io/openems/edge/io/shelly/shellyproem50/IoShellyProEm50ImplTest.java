package io.openems.edge.io.shelly.shellyproem50;

import static io.openems.common.types.MeterType.CONSUMPTION_METERED;
import static io.openems.common.types.MeterType.GRID;
import static io.openems.common.types.MeterType.PRODUCTION;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.openems.common.bridge.http.api.HttpError;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.bridge.http.dummy.DummyBridgeHttpBundle;
import io.openems.edge.bridge.http.cycle.HttpBridgeCycleServiceDefinition;
import io.openems.edge.bridge.http.cycle.dummy.DummyCycleSubscriber;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.type.Phase.SinglePhase;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.timedata.test.DummyTimedata;

public class IoShellyProEm50ImplTest {

	// Reusable JSON helpers
	private static String em50Json(int channel, double voltage, double current, double actPower,
			boolean restartRequired) {
		return """
				{
				  "em1:%d": {
				    "id": %d,
				    "voltage": %.1f,
				    "current": %.3f,
				    "act_power": %.1f,
				    "freq": 50.0
				  },
				  "sys": {
				    "restart_required": %b
				  }
				}
				""".formatted(channel, channel, voltage, current, actPower, restartRequired);
	}

	// -------------------------------------------------------------------------
	// test(): Channel 0, no invert, CONSUMPTION_METERED
	// -------------------------------------------------------------------------
	@Test
	public void test() throws Exception {
		final var sut = new IoShellyProEm50Impl();
		final var httpTestBundle = new DummyBridgeHttpBundle();
		final var dummyCycleSubscriber = new DummyCycleSubscriber();
		new ComponentTest(sut) //
				.addReference("httpBridgeFactory", httpTestBundle.factory()) //
				.addReference("httpBridgeCycleServiceDefinition",
						new HttpBridgeCycleServiceDefinition(dummyCycleSubscriber)) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.activate(MyConfig.create() //
						.setId("io0") //
						.setIp("127.0.0.1") //
						.setType(CONSUMPTION_METERED) //
						.setPhase(SinglePhase.L1) //
						.setChannel(0) //
						.setInvert(false) //
						.build()) //

				// --- Successful response ---
				.next(new TestCase("Successful read - channel 0") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(
									HttpResponse.ok(em50Json(0, 237.2, 0.106, 6.7, false)));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.onAfterProcessImage(() -> assertEquals("L1:7 W", sut.debugLog())) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 7) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, 7) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 237200) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 237200) //
						.output(ElectricityMeter.ChannelId.CURRENT, 106) //
						.output(ElectricityMeter.ChannelId.CURRENT_L1, 106) //
						.output(IoShellyProEm50.ChannelId.SLAVE_COMMUNICATION_FAILED, false) //
						.output(IoShellyProEm50.ChannelId.NEEDS_RESTART, false)) //

				// --- NEEDS_RESTART flag ---
				.next(new TestCase("Restart required flag") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(
									HttpResponse.ok(em50Json(0, 237.2, 0.106, 6.7, true)));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.output(IoShellyProEm50.ChannelId.NEEDS_RESTART, true) //
						.output(IoShellyProEm50.ChannelId.SLAVE_COMMUNICATION_FAILED, false)) //

				// --- HTTP error response ---
				.next(new TestCase("Invalid read response") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextFailedResult(HttpError.ResponseError.notFound());
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.onAfterProcessImage(() -> assertEquals("L1:UNDEFINED", sut.debugLog())) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, null) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, null) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, null) //
						.output(ElectricityMeter.ChannelId.CURRENT, null) //
						.output(ElectricityMeter.ChannelId.CURRENT_L1, null) //
						.output(IoShellyProEm50.ChannelId.SLAVE_COMMUNICATION_FAILED, true)) //

				.deactivate();
	}

	// -------------------------------------------------------------------------
	// testChannel1(): Channel 1 (EM50-spezifisch – zweiter Eingang)
	// -------------------------------------------------------------------------
	@Test
	public void testChannel1() throws Exception {
		final var sut = new IoShellyProEm50Impl();
		final var httpTestBundle = new DummyBridgeHttpBundle();
		final var dummyCycleSubscriber = new DummyCycleSubscriber();
		new ComponentTest(sut) //
				.addReference("httpBridgeFactory", httpTestBundle.factory()) //
				.addReference("httpBridgeCycleServiceDefinition",
						new HttpBridgeCycleServiceDefinition(dummyCycleSubscriber)) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.activate(MyConfig.create() //
						.setId("io0") //
						.setIp("127.0.0.1") //
						.setType(CONSUMPTION_METERED) //
						.setPhase(SinglePhase.L2) //
						.setChannel(1) //
						.setInvert(false) //
						.build()) //

				.next(new TestCase("Successful read - channel 1") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(
									HttpResponse.ok(em50Json(1, 230.0, 0.500, 115.0, false)));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.onAfterProcessImage(() -> assertEquals("L2:115 W", sut.debugLog())) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 115) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 230000) //
						.output(ElectricityMeter.ChannelId.CURRENT, 500) //
						.output(IoShellyProEm50.ChannelId.SLAVE_COMMUNICATION_FAILED, false)) //

				// Verifies that channel 0 data is ignored when channel=1 is configured
				.next(new TestCase("Wrong channel in response → parse error, null values") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(HttpResponse.ok("""
									{
									  "em1:0": { "id": 0, "voltage": 230.0, "current": 0.5, "act_power": 115.0 },
									  "sys": { "restart_required": false }
									}
									"""));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, null) //
						.output(IoShellyProEm50.ChannelId.SLAVE_COMMUNICATION_FAILED, false)) //

				.deactivate();
	}

	// -------------------------------------------------------------------------
	// testInvert(): Invert-Flag invertiert Power und Current, nicht Voltage
	// -------------------------------------------------------------------------
	@Test
	public void testInvert() throws Exception {
		final var sut = new IoShellyProEm50Impl();
		final var httpTestBundle = new DummyBridgeHttpBundle();
		final var dummyCycleSubscriber = new DummyCycleSubscriber();
		new ComponentTest(sut) //
				.addReference("httpBridgeFactory", httpTestBundle.factory()) //
				.addReference("httpBridgeCycleServiceDefinition",
						new HttpBridgeCycleServiceDefinition(dummyCycleSubscriber)) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.activate(MyConfig.create() //
						.setId("io0") //
						.setIp("127.0.0.1") //
						.setType(CONSUMPTION_METERED) //
						.setPhase(SinglePhase.L1) //
						.setChannel(0) //
						.setInvert(true) //
						.build()) //

				.next(new TestCase("Invert: positive power becomes negative") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(
									HttpResponse.ok(em50Json(0, 237.2, 0.106, 6.7, false)));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.onAfterProcessImage(() -> assertEquals("L1:-7 W", sut.debugLog())) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, -7) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, -7) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 237200) //   Voltage is NOT inverted
						.output(ElectricityMeter.ChannelId.CURRENT, -106) //
						.output(IoShellyProEm50.ChannelId.SLAVE_COMMUNICATION_FAILED, false));
	}

	// -------------------------------------------------------------------------
	// testMeterTypeGrid(): GRID – Bezug positiv, Einspeisung negativ
	// -------------------------------------------------------------------------
	@Test
	public void testMeterTypeGrid() throws Exception {
		final var sut = new IoShellyProEm50Impl();
		final var httpTestBundle = new DummyBridgeHttpBundle();
		final var dummyCycleSubscriber = new DummyCycleSubscriber();
		new ComponentTest(sut) //
				.addReference("httpBridgeFactory", httpTestBundle.factory()) //
				.addReference("httpBridgeCycleServiceDefinition",
						new HttpBridgeCycleServiceDefinition(dummyCycleSubscriber)) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.activate(MyConfig.create() //
						.setId("io0") //
						.setIp("127.0.0.1") //
						.setType(GRID) //
						.setPhase(SinglePhase.L1) //
						.setChannel(0) //
						.setInvert(false) //
						.build()) //

				// Bezug vom Netz → positiv
				.next(new TestCase("GRID: consumption (positive power)") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(
									HttpResponse.ok(em50Json(0, 230.0, 1.0, 230.0, false)));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 230) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, null)) // no cycle completed yet

				// Einspeisung ins Netz → negativ
				.next(new TestCase("GRID: feed-in (negative power via invert)") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(
									HttpResponse.ok(em50Json(0, 230.0, 1.0, -230.0, false)));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, -230)) //

				.deactivate();
	}

	// -------------------------------------------------------------------------
	// testMeterTypeProduction(): PRODUCTION – immer abs(power)
	// -------------------------------------------------------------------------
	@Test
	public void testMeterTypeProduction() throws Exception {
		final var sut = new IoShellyProEm50Impl();
		final var httpTestBundle = new DummyBridgeHttpBundle();
		final var dummyCycleSubscriber = new DummyCycleSubscriber();
		new ComponentTest(sut) //
				.addReference("httpBridgeFactory", httpTestBundle.factory()) //
				.addReference("httpBridgeCycleServiceDefinition",
						new HttpBridgeCycleServiceDefinition(dummyCycleSubscriber)) //
				.addReference("timedata", new DummyTimedata("timedata0")) //
				.activate(MyConfig.create() //
						.setId("io0") //
						.setIp("127.0.0.1") //
						.setType(PRODUCTION) //
						.setPhase(SinglePhase.L1) //
						.setChannel(0) //
						.setInvert(false) //
						.build()) //

				.next(new TestCase("PRODUCTION: positive act_power") //
						.onBeforeProcessImage(() -> {
							httpTestBundle.forceNextSuccessfulResult(
									HttpResponse.ok(em50Json(0, 230.0, 0.5, 115.0, false)));
							dummyCycleSubscriber.triggerNextCycle();
						}) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 115)) //

				.deactivate();
	}
}