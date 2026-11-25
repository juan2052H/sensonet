-- TimescaleDB: hypertable de lecturas y retención del detalle
CREATE EXTENSION IF NOT EXISTS timescaledb;

SELECT create_hypertable('readings', 'ts', chunk_time_interval => INTERVAL '1 day', migrate_data => true);

-- Conservar el detalle 30 días; los promedios por hora (V3) se conservan.
SELECT add_retention_policy('readings', INTERVAL '30 days');
