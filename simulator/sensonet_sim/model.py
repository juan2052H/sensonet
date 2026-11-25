"""Lógica pura del simulador (sin red): entorno físico, buffer offline, relé y esquema de telemetría.

Se mantiene separada del cliente MQTT para poder probarla con pytest.
"""
from __future__ import annotations

import math
import random
from collections import deque
from dataclasses import dataclass, field

SCHEMA_VERSION = 1
FIRMWARE = "sim-1.0.0"

# Rangos físicos válidos (los mismos que valida el backend)
T_RANGE = (-40.0, 85.0)
H_RANGE = (0.0, 100.0)
P_RANGE = (300.0, 1100.0)

ESCENARIOS = ("normal", "ola-de-calor", "corte-wifi")

# Duración del ciclo de la ola de calor (s): calienta, se mantiene sobre 30 °C más de 5 min,
# tiene un pico sobre 35 °C y vuelve a bajar de 28 °C.
HEAT_CYCLE_S = 1200


def clamp(v: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, v))


@dataclass
class Environment:
    """Modelo sencillo del ambiente de una habitación en Medellín (≈ 850 hPa)."""

    base_temp: float = 22.0
    base_hum: float = 60.0
    base_pressure: float = 851.0
    heat_wave: bool = False
    rng: random.Random = field(default_factory=random.Random)

    def daily_temp(self, hour: float) -> float:
        # Mínimo al amanecer, máximo hacia las 3 p. m.
        return self.base_temp + 4.0 * math.sin((hour - 9.0) / 24.0 * 2.0 * math.pi)

    def heat_offset(self, elapsed_s: float, normal: float) -> float:
        """Temperatura durante la ola de calor según la posición en el ciclo."""
        c = elapsed_s % HEAT_CYCLE_S
        hold = 32.5
        if c < 60:
            return normal
        if c < 180:  # sube
            return normal + (hold - normal) * (c - 60) / 120
        if c < 720:  # se mantiene, con un pico crítico
            if 400 <= c < 500:
                return 36.0
            return hold
        if c < 900:  # baja hasta 26 °C
            return hold + (26.0 - hold) * (c - 720) / 180
        return normal

    def sample(self, elapsed_s: float, hour: float) -> tuple[float, float, float]:
        t = self.daily_temp(hour)
        if self.heat_wave:
            t = self.heat_offset(elapsed_s, t)
        t += self.rng.gauss(0, 0.15)
        h = self.base_hum - (t - self.base_temp) * 2.0 + self.rng.gauss(0, 0.5)
        p = self.base_pressure + 1.5 * math.sin(hour / 12.0 * 2.0 * math.pi) + self.rng.gauss(0, 0.2)
        return (
            round(clamp(t, *T_RANGE), 2),
            round(clamp(h, 5.0, 95.0), 1),
            round(clamp(p, *P_RANGE), 1),
        )


def telemetry(seq: int, ts_ms: int, t: float, h: float, p: float | None, rssi: int) -> dict:
    """Mensaje con el esquema v1 (idéntico al del firmware)."""
    msg = {"v": SCHEMA_VERSION, "seq": seq, "ts": ts_ms, "t": t, "h": h}
    if p is not None:
        msg["p"] = p
    msg["rssi"] = rssi
    return msg


class OfflineBuffer:
    """Cola circular de capacidad fija: si se llena descarta la más antigua y lo cuenta."""

    def __init__(self, capacity: int = 2000):
        self._q: deque[str] = deque(maxlen=capacity)
        self.dropped = 0

    def push(self, payload: str) -> None:
        if len(self._q) == self._q.maxlen:
            self.dropped += 1
        self._q.append(payload)

    def drain(self, max_items: int = 20) -> list[str]:
        out = []
        while self._q and len(out) < max_items:
            out.append(self._q.popleft())
        return out

    def requeue_front(self, items: list[str]) -> None:
        """Devuelve al frente los mensajes que no se pudieron enviar (sin alterar el orden)."""
        for item in reversed(items):
            self._q.appendleft(item)

    def __len__(self) -> int:
        return len(self._q)


class Relay:
    """Relé con comandos idempotentes: un id repetido no se vuelve a ejecutar."""

    def __init__(self, memory: int = 16):
        self.on = False
        self._seen: deque[str] = deque(maxlen=memory)
        self._results: dict[str, bool] = {}

    def handle(self, cmd: dict) -> dict:
        cid = cmd.get("id")
        if not isinstance(cid, str) or not isinstance(cmd.get("on"), bool):
            return {"id": cid, "ok": False, "error": "comando inválido", "relay": self.on}
        if cid in self._results:
            return {"id": cid, "ok": True, "relay": self._results[cid], "repeated": True}
        self.on = cmd["on"]
        if len(self._seen) == self._seen.maxlen:
            self._results.pop(self._seen[0], None)
        self._seen.append(cid)
        self._results[cid] = self.on
        return {"id": cid, "ok": True, "relay": self.on}


def wifi_is_down(elapsed_s: float, online_s: float, cut_s: float) -> bool:
    """Escenario corte-wifi: ciclos de `online_s` conectado seguidos de `cut_s` sin red."""
    cycle = online_s + cut_s
    return (elapsed_s % cycle) >= online_s
