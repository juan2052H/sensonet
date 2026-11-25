# ADR 0003: Backend: Spring Boot

Estado: aceptada · Fecha: 2026-11-24

## Decisión
Spring Boot 3.5 con Java 21. Paho para MQTT, Spring Data JPA para las entidades, `JdbcTemplate` para la
serie de tiempo, STOMP para el tiempo real, el servidor de recursos OAuth2 de Spring Security para validar
JWT y springdoc para OpenAPI.

## Alternativas
Node.js + Express: más liviano, pero el objetivo del portafolio es Java/Spring, que es lo que más piden
las ofertas locales.

## Consecuencias
- El motor de reglas y la validación son clases puras sin Spring: pruebas unitarias rápidas.
- Las consultas de series (`ON CONFLICT`, `time_bucket`) van en SQL explícito en vez de JPQL.
- Paho entrega los mensajes en su propio hilo; se pasan a un ejecutor de un solo hilo, lo que conserva
  el orden y evita bloqueos al publicar desde un callback.
