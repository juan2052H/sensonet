# Cómo presentar el proyecto

## Viñeta para el CV

**SensoNet: monitor IoT en tiempo real.** Diseñé y desarrollé un sistema de monitoreo con MQTT sobre TLS y control de acceso por dispositivo, backend en Spring Boot con motor de alertas con histéresis, PostgreSQL/TimescaleDB y dashboard en React con WebSocket. El firmware para ESP32 (C++, Arduino) tiene un buffer offline-first y la demo usa nodos simulados que siguen el mismo protocolo. Incluye 120+ pruebas automatizadas (unitarias, Testcontainers, Playwright) y CI con GitHub Actions.

Formula la parte del hardware con honestidad mientras el nodo no esté construido: «firmware para ESP32 (compilado, demo con nodos simulados)». Si en una entrevista te preguntan por el hardware, cuenta qué está probado (la lógica pura y la compilación) y qué no (el nodo físico).

## Post para LinkedIn (estructura)

- **Gancho:** «¿Qué pasa con los datos de un sensor cuando se va el WiFi? Construí un sistema para que no se pierda ninguno.»
- **Qué hice:** 3 o 4 líneas sobre el stack y la arquitectura (broker MQTT con TLS, Spring Boot, TimescaleDB, React).
- **Lo difícil:** la deduplicación por `(nodo, ts, seq)`, la histéresis de las alertas o la carrera entre el commit y el ACK del comando, y cómo lo resolví.
- **Video** de 30–60 s: el escenario `corte-wifi` en el dashboard (el nodo pasa a offline, se recupera y el tramo violeta se rellena solo).
- Enlace a GitHub y a la demo.
- Etiquetas: #IoT #ESP32 #SpringBoot #PostgreSQL #React #Ciberseguridad

## Guion de demo (2 minutos)

1. Inicio: tres nodos en vivo, con el indicador "En vivo".
2. Detalle de un nodo: encender el relé y ver "Esperando confirmación…" pasar a "Confirmado".
3. Reglas: crear "> 28 °C durante 1 min" y usar "Probar regla" sobre las últimas 24 h.
4. `SIM_ESCENARIO=corte-wifi`: el nodo pasa a offline por el LWT y, al volver, el tramo recuperado del buffer aparece en violeta.
5. Swagger, `docs/SECURITY.md` y la verificación de la ACL.
