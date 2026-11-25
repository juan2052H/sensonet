/*
 * SensoNet - nodo ESP32
 *
 * Mide temperatura, humedad y presión, publica telemetría por MQTT con TLS, guarda las lecturas
 * en LittleFS cuando no hay conexión (offline-first) y obedece comandos del relé con confirmación.
 *
 * Estado: el firmware compila para ESP32 pero NO se ha probado en hardware físico. La demo del
 * proyecto usa el simulador (simulator/), que implementa el mismo protocolo.
 *
 * Librerías (Gestor de librerías de Arduino IDE): PubSubClient, ArduinoJson (7.x),
 * Adafruit BME280 Library (o Adafruit SHT31 Library), Adafruit Unified Sensor, U8g2, RTClib, WiFiManager.
 * Placa: "ESP32 Dev Module" (núcleo esp32 de Espressif 3.x).
 * Herramientas > Partition Scheme: "Huge APP (3MB No OTA/1MB SPIFFS)" (el programa no cabe en la
 * partición por defecto y el buffer usa ~384 KB de LittleFS).
 * Antes de compilar: copia secrets.example.h como secrets.h y complétalo.
 */
#include <ArduinoJson.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <WiFiManager.h>
#include <Wire.h>
#include <esp_task_wdt.h>

#include "config.h"
#include "hardware.h"
#include "offline_ring.h"
#include "recent_ids.h"
#include "secrets.h"
#include "telemetry_codec.h"

using sensonet::Reading;
using sensonet::ReadingStatus;

// ---------------------------------------------------------------------------------------------
// Máquina de estados (sin delay(): el nodo nunca se bloquea)
// ---------------------------------------------------------------------------------------------
enum class State { ARRANQUE, CONECTANDO_WIFI, CONECTANDO_MQTT, OPERANDO, OFFLINE };
static const char* stateName(State s) {
  switch (s) {
    case State::ARRANQUE: return "ARRANQUE";
    case State::CONECTANDO_WIFI: return "WIFI...";
    case State::CONECTANDO_MQTT: return "MQTT...";
    case State::OPERANDO: return "EN LINEA";
    case State::OFFLINE: return "OFFLINE";
  }
  return "?";
}

static State state = State::ARRANQUE;
static unsigned long stateSince = 0;
static unsigned wifiAttempts = 0;
static unsigned mqttAttempts = 0;
static unsigned long retryAt = 0;

static WiFiClientSecure tlsClient;
static PubSubClient mqtt(tlsClient);
static WiFiManager wifiManager;
static bool portalActive = false;

static LittleFsStorage storage(BUFFER_FILE);
static sensonet::OfflineRing buffer(storage, BUFFER_CAPACITY, BUFFER_SLOT_BYTES);
static bool bufferOk = false;
static sensonet::RecentIds<8> recentCommands;

// Últimas lecturas publicadas: PubSubClient publica con QoS 0, así que si la conexión se cae
// justo después de publicar podrían perderse. Se reenvían al buffer al detectar la caída; el
// backend descarta duplicados por (nodo, ts, seq), así que reenviar nunca duplica datos.
static char recentSent[RESEND_ON_RECONNECT][BUFFER_SLOT_BYTES];
static uint8_t recentSentCount = 0, recentSentNext = 0;

static uint32_t seq = 0;
static uint32_t intervalS = DEFAULT_INTERVAL_S;
static float alarmTemp = NAN;  // umbral local opcional (tópico config)
static unsigned long nextReadingAt = 0, nextStatusAt = 0, nextBatchAt = 0, buttonDownAt = 0;
static Reading lastReading;
static bool hasLastReading = false;
static uint32_t skippedNoTime = 0;

static char topicTelemetry[64], topicStatus[64], topicAck[64], topicRelay[64], topicConfig[64];

static void setState(State s) {
  if (s == state) return;
  Serial.printf("[estado] %s -> %s\n", stateName(state), stateName(s));
  state = s;
  stateSince = millis();
}

