package io.openems.edge.io.gpio.hardware;

import java.util.List;

import io.openems.edge.common.channel.ChannelId;
import io.openems.edge.io.gpio.api.ReadChannelId;
import io.openems.edge.io.gpio.api.WriteChannelId;
import io.openems.edge.io.gpio.linuxfs.HardwareFactory;

public final class ModberryX500M41601WbMax extends ModBerryX500 {

	private final List<ChannelId> channelIds = List.of(//
			new ReadChannelId(586, "DIGITAL_INPUT_1"), //
			new ReadChannelId(587, "DIGITAL_INPUT_2"), //
			new ReadChannelId(588, "DIGITAL_INPUT_3"), //
			new ReadChannelId(589, "DIGITAL_INPUT_4"), //
			
			new WriteChannelId(578, "DIGITAL_OUTPUT_1"), //
			new WriteChannelId(579, "DIGITAL_OUTPUT_2"), //
			new WriteChannelId(580, "DIGITAL_OUTPUT_3"), //
			new WriteChannelId(581, "DIGITAL_OUTPUT_4"), //
			
			// Configurable I/Os (DIO 1-4) - configured as outputs only.
			// To use as inputs, the operating mode must be changed via the npe application
			// (e.g. "npe IDIOconf1" for DIO1/2, "npe IDIOconf2" for DIO3/4).
			// Bidirectional support can be added as a future feature.
			new WriteChannelId(582, "DIGITAL_INPUT_OUTPUT_1"), //
			new WriteChannelId(583, "DIGITAL_INPUT_OUTPUT_2"), //
			new WriteChannelId(584, "DIGITAL_INPUT_OUTPUT_3"), //
			new WriteChannelId(585, "DIGITAL_INPUT_OUTPUT_4") //

	);

	public ModberryX500M41601WbMax(HardwareFactory context) {
		super(context);
	}

	@Override
	public List<ChannelId> getAllChannelIds() {
		return this.channelIds;
	}
}
