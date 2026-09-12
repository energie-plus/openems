package io.openems.edge.controller.ess.socreserveestimator;

import static io.openems.edge.controller.ess.socreserveestimator.ControllerEssSocReserveEstimator.ChannelId.CALCULATED_MIN_SOC;
import static io.openems.edge.controller.ess.socreserveestimator.ControllerEssSocReserveEstimator.ChannelId.MIN_SOC_CLAMPED;
import static io.openems.edge.controller.ess.socreserveestimator.ControllerEssSocReserveEstimator.ChannelId.PREDICTION_INCOMPLETE;
import static io.openems.edge.controller.ess.socreserveestimator.ControllerEssSocReserveEstimator.ChannelId.REQUIRED_RESERVE_ENERGY;
import static io.openems.edge.controller.ess.socreserveestimator.ControllerEssSocReserveEstimator.ChannelId.RESERVE_HORIZON;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

import org.junit.Test;

import io.openems.common.test.DummyConfigurationAdmin;
import io.openems.common.test.TimeLeapClock;
import io.openems.common.types.ChannelAddress;
import io.openems.edge.common.test.AbstractComponentTest.TestCase;
import io.openems.edge.common.test.DummyComponentManager;
import io.openems.edge.controller.test.ControllerTest;
import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.predictor.api.prediction.Prediction;
import io.openems.edge.predictor.api.test.DummyPredictor;
import io.openems.edge.predictor.api.test.DummyPredictorManager;

public class ControllerEssSocReserveEstimatorImplTest {

	@Test
	public void test() throws Exception {
		var now = Instant.parse("2026-01-15T22:00:00Z");
		var clock = new TimeLeapClock(now, ZoneId.of("UTC"));
		var componentManager = new DummyComponentManager(clock);

		// No production for 8h (32 quarters), then a spike above the threshold.
		var productionValues = new Integer[33];
		Arrays.fill(productionValues, 0);
		productionValues[32] = 1200;

		// Constant 400W consumption throughout the reserve window.
		var consumptionValues = new Integer[32];
		Arrays.fill(consumptionValues, 400);

		var productionPredictor = new DummyPredictor("predictor0", componentManager,
				Prediction.from(now, productionValues), ChannelAddress.fromString("_sum/ProductionActivePower"));
		var consumptionPredictor = new DummyPredictor("predictor1", componentManager,
				Prediction.from(now, consumptionValues), ChannelAddress.fromString("_sum/ConsumptionActivePower"));
		var predictorManager = new DummyPredictorManager(productionPredictor, consumptionPredictor);

		new ControllerTest(new ControllerEssSocReserveEstimatorImpl()) //
				.addReference("componentManager", componentManager) //
				.addReference("cm", new DummyConfigurationAdmin()) //
				.addReference("predictorManager", predictorManager) //
				.addReference("ess", new DummyManagedSymmetricEss("ess0") //
						.withCapacity(10_000)) //
				.activate(MyConfig.create() //
						.setId("ctrl0") //
						.setEssId("ess0") //
						.setProductionThreshold(1000) //
						.setSafetyMargin(0.1) //
						.setEndSocReserve(10) //
						.setFallbackHorizonTime("08:00") //
						.setMinSocClampLow(5) //
						.setMinSocClampHigh(80) //
						.build()) //
				.next(new TestCase("dynamic reserve is calculated from forecasts") //
						.output(REQUIRED_RESERVE_ENERGY, 4520) //
						.output(CALCULATED_MIN_SOC, 45) //
						.output(RESERVE_HORIZON, now.plusSeconds(8 * 3600).toEpochMilli()) //
						.output(PREDICTION_INCOMPLETE, false) //
						.output(MIN_SOC_CLAMPED, false)) //
				.deactivate();
	}
}