// ---------------------------------------------------------------------------------------------
// MQTT
// ---------------------------------------------------------------------------------------------
static void publishStatus() {
  char payload[200];
  size_t n = sensonet::encodeStatus(payload, sizeof(payload), true, FIRMWARE_VERSION, WiFi.RSSI(), buffer.size(),
                                    buffer.dropped(), relay::get(), intervalS);
  if (n) mqtt.publish(topicStatus, reinterpret_cast<const uint8_t*>(payload), n, true);
}

static void handleRelayCommand(const JsonDocument& doc) {
  const char* id = doc["id"] | "";
  char ack[160];
  size_t n;
  if (strlen(id) == 0 || strlen(id) > 40 || !doc["on"].is<bool>()) {
    n = sensonet::encodeAck(ack, sizeof(ack), id, false, relay::get(), false, "comando-invalido");
  } else if (recentCommands.contains(id)) {
    n = sensonet::encodeAck(ack, sizeof(ack), id, true, relay::get(), true, nullptr);  // idempotencia
  } else {
    bool on = doc["on"].as<bool>();
    relay::set(on);  // también se guarda en memoria no volátil
    recentCommands.remember(id);
    ui::beep(40);
    n = sensonet::encodeAck(ack, sizeof(ack), id, true, on, false, nullptr);
    Serial.printf("[relé] %s (comando %s)\n", on ? "ON" : "OFF", id);
  }
  if (n) mqtt.publish(topicAck, reinterpret_cast<const uint8_t*>(ack), n, false);
  publishStatus();
}

static void handleConfig(const JsonDocument& doc) {
  if (doc["interval"].is<int>()) {
    int v = doc["interval"].as<int>();
    if (v >= 5 && v <= 3600) {
      intervalS = static_cast<uint32_t>(v);
      Serial.printf("[config] intervalo = %d s\n", v);
    }
  }
  if (doc["alarm_t"].is<float>()) alarmTemp = doc["alarm_t"].as<float>();
  publishStatus();
}

static void onMqttMessage(char* topic, byte* payload, unsigned int length) {
  if (length > 512) return;  // límite defensivo
  JsonDocument doc;
  if (deserializeJson(doc, payload, length)) {
    Serial.printf("[mqtt] JSON inválido en %s\n", topic);
    return;
  }
  if (strcmp(topic, topicRelay) == 0) handleRelayCommand(doc);
  else if (strcmp(topic, topicConfig) == 0) handleConfig(doc);
}

static bool mqttConnect() {
  const char* lwt = "{\"state\":\"offline\"}";
  bool ok = mqtt.connect(NODE_CODE, NODE_CODE, MQTT_PASSWORD, topicStatus, 1, true, lwt);
  if (!ok) {
    Serial.printf("[mqtt] fallo de conexión, rc=%d\n", mqtt.state());
    return false;
  }
  mqtt.subscribe(topicRelay, 1);
  mqtt.subscribe(topicConfig, 1);
  publishStatus();
  return true;
}

// ---------------------------------------------------------------------------------------------
// Lecturas y buffer offline
// ---------------------------------------------------------------------------------------------
static void rememberSent(const char* payload, size_t n) {
  memcpy(recentSent[recentSentNext], payload, n);
  recentSent[recentSentNext][n] = '\0';
  recentSentNext = (recentSentNext + 1) % RESEND_ON_RECONNECT;
  if (recentSentCount < RESEND_ON_RECONNECT) recentSentCount++;
}

static void requeueRecentlySent() {
  if (!bufferOk) return;
  uint8_t start = (recentSentNext + RESEND_ON_RECONNECT - recentSentCount) % RESEND_ON_RECONNECT;
  for (uint8_t i = 0; i < recentSentCount; i++) {
    const char* p = recentSent[(start + i) % RESEND_ON_RECONNECT];
    buffer.push(p, strlen(p));
  }
  recentSentCount = 0;
}

static void storeOffline(const char* payload, size_t n) {
  if (bufferOk && buffer.push(payload, n)) {
    ui::led(LedColor::BLUE);
  } else {
    ui::led(LedColor::RED);
  }
}

