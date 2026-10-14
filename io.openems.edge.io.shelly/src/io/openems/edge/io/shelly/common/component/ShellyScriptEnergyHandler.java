package io.openems.edge.io.shelly.common.component;

import static io.openems.common.utils.JsonUtils.getAsBoolean;
import static io.openems.common.utils.JsonUtils.getAsInt;
import static io.openems.common.utils.JsonUtils.getAsLong;

import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.edge.meter.api.ElectricityMeter;

/**
 * Applies the energy counters served by a net-metering script that runs on the
 * Shelly device.
 *
 * <p>
 * Expected payload of {@code /script/<id>/energy}:
 *
 * <pre>
 * {"v":1,"ready":true,"import_wh":201795,"export_wh":173725,"uptime_ms":123456}
 * </pre>
 *
 * <p>
 * Import is the net energy drawn from the grid and maps to
 * ActiveProductionEnergy, export maps to ActiveConsumptionEnergy - the same
 * convention as the energy integrated from ActivePower. With 'invert' both are
 * swapped.
 */
public class ShellyScriptEnergyHandler {

	/** Payload version understood by this handler. */
	public static final int SUPPORTED_VERSION = 1;

	private final ElectricityMeter component;
	private final boolean shouldInvert;

	/*
	 * The script counters fall back to their last saved value if the device lost
	 * power. Only values that do not decrease are published, so the Channels stay
	 * steadily increasing. The held values are kept in memory only: after a restart
	 * of this Component the first reading of the script is taken as it is.
	 */
	private Long heldImportWh = null;
	private Long heldExportWh = null;

	public ShellyScriptEnergyHandler(ElectricityMeter component, boolean shouldInvert) {
		this.component = component;
		this.shouldInvert = shouldInvert;
	}

	/**
	 * Applies one response of the energy script.
	 *
	 * @param json the parsed response
	 * @return true if the energy values were applied; false if the script reports
	 *         that it is not ready (no valid start value loaded yet)
	 * @throws OpenemsNamedException on missing fields, invalid values or an
	 *                               unsupported payload version
	 */
	public boolean process(JsonObject json) throws OpenemsNamedException {
		final var version = getAsInt(json, "v");
		if (version != SUPPORTED_VERSION) {
			throw new OpenemsException("Unsupported energy script payload version [" + version + "]");
		}
		if (!getAsBoolean(json, "ready")) {
			return false;
		}

		final var importWh = getAsLong(json, "import_wh");
		final var exportWh = getAsLong(json, "export_wh");
		if (importWh < 0 || exportWh < 0) {
			throw new OpenemsException(
					"Invalid energy script values: import [" + importWh + "] export [" + exportWh + "]");
		}

		this.heldImportWh = this.heldImportWh == null ? importWh : Math.max(this.heldImportWh, importWh);
		this.heldExportWh = this.heldExportWh == null ? exportWh : Math.max(this.heldExportWh, exportWh);

		if (this.shouldInvert) {
			this.component._setActiveConsumptionEnergy(this.heldImportWh);
			this.component._setActiveProductionEnergy(this.heldExportWh);
		} else {
			this.component._setActiveProductionEnergy(this.heldImportWh);
			this.component._setActiveConsumptionEnergy(this.heldExportWh);
		}
		return true;
	}
}
