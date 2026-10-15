package io.openems.edge.meter.mqtt.shelly;

import java.util.function.Function;

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
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.osgi.service.event.propertytypes.EventTopics;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.types.MeterType;
import io.openems.edge.bridge.mqtt.api.BridgeMqtt;
import io.openems.edge.bridge.mqtt.api.BridgeMqtt.MqttSubscription;
import io.openems.edge.bridge.mqtt.api.MqttComponent;
import io.openems.edge.bridge.mqtt.api.MqttMessage;
import io.openems.edge.bridge.mqtt.api.QoS;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.event.EdgeEventConstants;
import io.openems.edge.common.type.Phase.SinglePhase;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.api.SinglePhaseMeter;
import io.openems.edge.meter.mqtt.api.MeterSample;
import io.openems.edge.meter.mqtt.api.MqttMeterAdapter;
import io.openems.edge.timedata.api.Timedata;
import io.openems.edge.timedata.api.TimedataProvider;
import io.openems.edge.timedata.api.utils.CalculateEnergyFromPower;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Meter.Mqtt.Shelly", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
@EventTopics({ //
		EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE //
})
public class MeterMqttShellyImpl extends AbstractOpenemsComponent implements MeterMqttShelly, ElectricityMeter,
		SinglePhaseMeter, MqttComponent, OpenemsComponent, TimedataProvider, EventHandler {

	private final Logger log = LoggerFactory.getLogger(MeterMqttShellyImpl.class);

	private final CalculateEnergyFromPower calculateProductionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_PRODUCTION_ENERGY);
	private final CalculateEnergyFromPower calculateConsumptionEnergy = new CalculateEnergyFromPower(this,
			ElectricityMeter.ChannelId.ACTIVE_CONSUMPTION_ENERGY);

	@Reference
	protected ConfigurationAdmin cm;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	private volatile Timedata timedata;

	private volatile BridgeMqtt mqttBridge;

	private Config config;
	private MqttMeterAdapter adapter;
	private MqttSubscription subscription;

	public MeterMqttShellyImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				ElectricityMeter.ChannelId.values(), //
				MqttComponent.ChannelId.values() //
		);

		SinglePhaseMeter.calculateSinglePhaseFromActivePower(this);
		SinglePhaseMeter.calculateSinglePhaseFromCurrent(this);
		SinglePhaseMeter.calculateSinglePhaseFromVoltage(this);
	}

	@Reference(policy = ReferencePolicy.STATIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MANDATORY)
	protected void setMqtt(BridgeMqtt mqtt) {
		this.mqttBridge = mqtt;
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		this.config = config;
		this.adapter = new ShellyGen2Adapter(config.channel());

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
			var topicFilter = this.adapter.getTopicFilter(this.config.topicPrefix());
			this.subscription = this.mqttBridge.subscribe(topicFilter, QoS.AT_LEAST_ONCE, this::handleMqttMessage);
			this.logInfo(this.log, "Subscribed to: " + topicFilter);
			this._setMqttCommunicationFailed(false);
		} catch (Exception e) {
			this.logError(this.log, "Failed to subscribe to MQTT topics: " + e.getMessage());
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
			var sample = this.adapter.parseMessage(message, this.config.topicPrefix());
			this.applySample(sample);
		} catch (Exception e) {
			this.logWarn(this.log, "Failed to parse MQTT message on topic " + message.topic() + ": " + e.getMessage());
		}
	}

	private void applySample(MeterSample sample) {
		if (sample.online() != null && !sample.online()) {
			// Device reported itself offline: invalidate readings instead of keeping the last
			// known (now stale) values.
			this._setActivePower(null);
			this._setVoltage(null);
			this._setCurrent(null);
			this._setFrequency(null);
			this._setMqttCommunicationFailed(true);
			return;
		}
		// Any other message - including a "device is online" notification - means MQTT
		// communication with this device is working.
		this._setMqttCommunicationFailed(false);

		final Function<Integer, Integer> invert = value -> this.config.invert() ? -value : value;

		if (sample.activePower() != null) {
			this._setActivePower(invert.apply(sample.activePower()));
		}
		if (sample.current() != null) {
			this._setCurrent(invert.apply(sample.current()));
		}
		if (sample.voltage() != null) {
			this._setVoltage(sample.voltage());
		}
		if (sample.frequency() != null) {
			this._setFrequency(sample.frequency());
		}
	}

	@Override
	public void handleEvent(Event event) {
		if (!this.isEnabled()) {
			return;
		}
		if (event.getTopic().equals(EdgeEventConstants.TOPIC_CYCLE_AFTER_PROCESS_IMAGE)) {
			this.calculateEnergy();
		}
	}

	/**
	 * Calculates cumulated Production-/Consumption-Energy from instantaneous ActivePower, as
	 * Shelly Gen2 NotifyStatus events do not carry a cumulative energy reading for the em1:x
	 * channel.
	 */
	private void calculateEnergy() {
		var activePower = this.getActivePower().get();
		if (activePower == null) {
			this.calculateProductionEnergy.update(null);
			this.calculateConsumptionEnergy.update(null);
			return;
		}
		switch (this.config.type()) {
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
		case CONSUMPTION_METERED, MANAGED_CONSUMPTION_METERED, CONSUMPTION_NOT_METERED -> {
			this.calculateConsumptionEnergy.update(Math.abs(activePower));
			this.calculateProductionEnergy.update(0);
		}
		}
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
		return this.getPhase() + ":" + this.getActivePower().asString();
	}

	@Override
	public Timedata getTimedata() {
		return this.timedata;
	}

	@Override
	public MeterType getMeterType() {
		return this.config.type();
	}

	@Override
	public SinglePhase getPhase() {
		return this.config.phase();
	}

	@Override
	public boolean addToSum() {
		return this.config.addToSum();
	}

}
