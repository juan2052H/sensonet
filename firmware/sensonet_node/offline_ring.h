// Cola circular persistente de tamaño fijo para el buffer offline (offline-first).
// Lógica pura: trabaja sobre una interfaz BlockStorage (LittleFS en el ESP32, memoria en las pruebas).
#pragma once

#include <stddef.h>
#include <stdint.h>

namespace sensonet {

class BlockStorage {
 public:
  virtual ~BlockStorage() = default;
  virtual bool read(uint32_t offset, uint8_t* buf, size_t len) = 0;
  virtual bool write(uint32_t offset, const uint8_t* buf, size_t len) = 0;
  virtual bool flush() { return true; }
};

class OfflineRing {
 public:
  static constexpr uint32_t MAGIC = 0x534E4252;  // "SNBR"
  static constexpr uint16_t VERSION = 1;
  static constexpr uint32_t HEADER_SIZE = 32;

  // capacity: número de lecturas; slotSize: bytes por lectura (incluye 2 bytes de longitud).
  OfflineRing(BlockStorage& storage, uint32_t capacity, uint16_t slotSize = 192);

  // Carga la cabecera existente o inicializa una cola vacía. Devuelve false si el almacenamiento falla.
  bool begin();

  // Guarda un mensaje. Si la cola está llena descarta el más antiguo y lo cuenta en dropped().
  bool push(const char* payload, size_t len);

  // Copia el mensaje más antiguo en out ('\0' final). Devuelve su longitud o 0 si está vacía.
  size_t peek(char* out, size_t cap);

  // Elimina el mensaje más antiguo (después de publicarlo con éxito).
  bool pop();

  uint32_t size() const { return count_; }
  uint32_t capacity() const { return capacity_; }
  uint32_t dropped() const { return dropped_; }
  bool empty() const { return count_ == 0; }
  uint32_t storageBytes() const { return HEADER_SIZE + capacity_ * slotSize_; }

 private:
  bool saveHeader();
  uint32_t slotOffset(uint32_t index) const { return HEADER_SIZE + index * slotSize_; }

  BlockStorage& storage_;
  uint32_t capacity_;
  uint16_t slotSize_;
  uint32_t head_ = 0;   // índice del más antiguo
  uint32_t count_ = 0;
  uint32_t dropped_ = 0;
};

}  // namespace sensonet
