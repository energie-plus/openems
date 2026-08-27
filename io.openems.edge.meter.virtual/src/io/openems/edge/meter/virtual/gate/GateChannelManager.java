package io.openems.edge.meter.virtual.gate;

import java.util.function.Consumer;

import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.meter.api.ElectricityMeter;

public class GateChannelManager extends AbstractChannelListenerManager {

	private static final ElectricityMeter.ChannelId[] GATED_CHANNELS = { //
			ElectricityMeter.ChannelId.ACTIVE_POWER, //
			ElectricityMeter.ChannelId.ACTIVE_POWER_L1, //
			ElectricityMeter.ChannelId.ACTIVE_POWER_L2, //
			ElectricityMeter.ChannelId.ACTIVE_POWER_L3, //
			ElectricityMeter.ChannelId.REACTIVE_POWER, //
			ElectricityMeter.ChannelId.REACTIVE_POWER_L1, //
			ElectricityMeter.ChannelId.REACTIVE_POWER_L2, //
			ElectricityMeter.ChannelId.REACTIVE_POWER_L3, //
	};

	private final MeterVirtualGate parent;

	public GateChannelManager(MeterVirtualGate parent) {
		this.parent = parent;
	}

	/**
	 * Called on Component activate().
	 *
	 * @param meter                    the gated {@link ElectricityMeter}
	 * @param ess                      the {@link SymmetricEss} providing the
	 *                                 State-of-Charge
	 * @param fixedReserveSoc          the fixed reserve State-of-Charge threshold
	 *                                 [%], used when {@code reserveSocChannelAddress}
	 *                                 is {@code null} or currently undefined
	 * @param reserveSocChannelAddress optional {@link ChannelAddress} of a Channel
	 *                                 providing a dynamic reserve threshold [%];
	 *                                 {@code null} to always use
	 *                                 {@code fixedReserveSoc}
	 * @param hysteresis               once the gate closes, SoC must rise this
	 *                                 many percentage points above the effective
	 *                                 threshold before it reopens
	 * @param componentManager         used to resolve {@code reserveSocChannelAddress}
	 *                                 freshly on every cycle
	 */
	protected void activate(ElectricityMeter meter, SymmetricEss ess, int fixedReserveSoc,
			ChannelAddress reserveSocChannelAddress, int hysteresis, ComponentManager componentManager) {
		for (var channelId : GATED_CHANNELS) {
			this.forward(meter, ess, fixedReserveSoc, reserveSocChannelAddress, hysteresis, componentManager,
					channelId);
		}
	}

	private void forward(ElectricityMeter meter, SymmetricEss ess, int fixedReserveSoc,
			ChannelAddress reserveSocChannelAddress, int hysteresis, ComponentManager componentManager,
			ElectricityMeter.ChannelId channelId) {
		final Consumer<Value<Integer>> callback = (ignore) -> {
			var effectiveReserveSoc = this.resolveEffectiveReserveSoc(fixedReserveSoc, reserveSocChannelAddress,
					componentManager);

			IntegerReadChannel socChannel = ess.channel(SymmetricEss.ChannelId.SOC);
			Integer soc = socChannel.getNextValue().get();
			boolean isGateClosed;
			if (soc == null) {
				// Unchanged legacy behavior: unknown SoC -> gate stays open (pass through).
				isGateClosed = false;
			} else {
				// Hysteresis derived from the gate's own previous state, not a fixed band
				// around the threshold - this absorbs jitter both in SoC and in a dynamic
				// threshold, without needing extra state beyond GATE_CLOSED itself.
				var wasClosed = this.parent.getGateClosed().orElse(false);
				var effectiveThreshold = wasClosed ? effectiveReserveSoc + hysteresis : effectiveReserveSoc;
				isGateClosed = soc <= effectiveThreshold;
			}
			this.parent._setGateClosed(isGateClosed);

			IntegerReadChannel inputChannel = meter.channel(channelId);
			Integer value = inputChannel.getNextValue().get();
			Integer result;
			if (value == null) {
				result = null;
			} else if (isGateClosed) {
				result = 0;
			} else {
				result = value;
			}

			IntegerReadChannel outputChannel = this.parent.channel(channelId);
			outputChannel.setNextValue(result);
		};

		this.addOnSetNextValueListener(meter, channelId, callback);
		this.addOnSetNextValueListener(ess, SymmetricEss.ChannelId.SOC, callback);
	}

	/**
	 * Resolves the reserve SoC threshold to use for this cycle: the value of
	 * {@code reserveSocChannelAddress} if configured and defined, otherwise
	 * {@code fixedReserveSoc}.
	 */
	private int resolveEffectiveReserveSoc(int fixedReserveSoc, ChannelAddress reserveSocChannelAddress,
			ComponentManager componentManager) {
		if (reserveSocChannelAddress == null) {
			return fixedReserveSoc;
		}
		try {
			IntegerReadChannel dynamicChannel = componentManager.getChannel(reserveSocChannelAddress);
			// Use getNextValue(), not value(): the referenced component may not have run
			// its own process image update in this cycle yet, in which case value()
			// stays undefined forever in some contexts (see gate test-framework learnings).
			Integer dynamicValue = dynamicChannel.getNextValue().get();
			this.parent._setReserveSocSourceUnavailable(dynamicValue == null);
			return dynamicValue != null ? dynamicValue : fixedReserveSoc;
		} catch (Exception e) {
			this.parent._setReserveSocSourceUnavailable(true);
			return fixedReserveSoc;
		}
	}
}
