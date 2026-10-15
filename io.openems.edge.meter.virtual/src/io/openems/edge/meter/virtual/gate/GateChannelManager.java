package io.openems.edge.meter.virtual.gate;

import java.util.function.Consumer;

import io.openems.edge.common.channel.AbstractChannelListenerManager;
import io.openems.edge.common.channel.IntegerReadChannel;
import io.openems.edge.common.channel.value.Value;
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
	 * @param meter      the gated {@link ElectricityMeter}
	 * @param ess        the {@link SymmetricEss} providing the State-of-Charge
	 * @param reserveSoc the reserve State-of-Charge threshold [%]
	 */
	protected void activate(ElectricityMeter meter, SymmetricEss ess, int reserveSoc) {
		for (var channelId : GATED_CHANNELS) {
			this.forward(meter, ess, reserveSoc, channelId);
		}
	}

	private void forward(ElectricityMeter meter, SymmetricEss ess, int reserveSoc,
			ElectricityMeter.ChannelId channelId) {
		final Consumer<Value<Integer>> callback = (ignore) -> {
			IntegerReadChannel socChannel = ess.channel(SymmetricEss.ChannelId.SOC);
			Integer soc = socChannel.getNextValue().get();
			var isGateClosed = soc != null && soc <= reserveSoc;
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
}
