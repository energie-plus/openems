package io.openems.edge.io.shelly.common.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.JsonUtils;
import io.openems.edge.meter.test.DummyElectricityMeter;

public class ShellyScriptEnergyHandlerTest {

	private static com.google.gson.JsonObject json(String s) throws OpenemsNamedException {
		return JsonUtils.parseToJsonObject(s);
	}

	private static String payload(boolean ready, long importWh, long exportWh) {
		return "{\"v\":1,\"ready\":" + ready + ",\"import_wh\":" + importWh + ",\"export_wh\":" + exportWh
				+ ",\"uptime_ms\":1000}";
	}

	private static Long productionEnergy(DummyElectricityMeter c) {
		c.getActiveProductionEnergyChannel().nextProcessImage();
		return c.getActiveProductionEnergy().get();
	}

	private static Long consumptionEnergy(DummyElectricityMeter c) {
		c.getActiveConsumptionEnergyChannel().nextProcessImage();
		return c.getActiveConsumptionEnergy().get();
	}

	@Test
	public void testImportIsProductionExportIsConsumption() throws Exception {
		final var c = new DummyElectricityMeter("meter0");
		final var sut = new ShellyScriptEnergyHandler(c, false);

		assertTrue(sut.process(json(payload(true, 201_795, 173_725))));

		assertEquals(Long.valueOf(201_795), productionEnergy(c));
		assertEquals(Long.valueOf(173_725), consumptionEnergy(c));
	}

	@Test
	public void testInvertSwapsEnergies() throws Exception {
		final var c = new DummyElectricityMeter("meter0");
		final var sut = new ShellyScriptEnergyHandler(c, true);

		assertTrue(sut.process(json(payload(true, 201_795, 173_725))));

		assertEquals(Long.valueOf(173_725), productionEnergy(c));
		assertEquals(Long.valueOf(201_795), consumptionEnergy(c));
	}

	@Test
	public void testNotReadyLeavesChannelsUntouched() throws Exception {
		final var c = new DummyElectricityMeter("meter0");
		final var sut = new ShellyScriptEnergyHandler(c, false);

		assertFalse(sut.process(json(payload(false, 0, 0))));

		assertNull(productionEnergy(c));
		assertNull(consumptionEnergy(c));
	}

	@Test
	public void testDecreasingValuesAreHeld() throws Exception {
		final var c = new DummyElectricityMeter("meter0");
		final var sut = new ShellyScriptEnergyHandler(c, false);

		sut.process(json(payload(true, 1_000, 500)));
		// Device lost power: script fell back to its last saved value
		sut.process(json(payload(true, 990, 480)));

		assertEquals(Long.valueOf(1_000), productionEnergy(c));
		assertEquals(Long.valueOf(500), consumptionEnergy(c));

		// Script caught up again
		sut.process(json(payload(true, 1_010, 520)));

		assertEquals(Long.valueOf(1_010), productionEnergy(c));
		assertEquals(Long.valueOf(520), consumptionEnergy(c));
	}

	@Test
	public void testChannelsAreHeldIndependently() throws Exception {
		final var c = new DummyElectricityMeter("meter0");
		final var sut = new ShellyScriptEnergyHandler(c, false);

		sut.process(json(payload(true, 1_000, 500)));
		sut.process(json(payload(true, 1_005, 400)));

		assertEquals(Long.valueOf(1_005), productionEnergy(c));
		assertEquals(Long.valueOf(500), consumptionEnergy(c));
	}

	@Test
	public void testUnsupportedVersionIsRejected() throws Exception {
		final var c = new DummyElectricityMeter("meter0");
		final var sut = new ShellyScriptEnergyHandler(c, false);

		assertThrows(OpenemsNamedException.class,
				() -> sut.process(json("{\"v\":2,\"ready\":true,\"import_wh\":1,\"export_wh\":1}")));
		assertNull(productionEnergy(c));
	}

	@Test
	public void testMissingOrNegativeValuesAreRejected() throws Exception {
		final var c = new DummyElectricityMeter("meter0");
		final var sut = new ShellyScriptEnergyHandler(c, false);

		assertThrows(OpenemsNamedException.class, () -> sut.process(json("{\"v\":1,\"ready\":true}")));
		assertThrows(OpenemsNamedException.class, () -> sut.process(json(payload(true, -1, 5))));
		assertNull(productionEnergy(c));
		assertNull(consumptionEnergy(c));
	}
}
