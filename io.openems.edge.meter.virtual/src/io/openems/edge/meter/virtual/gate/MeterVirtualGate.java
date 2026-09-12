package io.openems.edge.meter.virtual.gate;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.StateChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.modbusslave.ModbusSlave;
import io.openems.edge.common.sum.SumOptions;
import io.openems.edge.meter.api.ElectricityMeter;

public interface MeterVirtualGate extends ElectricityMeter, OpenemsComponent, ModbusSlave, SumOptions {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		GATE_CLOSED(Doc.of(Level.INFO) //
				.text("Gated Meter is suppressed to zero because Ess State-of-Charge is at or below the reserve threshold")); //

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
	 * Gets the Channel for {@link ChannelId#GATE_CLOSED}.
	 *
	 * @return the Channel
	 */
	public default StateChannel getGateClosedChannel() {
		return this.channel(ChannelId.GATE_CLOSED);
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#GATE_CLOSED}
	 * Channel.
	 *
	 * @param value the next value
	 */
	public default void _setGateClosed(boolean value) {
		this.getGateClosedChannel().setNextValue(value);
	}

	/**
	 * Gets whether the gate is currently closed. See
	 * {@link ChannelId#GATE_CLOSED}.
	 *
	 * @return the Channel {@link Value}
	 */
	public default Value<Boolean> getGateClosed() {
		return this.getGateClosedChannel().value();
	}
}
