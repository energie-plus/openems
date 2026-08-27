package io.openems.edge.controller.ess.socreserveestimator;

import java.time.Instant;
import java.time.LocalTime;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;
import io.openems.edge.ess.api.SymmetricEss;
import io.openems.edge.predictor.api.manager.PredictorManager;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Controller.Ess.SocReserveEstimator", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class ControllerEssSocReserveEstimatorImpl extends AbstractOpenemsComponent
		implements ControllerEssSocReserveEstimator, Controller, OpenemsComponent {

	private static final ChannelAddress CONSUMPTION_ADDRESS = new ChannelAddress("_sum", "ConsumptionActivePower");
	private static final ChannelAddress PRODUCTION_ADDRESS = new ChannelAddress("_sum", "ProductionActivePower");

	@Reference
	private ComponentManager componentManager;

	@Reference
	protected ConfigurationAdmin cm;

	@Reference
	private SymmetricEss ess;

	@Reference
	protected PredictorManager predictorManager;

	private Config config;

	public ControllerEssSocReserveEstimatorImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				ControllerEssSocReserveEstimator.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "ess", config.ess_id())) {
			return;
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		var consumption = this.predictorManager.getPrediction(CONSUMPTION_ADDRESS);
		var production = this.predictorManager.getPrediction(PRODUCTION_ADDRESS);
		var capacity = this.ess.getCapacity();
		var clock = this.componentManager.getClock();

		var result = SocReserveCalculator.calculate(//
				consumption, //
				production, //
				Instant.now(clock), //
				this.config.productionThreshold(), //
				LocalTime.parse(this.config.horizonSearchStartTime()), //
				LocalTime.parse(this.config.fallbackHorizonTime()), //
				clock.getZone(), //
				this.config.safetyMargin(), //
				this.config.endSocReserve(), //
				capacity.isDefined() ? capacity.get() : 0, //
				this.config.minSocClampLow(), //
				this.config.minSocClampHigh());

		this._setRequiredReserveEnergy(result.requiredReserveEnergyWh());
		this._setCalculatedMinSoc(result.calculatedMinSocPercent());
		this._setReserveHorizon(result.horizon().toEpochMilli());
		this._setPredictionIncomplete(result.predictionIncomplete());
		this._setMinSocClamped(result.clamped());
	}

	@Override
	public String debugLog() {
		return "MinSoc:" + this.getCalculatedMinSoc().asString();
	}
}
