#!/bin/sh
# Genera el archivo de contraseñas (hash PBKDF2) desde variables de entorno y
# arranca Mosquitto. Así no hay contraseñas en el repositorio.
set -eu

: "${MQTT_BACKEND_PASSWORD:?Falta MQTT_BACKEND_PASSWORD}"
: "${MQTT_NODES:?Falta MQTT_NODES (usuario:clave,usuario:clave)}"

PW=/mosquitto/data/passwd
rm -f "$PW"
mosquitto_passwd -c -b "$PW" backend "$MQTT_BACKEND_PASSWORD"

OLD_IFS=$IFS
IFS=','
for pair in $MQTT_NODES; do
  user=${pair%%:*}
  pass=${pair#*:}
  if [ -z "$user" ] || [ "$user" = "$pair" ] || [ -z "$pass" ]; then
    echo "Entrada inválida en MQTT_NODES: '$pair'" >&2
    exit 1
  fi
  mosquitto_passwd -b "$PW" "$user" "$pass"
done
IFS=$OLD_IFS

chown mosquitto:mosquitto "$PW"
chmod 0700 "$PW"
echo "passwd generado con $(wc -l < "$PW") usuarios"
exec mosquitto -c /mosquitto/config/mosquitto.conf
