// Memoria de los últimos ids de comando ejecutados: un comando repetido (reintento QoS 1 o
// reenvío) no se vuelve a ejecutar (idempotencia). Lógica pura.
#pragma once

#include <stddef.h>
#include <string.h>

namespace sensonet {

template <size_t N, size_t ID_LEN = 40>
class RecentIds {
 public:
  bool contains(const char* id) const {
    for (size_t i = 0; i < count_; i++) {
      if (strncmp(ids_[i], id, ID_LEN) == 0) return true;
    }
    return false;
  }

  void remember(const char* id) {
    if (contains(id)) return;
    strncpy(ids_[next_], id, ID_LEN);
    ids_[next_][ID_LEN] = '\0';
    next_ = (next_ + 1) % N;
    if (count_ < N) count_++;
  }

  size_t size() const { return count_; }

 private:
  char ids_[N][ID_LEN + 1] = {};
  size_t next_ = 0;
  size_t count_ = 0;
};

// Espera creciente entre reintentos de conexión: 1 s, 2 s, 4 s ... hasta maxMs.
inline unsigned long backoffMs(unsigned attempt, unsigned long baseMs = 1000, unsigned long maxMs = 60000) {
  unsigned long d = baseMs;
  for (unsigned i = 0; i < attempt && d < maxMs; i++) d *= 2;
  return d > maxMs ? maxMs : d;
}

}  // namespace sensonet
