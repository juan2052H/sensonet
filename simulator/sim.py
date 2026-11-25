"""Simulador de nodos SensoNet.

Se comporta como uno o varios ESP32: publica en los mismos tópicos, con el mismo esquema,
responde a los comandos y usa un buffer offline. Sirve para desarrollar sin hardware,
para las pruebas de extremo a extremo y para que la demo siempre tenga datos.

Ejemplos:
    python sim.py --nodos sim-sala,sim-lab
    python sim.py --escenario ola-de-calor        # dispara alertas en el primer nodo
    python sim.py --escenario corte-wifi --corte-min 10

Credenciales: MQTT_NODES="sim-sala:clave,sim-lab:clave" (las mismas que usa Mosquitto).
TLS: MQTT_CA_FILE=/ruta/ca.crt (si no se define, se conecta sin TLS: solo para pruebas locales).
"""
from __future__ import annotations

import argparse
import logging
import os
import random
import signal
import sys
import time

from sensonet_sim.model import ESCENARIOS, Environment
from sensonet_sim.node import SimNode

BASES = [(22.0, 60.0), (24.0, 55.0), (19.0, 68.0), (21.0, 62.0), (23.0, 58.0)]


def parse_credentials(spec: str) -> dict[str, str]:
    creds = {}
    for pair in filter(None, (p.strip() for p in spec.split(","))):
        user, sep, password = pair.partition(":")
        if sep and user and password:
            creds[user] = password
    return creds


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Simulador de nodos SensoNet")
    ap.add_argument("--nodos", default=os.getenv("SIM_NODOS", "sim-sala"),
                    help="códigos de nodo separados por coma")
    ap.add_argument("--escenario", choices=ESCENARIOS, default=os.getenv("SIM_ESCENARIO", "normal"))
    ap.add_argument("--intervalo", type=int, default=int(os.getenv("SIM_INTERVALO", "10")),
                    help="segundos entre lecturas")
    ap.add_argument("--corte-min", type=float, default=float(os.getenv("SIM_CORTE_MIN", "10")),
                    help="minutos sin WiFi en el escenario corte-wifi")
    ap.add_argument("--host", default=os.getenv("MQTT_HOST", "localhost"))
    ap.add_argument("--port", type=int, default=int(os.getenv("MQTT_PORT", "8883")))
    ap.add_argument("--ca", default=os.getenv("MQTT_CA_FILE"), help="certificado de la CA del broker")
    ap.add_argument("--semilla", type=int, default=None, help="semilla aleatoria (reproducible)")
    args = ap.parse_args(argv)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    log = logging.getLogger("sensonet.sim")
    creds = parse_credentials(os.getenv("MQTT_NODES", ""))
    fallback = os.getenv("MQTT_PASSWORD")
    rng = random.Random(args.semilla)

    codes = [c.strip() for c in args.nodos.split(",") if c.strip()]
    nodes = []
    for i, code in enumerate(codes):
        base_t, base_h = BASES[i % len(BASES)]
        first = i == 0  # los escenarios especiales afectan solo al primer nodo, para comparar
        env = Environment(base_temp=base_t, base_hum=base_h,
                          heat_wave=first and args.escenario == "ola-de-calor",
                          rng=random.Random(rng.random()))
        password = creds.get(code, fallback)
        if password is None:
            log.warning("Sin contraseña para %s: se intentará conectar sin autenticación", code)
        nodes.append(SimNode(code, password, env, host=args.host, port=args.port, ca_file=args.ca,
                             interval=args.intervalo,
                             cut_wifi=first and args.escenario == "corte-wifi",
                             cut_minutes=args.corte_min))

    log.info("Escenario '%s' con %d nodos (%s) → %s:%s %s", args.escenario, len(nodes), ", ".join(codes),
             args.host, args.port, "TLS" if args.ca else "SIN TLS")
    for n in nodes:
        n.start()

    running = True

    def _stop(*_):
        nonlocal running
        running = False

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)
    while running:
        now = time.time()
        for n in nodes:
            n.tick(now)
        time.sleep(1)

    log.info("Deteniendo simulador…")
    for n in nodes:
        n.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
