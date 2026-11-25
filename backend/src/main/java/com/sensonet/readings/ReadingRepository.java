package com.sensonet.readings;

import com.sensonet.config.SensonetProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acceso a la serie de tiempo con SQL directo: la inserción idempotente (ON CONFLICT) y las
 * agregaciones (time_bucket / date_bin) son más claras en SQL que con JPA.
 */
@Repository
public class ReadingRepository {

    private final JdbcTemplate jdbc;
    private final boolean timescale;
    private final long backfillSeconds;

    public ReadingRepository(JdbcTemplate jdbc, SensonetProperties props) {
        this.jdbc = jdbc;
        this.timescale = props.db().timescale();
        this.backfillSeconds = props.ingestion().backfillThreshold().toSeconds();
    }

    /** Inserta la lectura; devuelve false si ya existía (mismo nodo, ts y seq). */
    public boolean insert(Reading r) {
        int rows = jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO readings (node_id, ts, seq, temperature, humidity, pressure, rssi, received_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING""");
            ps.setLong(1, r.nodeId());
            ps.setTimestamp(2, Timestamp.from(r.ts()));
            ps.setLong(3, r.seq());
            setDouble(ps, 4, r.temperature());
            setDouble(ps, 5, r.humidity());
            setDouble(ps, 6, r.pressure());
            if (r.rssi() == null) ps.setNull(7, Types.SMALLINT); else ps.setInt(7, r.rssi());
            ps.setTimestamp(8, Timestamp.from(r.receivedAt()));
            return ps;
        });
        return rows == 1;
    }

    private static void setDouble(java.sql.PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, Types.REAL); else ps.setDouble(i, v);
    }

    public List<ReadingPoint> series(long nodeId, Instant from, Instant to, Bucket bucket) {
        Timestamp f = Timestamp.from(from);
        Timestamp t = Timestamp.from(to);
        String late = "(received_at - ts) > make_interval(secs => " + backfillSeconds + ")";
        return switch (bucket) {
            case RAW -> jdbc.query("""
                    SELECT ts, temperature, humidity, pressure, %s AS late
                    FROM readings WHERE node_id = ? AND ts >= ? AND ts <= ?
                    ORDER BY ts LIMIT 50000""".formatted(late),
                    ReadingRepository::mapPoint, nodeId, f, t);
            case MINUTE -> jdbc.query("""
                    SELECT %s AS ts, avg(temperature) AS temperature, avg(humidity) AS humidity,
                           avg(pressure) AS pressure, bool_or(%s) AS late
                    FROM readings WHERE node_id = ? AND ts >= ? AND ts <= ?
                    GROUP BY 1 ORDER BY 1""".formatted(bucketExpr("1 minute", "ts"), late),
                    ReadingRepository::mapPoint, nodeId, f, t);
            case HOUR, DAY -> aggregated(nodeId, f, t, bucket, late);
        };
    }

    private List<ReadingPoint> aggregated(long nodeId, Timestamp f, Timestamp t, Bucket bucket, String late) {
        String size = bucket == Bucket.HOUR ? "1 hour" : "1 day";
        if (timescale) {
            // Agregado continuo readings_hourly (sobrevive a la retención del detalle).
            return jdbc.query("""
                    SELECT time_bucket(INTERVAL '%s', bucket) AS ts,
                           sum(t_avg * samples) / sum(samples) AS temperature,
                           sum(h_avg * samples) / sum(samples) AS humidity,
                           sum(p_avg * samples) / nullif(sum(CASE WHEN p_avg IS NULL THEN 0 ELSE samples END), 0) AS pressure,
                           max(late) = 1 AS late
                    FROM readings_hourly WHERE node_id = ? AND bucket >= ? AND bucket <= ?
                    GROUP BY 1 ORDER BY 1""".formatted(size),
                    ReadingRepository::mapPoint, nodeId, f, t);
        }
        return jdbc.query("""
                SELECT %s AS ts, avg(temperature) AS temperature, avg(humidity) AS humidity,
                       avg(pressure) AS pressure, bool_or(%s) AS late
                FROM readings WHERE node_id = ? AND ts >= ? AND ts <= ?
                GROUP BY 1 ORDER BY 1""".formatted(bucketExpr(size, "ts"), late),
                ReadingRepository::mapPoint, nodeId, f, t);
    }

    private String bucketExpr(String size, String column) {
        return timescale
                ? "time_bucket(INTERVAL '" + size + "', " + column + ")"
                : "date_bin(INTERVAL '" + size + "', " + column + ", TIMESTAMPTZ '2000-01-01 00:00:00+00')";
    }

    /** Recorre las lecturas crudas en orden de tiempo (vista previa de reglas). */
    public void forEachRaw(long nodeId, Instant from, Instant to, Consumer<Reading> consumer) {
        jdbc.query("""
                SELECT node_id, ts, seq, temperature, humidity, pressure, rssi, received_at
                FROM readings WHERE node_id = ? AND ts >= ? AND ts <= ? ORDER BY ts""",
                rs -> {
                    consumer.accept(new Reading(rs.getLong("node_id"), rs.getTimestamp("ts").toInstant(),
                            rs.getLong("seq"), nullableDouble(rs, "temperature"), nullableDouble(rs, "humidity"),
                            nullableDouble(rs, "pressure"), (Integer) rs.getObject("rssi", Integer.class),
                            rs.getTimestamp("received_at").toInstant()));
                },
                nodeId, Timestamp.from(from), Timestamp.from(to));
    }

    public long count(long nodeId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM readings WHERE node_id = ?", Long.class, nodeId);
        return n == null ? 0 : n;
    }

    /** Retención para PostgreSQL simple (en TimescaleDB la hace add_retention_policy). */
    public int deleteOlderThan(Instant threshold) {
        return jdbc.update("DELETE FROM readings WHERE ts < ?", Timestamp.from(threshold));
    }

    private static ReadingPoint mapPoint(ResultSet rs, int i) throws SQLException {
        return new ReadingPoint(rs.getTimestamp("ts").toInstant(), nullableDouble(rs, "temperature"),
                nullableDouble(rs, "humidity"), nullableDouble(rs, "pressure"), rs.getBoolean("late"));
    }

    private static Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : Math.round(v * 100.0) / 100.0;
    }
}
