# ADR 0007: Firmware: sketch de Arduino IDE (no PlatformIO) y QoS de PubSubClient

Estado: aceptada · Fecha: 2026-11-24

## Contexto
La guía original proponía PlatformIO. El nodo físico no se va a construir por ahora; el firmware debe
poder abrirse y compilarse en Arduino IDE.

## Decisión
- Sketch de Arduino IDE (`firmware/sensonet_node/`) con archivos planos.
- La lógica pura (cola circular, JSON, validación, ids recientes, espera creciente) no incluye `Arduino.h`
  y se prueba en el PC con g++ (`firmware/test/`).
- La CI compila con `arduino-cli`, que usa el mismo compilador, para BME280 y SHT31.
- Esquema de particiones **Huge APP** (3 MB de programa, 1 MB de LittleFS): con TLS, WiFiManager y U8g2
  el programa ocupa ≈ 1,29 MB y no cabe en la partición por defecto. El buffer necesita ≈ 384 KB.
  Consecuencia: no hay espacio para OTA; si se agrega, hará falta una tabla de particiones a medida.

## PubSubClient y QoS
PubSubClient solo **publica con QoS 0**. Para que no se pierdan lecturas:
1. Una lectura solo cuenta como enviada si `publish()` devolvió `true` con la conexión activa; si no, va al buffer.
2. El nodo recuerda las últimas 6 lecturas publicadas y, cuando detecta que perdió la conexión, las vuelve
   a encolar. El backend deduplica por `(nodo, ts, seq)` (ADR 0008), así que reenviarlas no duplica datos.

Riesgo residual: lecturas perdidas en vuelo si la caída ocurre más de 6 lecturas (≈ 1 min) después de
publicarlas sin que el nodo lo note. Mejora futura: migrar a `espMqttClient`, que soporta QoS 1 al publicar.

## Estado
Compila; **no se ha probado en hardware**. El simulador implementa el mismo protocolo con QoS 1.
