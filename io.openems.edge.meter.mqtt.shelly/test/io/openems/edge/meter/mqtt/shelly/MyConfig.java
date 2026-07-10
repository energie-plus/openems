package io.openems.edge.meter.mqtt.shelly;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.types.MeterType;
import io.openems.common.utils.ConfigUtils;
import io.openems.edge.common.type.Phase.SinglePhase;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String mqttId = "mqtt0";
		private String topicPrefix = "shelly01";
		private int channel = 0;
		private SinglePhase phase = SinglePhase.L1;
		private MeterType type = MeterType.GRID;
		private boolean invert = false;
		private boolean addToSum = false;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setMqttId(String mqttId) {
			this.mqttId = mqttId;
			return this;
		}

		public Builder setTopicPrefix(String topicPrefix) {
			this.topicPrefix = topicPrefix;
			return this;
		}

		public Builder setChannel(int channel) {
			this.channel = channel;
			return this;
		}

		public Builder setPhase(SinglePhase phase) {
			this.phase = phase;
			return this;
		}

		public Builder setType(MeterType type) {
			this.type = type;
			return this;
		}

		public Builder setInvert(boolean invert) {
			this.invert = invert;
			return this;
		}

		public Builder setAddToSum(boolean addToSum) {
			this.addToSum = addToSum;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String mqtt_id() {
		return this.builder.mqttId;
	}

	@Override
	public String topicPrefix() {
		return this.builder.topicPrefix;
	}

	@Override
	public int channel() {
		return this.builder.channel;
	}

	@Override
	public SinglePhase phase() {
		return this.builder.phase;
	}

	@Override
	public MeterType type() {
		return this.builder.type;
	}

	@Override
	public boolean invert() {
		return this.builder.invert;
	}

	@Override
	public boolean addToSum() {
		return this.builder.addToSum;
	}

	@Override
	public String Mqtt_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.mqtt_id());
	}

}
