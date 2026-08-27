package io.openems.edge.controller.ess.socreserveestimator;

import static io.openems.edge.common.type.QuarterlyValues.streamQuartersExclusive;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import io.openems.edge.predictor.api.prediction.Prediction;

/**
 * Pure calculation core for {@link ControllerEssSocReserveEstimatorImpl}.
 *
 * <p>
 * Has no dependency on OSGi, {@code Ess} or {@code PredictorManager} - inputs
 * are plain {@link Prediction} objects and primitive config values, so it can
 * be unit-tested in isolation.
 */
public class SocReserveCalculator {

	/** How far into the future the production forecast is searched for the horizon. */
	private static final long MAX_HORIZON_LOOKAHEAD_HOURS = 24;
	private static final double HOURS_PER_QUARTER = 0.25;

	/**
	 * Result of {@link SocReserveCalculator#calculate}.
	 *
	 * @param requiredReserveEnergyWh energy that needs to stay reserved [Wh],
	 *                                including safety margin and End-SoC Reserve
	 * @param calculatedMinSocPercent {@code requiredReserveEnergyWh} converted to
	 *                                a percentage of the Ess capacity, clamped
	 * @param horizon                 point in time up to which the reserve is
	 *                                calculated
	 * @param predictionIncomplete    {@code true} if the production or
	 *                                consumption forecast was missing or
	 *                                incomplete and a fallback was used
	 * @param clamped                 {@code true} if the raw calculated value was
	 *                                outside the clamp range and had to be
	 *                                adjusted
	 */
	public record Result(//
			int requiredReserveEnergyWh, //
			int calculatedMinSocPercent, //
			Instant horizon, //
			boolean predictionIncomplete, //
			boolean clamped) {
	}

	private SocReserveCalculator() {
	}

	/**
	 * Calculates the dynamic Min-SoC reserve.
	 *
	 * @param consumption           the consumption forecast
	 * @param production            the production forecast
	 * @param now                   the current point in time
	 * @param productionThresholdW  forecasted production at or above this value
	 *                              marks the end of the reserve window [W]
	 * @param fallbackHorizonTime   local time used as horizon if the production
	 *                              forecast never reaches the threshold
	 * @param zone                  the zone {@code fallbackHorizonTime} is
	 *                              interpreted in
	 * @param safetyMargin          extra fraction added on top of the forecasted
	 *                              energy need (0.1 = +10%)
	 * @param endSocReservePercent  fixed minimum SoC that should remain at the
	 *                              horizon itself [%]
	 * @param capacityWh            the Ess capacity [Wh]
	 * @param clampLowPercent       lower bound for the result [%]
	 * @param clampHighPercent      upper bound for the result [%]
	 * @return the {@link Result}
	 */
	public static Result calculate(Prediction consumption, Prediction production, Instant now,
			int productionThresholdW, LocalTime fallbackHorizonTime, ZoneId zone, double safetyMargin,
			int endSocReservePercent, int capacityWh, int clampLowPercent, int clampHighPercent) {
		if (capacityWh <= 0) {
			// Cannot convert Wh to % without a valid capacity - fail safe towards
			// "protect everything" instead of dividing by zero.
			return new Result(0, clampHighPercent, now, true, true);
		}

		var predictionIncomplete = false;

		var horizon = findHorizon(production, now, productionThresholdW);
		if (horizon == null) {
			horizon = nextOccurrenceOf(fallbackHorizonTime, now, zone);
			predictionIncomplete = true;
		}

		var requiredWh = 0.0;
		for (var t : streamQuartersExclusive(now, horizon).toList()) {
			var consumptionValue = consumption == null ? null : consumption.getAt(t);
			var productionValue = production == null ? null : production.getAt(t);
			if (consumptionValue == null || productionValue == null) {
				predictionIncomplete = true;
				continue;
			}
			requiredWh += Math.max(0, consumptionValue - productionValue) * HOURS_PER_QUARTER;
		}

		var endSocReserveWh = endSocReservePercent / 100.0 * capacityWh;
		var reserveWh = requiredWh * (1 + safetyMargin) + endSocReserveWh;

		var rawMinSocPercent = (int) Math.round(reserveWh / capacityWh * 100);
		var minSocPercent = Math.max(clampLowPercent, Math.min(clampHighPercent, rawMinSocPercent));
		var clamped = minSocPercent != rawMinSocPercent;

		return new Result((int) Math.round(reserveWh), minSocPercent, horizon, predictionIncomplete, clamped);
	}

	/**
	 * Finds the next quarter within {@link #MAX_HORIZON_LOOKAHEAD_HOURS} whose
	 * forecasted production reaches {@code productionThresholdW} again, after a
	 * preceding quarter where it was below the threshold.
	 *
	 * <p>
	 * Requiring a preceding dip is essential: if called while production is
	 * currently already above the threshold (e.g. a sunny afternoon), the naive
	 * "first quarter >= threshold" would trivially resolve to right now, ignoring
	 * the coming night entirely. Waiting for a dip first means the search skips
	 * over the remaining daylight, finds dusk, and only then looks for the actual
	 * next sunrise.
	 *
	 * @return the horizon; or {@code null} if no such dip-then-rise was found
	 */
	private static Instant findHorizon(Prediction production, Instant now, int productionThresholdW) {
		if (production == null || production.isEmpty()) {
			return null;
		}
		var sawDip = new boolean[] { false };
		return streamQuartersExclusive(now, now.plus(MAX_HORIZON_LOOKAHEAD_HOURS, ChronoUnit.HOURS)) //
				.filter(t -> {
					var value = production.getAt(t);
					if (value == null) {
						return false;
					}
					if (value < productionThresholdW) {
						sawDip[0] = true;
						return false;
					}
					return sawDip[0];
				}) //
				.findFirst() //
				.orElse(null);
	}

	/**
	 * Gets the next occurrence of {@code time} strictly after {@code now}.
	 */
	private static Instant nextOccurrenceOf(LocalTime time, Instant now, ZoneId zone) {
		var nowZoned = ZonedDateTime.ofInstant(now, zone);
		var candidate = nowZoned.toLocalDate().atTime(time).atZone(zone);
		if (!candidate.isAfter(nowZoned)) {
			candidate = candidate.plusDays(1);
		}
		return candidate.toInstant();
	}
}
