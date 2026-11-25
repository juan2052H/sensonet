#include "telemetry_codec.h"

#include <inttypes.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

namespace sensonet {

static bool inRange(float v, float lo, float hi) { return v >= lo && v <= hi; }

ReadingStatus validateReading(const Reading& r) {
  if (isnan(r.temperature) || isnan(r.humidity) || (r.hasPressure && isnan(r.pressure))) {
    return ReadingStatus::NOT_A_NUMBER;
  }
  if (!inRange(r.temperature, T_MIN, T_MAX)) return ReadingStatus::TEMPERATURE_OUT_OF_RANGE;
  if (!inRange(r.humidity, H_MIN, H_MAX)) return ReadingStatus::HUMIDITY_OUT_OF_RANGE;
  if (r.hasPressure && !inRange(r.pressure, P_MIN, P_MAX)) return ReadingStatus::PRESSURE_OUT_OF_RANGE;
  return ReadingStatus::OK;
}

const char* readingStatusText(ReadingStatus s) {
  switch (s) {
    case ReadingStatus::OK: return "ok";
    case ReadingStatus::NOT_A_NUMBER: return "sensor sin respuesta (NaN)";
    case ReadingStatus::TEMPERATURE_OUT_OF_RANGE: return "temperatura fuera de rango";
    case ReadingStatus::HUMIDITY_OUT_OF_RANGE: return "humedad fuera de rango";
    case ReadingStatus::PRESSURE_OUT_OF_RANGE: return "presion fuera de rango";
  }
  return "?";
}

static size_t finish(int n, size_t cap) {
  return (n < 0 || static_cast<size_t>(n) >= cap) ? 0 : static_cast<size_t>(n);
}

size_t encodeTelemetry(char* out, size_t cap, uint32_t seq, uint64_t tsMs, const Reading& r, int rssi) {
  int n;
  if (r.hasPressure) {
    n = snprintf(out, cap,
                 "{\"v\":1,\"seq\":%" PRIu32 ",\"ts\":%" PRIu64 ",\"t\":%.2f,\"h\":%.1f,\"p\":%.1f,\"rssi\":%d}",
                 seq, tsMs, r.temperature, r.humidity, r.pressure, rssi);
  } else {
    n = snprintf(out, cap, "{\"v\":1,\"seq\":%" PRIu32 ",\"ts\":%" PRIu64 ",\"t\":%.2f,\"h\":%.1f,\"rssi\":%d}",
                 seq, tsMs, r.temperature, r.humidity, rssi);
  }
  return finish(n, cap);
}

size_t encodeStatus(char* out, size_t cap, bool online, const char* fw, int rssi, uint32_t buffered,
                    uint32_t dropped, bool relay, uint32_t intervalS) {
  int n = snprintf(out, cap,
                   "{\"state\":\"%s\",\"fw\":\"%s\",\"rssi\":%d,\"buffered\":%" PRIu32 ",\"dropped\":%" PRIu32
                   ",\"relay\":%s,\"interval\":%" PRIu32 "}",
                   online ? "online" : "offline", fw, rssi, buffered, dropped, relay ? "true" : "false", intervalS);
  return finish(n, cap);
}

// Los ids de comando son UUID (hex y guiones); por seguridad se descarta cualquier otro carácter.
static void sanitizeId(const char* in, char* out, size_t cap) {
  size_t j = 0;
  for (size_t i = 0; in && in[i] && j + 1 < cap; i++) {
    char c = in[i];
    bool ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-';
    if (ok) out[j++] = c;
  }
  out[j] = '\0';
}

size_t encodeAck(char* out, size_t cap, const char* id, bool ok, bool relay, bool repeated, const char* error) {
  char safeId[48];
  sanitizeId(id, safeId, sizeof(safeId));
  int n;
  if (!ok) {
    char safeErr[64];
    sanitizeId(error ? error : "error", safeErr, sizeof(safeErr));
    n = snprintf(out, cap, "{\"id\":\"%s\",\"ok\":false,\"relay\":%s,\"error\":\"%s\"}", safeId,
                 relay ? "true" : "false", safeErr);
  } else if (repeated) {
    n = snprintf(out, cap, "{\"id\":\"%s\",\"ok\":true,\"relay\":%s,\"repeated\":true}", safeId,
                 relay ? "true" : "false");
  } else {
    n = snprintf(out, cap, "{\"id\":\"%s\",\"ok\":true,\"relay\":%s}", safeId, relay ? "true" : "false");
  }
  return finish(n, cap);
}

}  // namespace sensonet
