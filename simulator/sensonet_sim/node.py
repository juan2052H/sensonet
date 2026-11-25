"""Nodo virtual: se comporta como el ESP32 (mismos tópicos, esquema, LWT, comandos y buffer offline)."""
from __future__ import annotations

import json
import logging
import random
import ssl
import time

import paho.mqtt.client as mqtt
from paho.mqtt.packettypes import PacketTypes
from paho.mqtt.reasoncodes import ReasonCode

from .model import FIRMWARE, Environment, OfflineBuffer, Relay, telemetry, wifi_is_down

log = logging.getLogger("sensonet.sim")

BATCH = 20  # mensajes del buffer por lote al reconectar


class SimNode:
    def __init__(self, code: str, password: str | None, env: Environment, *, host: str, port: int,
                 ca_file: str | None, interval: int, cut_wifi: bool = False, cut_minutes: float = 10,
                 online_minutes: float = 3):
        self.code = code
        self.env = env
        self.interval = interval
        self.cut_wifi = cut_wifi
        self.cut_s = cut_minutes * 60
        self.online_s = online_minutes * 60
        self.buffer = OfflineBuffer(2000)
        self.relay = Relay()
        self.seq = 0
        self.rssi = random.randint(-70, -50)
        self.started = time.time()
        self.next_reading = time.time()
        self.next_status = 0.0
        self.host, self.port = host, port
        self.wifi_down = False
        self.connected = False

        self.base = f"sensonet/v1/{code}"
        # MQTT 5: permite simular un corte "sucio" que dispara el LWT (reason code 0x04).
        self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=code, protocol=mqtt.MQTTv5)
        if password is not None:
            self.client.username_pw_set(code, password)
        if ca_file:
            self.client.tls_set(ca_certs=ca_file, cert_reqs=ssl.CERT_REQUIRED, tls_version=ssl.PROTOCOL_TLS_CLIENT)
        self.client.will_set(f"{self.base}/status", json.dumps({"state": "offline"}), qos=1, retain=True)
        self.client.reconnect_delay_set(min_delay=1, max_delay=30)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect
        self.client.on_message = self._on_message

    # --- ciclo de vida -------------------------------------------------------------------------
    def start(self) -> None:
        self.client.connect_async(self.host, self.port, keepalive=30)
        self.client.loop_start()

    def stop(self) -> None:
        self.client.publish(f"{self.base}/status", self._status("offline"), qos=1, retain=True)
        self.client.disconnect()
        self.client.loop_stop()

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            log.error("[%s] conexión rechazada: %s", self.code, reason_code)
            return
        self.connected = True
        log.info("[%s] conectado al broker", self.code)
        client.subscribe([(f"{self.base}/cmd/relay", 1), (f"{self.base}/config", 1)])
        self.next_status = 0  # publicar estado de inmediato

    def _on_disconnect(self, client, userdata, flags, reason_code, properties):
        self.connected = False
        log.warning("[%s] desconectado (%s)", self.code, reason_code)

    def _on_message(self, client, userdata, msg):
        try:
            data = json.loads(msg.payload or b"{}")
        except json.JSONDecodeError:
            log.warning("[%s] mensaje inválido en %s", self.code, msg.topic)
            return
        if msg.topic.endswith("/cmd/relay"):
            ack = self.relay.handle(data)
            client.publish(f"{self.base}/cmd/ack", json.dumps(ack), qos=1)
            log.info("[%s] relé %s (comando %s)", self.code, "ON" if self.relay.on else "OFF", data.get("id"))
            self.next_status = 0
        elif msg.topic.endswith("/config"):
            interval = data.get("interval")
            if isinstance(interval, int) and 5 <= interval <= 3600:
                self.interval = interval
                log.info("[%s] nuevo intervalo de muestreo: %s s", self.code, interval)
                self.next_status = 0

    # --- tick (cada segundo) -------------------------------------------------------------------
    def tick(self, now: float) -> None:
        elapsed = now - self.started
        if self.cut_wifi:
            self._handle_wifi(wifi_is_down(elapsed, self.online_s, self.cut_s))

        if now >= self.next_reading:
            self.next_reading = now + self.interval
            self._measure(now, elapsed)

        online = self.connected and not self.wifi_down
        if online and len(self.buffer):
            self._flush_batch()
        if online and now >= self.next_status:
            self.next_status = now + 60
            self.client.publish(f"{self.base}/status", self._status("online"), qos=1, retain=True)

    def _measure(self, now: float, elapsed: float) -> None:
        local = time.localtime(now)
        hour = local.tm_hour + local.tm_min / 60
        t, h, p = self.env.sample(elapsed, hour)
        self.rssi = max(-90, min(-40, self.rssi + random.randint(-2, 2)))
        self.seq += 1
        payload = json.dumps(telemetry(self.seq, int(now * 1000), t, h, p, self.rssi), separators=(",", ":"))
        if self.connected and not self.wifi_down:
            info = self.client.publish(f"{self.base}/telemetry", payload, qos=1)
            if info.rc == mqtt.MQTT_ERR_SUCCESS:
                return
        self.buffer.push(payload)  # offline-first: no se pierde

    def _flush_batch(self) -> None:
        batch = self.buffer.drain(BATCH)
        for i, payload in enumerate(batch):
            info = self.client.publish(f"{self.base}/telemetry", payload, qos=1)
            if info.rc != mqtt.MQTT_ERR_SUCCESS:
                self.buffer.requeue_front(batch[i:])
                return
        if batch:
            log.info("[%s] reenviadas %d lecturas del buffer (%d pendientes)", self.code, len(batch), len(self.buffer))

    def _handle_wifi(self, down: bool) -> None:
        if down and not self.wifi_down:
            self.wifi_down = True
            log.warning("[%s] corte de WiFi simulado: se sigue midiendo y guardando en el buffer", self.code)
            # Desconexión con Will: el broker publica el LWT "offline" como en un corte real.
            self.client.disconnect(reasoncode=ReasonCode(PacketTypes.DISCONNECT, identifier=4))
            self.client.loop_stop()  # tras una desconexión explícita el hilo de red de paho termina
        elif not down and self.wifi_down:
            self.wifi_down = False
            log.info("[%s] WiFi restablecido; reconectando (buffer: %d lecturas)", self.code, len(self.buffer))
            self.client.connect_async(self.host, self.port, keepalive=30)
            self.client.loop_start()

    def _status(self, state: str) -> str:
        return json.dumps({
            "state": state,
            "fw": FIRMWARE,
            "rssi": self.rssi,
            "buffered": len(self.buffer),
            "dropped": self.buffer.dropped,
            "relay": self.relay.on,
            "interval": self.interval,
        })
