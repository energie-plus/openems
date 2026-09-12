/**
 * MQTT Meter Adapter API.
 *
 * <p>
 * Common abstraction that lets OpenEMS Meters receive their readings via MQTT, independent of
 * the concrete device family. See the bundle's {@code readme.adoc} for details.
 * <ul>
 * <li>{@link io.openems.edge.meter.mqtt.api.MqttMeterAdapter} - per-device-family payload parser
 * <li>{@link io.openems.edge.meter.mqtt.api.MeterSample} - neutral, unit-converted reading
 * </ul>
 */
@org.osgi.annotation.versioning.Version("1.0.0")
@org.osgi.annotation.bundle.Export
package io.openems.edge.meter.mqtt.api;
