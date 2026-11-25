package com.sensonet.ingestion;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvalidReadingRepository {

    private final JdbcTemplate jdbc;

    public InvalidReadingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(String nodeCode, String reason, String payload) {
        String code = nodeCode.length() > 40 ? nodeCode.substring(0, 40) : nodeCode;
        String r = reason.length() > 200 ? reason.substring(0, 200) : reason;
        String p = payload != null && payload.length() > 2000 ? payload.substring(0, 2000) : payload;
        jdbc.update("INSERT INTO invalid_readings (node_code, reason, payload) VALUES (?, ?, ?)", code, r, p);
    }

    public record InvalidReading(long id, String nodeCode, Instant receivedAt, String reason, String payload) {}

    public List<InvalidReading> latest(int limit) {
        return jdbc.query("SELECT id, node_code, received_at, reason, payload FROM invalid_readings "
                        + "ORDER BY received_at DESC LIMIT ?",
                (rs, i) -> new InvalidReading(rs.getLong("id"), rs.getString("node_code"),
                        rs.getTimestamp("received_at").toInstant(), rs.getString("reason"), rs.getString("payload")),
                limit);
    }
}
