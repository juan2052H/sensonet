-- Agregado continuo por hora. materialized_only = false: incluye en tiempo real
-- las horas que todavía no se han materializado.
CREATE MATERIALIZED VIEW readings_hourly
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT node_id,
       time_bucket(INTERVAL '1 hour', ts) AS bucket,
       avg(temperature) AS t_avg, min(temperature) AS t_min, max(temperature) AS t_max,
       avg(humidity)    AS h_avg, min(humidity)    AS h_min, max(humidity)    AS h_max,
       avg(pressure)    AS p_avg,
       count(*)         AS samples,
       max(CASE WHEN received_at - ts > INTERVAL '20 seconds' THEN 1 ELSE 0 END) AS late
FROM readings
GROUP BY node_id, bucket
WITH NO DATA;

SELECT add_continuous_aggregate_policy('readings_hourly',
    start_offset      => INTERVAL '3 days',
    end_offset        => INTERVAL '1 hour',
    schedule_interval => INTERVAL '30 minutes');
