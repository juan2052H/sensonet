# Hardware de referencia del nodo

> **Diseño de referencia, no construido.** El firmware compila para ESP32 con esta configuración, pero el nodo no se ha armado ni probado físicamente. Si lo construyes, sigue [PRUEBAS_HARDWARE.md](PRUEBAS_HARDWARE.md).

## Lista de materiales

| Componente | Uso | Cant. | Precio aprox. (COP) |
|---|---|---|---|
| ESP32 DevKit V1 (ESP-WROOM-32) | Microcontrolador con WiFi | 1–2 | 25.000 – 40.000 |
| BME280 (I²C) | Temperatura, humedad y presión | 1 | 15.000 – 30.000 |
| SHT31 (I²C), alternativa | Temperatura y humedad más precisas | 1 | 20.000 – 35.000 |
| OLED SSD1306 0,96" (I²C) | Estado local | 1 | 12.000 – 20.000 |
| RTC DS3231 (I²C) | Hora sin internet | 1 | 10.000 – 18.000 |
| Módulo relé 1 canal 5 V con optoacoplador | Control de una carga | 1 | 6.000 – 12.000 |
| LED RGB cátodo común + 3 resistencias de 220 Ω | Indicador de estado | 1 | 2.000 – 4.000 |
| Buzzer activo 3,3–5 V | Alarma local | 1 | 2.000 – 4.000 |
| Pulsador | Borrar la red WiFi guardada | 1 | 1.000 – 2.000 |
| Protoboard, cables, fuente 5 V 2 A | Montaje | 1 | 20.000 – 35.000 |

Precios de referencia (noviembre de 2026); verifícalos en tiendas locales.

> **Seguridad eléctrica:** usa el relé solo con cargas de 5–12 V DC (un ventilador de PC, una tira LED). Los 110–120 V AC de la red pueden ser mortales. Nunca los conectes en protoboard.

## Conexiones

| Componente | Pin | GPIO ESP32 | Notas |
|---|---|---|---|
| Bus I²C (BME280/SHT31, OLED, DS3231) | SDA / SCL | 21 / 22 | Alimentar a 3,3 V |
| Relé | IN | 26 | Activo en bajo (`RELAY_ACTIVE_LOW` en `config.h`) |
| LED RGB | R / G / B | 25 / 33 / 32 | Con 220 Ω; PWM con LEDC |
| Buzzer | + | 27 | Si consume más de 20 mA, usar un transistor |
| Pulsador | — | 4 | `INPUT_PULLUP`; mantener 5 s borra el WiFi |

Pines a evitar: GPIO 6–11 (memoria flash), 0, 2, 12 y 15 (afectan el arranque), y 34–39 como salida.

### Direcciones I²C

| Dispositivo | Dirección | Constante en `config.h` |
|---|---|---|
| BME280 | 0x76 o 0x77 | `SN_BME280_ADDR` |
| SHT31 | 0x44 o 0x45 | `SN_SHT31_ADDR` |
| SSD1306 | 0x3C | `SN_OLED_ADDR` |
| DS3231 | 0x68 | — |

## Indicadores

| LED | Significado |
|---|---|
| Blanco | Arrancando |
| Verde | Lectura publicada |
| Azul | Sin conexión: lectura guardada en el buffer |
| Amarillo | Lectura fuera de rango físico (descartada) |
| Rojo | Sensor sin respuesta o error del buffer |
| Magenta | Portal de configuración WiFi abierto (`SensoNet-Setup`) |

## Conectar un nodo real al broker local

1. Genera los certificados con la IP de tu PC en el SAN: `MQTT_EXTRA_SAN=192.168.1.50` en `.env`. Si ya existían, borra `infra/certs/generated/` antes de `docker compose up`.
2. Agrega el nodo a `MQTT_NODES` en `.env` (por ejemplo `nodo-sala:una-clave-larga`) y reinicia Mosquitto.
3. En el firmware, copia `secrets.example.h` como `secrets.h` y pon `NODE_CODE`, `MQTT_PASSWORD`, `MQTT_HOST` y el contenido de `infra/certs/generated/ca.crt`.
4. Flashea el ESP32 (ver [firmware/README.md](../firmware/README.md)) y conéctate a la red `SensoNet-Setup` para elegir tu WiFi.
5. El nodo aparece solo en el dashboard con su primera lectura.

Diagrama de conexiones (Fritzing/KiCad) y caja 3D: pendientes hasta que se construya el nodo.
