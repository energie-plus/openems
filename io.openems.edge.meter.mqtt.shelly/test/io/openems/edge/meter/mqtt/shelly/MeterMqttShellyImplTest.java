package io.openems.edge.meter.mqtt.shelly;

import static io.openems.common.types.MeterType.CONSUMPTION_METERED;
import static io.openems.common.types.MeterType.GRID;
import static io.openems.common.types.MeterType.PRODUCTION;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.edge.bridge.mqtt.api.MqttComponent;
import io.openems.edge.bridge.mqtt.test.DummyBridgeMqtt;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.ComponentTest;
import io.openems.edge.common.type.Phase.SinglePhase;
import io.openems.edge.meter.api.ElectricityMeter;

public class MeterMqttShellyImplTest {

	private static final String COMPONENT_ID = "meter0";
	private static final String MQTT_BRIDGE_ID = "mqtt0";
	private static final String TOPIC_PREFIX = "shelly01";

	// Real-world example payload as observed on the broker
	private static String notifyStatus(int channel, double actPower, double current, double voltage, double freq) {
		return """
				{"src":"shellyproem50-ece334f78680","dst":"%s/events","method":"NotifyStatus",\
				"params":{"ts":1783258365.73,"em1:%d":{"id":%d,"act_power":%.1f,"aprt_power":143.0,\
				"current":%.3f,"freq":%.1f,"pf":0.95,"voltage":%.1f}}}\
				""".formatted(TOPIC_PREFIX, channel, channel, actPower, current, freq, voltage);
	}

	@Test
	public void testSubscribesToDeviceTopic() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttShellyImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopicPrefix(TOPIC_PREFIX) //
						.build()) //
				.next(new TestCase() //
						.onBeforeProcessImage(() -> assertTrue(mqttBridge.isSubscribed(TOPIC_PREFIX + "/#")))) //
				.deactivate();
	}

	@Test
	public void testEventsRpcMessageChannel0() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttShellyImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopicPrefix(TOPIC_PREFIX) //
						.setChannel(0) //
						.setPhase(SinglePhase.L1) //
						.setType(GRID) //
						.build()) //
				.next(new TestCase("feed-in via em1:0") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC_PREFIX + "/events/rpc",
								notifyStatus(0, -134.7, 0.607, 234.2, 50.0))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, -135) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 234200) //
						.output(ElectricityMeter.ChannelId.CURRENT, 607) //
						.output(ElectricityMeter.ChannelId.FREQUENCY, 50000) //
						.output(MqttComponent.ChannelId.MQTT_COMMUNICATION_FAILED, false)) //
				.deactivate();
	}

	@Test
	public void testIgnoresMessageForOtherChannel() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttShellyImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopicPrefix(TOPIC_PREFIX) //
						.setChannel(1) // configured for em1:1, message below only has em1:0
						.build()) //
				.next(new TestCase("em1:0 present, but channel 1 configured") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC_PREFIX + "/events/rpc",
								notifyStatus(0, -134.7, 0.607, 234.2, 50.0))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, null)) //
				.deactivate();
	}

	@Test
	public void testInvert() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttShellyImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopicPrefix(TOPIC_PREFIX) //
						.setChannel(0) //
						.setInvert(true) //
						.build()) //
				.next(new TestCase("invert flips power and current, not voltage") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC_PREFIX + "/events/rpc",
								notifyStatus(0, -134.7, 0.607, 234.2, 50.0))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 135) //
						.output(ElectricityMeter.ChannelId.CURRENT, -607) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, 234200)) //
				.deactivate();
	}

	@Test
	public void testOfflineInvalidatesChannels() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttShellyImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopicPrefix(TOPIC_PREFIX) //
						.setChannel(0) //
						.setType(CONSUMPTION_METERED) //
						.build()) //
				.next(new TestCase("device reports a measurement") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC_PREFIX + "/events/rpc",
								notifyStatus(0, 50.0, 0.2, 230.0, 50.0))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 50)) //
				.next(new TestCase("device goes offline") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC_PREFIX + "/online", "false")) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, null) //
						.output(ElectricityMeter.ChannelId.VOLTAGE, null) //
						.output(ElectricityMeter.ChannelId.CURRENT, null) //
						.output(ElectricityMeter.ChannelId.FREQUENCY, null) //
						.output(MqttComponent.ChannelId.MQTT_COMMUNICATION_FAILED, true)) //
				.next(new TestCase("device comes back online with a fresh measurement") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC_PREFIX + "/events/rpc",
								notifyStatus(0, 60.0, 0.3, 231.0, 50.0))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, 60) //
						.output(MqttComponent.ChannelId.MQTT_COMMUNICATION_FAILED, false)) //
				.deactivate();
	}

	@Test
	public void testMeterTypeProductionUsesAbsoluteEnergy() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);

		new ComponentTest(new MeterMqttShellyImpl()) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopicPrefix(TOPIC_PREFIX) //
						.setChannel(0) //
						.setType(PRODUCTION) //
						.build()) //
				.next(new TestCase("production channel reports negative (feed-in) power") //
						.onBeforeProcessImage(() -> mqttBridge.simulateMessage(TOPIC_PREFIX + "/events/rpc",
								notifyStatus(0, -300.0, 1.3, 230.0, 50.0))) //
						.output(ElectricityMeter.ChannelId.ACTIVE_POWER, -300)) //
				.deactivate();
	}

	@Test
	public void testAddToSumDefaultsToFalse() throws Exception {
		var mqttBridge = new DummyBridgeMqtt(MQTT_BRIDGE_ID);
		var sut = new MeterMqttShellyImpl();

		new ComponentTest(sut) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("setMqtt", mqttBridge) //
				.activate(MyConfig.create() //
						.setId(COMPONENT_ID) //
						.setMqttId(MQTT_BRIDGE_ID) //
						.setTopicPrefix(TOPIC_PREFIX) //
						.build()) //
				.deactivate();

		assertTrue(!sut.addToSum());
	}

}
