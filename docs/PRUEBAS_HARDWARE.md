# Lista de verificación del nodo físico

> **Ninguna de estas pruebas se ha ejecutado**: el nodo no se ha construido. Esta lista es para quien lo arme. Marca cada punto y anota la fecha y la versión del firmware.

Firmware: `____` · Fecha: `____` · Sensor: BME280 / SHT31

## Arranque y periféricos
- [ ] El monitor serie (115200) muestra `[buffer] listo` y no muestra `[sensor] no detectado`.
- [ ] Un escáner I²C detecta el sensor (0x76/0x77 o 0x44/0x45), el OLED (0x3C) y el DS3231 (0x68).
- [ ] El OLED muestra temperatura, humedad y presión.
- [ ] El LED pasa a blanco al arrancar y a verde al publicar.

## Medición
- [ ] La temperatura difiere menos de 1 °C de un termómetro de referencia, con el sensor separado de la placa (el ESP32 se calienta).
- [ ] La presión ronda los 850 hPa en Medellín.

## Conectividad
- [ ] Sin red guardada se abre el portal `SensoNet-Setup` (LED magenta) y permite elegir el WiFi.
- [ ] Mantener el pulsador 5 s borra el WiFi y reinicia.
- [ ] Conecta a Mosquitto por TLS: el log del broker muestra el cliente con su usuario.
- [ ] Con una CA equivocada, la conexión falla (no hay que desactivar la verificación).
- [ ] El dashboard muestra el nodo "En línea" y la gráfica avanza sola.

## Offline-first
- [ ] Apagar el router 15 min: el LED pasa a azul, el dashboard marca el nodo offline (LWT) y el contador del buffer sube.
- [ ] Al volver la red, el nodo reenvía todas las lecturas pendientes en orden y sin huecos (tramo violeta en la gráfica).
- [ ] Reiniciar el ESP32 con lecturas en el buffer: siguen ahí después del reinicio.
- [ ] Sin NTP pero con el RTC ya ajustado, las lecturas tienen la hora correcta.

## Relé
- [ ] Encender o apagar desde el dashboard: el relé conmuta y el comando queda "Confirmado" en menos de 2 s.
- [ ] Con el nodo desconectado, el comando queda "Fallido" a los 10 s.
- [ ] Tras un reinicio, el relé recupera su último estado.

## Robustez
- [ ] 24 h de funcionamiento continuo sin reinicios del watchdog (revisar el log serie).
- [ ] Desconectar el sensor en caliente: LED rojo, no se publican lecturas, el nodo no se cuelga.
