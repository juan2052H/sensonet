-- PostgreSQL sin TimescaleDB (p. ej. Neon o Render): índice BRIN sobre ts.
-- Los datos llegan casi en orden de tiempo, así que BRIN es diminuto y eficiente.
-- La agregación se calcula al vuelo con date_bin y la retención la hace RetentionJob.
CREATE INDEX readings_ts_brin ON readings USING brin (ts);