static void takeReading() {
  Reading r{};
  if (!sensors::read(r)) {
    ui::led(LedColor::RED);
    Serial.println("[sensor] sin respuesta");
    return;
  }
  ReadingStatus st = sensonet::validateReading(r);
  if (st != ReadingStatus::OK) {
    ui::led(LedColor::YELLOW);  // fuera de rango físico: no se envía
    Serial.printf("[sensor] lectura descartada: %s\n", sensonet::readingStatusText(st));
    return;
  }
  lastReading = r;
  hasLastReading = true;
  if (!isnan(alarmTemp) && r.temperature > alarmTemp) ui::beep(150);

  uint64_t ts = clockx::epochMs();
  if (ts == 0) {
    skippedNoTime++;  // sin NTP ni RTC no hay marca de tiempo válida
    return;
  }
  char payload[BUFFER_SLOT_BYTES];
  size_t n = sensonet::encodeTelemetry(payload, sizeof(payload), ++seq, ts, r, WiFi.RSSI());
  if (n == 0) return;

  if (state == State::OPERANDO && mqtt.connected() &&
      mqtt.publish(topicTelemetry, reinterpret_cast<const uint8_t*>(payload), n, false)) {
    rememberSent(payload, n);
    ui::led(LedColor::GREEN);
  } else {
    storeOffline(payload, n);  // offline-first: no se pierde
  }
}

static void flushBufferBatch() {
  char payload[BUFFER_SLOT_BYTES];
  for (int i = 0; i < BUFFER_BATCH && !buffer.empty(); i++) {
    size_t n = buffer.peek(payload, sizeof(payload));
    if (n == 0) { buffer.pop(); continue; }  // registro corrupto: se descarta
    if (!mqtt.publish(topicTelemetry, reinterpret_cast<const uint8_t*>(payload), n, false)) return;
    buffer.pop();
    mqtt.loop();
  }
  if (buffer.empty()) publishStatus();  // informa buffered = 0
}

// ---------------------------------------------------------------------------------------------
// Botón: mantener 5 s borra la red guardada y reinicia (abre el portal de configuración)
// ---------------------------------------------------------------------------------------------
static void checkButton() {
  if (digitalRead(PIN_BUTTON) == LOW) {
    if (buttonDownAt == 0) buttonDownAt = millis();
    if (millis() - buttonDownAt > BUTTON_RESET_MS) {
      Serial.println("[wifi] borrando credenciales y reiniciando");
      ui::led(LedColor::MAGENTA);
      wifiManager.resetSettings();
      ESP.restart();
    }
  } else {
    buttonDownAt = 0;
  }
}

static void watchdogBegin() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  esp_task_wdt_config_t cfg = {};
  cfg.timeout_ms = WATCHDOG_TIMEOUT_S * 1000;
  cfg.idle_core_mask = 0;
  cfg.trigger_panic = true;
  if (esp_task_wdt_reconfigure(&cfg) != ESP_OK) esp_task_wdt_init(&cfg);
#else
  esp_task_wdt_init(WATCHDOG_TIMEOUT_S, true);
#endif
  esp_task_wdt_add(nullptr);  // si loop() se cuelga, el ESP32 se reinicia solo
}

