package io.openems.edge.meter.mqtt.shelly;

import io.openems.edge.bridge.mqtt.api.MqttComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.SumOptions;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.meter.api.SinglePhaseMeter;

public interface MeterMqttShelly extends ElectricityMeter, SinglePhaseMeter, MqttComponent, SumOptions,
		OpenemsComponent {

}
