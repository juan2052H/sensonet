// Configuración del nodo SensoNet (pines, intervalos, tópicos). Los secretos van en secrets.h.
#pragma once

#define FIRMWARE_VERSION "1.0.0"

// --- Sensor: deja UNA sola línea activa ---
#define SENSOR_BME280   // temperatura, humedad y presión
// #define SENSOR_SHT31  // temperatura y humedad (más preciso, sin presión)

// Prefijo SN_: la librería de Adafruit ya define BME280_ADDRESS (0x77) y lo pisaría
#define SN_BME280_ADDR 0x76   // 0x76 o 0x77 según el módulo
#define SN_SHT31_ADDR  0x44   // 0x44 o 0x45 según el pin ADDR
#define SN_OLED_ADDR   0x3C

// --- Pines (ESP32 DevKit V1). Evitar GPIO 6-11, 0, 2, 12, 15 y usar 34-39 solo como entrada ---
#define PIN_I2C_SDA     21
#define PIN_I2C_SCL     22
#define PIN_RELAY       26
#define RELAY_ACTIVE_LOW true   // la mayoría de módulos con optoacoplador se activan en bajo
#define PIN_LED_R       25
#define PIN_LED_G       33
#define PIN_LED_B       32
#define PIN_BUZZER      27
#define PIN_BUTTON       4      // INPUT_PULLUP; mantener 5 s = borrar WiFi guardado

// --- Tiempos ---
#define DEFAULT_INTERVAL_S        10      // muestreo (el backend puede cambiarlo por el tópico config)
#define STATUS_EVERY_MS        60000UL    // estado retenido
#define WIFI_ATTEMPT_MS        10000UL    // espera por intento de WiFi
#define WIFI_MAX_ATTEMPTS          3      // luego se abre el portal de configuración
#define BUFFER_BATCH              20      // mensajes del buffer por lote al reconectar
#define BUFFER_BATCH_EVERY_MS   1000UL
#define BUTTON_RESET_MS         5000UL
#define WATCHDOG_TIMEOUT_S        30

// --- Buffer offline ---
#define BUFFER_CAPACITY         2000      // ≈ 5,5 horas a 10 s
#define BUFFER_SLOT_BYTES        192
#define BUFFER_FILE  "/buffer.bin"
#define RESEND_ON_RECONNECT        6      // últimas lecturas que se reenvían si se cae la conexión

// --- MQTT ---
#define MQTT_BUFFER_BYTES       1024
#define MQTT_KEEPALIVE_S          30
#define TOPIC_PREFIX "sensonet/v1/"
#define WIFI_PORTAL_SSID "SensoNet-Setup"
