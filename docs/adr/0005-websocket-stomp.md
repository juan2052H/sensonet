# ADR 0005: Tiempo real: WebSocket con STOMP

Estado: aceptada · Fecha: 2026-11-24

## Decisión
WebSocket con STOMP y el broker simple de Spring. Destinos `/topic/nodes`, `/topic/nodes/{id}`,
`/topic/alerts` y `/topic/commands`. El JWT viaja en el frame `CONNECT`, porque los navegadores no permiten
encabezados en el handshake.

## Alternativas
Server-Sent Events: más simple y suficiente para servidor → cliente, pero STOMP da suscripciones por
destino y reconexión con `@stomp/stompjs`, y deja abierta la puerta a comandos por WebSocket.

## Consecuencias
- Un solo socket por sesión del dashboard; las tarjetas y las gráficas se suscriben a los destinos que necesitan.
- El broker simple vive en memoria: con varias instancias del backend se necesitaría un broker externo (RabbitMQ).