// ---------------------------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  pinMode(PIN_BUTTON, INPUT_PULLUP);
  snprintf(topicTelemetry, sizeof(topicTelemetry), TOPIC_PREFIX "%s/telemetry", NODE_CODE);
  snprintf(topicStatus, sizeof(topicStatus), TOPIC_PREFIX "%s/status", NODE_CODE);
  snprintf(topicAck, sizeof(topicAck), TOPIC_PREFIX "%s/cmd/ack", NODE_CODE);
  snprintf(topicRelay, sizeof(topicRelay), TOPIC_PREFIX "%s/cmd/relay", NODE_CODE);
  snprintf(topicConfig, sizeof(topicConfig), TOPIC_PREFIX "%s/config", NODE_CODE);

  // ARRANQUE: I2C, sensores, OLED, RTC, LittleFS
  Wire.begin(PIN_I2C_SDA, PIN_I2C_SCL);
  ui::begin();
  ui::led(LedColor::WHITE);
  relay::begin();
  if (!sensors::begin()) Serial.println("[sensor] no detectado: revisa cableado y dirección I2C");
  clockx::begin(NTP_SERVER_1, NTP_SERVER_2);
  bufferOk = storage.begin(buffer.storageBytes()) && buffer.begin();
  Serial.printf("[buffer] %s, %lu lecturas pendientes\n", bufferOk ? "listo" : "ERROR",
                static_cast<unsigned long>(buffer.size()));

  tlsClient.setCACert(MQTT_CA_CERT);
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(MQTT_BUFFER_BYTES);
  mqtt.setKeepAlive(MQTT_KEEPALIVE_S);
  mqtt.setCallback(onMqttMessage);

  WiFi.mode(WIFI_STA);
  wifiManager.setConfigPortalBlocking(false);
  watchdogBegin();
  ui::show(stateName(state), nullptr, 0, buffer.size(), relay::get());
  setState(State::CONECTANDO_WIFI);
  WiFi.begin();  // usa la red guardada por WiFiManager
}

void loop() {
  esp_task_wdt_reset();
  unsigned long now = millis();
  checkButton();
  clockx::loop();
  if (portalActive) wifiManager.process();

  switch (state) {
    case State::ARRANQUE:
      setState(State::CONECTANDO_WIFI);
      break;

    case State::CONECTANDO_WIFI:
      if (WiFi.status() == WL_CONNECTED) {
        wifiAttempts = 0;
        portalActive = false;
        Serial.printf("[wifi] conectado, IP %s\n", WiFi.localIP().toString().c_str());
        setState(State::CONECTANDO_MQTT);
      } else if (now - stateSince > WIFI_ATTEMPT_MS) {
        if (++wifiAttempts >= WIFI_MAX_ATTEMPTS && !portalActive) {
          Serial.println("[wifi] abriendo portal de configuración " WIFI_PORTAL_SSID);
          ui::led(LedColor::MAGENTA);
          wifiManager.startConfigPortal(WIFI_PORTAL_SSID);
          portalActive = true;
        }
        retryAt = now + sensonet::backoffMs(wifiAttempts);
        setState(State::OFFLINE);
      }
      break;

    case State::CONECTANDO_MQTT:
      if (WiFi.status() != WL_CONNECTED) {
        setState(State::OFFLINE);
      } else if (mqttConnect()) {
        mqttAttempts = 0;
        setState(State::OPERANDO);
      } else {
        retryAt = now + sensonet::backoffMs(++mqttAttempts);
        setState(State::OFFLINE);
      }
      break;

    case State::OPERANDO:
      if (!mqtt.connected() || WiFi.status() != WL_CONNECTED) {
        Serial.println("[mqtt] conexión perdida");
        requeueRecentlySent();
        retryAt = now + 1000;
        setState(State::OFFLINE);
        break;
      }
      mqtt.loop();
      if (!buffer.empty() && now >= nextBatchAt) {
        nextBatchAt = now + BUFFER_BATCH_EVERY_MS;  // lotes pequeños intercalados con lecturas nuevas
        flushBufferBatch();
      }
      if (now >= nextStatusAt) {
        nextStatusAt = now + STATUS_EVERY_MS;
        publishStatus();
      }
      break;

    case State::OFFLINE:
      // Sigue midiendo (ver abajo) y reintenta con espera creciente.
      if (now >= retryAt) {
        if (WiFi.status() == WL_CONNECTED) {
          setState(State::CONECTANDO_MQTT);
        } else {
          if (!portalActive) WiFi.reconnect();
          setState(State::CONECTANDO_WIFI);
        }
      }
      break;
  }

  if (now >= nextReadingAt) {
    nextReadingAt = now + intervalS * 1000UL;
    takeReading();
    ui::show(stateName(state), hasLastReading ? &lastReading : nullptr, WiFi.RSSI(), buffer.size(), relay::get());
  }
}
