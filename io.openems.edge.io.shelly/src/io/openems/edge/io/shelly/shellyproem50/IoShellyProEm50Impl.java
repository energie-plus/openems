package io.openems.edge.io.shelly.shellyproem50;

import static io.openems.common.utils.JsonUtils.getAsBoolean;
import static io.openems.common.utils.JsonUtils.getAsFloat;
import static io.openems.common.utils.JsonUtils.getAsJsonObject;
import static io.openems.edge.common.channel.ChannelUtils.setValue;
import static io.openems.edge.common.event.EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE;
import static java.lang.Math.round;
import static org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE;
import static org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL;
import static org.osgi.service.component.annotations.ReferencePolicy.DYNAMIC;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;

import java.util.function.IntFunction;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;

import io.openems.common.bridge.http.api.BridgeHttp;
import io.openems.common.bridge.http.api.BridgeHttpFactory;
import io.openems.common.bridge.http.api.HttpResponse;
import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.http.cycle.HttpBridgeCycleServiceDefinition;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.type.Phase.SinglePhase;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.api.SinglePhaseMeter;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "IO.Shelly.ProEM50", //
		immediate = true, //
		configurationPolicy = REQUIRE)
@EventTopics({ //
		TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
})
public class IoShellyProEm50Impl extends AbstractOpenemsComponent implements IoShellyProEm50, SinglePhaseMeter,
		ElectricityMeter, OpenemsComponent, TimedataProvider, EventHandler {

	private final CalculateEnergyFromPower calculateProductionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);
	private final CalculateEnergyFromPower calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY);

	private final Logger log = LoggerFactory.getLogger(IoShellyProEm50Impl.class);

	private MeterType meterType = null;
	private SinglePhase phase = null;
	private boolean invert = false;
	private String baseUrl;
	private int channel = 0;

	@Reference(policy = DYNAMIC, policyOption = GREEDY, cardinality = OPTIONAL)
	private volatile Timedata timedata;

	@Reference
	private BridgeHttpFactory httpBridgeFactory;

	@Reference
	private HttpBridgeCycleServiceDefinition httpBridgeCycleServiceDefinition;

	private BridgeHttp httpBridge;

	public IoShellyProEm50Impl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				IoShellyProEm50.ChannelId.values() //
		);

		SinglePhaseMeter.calculateSinglePhaseFromActivePower(this);
		SinglePhaseMeter.calculateSinglePhaseFromCurrent(this);
		SinglePhaseMeter.calculateSinglePhaseFromVoltage(this);
	}

	@Activate
	protected void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.meterType = config.type();
		this.phase = config.phase();
		this.invert = config.invert();
		this.channel = config.channel();
		this.baseUrl = "http://" + config.ip();
		this.httpBridge = this.httpBridgeFactory.get();
		final var cycleService = this.httpBridge.createService(this.httpBridgeCycleServiceDefinition);

		if (!this.isEnabled()) {
			return;
		}

		cycleService.subscribeJsonEveryCycle(this.baseUrl + "/rpc/Shelly.GetStatus", this::processHttpResult);
	}

	@Override
	@Deactivate
	protected void deactivate() {
		if (this.httpBridge != null) {
			this.httpBridgeFactory.unget(this.httpBridge);
			this.httpBridge = null;
		}
		super.deactivate();
	}

	@Override
	public String debugLog() {
		return this.getPhase() + ":" + this.getActivePowerChannel().value().asString();
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}

		switch (event.getTopic()) {
		case TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
			-> this.calculateEnergy();
		}
	}

	private void processHttpResult(HttpResponse<JsonElement> result, Throwable error) {
		setValue(this, IoShellyProEm50.ChannelId.SLAVE_COMMUNICATION_FAILED, result == null || error != null);

		final IntFunction<Integer> invert = value -> this.invert ? value * -1 : value;

		Integer power = null;
		Integer voltage = null;
		Integer current = null;
		boolean restartRequired = false;

		if (error != null) {
			this.logDebug(this.log, error.getMessage());

		} else {
			try {
				final var jsonResponse = getAsJsonObject(result.data());

				final var em1 = getAsJsonObject(jsonResponse, "em1:" + this.channel);
				power = invert.apply(round(getAsFloat(em1, "act_power")));
				voltage = round(getAsFloat(em1, "voltage") * 1000);
				current = invert.apply(round(getAsFloat(em1, "current") * 1000));

				final var sys = getAsJsonObject(jsonResponse, "sys");
				restartRequired = getAsBoolean(sys, "restart_required");

			} catch (OpenemsNamedException e) {
				this.logDebug(this.log, e.getMessage());
			}
		}

		this._setActivePower(power);
		this._setCurrent(current);
		this._setVoltage(voltage);

		setValue(this, IoShellyProEm50.ChannelId.NEEDS_RESTART, restartRequired);
	}

	private void calculateEnergy() {
		final var activePower = this.getActivePower().get();

		if (activePower == null) {
			this.calculateProductionEnergy.update(null);
			this.calculateConsumptionEnergy.update(null);
			return;
		}
		// as the invert-feature, does not impose any change on activePower, the correct sign has to be handled through cases:
		switch (this.meterType) {

		case GRID, GRID_GENSET -> {
			if (activePower >= 0) {
				this.calculateConsumptionEnergy.update(activePower);
				this.calculateProductionEnergy.update(0);
			} else {
				this.calculateConsumptionEnergy.update(0);
				this.calculateProductionEnergy.update(-activePower);
			}
		}

		case PRODUCTION -> {
			this.calculateProductionEnergy.update(Math.abs(activePower));
			this.calculateConsumptionEnergy.update(0);
		}

		case PRODUCTION_AND_CONSUMPTION -> {
			if (activePower >= 0) {
				this.calculateConsumptionEnergy.update(activePower);
				this.calculateProductionEnergy.update(0);
			} else {
				this.calculateConsumptionEnergy.update(0);
				this.calculateProductionEnergy.update(-activePower);
			}
		}

		case CONSUMPTION_METERED,
		     MANAGED_CONSUMPTION_METERED,
		     CONSUMPTION_NOT_METERED -> {
			this.calculateConsumptionEnergy.update(Math.abs(activePower));
			this.calculateProductionEnergy.update(0);
		}
		}
	}

	@Override
	public MeterType getMeterType() {
		return this.meterType;
	}

	@Override
	public SinglePhase getPhase() {
		return this.phase;
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

}