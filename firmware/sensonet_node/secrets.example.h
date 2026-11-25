// PLANTILLA. Copia este archivo como "secrets.h" (está en .gitignore) y completa tus datos.
// La red WiFi NO va aquí: se configura desde el portal cautivo "SensoNet-Setup" (WiFiManager).
#pragma once

// Código del nodo = usuario MQTT. Por la ACL de Mosquitto solo puede publicar en sensonet/v1/<código>/...
#define NODE_CODE      "nodo-sala"
#define MQTT_PASSWORD  "la-clave-de-nodo-sala-definida-en-MQTT_NODES"

// IP o nombre del PC/servidor con Mosquitto. Debe estar en el certificado del servidor:
// genera los certificados con MQTT_EXTRA_SAN=<tu IP> (ver docs/HARDWARE.md).
#define MQTT_HOST      "192.168.1.50"
#define MQTT_PORT      8883

// Contenido de infra/certs/generated/ca.crt (la CA propia que firmó el certificado del broker).
static const char MQTT_CA_CERT[] = R"PEM(
-----BEGIN CERTIFICATE-----
PEGA AQUÍ EL CONTENIDO DE ca.crt
-----END CERTIFICATE-----
)PEM";

// Servidores NTP y zona horaria (Colombia, UTC-5). Las marcas de tiempo se envían en UTC.
#define NTP_SERVER_1   "pool.ntp.org"
#define NTP_SERVER_2   "time.google.com"
