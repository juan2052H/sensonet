# ADR 0008: Deduplicación por (nodo, ts, seq)

Estado: aceptada · Fecha: 2026-11-24

## Contexto
Con QoS 1 y con el buffer offline, el mismo mensaje puede llegar más de una vez. La guía sugería
deduplicar por `(nodo, seq)`.

## Decisión
Clave primaria `(node_id, ts, seq)` e `INSERT ... ON CONFLICT DO NOTHING`.

## Por qué incluir ts
- El número de secuencia vive en RAM y vuelve a 0 cuando el nodo se reinicia. Con `(nodo, seq)`, las
  lecturas nuevas después de un reinicio chocarían con las antiguas y se descartarían por error.
- TimescaleDB exige que los índices únicos de una hypertable incluyan la columna de partición (`ts`).
- Un reenvío desde el buffer conserva el mismo `ts` y el mismo `seq`, así que se detecta como duplicado.

## Consecuencias
- Reenviar es siempre seguro: es la base del offline-first y de la mitigación del ADR 0007.
- El motor de reglas solo avanza en el tiempo: una lectura antigua que llega tarde no abre ni cierra alertas.
