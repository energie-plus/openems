package io.openems.edge.meter.virtual.gate;

import static io.openems.common.types.MeterType.GRID;
import static io.openems.edge.meter.virtual.gate.MeterVirtualGate.ChannelId.GATE_CLOSED;
import static io.openems.edge.meter.virtual.gate.MeterVirtualGate.ChannelId.RESERVE_SOC_SOURCE_UNAVAILABLE;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.test.DummyElectricityMeter;

public class MeterVirtualGateImplTest {

	@Test
	public void fixedReserveSoc_withHysteresis() throws Exception {
		new ComponentTest(new MeterVirtualGateImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("meter", new DummyElectricityMeter("meter1")) //
				.addReference("ess", new DummyManagedSymmetricEss("ess0")) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setType(GRID) //
						.setAddToSum(false) //
						.setMeterId("meter1") //
						.setEssId("ess0") //
						.setReserveSoc(30) //
						.setHysteresis(5) //
						.build()) //
				.next(new TestCase("SoC above reserve: pass through") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 50) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.output(GATE_CLOSED, false)) //
				.next(new TestCase("SoC at reserve: gate closes") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 30) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 0) //
						.output(GATE_CLOSED, true)) //
				.next(new TestCase("SoC below reserve: stays closed") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 500) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 20) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 0) //
						.output(GATE_CLOSED, true)) //
				.next(new TestCase("SoC just above reserve, still within hysteresis band: stays closed") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 700) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 35) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 0) //
						.output(GATE_CLOSED, true)) //
				.next(new TestCase("SoC rises past reserve + hysteresis: opens again") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 700) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 36) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 700) //
						.output(GATE_CLOSED, false));
	}

	@Test
	public void dynamicReserveSoc_followsReferencedChannel() throws Exception {
		// Stand-in for a real dynamic source (e.g. Controller.Ess.SocReserveEstimator's
		// CalculatedMinSoc): any component with an Integer channel works, so a second
		// dummy Ess's SoC channel is reused here purely for its matching [%] unit.
		var estimator = new DummyManagedSymmetricEss("estimator0");
		var componentManager = new DummyComponentManager();

		new ComponentTest(new MeterVirtualGateImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", componentManager) //
				.addReference("meter", new DummyElectricityMeter("meter1")) //
				.addReference("ess", new DummyManagedSymmetricEss("ess0")) //
				.addComponent(estimator) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setType(GRID) //
						.setAddToSum(false) //
						.setMeterId("meter1") //
						.setEssId("ess0") //
						.setReserveSoc(30) // fallback only, must not be used while estimator0/Soc is defined
						.setReserveSocChannelAddress("estimator0/Soc") //
						.build()) //
				.next(new TestCase("dynamic threshold undefined at first: falls back to fixed value") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 35) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.output(GATE_CLOSED, false) //
						.output(RESERVE_SOC_SOURCE_UNAVAILABLE, true)) //
				.next(new TestCase("dynamic threshold now above SoC: gate closes, ignoring the fixed 30") //
						// estimator0 must be input BEFORE meter1/ess0: the gate's listener callback
						// fires synchronously off meter1's/ess0's setNextValue, so it would otherwise
						// read estimator0's not-yet-applied value for this cycle.
						.input("estimator0", SymmetricEss.ChannelId.SOC, 40) //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 35) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 0) //
						.output(GATE_CLOSED, true) //
						.output(RESERVE_SOC_SOURCE_UNAVAILABLE, false)) //
				.next(new TestCase("dynamic threshold drops below SoC: gate opens again") //
						.input("estimator0", SymmetricEss.ChannelId.SOC, 20) //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 35) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.output(GATE_CLOSED, false));
	}

	@Test
	public void dynamicReserveSoc_fallsBackWhenSourceComponentMissing() throws Exception {
		new ComponentTest(new MeterVirtualGateImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("meter", new DummyElectricityMeter("meter1")) //
				.addReference("ess", new DummyManagedSymmetricEss("ess0")) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setType(GRID) //
						.setAddToSum(false) //
						.setMeterId("meter1") //
						.setEssId("ess0") //
						.setReserveSoc(30) //
						.setReserveSocChannelAddress("doesNotExist0/Soc") //
						.build()) //
				.next(new TestCase("referenced component does not exist: falls back, no crash") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 20) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 0) //
						.output(GATE_CLOSED, true) //
						.output(RESERVE_SOC_SOURCE_UNAVAILABLE, true));
	}

	@Test
	public void hysteresis_preventsFlickerWhileSocOscillatesNearThreshold() throws Exception {
		new ComponentTest(new MeterVirtualGateImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("componentManager", new DummyComponentManager()) //
				.addReference("meter", new DummyElectricityMeter("meter1")) //
				.addReference("ess", new DummyManagedSymmetricEss("ess0")) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setType(GRID) //
						.setAddToSum(false) //
						.setMeterId("meter1") //
						.setEssId("ess0") //
						.setReserveSoc(30) //
						.setHysteresis(5) //
						.build()) //
				.next(new TestCase("crosses down to close") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 1_000) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 29) //
						.output(GATE_CLOSED, true)) //
				.next(new TestCase("noisy uptick, still within band: stays closed") //
						.input("ess0", SymmetricEss.ChannelId.SOC, 31) //
						.output(GATE_CLOSED, true)) //
				.next(new TestCase("noisy downtick again: stays closed") //
						.input("ess0", SymmetricEss.ChannelId.SOC, 29) //
						.output(GATE_CLOSED, true)) //
				.next(new TestCase("noisy uptick again, still within band: stays closed") //
						.input("ess0", SymmetricEss.ChannelId.SOC, 32) //
						.output(GATE_CLOSED, true)) //
				.next(new TestCase("decisively past the band: opens") //
						.input("ess0", SymmetricEss.ChannelId.SOC, 36) //
						.output(GATE_CLOSED, false));
	}
}
