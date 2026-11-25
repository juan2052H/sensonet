// Pruebas nativas (PC) de la lógica pura del firmware: buffer circular, JSON y validación.
// Ejecutar: firmware/test/run_tests.sh  (o con Docker: ver firmware/README.md)
#include <cmath>
#include <cstdio>
#include <cstring>
#include <map>
#include <string>
#include <vector>

#include "offline_ring.h"
#include "recent_ids.h"
#include "telemetry_codec.h"

using namespace sensonet;

static int failures = 0, checks = 0;
#define CHECK(cond)                                                       \
  do {                                                                    \
    checks++;                                                             \
    if (!(cond)) {                                                        \
      failures++;                                                         \
      std::printf("  FALLO %s:%d: %s\n", __FILE__, __LINE__, #cond);      \
    }                                                                     \
  } while (0)
#define TEST(name) static void name()
#define RUN(name)                      \
  do {                                 \
    std::printf("- %s\n", #name);      \
    name();                            \
  } while (0)

// Almacenamiento en memoria que simula LittleFS (y puede "fallar" a propósito)
class MemStorage : public BlockStorage {
 public:
  explicit MemStorage(size_t size) : data(size, 0xFF) {}
  bool read(uint32_t off, uint8_t* buf, size_t len) override {
    if (off + len > data.size()) return false;
    std::memcpy(buf, data.data() + off, len);
    return true;
  }
  bool write(uint32_t off, const uint8_t* buf, size_t len) override {
    if (failWrites || off + len > data.size()) return false;
    std::memcpy(data.data() + off, buf, len);
    return true;
  }
  std::vector<uint8_t> data;
  bool failWrites = false;
};

static std::string popString(OfflineRing& ring) {
  char buf[256];
  size_t n = ring.peek(buf, sizeof(buf));
  if (n == 0) return "";
  ring.pop();
  return std::string(buf, n);
}

TEST(ring_keeps_fifo_order) {
  MemStorage mem(OfflineRing::HEADER_SIZE + 5 * 64);
  OfflineRing ring(mem, 5, 64);
  CHECK(ring.begin());
  CHECK(ring.empty());
  CHECK(ring.push("a", 1));
  CHECK(ring.push("bb", 2));
  CHECK(ring.push("ccc", 3));
  CHECK(ring.size() == 3);
  CHECK(popString(ring) == "a");
  CHECK(popString(ring) == "bb");
  CHECK(popString(ring) == "ccc");
  CHECK(ring.empty());
  CHECK(!ring.pop());
}

TEST(ring_drops_oldest_when_full_and_counts) {
  MemStorage mem(OfflineRing::HEADER_SIZE + 3 * 64);
  OfflineRing ring(mem, 3, 64);
  CHECK(ring.begin());
  for (int i = 0; i < 5; i++) {
    std::string s = "m" + std::to_string(i);
    CHECK(ring.push(s.c_str(), s.size()));
  }
  CHECK(ring.size() == 3);
  CHECK(ring.dropped() == 2);
  CHECK(popString(ring) == "m2");
  CHECK(popString(ring) == "m3");
  CHECK(popString(ring) == "m4");
}

TEST(ring_survives_reboot) {
  MemStorage mem(OfflineRing::HEADER_SIZE + 10 * 64);
  {
    OfflineRing ring(mem, 10, 64);
    CHECK(ring.begin());
    ring.push("uno", 3);
    ring.push("dos", 3);
    ring.push("tres", 4);
    ring.pop();
  }
  OfflineRing again(mem, 10, 64);  // "reinicio": nueva instancia sobre el mismo almacenamiento
  CHECK(again.begin());
  CHECK(again.size() == 2);
  CHECK(popString(again) == "dos");
  CHECK(popString(again) == "tres");
}

TEST(ring_resets_on_incompatible_header) {
  MemStorage mem(OfflineRing::HEADER_SIZE + 10 * 64);
  {
    OfflineRing ring(mem, 10, 64);
    ring.begin();
    ring.push("x", 1);
  }
  OfflineRing different(mem, 8, 64);  // otra capacidad: formato incompatible
  CHECK(different.begin());
  CHECK(different.empty());
}

TEST(ring_rejects_oversized_and_handles_storage_errors) {
  MemStorage mem(OfflineRing::HEADER_SIZE + 4 * 16);
  OfflineRing ring(mem, 4, 16);
  CHECK(ring.begin());
  CHECK(!ring.push("0123456789abcdef", 16));  // no cabe (14 bytes útiles)
  CHECK(!ring.push("", 0));
  mem.failWrites = true;
  CHECK(!ring.push("abc", 3));
  CHECK(ring.empty());
}

TEST(ring_holds_30_minutes_offline_without_gaps) {
  // 30 min a 10 s = 180 lecturas; con capacidad 2000 no se pierde ninguna y salen en orden.
  MemStorage mem(OfflineRing::HEADER_SIZE + 2000u * 192u);
  OfflineRing ring(mem, 2000, 192);
  CHECK(ring.begin());
  char payload[192];
  Reading r{24.5f, 55.0f, 851.0f, true};
  for (uint32_t s = 1; s <= 180; s++) {
    size_t n = encodeTelemetry(payload, sizeof(payload), s, 1795564800000ULL + s * 10000ULL, r, -60);
    CHECK(ring.push(payload, n));
  }
  CHECK(ring.dropped() == 0);
  uint32_t expected = 1;
  bool ordered = true;
  while (!ring.empty()) {
    std::string m = popString(ring);
    std::string tag = "\"seq\":" + std::to_string(expected) + ",";
    if (m.find(tag) == std::string::npos) ordered = false;
    expected++;
  }
  CHECK(ordered);
  CHECK(expected == 181);
}

TEST(telemetry_json_matches_schema_v1) {
  char out[256];
  Reading r{24.61f, 58.2f, 851.3f, true};
  size_t n = encodeTelemetry(out, sizeof(out), 18452, 1795564800123ULL, r, -61);
  CHECK(n > 0);
  CHECK(std::string(out) == "{\"v\":1,\"seq\":18452,\"ts\":1795564800123,\"t\":24.61,\"h\":58.2,\"p\":851.3,\"rssi\":-61}");
  Reading sht{22.0f, 50.0f, NAN, false};
  encodeTelemetry(out, sizeof(out), 1, 1000, sht, -70);
  CHECK(std::strstr(out, "\"p\"") == nullptr);
  CHECK(encodeTelemetry(out, 20, 1, 1000, r, -61) == 0);  // no cabe: no se trunca a medias
}

TEST(reading_validation_uses_physical_ranges) {
  CHECK(validateReading({24.0f, 50.0f, 850.0f, true}) == ReadingStatus::OK);
  CHECK(validateReading({24.0f, 140.0f, 850.0f, true}) == ReadingStatus::HUMIDITY_OUT_OF_RANGE);
  CHECK(validateReading({90.0f, 50.0f, 850.0f, true}) == ReadingStatus::TEMPERATURE_OUT_OF_RANGE);
  CHECK(validateReading({24.0f, 50.0f, 200.0f, true}) == ReadingStatus::PRESSURE_OUT_OF_RANGE);
  CHECK(validateReading({24.0f, 50.0f, 200.0f, false}) == ReadingStatus::OK);
  CHECK(validateReading({NAN, 50.0f, 850.0f, true}) == ReadingStatus::NOT_A_NUMBER);
}

TEST(status_and_ack_json) {
  char out[200];
  encodeStatus(out, sizeof(out), true, "1.0.0", -55, 3, 1, true, 10);
  CHECK(std::string(out) ==
        "{\"state\":\"online\",\"fw\":\"1.0.0\",\"rssi\":-55,\"buffered\":3,\"dropped\":1,\"relay\":true,\"interval\":10}");
  encodeAck(out, sizeof(out), "1b4e28ba-2fa1-11d2-883f-0016d3cca427", true, false, false, nullptr);
  CHECK(std::string(out) == "{\"id\":\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\",\"ok\":true,\"relay\":false}");
  encodeAck(out, sizeof(out), "abc\"},\"x", true, true, true, nullptr);  // intento de inyección en el JSON
  CHECK(std::string(out) == "{\"id\":\"abcx\",\"ok\":true,\"relay\":true,\"repeated\":true}");
}

TEST(recent_ids_make_commands_idempotent) {
  RecentIds<3> ids;
  CHECK(!ids.contains("a"));
  ids.remember("a");
  ids.remember("a");
  CHECK(ids.size() == 1);
  ids.remember("b");
  ids.remember("c");
  CHECK(ids.contains("a"));
  ids.remember("d");  // desplaza al más antiguo
  CHECK(!ids.contains("a"));
  CHECK(ids.contains("d"));
}

TEST(backoff_grows_and_caps) {
  CHECK(backoffMs(0) == 1000);
  CHECK(backoffMs(1) == 2000);
  CHECK(backoffMs(3) == 8000);
  CHECK(backoffMs(20) == 60000);
}

int main() {
  RUN(ring_keeps_fifo_order);
  RUN(ring_drops_oldest_when_full_and_counts);
  RUN(ring_survives_reboot);
  RUN(ring_resets_on_incompatible_header);
  RUN(ring_rejects_oversized_and_handles_storage_errors);
  RUN(ring_holds_30_minutes_offline_without_gaps);
  RUN(telemetry_json_matches_schema_v1);
  RUN(reading_validation_uses_physical_ranges);
  RUN(status_and_ack_json);
  RUN(recent_ids_make_commands_idempotent);
  RUN(backoff_grows_and_caps);
  std::printf("\n%d comprobaciones, %d fallos\n", checks, failures);
  return failures == 0 ? 0 : 1;
}
