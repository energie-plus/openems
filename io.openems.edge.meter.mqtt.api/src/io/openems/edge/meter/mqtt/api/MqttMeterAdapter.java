package io.openems.edge.meter.mqtt.api;

import io.openems.edge.bridge.mqtt.api.MqttMessage;

/**
 * Translates the MQTT topic layout and payload format of one device family (e.g. Shelly Gen2
 * RPC devices) into the neutral {@link MeterSample} model.
 *
 * <p>
 * Implementations are instantiated by the Meter component with whatever device-specific
 * settings they need (e.g. which sub-channel of a multi-channel device to read), so this
 * interface itself stays free of device-family-specific Config.
 *
 * <p>
 * See the {@code readme.adoc} of this bundle for how to add a new device family.
 */
public interface MqttMeterAdapter {

	/**
	 * Gets the MQTT topic filter to subscribe to.
	 *
	 * @param topicPrefix the device's topic prefix (usually equal to its MQTT username)
	 * @return the topic filter, e.g. {@code topicPrefix + "/#"}
	 */
	String getTopicFilter(String topicPrefix);

	/**
	 * Parses one incoming MQTT message.
	 *
	 * @param message     the received {@link MqttMessage}
	 * @param topicPrefix the device's topic prefix, as passed to {@link #getTopicFilter(String)}
	 * @return the parsed {@link MeterSample}; {@link MeterSample#EMPTY} if the message is not
	 *         relevant (e.g. an unrelated sub-topic)
	 */
	MeterSample parseMessage(MqttMessage message, String topicPrefix);

}
