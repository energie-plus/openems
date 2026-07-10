package io.openems.edge.meter.mqtt.shelly;

import static io.openems.common.utils.JsonUtils.getAsFloat;
import static io.openems.common.utils.JsonUtils.getAsJsonObject;
import static io.openems.common.utils.JsonUtils.parseToJsonObject;
import static java.lang.Math.round;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.bridge.mqtt.api.MqttMessage;
import io.openems.edge.meter.mqtt.api.MeterSample;
import io.openems.edge.meter.mqtt.api.MqttMeterAdapter;

/**
 * Adapter for Shelly Gen2 (RPC-firmware) energy-monitoring devices, e.g. Shelly Pro EM-50.
 *
 * <p>
 * Reads instantaneous values from {@code <topicPrefix>/events/rpc} NotifyStatus events (one
 * {@code em1:<channel>} sub-object per monitored channel) and the device's online/offline
 * state from {@code <topicPrefix>/online}.
 */
public class ShellyGen2Adapter implements MqttMeterAdapter {

	private static final String TOPIC_EVENTS_RPC = "events/rpc";
	private static final String TOPIC_ONLINE = "online";

	private final int channel;

	public ShellyGen2Adapter(int channel) {
		this.channel = channel;
	}

	@Override
	public String getTopicFilter(String topicPrefix) {
		return topicPrefix + "/#";
	}

	@Override
	public MeterSample parseMessage(MqttMessage message, String topicPrefix) {
		var subTopic = message.topic().substring(topicPrefix.length() + 1);
		switch (subTopic) {
		case TOPIC_ONLINE -> {
			return new MeterSample(null, null, null, null, Boolean.parseBoolean(message.payloadAsString()));
		}
		case TOPIC_EVENTS_RPC -> {
			return this.parseEventsRpc(message.payloadAsString());
		}
		default -> {
			return MeterSample.EMPTY;
		}
		}
	}

	private MeterSample parseEventsRpc(String payload) {
		try {
			var root = parseToJsonObject(payload);
			var params = getAsJsonObject(root, "params");
			var em1 = getAsJsonObject(params, "em1:" + this.channel);

			// Shelly's act_power sign convention (negative = feed-in) already matches
			// OpenEMS' convention (positive = consumption/import, negative = feed-in), so no
			// device-family normalization is needed here. Per-installation wiring issues are
			// handled by the Meter's "invert" Config flag instead.
			var activePower = round(getAsFloat(em1, "act_power"));
			var current = round(getAsFloat(em1, "current") * 1000);
			var voltage = round(getAsFloat(em1, "voltage") * 1000);
			var frequency = round(getAsFloat(em1, "freq") * 1000);

			return new MeterSample(activePower, voltage, current, frequency, null);
		} catch (OpenemsNamedException e) {
			// e.g. a NotifyStatus event that does not carry this channel - not an error
			return MeterSample.EMPTY;
		}
	}

}
