package io.openems.edge.meter.mqtt.whatwatt;

import static io.openems.common.utils.JsonUtils.getAsOptionalDouble;
import static io.openems.common.utils.JsonUtils.parseToJsonObject;
import static java.lang.Math.round;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.types.MeterType;
import io.openems.edge.bridge.mqtt.api.BridgeMqtt;
import io.openems.edge.bridge.mqtt.api.BridgeMqtt.MqttSubscription;
import io.openems.edge.bridge.mqtt.api.MqttComponent;
import io.openems.edge.bridge.mqtt.api.MqttMessage;
import io.openems.edge.bridge.mqtt.api.QoS;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Mqtt.WhatWatt", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class MeterMqttWhatWattImpl extends AbstractOpenemsComponent
		implements MeterMqttWhatWatt, ElectricityMeter, MqttComponent, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(MeterMqttWhatWattImpl.class);

	@Reference
	protected ConfigurationAdmin cm;

	private volatile BridgeMqtt mqttBridge;

	private Config config;
	private MqttSubscription subscription;

	public MeterMqttWhatWattImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MqttComponent.ChannelId.values() //
		);
	}

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setMqtt(BridgeMqtt mqtt) {
		this.mqttBridge = mqtt;
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		this.config = config;

		super.activate(context, config.id(), config.alias(), config.enabled());
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "Mqtt", config.mqtt_id())) {
			return;
		}

		if (!this.isEnabled()) {
			return;
		}
		this.subscribeToMqtt();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.unsubscribeFromMqtt();
		super.deactivate();
	}

	private void subscribeToMqtt() {
		try {
			this.subscription = this.mqttBridge.subscribe(this.config.topic(), QoS.AT_LEAST_ONCE,
					this::handleMqttMessage);
			this.logInfo(this.log, "Subscribed to: " + this.config.topic());
			this._setMqttCommunicationFailed(false);
		} catch (Exception e) {
			this.logError(this.log, "Failed to subscribe to MQTT topic: " + e.getMessage());
			this._setMqttCommunicationFailed(true);
		}
	}

	private void unsubscribeFromMqtt() {
		if (this.subscription != null) {
			try {
				this.subscription.unsubscribe();
			} catch (Exception e) {
				this.logWarn(this.log, "Error unsubscribing from MQTT: " + e.getMessage());
			}
			this.subscription = null;
		}
	}

	@Override
	public void retryMqttCommunication() {
		this.unsubscribeFromMqtt();
		this.subscribeToMqtt();
	}

	/**
	 * Handles an incoming MQTT message from the bridge.
	 *
	 * @param message the received {@link MqttMessage}
	 */
	private void handleMqttMessage(MqttMessage message) {
		try {
			this.applyPayload(message.payloadAsString());
			this._setMqttCommunicationFailed(false);
		} catch (OpenemsNamedException e) {
			this.logWarn(this.log,
					"Failed to parse MQTT message on topic " + message.topic() + ": " + e.getMessage());
		}
	}

	/**
	 * Applies one whatwatt Go JSON payload to the Channels.
	 *
	 * <p>
	 * Every field is applied independently and only if present - a missing field leaves the
	 * corresponding Channel untouched instead of failing the whole message. {@code power_in} and
	 * {@code power_out} are only applied together, since {@link ElectricityMeter.ChannelId#ACTIVE_POWER}
	 * is their difference.
	 *
	 * <p>
	 * {@code energy_in}/{@code energy_out} are the device's own cumulative meter registers, not
	 * integrated on-edge from power - they are written through directly on every message (in kWh,
	 * converted to Wh) so that a lost MQTT message never causes the accumulated energy to drift.
	 *
	 * <p>
	 * The device only reports combined 3-phase totals for power and energy, never per-phase - so
	 * {@code ActivePowerL1/L2/L3} and the per-phase energy Channels are intentionally never set
	 * here and stay {@code UNDEFINED}, rather than fabricating an even split across phases.
	 *
	 * @param payload the raw JSON payload
	 * @throws OpenemsNamedException if the payload is not valid JSON
	 */
	private void applyPayload(String payload) throws OpenemsNamedException {
		var root = parseToJsonObject(payload);

		var powerIn = getAsOptionalDouble(root, "power_in");
		var powerOut = getAsOptionalDouble(root, "power_out");
		if (powerIn.isPresent() && powerOut.isPresent()) {
			this._setActivePower((int) round((powerIn.get() - powerOut.get()) * 1000));
		}

		getAsOptionalDouble(root, "voltage_l1").ifPresent(v -> this._setVoltageL1((int) round(v * 1000)));
		getAsOptionalDouble(root, "voltage_l2").ifPresent(v -> this._setVoltageL2((int) round(v * 1000)));
		getAsOptionalDouble(root, "voltage_l3").ifPresent(v -> this._setVoltageL3((int) round(v * 1000)));

		getAsOptionalDouble(root, "energy_in").ifPresent(e -> this._setActiveConsumptionEnergy(round(e * 1000)));
		getAsOptionalDouble(root, "energy_out").ifPresent(e -> this._setActiveProductionEnergy(round(e * 1000)));
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link MqttComponent.ChannelId#MQTT_COMMUNICATION_FAILED} Channel.
	 *
	 * @param value the next value
	 */
	private void _setMqttCommunicationFailed(boolean value) {
		this.channel(MqttComponent.ChannelId.MQTT_COMMUNICATION_FAILED).setNextValue(value);
	}

	@Override
	public String debugLog() {
		return this.getActivePower().asString();
	}

	@Override
	public MeterType getMeterType() {
		return this.config.type();
	}

	@Override
	public boolean addToSum() {
		return this.config.addToSum();
	}

}
