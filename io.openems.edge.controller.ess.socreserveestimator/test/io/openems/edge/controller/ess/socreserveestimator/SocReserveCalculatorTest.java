package io.openems.edge.controller.ess.socreserveestimator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;

import org.junit.Test;

import io.openems.edge.predictor.api.prediction.Prediction;

public class SocReserveCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-01-15T22:00:00Z");
	/** Matches the real Config default - used wherever the anchor mechanic itself matters. */
	private static final LocalTime SEARCH_START_TIME = LocalTime.of(4, 0);
	/**
	 * Just after {@link #NOW}'s time-of-day, so the search effectively starts
	 * right away - used by tests that isolate a different calculation concern and
	 * don't care about the anchor mechanic itself.
	 */
	private static final LocalTime IMMEDIATE_SEARCH_START_TIME = LocalTime.of(22, 15);
	private static final LocalTime FALLBACK_TIME = LocalTime.of(8, 0);
	private static final ZoneId ZONE = ZoneId.of("UTC");
	private static final int THRESHOLD_W = 1000;

	private static Integer[] repeat(int value, int count) {
		var result = new Integer[count];
		Arrays.fill(result, value);
		return result;
	}

	/**
	 * Builds an array of {@code length} zeros with {@code value} at
	 * {@code spikeIndex} - used to place the production threshold crossing at a
	 * specific quarter.
	 */
	private static Integer[] withSpikeAt(int length, int spikeIndex, int value) {
		var result = repeat(0, length);
		result[spikeIndex] = value;
		return result;
	}

	@Test
	public void normalNight_realisticScenario() {
		// Horizon 8h (32 quarters) ahead, constant 400W consumption, no production
		// until the spike.
		var production = Prediction.from(NOW, withSpikeAt(33, 32, 1200));
		var consumption = Prediction.from(NOW, repeat(400, 32));

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W, SEARCH_START_TIME,
				FALLBACK_TIME, ZONE, 0.1, 10, 10_000, 5, 80);

		assertEquals(NOW.plusSeconds(8 * 3600), result.horizon());
		assertEquals(4520, result.requiredReserveEnergyWh()); // (400*0.25*32)*1.1 + 10%*10000
		assertEquals(45, result.calculatedMinSocPercent());
		assertFalse(result.predictionIncomplete());
		assertFalse(result.clamped());
	}

	@Test
	public void daytimeDip_isIgnoredForHorizonButStillCountsInBalance() {
		// A passing thunderstorm briefly drops production below the threshold in the
		// early afternoon - this must NOT be mistaken for nightfall. The horizon
		// search only starts at the configured search-start time (04:00, safely
		// before sunrise), so the storm is entirely invisible to horizon detection.
		// Its energy impact still correctly counts into the balance though, since
		// that loop always runs from "now" all the way to the (correctly found)
		// real horizon.
		var productionValues = new Integer[33];
		Arrays.fill(productionValues, 1200); // sunny all day by default
		productionValues[10] = 200; // thunderstorm dip
		productionValues[11] = 200;
		for (var i = 24; i < 32; i++) {
			productionValues[i] = 0; // real dusk through the night
		}
		// index 32 stays 1200 - the real dawn
		var production = Prediction.from(NOW, productionValues);
		var consumption = Prediction.from(NOW, repeat(400, 32));

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W, SEARCH_START_TIME,
				FALLBACK_TIME, ZONE, 0.0, 0, 10_000, 5, 80);

		assertEquals(NOW.plusSeconds(8 * 3600), result.horizon());
		// Storm: 2 quarters * (400-200)W * 0.25h = 100 Wh. Real night: 8 quarters *
		// 400W * 0.25h = 800 Wh.
		assertEquals(900, result.requiredReserveEnergyWh());
		assertEquals(9, result.calculatedMinSocPercent());
		assertFalse(result.clamped());
	}

	@Test
	public void energyBalance_sumsConsumptionMinusProductionOnly() {
		// Isolate the plain energy balance: no safety margin, no End-SoC Reserve.
		var production = Prediction.from(NOW, withSpikeAt(3, 2, 1200));
		var consumption = Prediction.from(NOW, repeat(300, 2));

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W,
				IMMEDIATE_SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.0, 0, 1_000, 5, 80);

		// 2 quarters * 300W * 0.25h = 150 Wh
		assertEquals(150, result.requiredReserveEnergyWh());
		assertEquals(15, result.calculatedMinSocPercent());
		assertFalse(result.predictionIncomplete());
		assertFalse(result.clamped());
	}

	@Test
	public void missingSlot_isSkippedAndFlagsIncomplete() {
		// Production is undefined for quarter index 2 - that slot must be excluded
		// from the sum, not treated as 0.
		var productionValues = new Integer[] { 0, 0, null, 0, 1200 };
		var production = Prediction.from(NOW, productionValues);
		var consumption = Prediction.from(NOW, repeat(300, 4));

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W,
				IMMEDIATE_SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.0, 0, 1_000, 5, 80);

		// Only quarters 0, 1, 3 count: 3 * 300W * 0.25h = 225 Wh
		assertEquals(225, result.requiredReserveEnergyWh());
		assertTrue(result.predictionIncomplete());
	}

	@Test
	public void emptyProductionPrediction_fallsBackToFixedHorizon() {
		var result = SocReserveCalculator.calculate(Prediction.EMPTY_PREDICTION, Prediction.EMPTY_PREDICTION, NOW,
				THRESHOLD_W, SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.1, 10, 10_000, 5, 80);

		// NOW is 22:00 UTC, 08:00 has already passed today -> next occurrence is
		// tomorrow.
		assertEquals(Instant.parse("2026-01-16T08:00:00Z"), result.horizon());
		assertTrue(result.predictionIncomplete());
		// No slot has a defined production value -> nothing is added to the balance,
		// only the End-SoC Reserve remains. This is the accepted trade-off of the
		// "skip incomplete slots" strategy, see spec section 10.
		assertEquals(1_000, result.requiredReserveEnergyWh()); // 10% of 10'000 Wh
		assertEquals(10, result.calculatedMinSocPercent());
	}

	@Test
	public void fallbackHorizon_staysOnSameDayIfStillAhead() {
		var earlyNow = Instant.parse("2026-01-15T05:00:00Z");

		var result = SocReserveCalculator.calculate(Prediction.EMPTY_PREDICTION, Prediction.EMPTY_PREDICTION,
				earlyNow, THRESHOLD_W, SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.0, 0, 10_000, 5, 80);

		assertEquals(Instant.parse("2026-01-15T08:00:00Z"), result.horizon());
	}

	@Test
	public void zeroDeficit_stillReturnsEndSocReserveFloor() {
		// Production covers consumption throughout -> required energy is 0, but the
		// End-SoC Reserve must still show up in the result.
		var production = Prediction.from(NOW, new Integer[] { 300, 1200 });
		var consumption = Prediction.from(NOW, new Integer[] { 200 });

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W,
				IMMEDIATE_SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.0, 10, 8_000, 5, 80);

		assertEquals(800, result.requiredReserveEnergyWh()); // 10% of 8'000 Wh, nothing else
		assertEquals(10, result.calculatedMinSocPercent());
		assertFalse(result.clamped());
	}

	@Test
	public void safetyMargin_isAppliedOnTopOfRequiredEnergy() {
		var production = Prediction.from(NOW, withSpikeAt(2, 1, 1200));
		var consumption = Prediction.from(NOW, new Integer[] { 400 });

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W,
				IMMEDIATE_SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.5, 0, 1_000, 5, 80);

		// 400W * 0.25h = 100 Wh, * 1.5 safety margin = 150 Wh
		assertEquals(150, result.requiredReserveEnergyWh());
		assertEquals(15, result.calculatedMinSocPercent());
	}

	@Test
	public void resultBelowClampLow_isLiftedUp() {
		var production = Prediction.from(NOW, withSpikeAt(2, 1, 1200));
		var consumption = Prediction.from(NOW, new Integer[] { 300 });

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W,
				IMMEDIATE_SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.0, 0, 10_000, 5, 80);

		assertEquals(5, result.calculatedMinSocPercent());
		assertTrue(result.clamped());
	}

	@Test
	public void resultAboveClampHigh_isPulledDown() {
		var production = Prediction.from(NOW, withSpikeAt(5, 4, 1200));
		var consumption = Prediction.from(NOW, repeat(5000, 4));

		var result = SocReserveCalculator.calculate(consumption, production, NOW, THRESHOLD_W,
				IMMEDIATE_SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.0, 0, 1_000, 5, 80);

		assertEquals(5_000, result.requiredReserveEnergyWh()); // 4 * 5000W * 0.25h
		assertEquals(80, result.calculatedMinSocPercent());
		assertTrue(result.clamped());
	}

	@Test
	public void zeroCapacity_returnsSafeFallback() {
		var result = SocReserveCalculator.calculate(Prediction.EMPTY_PREDICTION, Prediction.EMPTY_PREDICTION, NOW,
				THRESHOLD_W, SEARCH_START_TIME, FALLBACK_TIME, ZONE, 0.1, 10, 0, 5, 80);

		assertEquals(0, result.requiredReserveEnergyWh());
		assertEquals(80, result.calculatedMinSocPercent());
		assertEquals(NOW, result.horizon());
		assertTrue(result.predictionIncomplete());
		assertTrue(result.clamped());
	}
}
