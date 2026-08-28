package io.openems.edge.controller.ess.socreserveestimator;

import static io.openems.edge.common.type.QuarterlyValues.streamQuartersExclusive;

import java.time.Duration;
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
	 * @param minimumDipDuration    how long forecasted production must stay below
	 *                              the threshold before a subsequent rise counts
	 *                              as the horizon (see {@link #findHorizon}); not
	 *                              applied to a dip already in progress right now
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
			int productionThresholdW, Duration minimumDipDuration, LocalTime fallbackHorizonTime, ZoneId zone,
			double safetyMargin, int endSocReservePercent, int capacityWh, int clampLowPercent,
			int clampHighPercent) {
		if (capacityWh <= 0) {
			// Cannot convert Wh to % without a valid capacity - fail safe towards
			// "protect everything" instead of dividing by zero.
			return new Result(0, clampHighPercent, now, true, true);
		}

		var predictionIncomplete = false;

		var horizon = findHorizon(production, now, productionThresholdW, minimumDipDuration);
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
	 * qualifying dip below it.
	 *
	 * <p>
	 * A dip qualifies in one of two ways:
	 * <ul>
	 * <li>It is already in progress at {@code now} (the very first quarter
	 * examined is already below the threshold) - accepted immediately, no
	 * duration required. If it's already low right now, that's simply the
	 * current state, not something to second-guess.
	 * <li>It starts only after the search has begun (i.e. production was still
	 * above the threshold at {@code now}) - only accepted once it has lasted at
	 * least {@code minimumDipDuration}, so a brief daytime dip (e.g. a passing
	 * thunderstorm) can't be mistaken for nightfall.
	 * </ul>
	 * Two naive alternatives were tried and rejected before this:
	 * <ul>
	 * <li>Searching from {@code now} for the first quarter >= threshold trivially
	 * resolves to right now if called while production is already above the
	 * threshold (e.g. a sunny afternoon), ignoring the coming night entirely.
	 * <li>Anchoring the search to a fixed pre-dawn clock time (e.g. never search
	 * before 04:00) avoided the thunderstorm problem, but broke the case where
	 * {@code now} falls shortly after that anchor while still genuinely before
	 * sunrise: the next occurrence of the anchor is then a full day away, wildly
	 * overestimating the reserve. Found live by Simon on ems4 (05:23, production
	 * already near zero, but the calculated horizon jumped to the next day).
	 * </ul>
	 *
	 * @return the horizon; or {@code null} if no qualifying dip-then-rise was
	 *         found
	 */
	private static Instant findHorizon(Prediction production, Instant now, int productionThresholdW,
			Duration minimumDipDuration) {
		if (production == null || production.isEmpty()) {
			return null;
		}
		var confirmedDip = false;
		Instant dipStart = null;
		var isFirstQuarter = true;
		for (var t : streamQuartersExclusive(now, now.plus(MAX_HORIZON_LOOKAHEAD_HOURS, ChronoUnit.HOURS)).toList()) {
			var value = production.getAt(t);
			if (value == null) {
				continue;
			}
			var wasFirstQuarter = isFirstQuarter;
			isFirstQuarter = false;

			if (value < productionThresholdW) {
				if (!confirmedDip) {
					if (wasFirstQuarter) {
						confirmedDip = true;
					} else if (dipStart == null) {
						dipStart = t;
					} else if (!Duration.between(dipStart, t).minus(minimumDipDuration).isNegative()) {
						confirmedDip = true;
					}
				}
				continue;
			}

			// value >= productionThresholdW
			if (confirmedDip) {
				return t;
			}
			dipStart = null; // discard any dip that was still too short when production recovered
		}
		return null;
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
