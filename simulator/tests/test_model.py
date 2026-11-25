import json
import random

from sensonet_sim.model import (
    HEAT_CYCLE_S,
    Environment,
    OfflineBuffer,
    Relay,
    telemetry,
    wifi_is_down,
)
from sim import parse_credentials


def test_buffer_keeps_order_and_counts_dropped():
    b = OfflineBuffer(capacity=3)
    for i in range(5):
        b.push(str(i))
    assert len(b) == 3
    assert b.dropped == 2
    assert b.drain(2) == ["2", "3"]
    assert b.drain(20) == ["4"]
    assert len(b) == 0


def test_buffer_requeue_preserves_order():
    b = OfflineBuffer(10)
    for i in range(5):
        b.push(str(i))
    batch = b.drain(3)
    b.requeue_front(batch[1:])
    assert b.drain(10) == ["1", "2", "3", "4"]


def test_buffer_holds_30_minutes_at_10_seconds():
    b = OfflineBuffer(2000)
    for i in range(180):  # 30 min
        b.push(str(i))
    assert b.dropped == 0
    out = []
    while len(b):
        out.extend(b.drain(20))
    assert out == [str(i) for i in range(180)]


def test_telemetry_schema_v1():
    msg = telemetry(7, 1795564800123, 24.61, 58.2, 851.3, -61)
    assert msg == {"v": 1, "seq": 7, "ts": 1795564800123, "t": 24.61, "h": 58.2, "p": 851.3, "rssi": -61}
    assert "p" not in telemetry(1, 1, 20.0, 50.0, None, -60)
    assert len(json.dumps(msg)) < 256


def test_normal_environment_stays_in_physical_range():
    env = Environment(rng=random.Random(1))
    for s in range(0, 86400, 300):
        t, h, p = env.sample(s, (s / 3600) % 24)
        assert 10 < t < 35
        assert 5 <= h <= 95
        assert 840 < p < 860


def test_heat_wave_exceeds_30_for_more_than_5_minutes_then_recovers():
    env = Environment(heat_wave=True, rng=random.Random(2))
    temps = [env.sample(s, 12.0)[0] for s in range(0, HEAT_CYCLE_S, 10)]
    above = [t > 30 for t in temps]
    longest = run = 0
    for a in above:
        run = run + 1 if a else 0
        longest = max(longest, run)
    assert longest * 10 >= 300 + 60  # margen sobre la regla de 5 min
    assert max(temps) > 35  # dispara la regla crítica
    first_hot = above.index(True)
    assert any(t < 28 for t in temps[first_hot:])  # se recupera (histéresis)


def test_relay_is_idempotent():
    r = Relay()
    assert r.handle({"id": "a", "on": True}) == {"id": "a", "ok": True, "relay": True}
    assert r.handle({"id": "b", "on": False})["relay"] is False
    repeated = r.handle({"id": "a", "on": True})
    assert repeated["repeated"] is True
    assert r.on is False  # el comando repetido no se volvió a ejecutar
    assert r.handle({"id": "c"})["ok"] is False


def test_relay_forgets_old_ids():
    r = Relay(memory=2)
    r.handle({"id": "a", "on": True})
    r.handle({"id": "b", "on": False})
    r.handle({"id": "c", "on": False})
    assert "repeated" not in r.handle({"id": "a", "on": True})


def test_wifi_cut_cycle():
    assert not wifi_is_down(0, 180, 600)
    assert not wifi_is_down(179, 180, 600)
    assert wifi_is_down(180, 180, 600)
    assert wifi_is_down(779, 180, 600)
    assert not wifi_is_down(780, 180, 600)


def test_parse_credentials():
    assert parse_credentials("a:1, b:dos:tres,,malo,c:") == {"a": "1", "b": "dos:tres"}
