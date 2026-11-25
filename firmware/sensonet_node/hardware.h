// Módulos que dependen del hardware: sensores, reloj, OLED/LED/buzzer, relé y almacenamiento.
#pragma once

#include <Arduino.h>

#include "offline_ring.h"
#include "telemetry_codec.h"

// ---------- Sensores ----------
namespace sensors {
bool begin();
bool present();
// Devuelve false si el sensor no respondió; la validación de rango se hace aparte.
bool read(sensonet::Reading& out);
}  // namespace sensors

// ---------- Reloj (NTP + DS3231) ----------
namespace clockx {
void begin(const char* ntp1, const char* ntp2);
// Llamar periódicamente: cuando NTP sincroniza, ajusta el RTC.
void loop();
// Epoch en ms (UTC) o 0 si todavía no se conoce la hora.
uint64_t epochMs();
bool synced();
}  // namespace clockx

// ---------- Indicadores locales ----------
enum class LedColor { OFF, WHITE, GREEN, BLUE, YELLOW, RED, MAGENTA };

namespace ui {
void begin();
void led(LedColor c);
void beep(uint16_t ms);
// Pantalla: estado + últimos valores
void show(const char* state, const sensonet::Reading* last, int rssi, uint32_t buffered, bool relay);
}  // namespace ui

// ---------- Relé con estado persistente (Preferences) ----------
namespace relay {
void begin();
void set(bool on);
bool get();
}  // namespace relay

// ---------- Almacenamiento del buffer en LittleFS ----------
class LittleFsStorage : public sensonet::BlockStorage {
 public:
  explicit LittleFsStorage(const char* path) : path_(path) {}
  bool begin(uint32_t sizeBytes);
  bool read(uint32_t offset, uint8_t* buf, size_t len) override;
  bool write(uint32_t offset, const uint8_t* buf, size_t len) override;

 private:
  const char* path_;
};
