package io.openems.edge.meter.mqtt.whatwatt;

import io.openems.edge.bridge.mqtt.api.MqttComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.common.sum.SumOptions;
import io.openems.edge.meter.api.ElectricityMeter;

public interface MeterMqttWhatWatt extends ElectricityMeter, MqttComponent, SumOptions, OpenemsComponent {

}
