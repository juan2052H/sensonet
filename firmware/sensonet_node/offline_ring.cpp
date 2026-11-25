#include "offline_ring.h"

#include <string.h>

namespace sensonet {

namespace {
struct Header {
  uint32_t magic;
  uint16_t version;
  uint16_t slotSize;
  uint32_t capacity;
  uint32_t head;
  uint32_t count;
  uint32_t dropped;
  uint32_t reserved[2];
};
static_assert(sizeof(Header) == OfflineRing::HEADER_SIZE, "la cabecera debe ocupar 32 bytes");
}  // namespace

OfflineRing::OfflineRing(BlockStorage& storage, uint32_t capacity, uint16_t slotSize)
    : storage_(storage), capacity_(capacity), slotSize_(slotSize) {}

bool OfflineRing::begin() {
  Header h{};
  if (storage_.read(0, reinterpret_cast<uint8_t*>(&h), sizeof(h)) && h.magic == MAGIC && h.version == VERSION &&
      h.slotSize == slotSize_ && h.capacity == capacity_ && h.head < capacity_ && h.count <= capacity_) {
    head_ = h.head;
    count_ = h.count;
    dropped_ = h.dropped;
    return true;
  }
  // Primera vez o formato distinto: cola vacía.
  head_ = count_ = dropped_ = 0;
  return saveHeader();
}

bool OfflineRing::saveHeader() {
  Header h{};
  h.magic = MAGIC;
  h.version = VERSION;
  h.slotSize = slotSize_;
  h.capacity = capacity_;
  h.head = head_;
  h.count = count_;
  h.dropped = dropped_;
  return storage_.write(0, reinterpret_cast<const uint8_t*>(&h), sizeof(h)) && storage_.flush();
}

bool OfflineRing::push(const char* payload, size_t len) {
  if (len == 0 || len > static_cast<size_t>(slotSize_ - 2)) return false;
  if (count_ == capacity_) {  // llena: se descarta la más antigua
    head_ = (head_ + 1) % capacity_;
    count_--;
    dropped_++;
  }
  uint32_t tail = (head_ + count_) % capacity_;
  uint8_t slot[2];
  slot[0] = static_cast<uint8_t>(len & 0xFF);
  slot[1] = static_cast<uint8_t>((len >> 8) & 0xFF);
  if (!storage_.write(slotOffset(tail), slot, 2)) return false;
  if (!storage_.write(slotOffset(tail) + 2, reinterpret_cast<const uint8_t*>(payload), len)) return false;
  count_++;
  return saveHeader();
}

size_t OfflineRing::peek(char* out, size_t cap) {
  if (count_ == 0 || cap == 0) return 0;
  uint8_t lenBytes[2];
  if (!storage_.read(slotOffset(head_), lenBytes, 2)) return 0;
  size_t len = lenBytes[0] | (static_cast<size_t>(lenBytes[1]) << 8);
  if (len == 0 || len > static_cast<size_t>(slotSize_ - 2) || len + 1 > cap) return 0;
  if (!storage_.read(slotOffset(head_) + 2, reinterpret_cast<uint8_t*>(out), len)) return 0;
  out[len] = '\0';
  return len;
}

bool OfflineRing::pop() {
  if (count_ == 0) return false;
  head_ = (head_ + 1) % capacity_;
  count_--;
  return saveHeader();
}

}  // namespace sensonet
