package io.openems.edge.meter.mqtt.whatwatt;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.types.MeterType;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String mqttId = "mqtt0";
		private String topic = "zevplus/c0001/wwg/meter";
		private MeterType type = MeterType.GRID;
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

		public Builder setTopic(String topic) {
			this.topic = topic;
			return this;
		}

		public Builder setType(MeterType type) {
			this.type = type;
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
	public String topic() {
		return this.builder.topic;
	}

	@Override
	public MeterType type() {
		return this.builder.type;
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
