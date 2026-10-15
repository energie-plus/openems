package io.openems.edge.meter.virtual.gate;

import static io.openems.common.types.MeterType.GRID;
import static io.openems.edge.meter.virtual.gate.MeterVirtualGate.ChannelId.GATE_CLOSED;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.test.DummyElectricityMeter;

public class MeterVirtualGateImplTest {

	@Test
	public void test() throws Exception {
		new ComponentTest(new MeterVirtualGateImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("meter", new DummyElectricityMeter("meter1")) //
				.addReference("ess", new DummyManagedSymmetricEss("ess0")) //
				.activate(MyConfig.create() //
						.setId("meter0") //
						.setType(GRID) //
						.setAddToSum(false) //
						.setMeterId("meter1") //
						.setEssId("ess0") //
						.setReserveSoc(30) //
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
				.next(new TestCase("SoC recovers above reserve: opens again") //
						.input("meter1", ElectricityMeter.ChannelId.ACTIVE_POWER, 700) //
						.input("ess0", SymmetricEss.ChannelId.SOC, 40) //
						.output("meter0", ElectricityMeter.ChannelId.ACTIVE_POWER, 700) //
						.output(GATE_CLOSED, false));
	}
}
