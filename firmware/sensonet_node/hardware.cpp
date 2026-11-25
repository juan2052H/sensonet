#include "hardware.h"

#include <LittleFS.h>
#include <Preferences.h>
#include <RTClib.h>
#include <U8g2lib.h>
#include <Wire.h>
#include <sys/time.h>
#include <time.h>

#include "config.h"

#if defined(SENSOR_BME280)
#include <Adafruit_BME280.h>
static Adafruit_BME280 bme;
#elif defined(SENSOR_SHT31)
#include <Adafruit_SHT31.h>
static Adafruit_SHT31 sht;
#else
#error "Define SENSOR_BME280 o SENSOR_SHT31 en config.h"
#endif

// ============================ Sensores ============================
static bool sensorOk = false;

bool sensors::begin() {
#if defined(SENSOR_BME280)
  sensorOk = bme.begin(SN_BME280_ADDR, &Wire);
  if (sensorOk) {
    // Modo forzado: una medición por lectura, menos autocalentamiento.
    bme.setSampling(Adafruit_BME280::MODE_FORCED, Adafruit_BME280::SAMPLING_X1, Adafruit_BME280::SAMPLING_X1,
                    Adafruit_BME280::SAMPLING_X1, Adafruit_BME280::FILTER_OFF);
  }
#else
  sensorOk = sht.begin(SN_SHT31_ADDR);
#endif
  return sensorOk;
}

bool sensors::present() { return sensorOk; }

bool sensors::read(sensonet::Reading& out) {
  if (!sensorOk) return false;
#if defined(SENSOR_BME280)
  if (!bme.takeForcedMeasurement()) return false;
  out.temperature = bme.readTemperature();
  out.humidity = bme.readHumidity();
  out.pressure = bme.readPressure() / 100.0f;
  out.hasPressure = true;
#else
  out.temperature = sht.readTemperature();
  out.humidity = sht.readHumidity();
  out.pressure = NAN;
  out.hasPressure = false;
#endif
  return true;
}

// ============================ Reloj ============================
static RTC_DS3231 rtc;
static bool rtcOk = false;
static bool ntpSynced = false;

void clockx::begin(const char* ntp1, const char* ntp2) {
  rtcOk = rtc.begin(&Wire);
  if (rtcOk && rtc.lostPower()) {
    rtcOk = false;  // hora del RTC no confiable hasta sincronizar con NTP
  }
  configTime(0, 0, ntp1, ntp2);  // UTC
}

void clockx::loop() {
  if (ntpSynced) return;
  time_t now = time(nullptr);
  if (now > 1700000000) {  // posterior a 2023: NTP respondió
    ntpSynced = true;
    if (rtc.begin(&Wire)) {
      rtc.adjust(DateTime(static_cast<uint32_t>(now)));
      rtcOk = true;
    }
  }
}

bool clockx::synced() { return ntpSynced || rtcOk; }

uint64_t clockx::epochMs() {
  if (ntpSynced) {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return static_cast<uint64_t>(tv.tv_sec) * 1000ULL + tv.tv_usec / 1000;
  }
  if (rtcOk) {
    return static_cast<uint64_t>(rtc.now().unixtime()) * 1000ULL + (millis() % 1000);
  }
  return 0;
}

// ============================ OLED / LED / buzzer ============================
static U8G2_SSD1306_128X64_NONAME_F_HW_I2C oled(U8G2_R0, U8X8_PIN_NONE);
static bool oledOk = false;

static void ledWrite(uint8_t pin, uint8_t duty) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(pin, duty);
#else
  ledcWrite(pin == PIN_LED_R ? 0 : pin == PIN_LED_G ? 1 : 2, duty);
#endif
}

void ui::begin() {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PIN_LED_R, 5000, 8);
  ledcAttach(PIN_LED_G, 5000, 8);
  ledcAttach(PIN_LED_B, 5000, 8);
#else
  ledcSetup(0, 5000, 8); ledcAttachPin(PIN_LED_R, 0);
  ledcSetup(1, 5000, 8); ledcAttachPin(PIN_LED_G, 1);
  ledcSetup(2, 5000, 8); ledcAttachPin(PIN_LED_B, 2);
