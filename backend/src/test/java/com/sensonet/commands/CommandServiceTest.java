package com.sensonet.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.sensonet.TestProps;
import com.sensonet.devices.Node;
import com.sensonet.devices.NodeService;
import com.sensonet.ingestion.MqttGateway;
import com.sensonet.realtime.RealtimePublisher;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommandServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");

    private final CommandRepository repo = mock(CommandRepository.class);
    private final NodeService nodes = mock(NodeService.class);
    private final MqttGateway mqtt = mock(MqttGateway.class);
    private final RealtimePublisher realtime = mock(RealtimePublisher.class);
    private final Map<UUID, Command> db = new HashMap<>();
    private MutableClock clock;
    private CommandService service;
    private Node node;

    @BeforeEach
    void setUp() throws Exception {
        clock = new MutableClock(T0);
        service = new CommandService(repo, nodes, mqtt, realtime, new ObjectMapper(), clock, TestProps.defaults());
        node = new Node("sim-sala", "Sala");
        Field id = Node.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(node, 7L);
        when(nodes.get(7L)).thenReturn(node);
        when(nodes.findByCode("sim-sala")).thenReturn(Optional.of(node));
        when(repo.save(any(Command.class))).thenAnswer(inv -> {
            Command c = inv.getArgument(0);
            db.put(c.getId(), c);
            return c;
        });
        when(repo.findById(any(UUID.class))).thenAnswer(inv -> Optional.ofNullable(db.get(inv.<UUID>getArgument(0))));
        when(repo.findByStatusAndCreatedAtBefore(eq(CommandStatus.PENDING), any(Instant.class))).thenAnswer(inv -> {
            Instant before = inv.getArgument(1);
            return db.values().stream()
                    .filter(c -> c.getStatus() == CommandStatus.PENDING && c.getCreatedAt().isBefore(before))
                    .toList();
        });
    }

    @Test
    @DisplayName("Publica el comando con id único en el tópico del nodo y queda PENDING")
    void publishesPending() {
        CommandDto dto = service.sendRelay(7L, true, "admin@x");
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(mqtt).publish(eq("sensonet/v1/sim-sala/cmd/relay"), payload.capture(), eq(1), eq(false));
        assertThat(payload.getValue()).contains("\"id\":\"" + dto.id() + "\"").contains("\"on\":true");
        assertThat(dto.status()).isEqualTo(CommandStatus.PENDING);
    }

    @Test
    @DisplayName("Un comando sin confirmación en 10 s queda FAILED")
    void expiresAfterTimeout() {
        CommandDto dto = service.sendRelay(7L, true, "admin@x");
        clock.advance(Duration.ofSeconds(9));
        assertThat(service.expirePending()).isZero();
        assertThat(db.get(dto.id()).getStatus()).isEqualTo(CommandStatus.PENDING);
        clock.advance(Duration.ofMillis(1001));
        assertThat(service.expirePending()).isEqualTo(1);
        assertThat(db.get(dto.id()).getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(db.get(dto.id()).getError()).contains("10 s");
    }

    @Test
    @DisplayName("El ACK con el mismo id confirma el comando y actualiza el estado del relé")
    void ackConfirms() {
        CommandDto dto = service.sendRelay(7L, true, "admin@x");
        clock.advance(Duration.ofMillis(300));
        service.handleAck("sim-sala", new CommandService.AckPayload(dto.id().toString(), true, true, null));
        assertThat(db.get(dto.id()).getStatus()).isEqualTo(CommandStatus.CONFIRMED);
        assertThat(db.get(dto.id()).getAckedAt()).isEqualTo(T0.plusMillis(300));
        verify(nodes).setRelayState(7L, true);
        clock.advance(Duration.ofSeconds(20));
        assertThat(service.expirePending()).isZero();
    }

    @Test
    void ackWithErrorFails() {
        CommandDto dto = service.sendRelay(7L, false, "admin@x");
        service.handleAck("sim-sala", new CommandService.AckPayload(dto.id().toString(), false, null, "relé trabado"));
        assertThat(db.get(dto.id()).getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(db.get(dto.id()).getError()).isEqualTo("relé trabado");
    }

    @Test
    @DisplayName("Un ACK de otro nodo no confirma el comando")
    void ackFromAnotherNodeIsIgnored() throws Exception {
        CommandDto dto = service.sendRelay(7L, true, "admin@x");
        Node other = new Node("intruso", "Otro");
        Field id = Node.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(other, 99L);
        when(nodes.findByCode("intruso")).thenReturn(Optional.of(other));
        service.handleAck("intruso", new CommandService.AckPayload(dto.id().toString(), true, true, null));
        assertThat(db.get(dto.id()).getStatus()).isEqualTo(CommandStatus.PENDING);
        verify(nodes, never()).setRelayState(anyLong(), anyBoolean());
    }

    @Test
    void malformedAcksAreIgnored() {
        service.handleAck("sim-sala", null);
        service.handleAck("sim-sala", new CommandService.AckPayload(null, true, true, null));
        service.handleAck("sim-sala", new CommandService.AckPayload("no-es-uuid", true, true, null));
        service.handleAck("sim-sala", new CommandService.AckPayload(UUID.randomUUID().toString(), true, true, null));
        verify(nodes, never()).setRelayState(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Si el broker no está disponible el comando falla de inmediato")
    void brokerDown() {
        doThrow(new MqttGateway.MqttUnavailableException("down", null))
                .when(mqtt).publish(anyString(), anyString(), anyInt(), anyBoolean());
        CommandDto dto = service.sendRelay(7L, true, "admin@x");
        assertThat(dto.status()).isEqualTo(CommandStatus.FAILED);
        assertThat(dto.error()).contains("Broker");
    }

    @Test
    void lateAckKeepsFailedButUpdatesRelay() {
        CommandDto dto = service.sendRelay(7L, true, "admin@x");
        clock.advance(Duration.ofSeconds(11));
        service.expirePending();
        service.handleAck("sim-sala", new CommandService.AckPayload(dto.id().toString(), true, true, null));
        assertThat(db.get(dto.id()).getStatus()).isEqualTo(CommandStatus.FAILED);
        verify(nodes).setRelayState(7L, true);
    }

    @Test
    void recentAndGet() {
        CommandDto dto = service.sendRelay(7L, true, "admin@x");
        when(repo.findByNodeIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(List.of(db.get(dto.id())));
        assertThat(service.recent(7L, 5)).extracting(CommandDto::id).containsExactly(dto.id());
        assertThat(service.get(dto.id()).id()).isEqualTo(dto.id());
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
