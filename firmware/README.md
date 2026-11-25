# Firmware del nodo ESP32

> **Estado:** compila para ESP32 (núcleo esp32 3.x, variantes BME280 y SHT31) y la lógica pura tiene pruebas nativas. **No se ha probado en hardware físico.** La demo usa el [simulador](../simulator/), que implementa el mismo protocolo.

## Abrir en Arduino IDE

1. Instala el núcleo **esp32 de Espressif** (Gestor de tarjetas, URL `https://espressif.github.io/arduino-esp32/package_esp32_index.json`).
2. Instala estas librerías desde el Gestor de librerías: `PubSubClient`, `ArduinoJson` (7.x), `Adafruit BME280 Library` (o `Adafruit SHT31 Library`), `Adafruit Unified Sensor`, `U8g2`, `RTClib` y `WiFiManager`.
3. Abre `sensonet_node/sensonet_node.ino`.
4. Copia `secrets.example.h` como `secrets.h` (ya está en `.gitignore`) y complétalo. Ver [docs/HARDWARE.md](../docs/HARDWARE.md).
5. En **Herramientas**, elige la placa *ESP32 Dev Module* y el esquema de particiones *Huge APP (3MB No OTA/1MB SPIFFS)*.
6. Elige el sensor en `config.h` (`SENSOR_BME280` o `SENSOR_SHT31`) y sube el sketch.

## Archivos

| Archivo | Contenido | ¿Depende de Arduino? |
|---|---|---|
| `sensonet_node.ino` | Máquina de estados, MQTT, comandos y buffer | Sí |
| `hardware.h/.cpp` | Sensores, reloj NTP + DS3231, OLED, LED RGB, buzzer, relé, almacenamiento LittleFS | Sí |
| `telemetry_codec.h/.cpp` | Validación de rango físico y JSON (telemetría, estado, ACK) | No |
| `offline_ring.h/.cpp` | Cola circular persistente con descarte del más antiguo | No |
| `recent_ids.h` | Idempotencia de comandos y espera creciente entre reintentos | No |
| `config.h` | Pines, intervalos y tópicos | — |
| `secrets.example.h` | Plantilla de credenciales y CA | — |

## Máquina de estados

| Estado | Qué hace | Pasa a |
|---|---|---|
| ARRANQUE | Inicializa I²C, sensores, OLED, RTC, LittleFS y watchdog | CONECTANDO_WIFI |
| CONECTANDO_WIFI | Intenta la red guardada; tras 3 fallos abre el portal (sin bloquear) | CONECTANDO_MQTT u OFFLINE |
| CONECTANDO_MQTT | TLS + usuario del nodo; registra el LWT; se suscribe a `cmd/relay` y `config` | OPERANDO u OFFLINE |
| OPERANDO | Publica cada N s, vacía el buffer en lotes de 20, atiende comandos | OFFLINE si pierde la conexión |
| OFFLINE | Sigue midiendo y guarda en LittleFS; reintenta con espera creciente | CONECTANDO_WIFI / MQTT |

Usa temporizadores con `millis()` en lugar de `delay()`. Si `loop()` se cuelga más de 30 s, el watchdog reinicia el ESP32.

## Pruebas y compilación sin Arduino IDE

```bash
sh test/run_tests.sh                                     # lógica pura con g++
docker run --rm -v "$PWD:/fw" -w /fw gcc:14 sh test/run_tests.sh
docker run --rm -v "$PWD:/fw" -w /fw ubuntu:24.04 sh compile.sh            # compilación para ESP32
docker run --rm -v "$PWD:/fw" -w /fw -e SENSOR=SHT31 ubuntu:24.04 sh compile.sh
```

Resultado actual: 11 pruebas y 240 comprobaciones correctas. El programa ocupa ≈ 1,29 MB (41 % de la partición Huge APP) y usa ≈ 57 KB de RAM.

Limitación conocida: PubSubClient publica con QoS 0. La compensación está en el [ADR 0007](../docs/adr/0007-firmware-arduino-ide.md).
