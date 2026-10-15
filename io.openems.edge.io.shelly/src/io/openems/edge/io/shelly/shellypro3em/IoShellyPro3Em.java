package io.openems.edge.io.shelly.shellypro3em;

import io.openems.common.channel.Level;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.io.shelly.common.component.ShellyEnergyMeter;
import io.openems.edge.io.shelly.common.gen2.IoGen2ShellyBase;

public interface IoShellyPro3Em extends IoGen2ShellyBase, ShellyEnergyMeter {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {
		/**
		 * The energy script on the Shelly is not reachable, not ready or returns
		 * invalid data. Only relevant with energy source SCRIPT_ENERGY_ENDPOINT.
		 *
		 * <ul>
		 * <li>Type: State
		 * </ul>
		 */
		ENERGY_SCRIPT_UNAVAILABLE(Doc.of(Level.WARNING) //
				.text("Energy script on the Shelly is not reachable, not ready or returned invalid data")),
		;

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

}