#endif
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);
  oled.setI2CAddress(SN_OLED_ADDR * 2);
  oledOk = oled.begin();
}

void ui::led(LedColor c) {
  uint8_t r = 0, g = 0, b = 0;
  switch (c) {
    case LedColor::OFF: break;
    case LedColor::WHITE: r = g = b = 60; break;
    case LedColor::GREEN: g = 80; break;
    case LedColor::BLUE: b = 90; break;
    case LedColor::YELLOW: r = 90; g = 60; break;
    case LedColor::RED: r = 100; break;
    case LedColor::MAGENTA: r = 80; b = 80; break;
  }
  ledWrite(PIN_LED_R, r);
  ledWrite(PIN_LED_G, g);
  ledWrite(PIN_LED_B, b);
}

void ui::beep(uint16_t ms) {
  digitalWrite(PIN_BUZZER, HIGH);
  delay(ms);  // pitido corto y ocasional; no afecta la operación
  digitalWrite(PIN_BUZZER, LOW);
}

void ui::show(const char* state, const sensonet::Reading* last, int rssi, uint32_t buffered, bool relayOn) {
  if (!oledOk) return;
  char line[32];
  oled.clearBuffer();
  oled.setFont(u8g2_font_6x10_tf);
  oled.drawStr(0, 9, "SensoNet");
  oled.drawStr(64, 9, state);
  oled.drawHLine(0, 12, 128);
  if (last) {
    oled.setFont(u8g2_font_logisoso16_tf);
    snprintf(line, sizeof(line), "%.1fC", last->temperature);
    oled.drawStr(0, 34, line);
    snprintf(line, sizeof(line), "%.0f%%", last->humidity);
    oled.drawStr(76, 34, line);
    oled.setFont(u8g2_font_6x10_tf);
    if (last->hasPressure) {
      snprintf(line, sizeof(line), "%.1f hPa", last->pressure);
      oled.drawStr(0, 48, line);
    }
  } else {
    oled.drawStr(0, 34, "Sin lecturas");
  }
  oled.setFont(u8g2_font_6x10_tf);
  snprintf(line, sizeof(line), "%ddBm B:%lu R:%s", rssi, static_cast<unsigned long>(buffered), relayOn ? "ON" : "OFF");
  oled.drawStr(0, 62, line);
  oled.sendBuffer();
}

// ============================ Relé ============================
static Preferences prefs;
static bool relayState = false;

static void relayApply(bool on) {
  digitalWrite(PIN_RELAY, (on ^ RELAY_ACTIVE_LOW) ? HIGH : LOW);
}

void relay::begin() {
  prefs.begin("sensonet", false);
  relayState = prefs.getBool("relay", false);  // se restaura tras un reinicio
  pinMode(PIN_RELAY, OUTPUT);
  relayApply(relayState);
}

void relay::set(bool on) {
  relayState = on;
  relayApply(on);
  prefs.putBool("relay", on);
}

bool relay::get() { return relayState; }

// ============================ LittleFS ============================
bool LittleFsStorage::begin(uint32_t sizeBytes) {
  if (!LittleFS.begin(true)) return false;  // true = formatear si no hay sistema de archivos
  if (!LittleFS.exists(path_)) {
    File f = LittleFS.open(path_, "w");
    if (!f) return false;
    uint8_t zeros[64] = {0};
    for (uint32_t written = 0; written < sizeBytes; written += sizeof(zeros)) {
      size_t n = (sizeBytes - written) < sizeof(zeros) ? (sizeBytes - written) : sizeof(zeros);
      if (f.write(zeros, n) != n) { f.close(); return false; }
    }
    f.close();
  }
  return true;
}

bool LittleFsStorage::read(uint32_t offset, uint8_t* buf, size_t len) {
  File f = LittleFS.open(path_, "r");
  if (!f) return false;
  bool ok = f.seek(offset) && f.read(buf, len) == len;
  f.close();
  return ok;
}

bool LittleFsStorage::write(uint32_t offset, const uint8_t* buf, size_t len) {
  File f = LittleFS.open(path_, "r+");
  if (!f) return false;
  bool ok = f.seek(offset) && f.write(buf, len) == len;
  f.close();
  return ok;
}
