package io.openems.edge.io.shelly.shellyproem50;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import io.openems.common.types.MeterType;
import io.openems.edge.common.type.Phase.SinglePhase;

@ObjectClassDefinition(//
		name = "IO Shelly Pro EM-50", //
		description = "Implements the Shelly Pro EM-50")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "meter0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name of this Component; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Phase", description = "Which phase is measured by this Shelly Pro EM-50 channel?")
	SinglePhase phase() default SinglePhase.L1;

	@AttributeDefinition(name = "IP-Address", description = "The IP address of the Shelly device.")
	String ip();

	@AttributeDefinition(name = "Channel", description = "Shelly EM1 channel id. Usually 0 or 1.")
	int channel() default 0;

	@AttributeDefinition(name = "Meter-Type", description = "What is measured by this Meter?")
	MeterType type() default MeterType.GRID;

	@AttributeDefinition(name = "Invert Power", description = "Inverts all power values, inverts current values, swaps production and consumption energy.")
	boolean invert() default false;

	String webconsole_configurationFactory_nameHint() default "IO Shelly Pro EM-50 [{id}]";
}