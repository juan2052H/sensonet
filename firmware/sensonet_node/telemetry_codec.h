// Lógica pura (sin Arduino): validación de rango físico y JSON de telemetría v1.
// Se compila igual en el ESP32 y en el PC (pruebas en firmware/test).
#pragma once

#include <stddef.h>
#include <stdint.h>

namespace sensonet {

struct Reading {
  float temperature;   // °C
  float humidity;      // % HR
  float pressure;      // hPa (solo si hasPressure)
  bool hasPressure;
};

enum class ReadingStatus {
  OK,
  NOT_A_NUMBER,
  TEMPERATURE_OUT_OF_RANGE,
  HUMIDITY_OUT_OF_RANGE,
  PRESSURE_OUT_OF_RANGE,
};

// Rangos físicos (los mismos que valida el backend)
constexpr float T_MIN = -40.0f, T_MAX = 85.0f;
constexpr float H_MIN = 0.0f, H_MAX = 100.0f;
constexpr float P_MIN = 300.0f, P_MAX = 1100.0f;

ReadingStatus validateReading(const Reading& r);
const char* readingStatusText(ReadingStatus s);

// Escribe {"v":1,"seq":..,"ts":..,"t":..,"h":..,"p":..,"rssi":..} en out.
// Devuelve la longitud (sin el '\0') o 0 si no cabe en cap.
size_t encodeTelemetry(char* out, size_t cap, uint32_t seq, uint64_t tsMs, const Reading& r, int rssi);

// Estado retenido en sensonet/v1/{nodo}/status
size_t encodeStatus(char* out, size_t cap, bool online, const char* fw, int rssi, uint32_t buffered,
                    uint32_t dropped, bool relay, uint32_t intervalS);

// Confirmación de comando en sensonet/v1/{nodo}/cmd/ack
size_t encodeAck(char* out, size_t cap, const char* id, bool ok, bool relay, bool repeated, const char* error);

}  // namespace sensonet
