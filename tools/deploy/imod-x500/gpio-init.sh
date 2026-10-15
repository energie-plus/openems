#!/usr/bin/env sh
# Set GPIO direction for iMOD/Modberry X500 (M41601).
# Configures the used IO pins as outputs.
# Pin mapping (enplus feature):
#   534 - 537 are out only
#   578 - 581 are hybrid (ind & out) and must be set to be out, to be in sync with their usage in openems
#             see ModberryX500M41601WbMax.java

set -eu

PINS="534 535 536 537 578 579 580 581"

for p in $PINS; do
  gpio_path="/sys/class/gpio/gpio$p"

  # Export the pin first if it is not yet exported
  if [ ! -d "$gpio_path" ]; then
    echo "$p" > /sys/class/gpio/export
  fi

  echo out > "$gpio_path/direction"
  echo "gpio$p -> out"
done
