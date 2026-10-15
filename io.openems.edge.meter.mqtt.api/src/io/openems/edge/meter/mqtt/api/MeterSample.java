package io.openems.edge.meter.mqtt.api;

/**
 * A neutral, already unit-converted reading extracted from one MQTT message by a
 * {@link MqttMeterAdapter}.
 *
 * <p>
 * Every field is nullable: a {@link MqttMeterAdapter} only fills in the fields that the
 * particular message actually carried and leaves the rest {@code null}, meaning "no update
 * for this value". The consuming Meter component applies non-null fields to its Channels and
 * leaves the others untouched.
 *
 * <p>
 * Sign convention: {@code activePower} and {@code current} must already be normalized to
 * OpenEMS' convention (positive = consumption/import, negative = feed-in/export) for the
 * device family. Per-installation wiring issues (e.g. a reversed CT clamp) are handled
 * separately via the Meter's {@code invert} Config flag, not by the adapter.
 *
 * @param activePower Active Power in [W], or {@code null}
 * @param voltage     Voltage in [mV], or {@code null}
 * @param current     Current in [mA], or {@code null}
 * @param frequency   Frequency in [mHz], or {@code null}
 * @param online      {@code true}/{@code false} if the message reported a device online/offline
 *                    state change, or {@code null} if the message did not carry such
 *                    information
 */
public record MeterSample(//
		Integer activePower, //
		Integer voltage, //
		Integer current, //
		Integer frequency, //
		Boolean online) {

	public static final MeterSample EMPTY = new MeterSample(null, null, null, null, null);

}
