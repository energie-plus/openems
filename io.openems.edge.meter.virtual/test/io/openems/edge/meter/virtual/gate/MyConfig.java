package io.openems.edge.meter.virtual.gate;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.types.MeterType;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private MeterType type;
		private boolean addToSum;
		private String meterId;
		private String essId;
		private int reserveSoc;
		private String reserveSocChannelAddress = "";
		private int hysteresis;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
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

		public Builder setMeterId(String meterId) {
			this.meterId = meterId;
			return this;
		}

		public Builder setEssId(String essId) {
			this.essId = essId;
			return this;
		}

		public Builder setReserveSoc(int reserveSoc) {
			this.reserveSoc = reserveSoc;
			return this;
		}

		public Builder setReserveSocChannelAddress(String reserveSocChannelAddress) {
			this.reserveSocChannelAddress = reserveSocChannelAddress;
			return this;
		}

		public Builder setHysteresis(int hysteresis) {
			this.hysteresis = hysteresis;
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
	public MeterType type() {
		return this.builder.type;
	}

	@Override
	public boolean addToSum() {
		return this.builder.addToSum;
	}

	@Override
	public String meter_id() {
		return this.builder.meterId;
	}

	@Override
	public String ess_id() {
		return this.builder.essId;
	}

	@Override
	public int reserveSoc() {
		return this.builder.reserveSoc;
	}

	@Override
	public String reserveSocChannelAddress() {
		return this.builder.reserveSocChannelAddress;
	}

	@Override
	public int hysteresis() {
		return this.builder.hysteresis;
	}

	@Override
	public String meter_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.meter_id());
	}

	@Override
	public String ess_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.ess_id());
	}

}
