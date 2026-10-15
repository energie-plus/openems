package io.openems.edge.controller.ess.socreserveestimator;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String essId;
		private int productionThreshold = 1000;
		private double safetyMargin = 0.1;
		private int endSocReserve = 10;
		private int minimumDipDurationMinutes = 60;
		private String fallbackHorizonTime = "08:00";
		private int minSocClampLow = 5;
		private int minSocClampHigh = 80;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setEssId(String essId) {
			this.essId = essId;
			return this;
		}

		public Builder setProductionThreshold(int productionThreshold) {
			this.productionThreshold = productionThreshold;
			return this;
		}

		public Builder setSafetyMargin(double safetyMargin) {
			this.safetyMargin = safetyMargin;
			return this;
		}

		public Builder setEndSocReserve(int endSocReserve) {
			this.endSocReserve = endSocReserve;
			return this;
		}

		public Builder setMinimumDipDurationMinutes(int minimumDipDurationMinutes) {
			this.minimumDipDurationMinutes = minimumDipDurationMinutes;
			return this;
		}

		public Builder setFallbackHorizonTime(String fallbackHorizonTime) {
			this.fallbackHorizonTime = fallbackHorizonTime;
			return this;
		}

		public Builder setMinSocClampLow(int minSocClampLow) {
			this.minSocClampLow = minSocClampLow;
			return this;
		}

		public Builder setMinSocClampHigh(int minSocClampHigh) {
			this.minSocClampHigh = minSocClampHigh;
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
	public String ess_id() {
		return this.builder.essId;
	}

	@Override
	public int productionThreshold() {
		return this.builder.productionThreshold;
	}

	@Override
	public double safetyMargin() {
		return this.builder.safetyMargin;
	}

	@Override
	public int endSocReserve() {
		return this.builder.endSocReserve;
	}

	@Override
	public int minimumDipDurationMinutes() {
		return this.builder.minimumDipDurationMinutes;
	}

	@Override
	public String fallbackHorizonTime() {
		return this.builder.fallbackHorizonTime;
	}

	@Override
	public int minSocClampLow() {
		return this.builder.minSocClampLow;
	}

	@Override
	public int minSocClampHigh() {
		return this.builder.minSocClampHigh;
	}

	@Override
	public String ess_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.ess_id());
	}

}
