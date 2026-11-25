#!/bin/sh
# Compila el sketch para ESP32 con arduino-cli (mismo compilador que Arduino IDE).
# Uso local con Docker (desde firmware/):
#   docker run --rm -v "$PWD:/fw" -w /fw ubuntu:24.04 sh compile.sh
# Variante SHT31: SENSOR=SHT31 sh compile.sh
set -eu

CLI_VERSION=1.6.0

if ! command -v arduino-cli >/dev/null 2>&1; then
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -qq && apt-get install -y -qq curl ca-certificates python3 >/dev/null
  fi
  # Binario oficial de GitHub Releases, verificado con su checksum publicado
  TMP=$(mktemp -d)
  BASE="https://github.com/arduino/arduino-cli/releases/download/v${CLI_VERSION}"
  FILE="arduino-cli_${CLI_VERSION}_Linux_64bit.tar.gz"
  curl -fsSL -o "$TMP/$FILE" "$BASE/$FILE"
  curl -fsSL -o "$TMP/checksums.txt" "$BASE/${CLI_VERSION}-checksums.txt"
  (cd "$TMP" && grep " $FILE\$" checksums.txt | sha256sum -c -)
  tar -xzf "$TMP/$FILE" -C /usr/local/bin arduino-cli
fi

arduino-cli config init --overwrite >/dev/null
arduino-cli config add board_manager.additional_urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core update-index
arduino-cli core install esp32:esp32
arduino-cli lib install PubSubClient ArduinoJson "Adafruit BME280 Library" "Adafruit SHT31 Library" \
  "Adafruit Unified Sensor" U8g2 RTClib WiFiManager

# Se compila una copia con los secretos de ejemplo (nunca los reales)
BUILD=$(mktemp -d)
cp -r sensonet_node "$BUILD/sensonet_node"
cp sensonet_node/secrets.example.h "$BUILD/sensonet_node/secrets.h"
if [ "${SENSOR:-BME280}" = "SHT31" ]; then
  sed -i 's|^#define SENSOR_BME280|// #define SENSOR_BME280|; s|^// #define SENSOR_SHT31|#define SENSOR_SHT31|' "$BUILD/sensonet_node/config.h"
fi
# huge_app: 3 MB para el programa y 1 MB de LittleFS para el buffer offline (sin OTA)
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=huge_app --warnings default "$BUILD/sensonet_node"
echo "Compilación correcta (sensor ${SENSOR:-BME280})"
