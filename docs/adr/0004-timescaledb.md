# ADR 0004: Base de datos: PostgreSQL + TimescaleDB (con alternativa sin TimescaleDB)

Estado: aceptada · Fecha: 2026-11-24

## Decisión
PostgreSQL 16 con TimescaleDB: `readings` es una hypertable (chunks de 1 día), con un agregado continuo
`readings_hourly` en tiempo real y retención de 30 días del detalle.

Como muchos proveedores gratuitos no ofrecen TimescaleDB, existe un segundo modo (`SENSONET_DB_FLAVOR=postgres`):
- Las migraciones Flyway se separan en `common/` + `timescale/` o `postgres/`.
- En PostgreSQL simple: índice **BRIN** sobre `ts`, agregación al vuelo con `date_bin` y retención con un
  `@Scheduled` (`RetentionJob`).

## Por qué no particionar por mes en el modo simple
Con ≈ 780.000 filas al mes (3 nodos), el índice BRIN y la clave primaria responden en milisegundos. El
particionado declarativo agregaría mantenimiento (crear particiones futuras) sin beneficio medible a esta
escala. Se reconsidera si el volumen crece en un orden de magnitud.

## Consecuencias
- Ambos modos tienen prueba de integración (`IngestionIT` con TimescaleDB, `PostgresFlavorIT` con
  PostgreSQL 16).
- En el modo simple, las consultas `1h` y `1d` de más de 30 días no tienen datos (no hay agregado que
  sobreviva a la retención).
- La migración del agregado continuo no puede ir en una transacción: `V3__hourly_aggregate.sql.conf`
  usa `executeInTransaction=false`.
