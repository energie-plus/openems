package io.openems.edge.meter.mqtt.whatwatt;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.bridge.mqtt.api.MqttComponent;
import io.openems.edge.bridge.mqtt.test.DummyBridgeMqtt;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.meter.api.ElectricityMeter;

public class MeterMqttWhatWattImplTest {

	private static final String COMPONENT_ID = "meter1";
	private static final String MQTT_BRIDGE_ID = "mqtt0";
	private static final String TOPIC = "zevplus/c0001/wwg/meter";

	// Real-world example payload as observed on the broker
	private static String payload(double powerIn, double powerOut, double energyIn, double energyOut, //
			Integer voltageL1, Integer voltageL2, Integer voltageL3, String meterId) {
		var voltages = new StringBuilder();
		if (voltageL1 != null) {
			voltages.append(",\"voltage_l1\":").append(voltageL1);
		}
		if (voltageL2 != null) {
			voltages.append(",\"voltage_l2\":").append(voltageL2);
		}
		if (voltageL3 != null) {
			voltages.append(",\"voltage_l3\":").append(voltageL3);
		}
		return """
				{"sys_id":"ECC9FF5C46E8","meter_id":"%s","time":"2026-08-18T21:08:50Z",\
				"power_in":%s,"power_out":%s,"energy_in":%s,"energy_out":%s%s}\
				""".formatted(meterId, powerIn, powerOut, energyIn, energyOut, voltages);
	}

	private static String payload(double powerIn, double powerOut, double energyIn, double energyOut) {
		return payload(powerIn, powerOut, energyIn, energyOut, 234, 235, 235, "");
	}

	@Test
	public void testSubscribesToConfiguredTopic() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase() //
						.onBeforeProcessImage(() -> assertTrue(mqttBridge.isSubscribed(TOPIC)))) //
				.deactivate();
	}

	@Test
	public void testFullPayloadMapping() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("pure consumption, real-world example payload") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0.604, 0, 4751.93, 9462.223))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 604) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 234000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 235000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L3, 235000) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 4751930L) //
						.output(ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY, 9462223L) //
						.output(MqttComponent.ChannelId.MQTT_COMMUNICATION_FAILED, false)) //
				.deactivate();
	}

	@Test
	public void testFeedInYieldsNegativeActivePower() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("pure feed-in") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0, 0.186, 4751.93, 9462.223))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, -186)) //
				.deactivate();
	}

	@Test
	public void testEmptyMeterIdDoesNotFail() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("empty meter_id, as delivered by this meter type") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0.014, 0, 4751.928, 9462.223, 234, 234, 235, ""))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 14)) //
				.deactivate();
	}

	@Test
	public void testMissingVoltagePhaseLeavesOnlyThatChannelUndefined() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("voltage_l3 missing from the template") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0.604, 0, 4751.93, 9462.223, 234, 235, null, ""))) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 234000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L2, 235000) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L3, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 604)) //
				.deactivate();
	}

	@Test
	public void testMissingPowerInLeavesActivePowerUndefined() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("only power_out present, power_in missing entirely") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC, """
								{"sys_id":"ECC9FF5C46E8","meter_id":"","time":"2026-08-18T21:08:50Z",\
								"power_out":0,"energy_in":4751.93,"energy_out":9462.223,\
								"voltage_l1":234,"voltage_l2":235,"voltage_l3":235}""")) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, null) //
						.output(ElectricityMeter.ChannelId.VOLTAGE_L1, 234000)) //
				.deactivate();
	}

	@Test
	public void testMalformedPayloadDoesNotCrash() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("not valid JSON at all") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC, "not-json")) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, null)) //
				.next(new TestCase("a later, valid message still works") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0.604, 0, 4751.93, 9462.223))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 604)) //
				.deactivate();
	}

	@Test
	public void testNoArtificialPerPhasePowerOrEnergySplit() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("device only reports combined totals, never per phase") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0.604, 0, 4751.93, 9462.223))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L1, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L2, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER_L3, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L1, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L2, null) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY_L3, null)) //
				.deactivate();
	}

	@Test
	public void testEnergyIsTakenDirectlyFromMeterRegisterNotIntegrated() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttWhatWattImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.next(new TestCase("first reading") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0.014, 0, 4751.928, 9462.223))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 4751928L)) //
				.next(new TestCase("next reading after a simulated gap - jumps straight to the new "
						+ "register value, no drift from the skipped interval") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC,
								payload(0.604, 0, 4751.930, 9462.223))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY, 4751930L)) //
				.deactivate();
	}

	@Test
	public void testAddToSumDefaultsToFalse() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);
		var sut = new MeterMqttWhatWattImpl();

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopic(TOPIC) //
						.build()) //
				.deactivate();

		assertTrue(!sut.addToSum());
	}

}
