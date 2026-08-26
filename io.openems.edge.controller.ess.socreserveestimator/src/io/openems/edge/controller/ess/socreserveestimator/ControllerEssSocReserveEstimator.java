package io.openems.edge.controller.ess.socreserveestimator;

import static io.openems.common.channel.Unit.NONE;
import static io.openems.common.channel.Unit.PERCENT;
import static io.openems.common.channel.Unit.WATT_HOURS;
import static io.openems.common.types.OpenemsType.INTEGER;
import static io.openems.common.types.OpenemsType.LONG;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.LongReadChannel;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

public interface ControllerEssSocReserveEstimator extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Energy that needs to stay reserved for own consumption until the reserve
		 * window ends, including safety margin and End-SoC Reserve.
		 */
		REQUIRED_RESERVE_ENERGY(Doc.of(INTEGER) //
				.unit(WATT_HOURS) //
				.text("Energy that needs to stay reserved for own consumption, incl. safety margin and End-SoC Reserve")), //

		/**
		 * The dynamically calculated Min-SoC, converted from
		 * {@link #REQUIRED_RESERVE_ENERGY} via the Ess capacity and clamped to the
		 * configured range.
		 */
		CALCULATED_MIN_SOC(Doc.of(INTEGER) //
				.unit(PERCENT) //
				.text("Dynamically calculated minimum State-of-Charge reserve")), //

		/**
		 * Point in time (epoch millis) up to which the reserve is calculated, i.e.
		 * when PV production is expected to exceed the configured threshold again.
		 */
		RESERVE_HORIZON(Doc.of(LONG) //
				.unit(NONE) //
				.text("Point in time (epoch millis) up to which the reserve is calculated")), //

		/**
		 * Set if the production or consumption forecast was missing or incomplete,
		 * so a fallback was used for the horizon and/or parts of the energy balance.
		 */
		PREDICTION_INCOMPLETE(Doc.of(Level.WARNING) //
				.text("Production or consumption forecast was missing or incomplete, a fallback was used")), //

		/**
		 * Set if the calculated Min-SoC was outside the configured clamp range and
		 * had to be adjusted.
		 */
		MIN_SOC_CLAMPED(Doc.of(Level.INFO) //
				.text("Calculated Min-SoC was outside the configured clamp range and had to be adjusted")); //

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#REQUIRED_RESERVE_ENERGY}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getRequiredReserveEnergyChannel() {
		return this.channel(ChannelId.REQUIRED_RESERVE_ENERGY);
	}

	/**
	 * Gets the required reserve energy in [Wh]. See
	 * {@link ChannelId#REQUIRED_RESERVE_ENERGY}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getRequiredReserveEnergy() {
		return this.getRequiredReserveEnergyChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#REQUIRED_RESERVE_ENERGY} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setRequiredReserveEnergy(Integer value) {
		this.getRequiredReserveEnergyChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CALCULATED_MIN_SOC}.
	 *
	 * @return the Channel
	 */
	public default IntegerReadChannel getCalculatedMinSocChannel() {
		return this.channel(ChannelId.CALCULATED_MIN_SOC);
	}

	/**
	 * Gets the dynamically calculated Min-SoC in [%]. See
	 * {@link ChannelId#CALCULATED_MIN_SOC}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Integer> getCalculatedMinSoc() {
		return this.getCalculatedMinSocChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#CALCULATED_MIN_SOC} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setCalculatedMinSoc(Integer value) {
		this.getCalculatedMinSocChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#RESERVE_HORIZON}.
	 *
	 * @return the Channel
	 */
	public default LongReadChannel getReserveHorizonChannel() {
		return this.channel(ChannelId.RESERVE_HORIZON);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#RESERVE_HORIZON}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setReserveHorizon(Long value) {
		this.getReserveHorizonChannel().setNextValue(value);
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#PREDICTION_INCOMPLETE} Channel.
	 *
	 * @param value the next value
	 */
	public default void _setPredictionIncomplete(boolean value) {
		this.channel(ChannelId.PREDICTION_INCOMPLETE).setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#PREDICTION_INCOMPLETE}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getPredictionIncompleteChannel() {
		return this.channel(ChannelId.PREDICTION_INCOMPLETE);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#MIN_SOC_CLAMPED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setMinSocClamped(boolean value) {
		this.channel(ChannelId.MIN_SOC_CLAMPED).setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#MIN_SOC_CLAMPED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getMinSocClampedChannel() {
		return this.channel(ChannelId.MIN_SOC_CLAMPED);
	}

}
