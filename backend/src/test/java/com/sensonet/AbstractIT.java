package com.sensonet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sensonet.ingestion.MqttMessageRouter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base de las pruebas de integración: TimescaleDB y Mosquitto reales en contenedores
 * (compartidos por todas las clases para que el contexto de Spring se reutilice).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIT {

    public static final String ADMIN = "admin@test.local";
    public static final String VIEWER = "viewer@test.local";
    public static final String PASSWORD = "clave-de-prueba-123";

    static final PostgreSQLContainer DB = new PostgreSQLContainer(
            DockerImageName.parse("timescale/timescaledb:latest-pg16").asCompatibleSubstituteFor("postgres"));

    static final GenericContainer<?> MQTT = new GenericContainer<>("eclipse-mosquitto:2")
            .withExposedPorts(1883)
            .withCopyFileToContainer(MountableFile.forClasspathResource("mosquitto-test.conf"),
                    "/mosquitto/config/mosquitto.conf")
            .waitingFor(Wait.forListeningPort());

    static {
        DB.start();
        MQTT.start();
    }

    public static String mqttUrl() {
        return "tcp://" + MQTT.getHost() + ":" + MQTT.getMappedPort(1883);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("sensonet.db.flavor", () -> "timescale");
        r.add("sensonet.jwt.secret", () -> "secreto-de-pruebas-de-integracion-0123456789");
        r.add("sensonet.mqtt.url", AbstractIT::mqttUrl);
        r.add("sensonet.mqtt.username", () -> "");
        r.add("sensonet.mqtt.password", () -> "");
        r.add("sensonet.mqtt.client-id", () -> "backend-it");
        r.add("sensonet.seed.admin-email", () -> ADMIN);
        r.add("sensonet.seed.admin-password", () -> PASSWORD);
        r.add("sensonet.seed.viewer-email", () -> VIEWER);
        r.add("sensonet.seed.viewer-password", () -> PASSWORD);
        r.add("sensonet.seed.demo-rules", () -> "false");
        r.add("sensonet.rate-limit.api-per-minute", () -> "100000");
        r.add("sensonet.rate-limit.login-per-minute", () -> "1000");
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MqttMessageRouter router;

    protected String token(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + mapper.readTree(body).get("token").asString();
    }

    protected JsonNode json(String s) throws Exception {
        return mapper.readTree(s);
    }

    protected static String uniqueCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String telemetry(long seq, Instant ts, double t, double h, Double p) {
        return "{\"v\":1,\"seq\":" + seq + ",\"ts\":" + ts.toEpochMilli() + ",\"t\":" + t + ",\"h\":" + h
                + (p == null ? "" : ",\"p\":" + p) + ",\"rssi\":-60}";
    }

    protected void sendTelemetry(String code, String json) {
        router.route("sensonet/v1/" + code + "/telemetry", json.getBytes(StandardCharsets.UTF_8));
    }

    protected long nodeId(String code) {
        return jdbc.queryForObject("SELECT id FROM nodes WHERE code = ?", Long.class, code);
    }

    protected long readingCount(String code) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM readings r JOIN nodes n ON n.id = r.node_id WHERE n.code = ?", Long.class, code);
    }
}
