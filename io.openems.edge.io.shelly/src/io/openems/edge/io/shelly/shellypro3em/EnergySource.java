package io.openems.edge.io.shelly.shellypro3em;

/**
 * Where the energy counters of the Shelly Pro 3EM come from.
 */
public enum EnergySource {
	/**
	 * Energy is integrated on the Edge from the measured active power.
	 */
	POWER_INTEGRATION,
	/**
	 * Energy counters are read from a net-metering script that runs on the Shelly
	 * and serves them at '/script/&lt;id&gt;/energy'.
	 */
	SCRIPT_ENERGY_ENDPOINT
}
