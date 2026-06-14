package io.openems.edge.io.gpio.hardware;

import java.util.List;

import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.io.gpio.api.ReadChannelId;
import io.openems.edge.io.gpio.api.WriteChannelId;
import io.openems.edge.io.gpio.linuxfs.HardwareFactory;

public final class ModberryX500M41601WbMax extends ModBerryX500 {

	private final List<ChannelId> channelIds = List.of(//
			new ReadChannelId(530, "DIGITAL_INPUT_1"), //
			new ReadChannelId(531, "DIGITAL_INPUT_2"), //
			new ReadChannelId(532, "DIGITAL_INPUT_3"), //
			new ReadChannelId(533, "DIGITAL_INPUT_4"), //

			new WriteChannelId(534, "DIGITAL_OUTPUT_1"), //
			new WriteChannelId(535, "DIGITAL_OUTPUT_2"), //
			new WriteChannelId(536, "DIGITAL_OUTPUT_3"), //
			new WriteChannelId(537, "DIGITAL_OUTPUT_4"), //

			new WriteChannelId(578, "DIGITAL_INPUT_OUTPUT_1_OUT"), //
			new WriteChannelId(579, "DIGITAL_INPUT_OUTPUT_2_OUT"), //
			new WriteChannelId(580, "DIGITAL_INPUT_OUTPUT_3_OUT"), //
			new WriteChannelId(581, "DIGITAL_INPUT_OUTPUT_4_OUT"), //

			// DIO 1-4 are bidirectional pins (see hardware layout).
			// All four pins are currently configured as output (direction=out),
			// so the following read channels simply return the last value written
			// to the output, not an external input signal.
			// To use a DIO pin as input, its direction must be set manually to "in"
			// via /sys/class/gpio/gpioXXX/direction.
			new ReadChannelId(582, "DIGITAL_INPUT_OUTPUT_1_IN"), //
			new ReadChannelId(583, "DIGITAL_INPUT_OUTPUT_2_IN"), //
			new ReadChannelId(584, "DIGITAL_INPUT_OUTPUT_3_IN"), //
			new ReadChannelId(585, "DIGITAL_INPUT_OUTPUT_4_IN") //
	);

	public ModberryX500M41601WbMax(HardwareFactory context) {
		super(context);
	}

	@Override
	public List<ChannelId> getAllChannelIds() {
		return this.channelIds;
	}
}