# ADR 0009: Migración a Spring Boot 4 y Java 25

Estado: aceptada · Fecha: 2026-11-25

## Contexto
El proyecto empezó con Spring Boot 3.5 y Java 21. Dependabot propuso Spring Boot 4.2, springdoc 3 y
nuevas imágenes de Java por separado. Aplicarlas una por una dejaba el build roto (springdoc 3 exige
Boot 4) o el proyecto mezclado (compilar con 21 y ejecutar con 25).

## Decisión
Migrar todo junto: Spring Boot 4.2, springdoc 3.2 y Java 25 (LTS) en el pom, el CI y las imágenes Docker.
Java 26 se descarta por no ser LTS.

## Cambios necesarios
| Área | Antes | Después |
|---|---|---|
| Starters | `spring-boot-starter-web`, `-oauth2-resource-server`, `flyway-core` | `spring-boot-starter-webmvc`, `-security-oauth2-resource-server`, `-flyway` (la autoconfiguración ahora es modular) y `-restclient` (para el `RestClient.Builder` de Telegram) |
| Jackson | 2.x (`com.fasterxml.jackson.databind`) | 3.x (`tools.jackson.databind`); las excepciones dejan de ser verificadas (`JacksonException`); las anotaciones siguen en `com.fasterxml.jackson.annotation` |
| Configuración | `spring.jackson.serialization.write-dates-as-timestamps=false` | se elimina: Jackson 3 ya escribe las fechas como ISO-8601 |
| Nulabilidad | `org.springframework.lang.NonNull` | `org.jspecify.annotations.NonNull` |
| Pruebas | `spring-boot-starter-test`, `spring-security-test`, Testcontainers 1 (`junit-jupiter`, `postgresql`) | `spring-boot-starter-webmvc-test`, `spring-boot-starter-security-test`, Testcontainers 2 (`testcontainers-junit-jupiter`, `testcontainers-postgresql`, paquete `org.testcontainers.postgresql`) |
| `@AutoConfigureMockMvc` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |

Spring Security 7, Hibernate 7 y Flyway 12 no requirieron cambios de código.

## Consecuencias
- Las 52 pruebas del backend pasan sin cambios de lógica, y la cobertura se mantiene sobre el 70 % exigido.
- Para compilar en local se necesita el JDK 25.
