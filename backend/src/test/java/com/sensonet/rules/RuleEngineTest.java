package com.sensonet.rules;

import static com.sensonet.rules.RuleEngine.Decision.CLOSE;
import static com.sensonet.rules.RuleEngine.Decision.NONE;
import static com.sensonet.rules.RuleEngine.Decision.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private static final Instant T0 = Instant.parse("2026-11-24T12:00:00Z");
    /** "> 30 °C durante 5 min; se recupera al bajar de 28 °C". */
    private final RuleSpec heat = new RuleSpec(Metric.TEMPERATURE, Operator.GT, 30, 28, Duration.ofMinutes(5));

    private RuleEngine.Decision eval(RuleState s, double v, Duration offset) {
        return RuleEngine.evaluate(heat, s, v, T0.plus(offset));
    }

    @Test
    @DisplayName("No abre con 4 min 59 s por encima del umbral y sí con 5 min")
    void opensExactlyAtDuration() {
        RuleState s = new RuleState();
        assertThat(eval(s, 31, Duration.ZERO)).isEqualTo(NONE);
        assertThat(eval(s, 31, Duration.ofSeconds(299))).isEqualTo(NONE);
        assertThat(eval(s, 31, Duration.ofSeconds(300))).isEqualTo(OPEN);
        assertThat(s.isOpen()).isTrue();
    }

    @Test
    @DisplayName("Con histéresis, valores entre 29,5 y 30,5 °C no abren y cierran la alerta repetidamente")
    void hysteresisAvoidsFlapping() {
        RuleState s = new RuleState();
        List<RuleEngine.Decision> decisions = new ArrayList<>();
        // 1 hora oscilando cada 10 s alrededor del umbral
        for (int i = 0; i < 360; i++) {
            double v = (i % 2 == 0) ? 30.5 : 29.5;
            decisions.add(eval(s, v, Duration.ofSeconds(10L * i)));
        }
        assertThat(decisions).doesNotContain(OPEN, CLOSE);

        // Una vez abierta, oscilar entre 29,5 y 30,5 no la cierra (recuperación en 28)
        RuleState open = new RuleState();
        for (int i = 0; i <= 30; i++) eval(open, 31, Duration.ofSeconds(10L * i));
        assertThat(open.isOpen()).isTrue();
        List<RuleEngine.Decision> after = new ArrayList<>();
        for (int i = 31; i < 400; i++) {
            after.add(eval(open, (i % 2 == 0) ? 30.5 : 29.5, Duration.ofSeconds(10L * i)));
        }
        assertThat(after).doesNotContain(OPEN, CLOSE);
        assertThat(open.isOpen()).isTrue();
    }

    @Test
    @DisplayName("Se cierra solo cuando baja del umbral de recuperación")
    void closesBelowRecovery() {
        RuleState s = RuleState.opened();
        assertThat(eval(s, 29, Duration.ZERO)).isEqualTo(NONE);
        assertThat(eval(s, 28, Duration.ofSeconds(10))).isEqualTo(NONE); // 28 no es < 28
        assertThat(eval(s, 27.9, Duration.ofSeconds(20))).isEqualTo(CLOSE);
        assertThat(s.isOpen()).isFalse();
    }

    @Test
    @DisplayName("Si el valor baja del umbral antes de cumplir la duración, el conteo se reinicia")
    void dipResetsTimer() {
        RuleState s = new RuleState();
        eval(s, 31, Duration.ZERO);
        eval(s, 31, Duration.ofMinutes(4));
        eval(s, 29, Duration.ofMinutes(4).plusSeconds(30)); // reinicia
        assertThat(eval(s, 31, Duration.ofMinutes(5))).isEqualTo(NONE);
        assertThat(eval(s, 31, Duration.ofMinutes(9).plusSeconds(59))).isEqualTo(NONE);
        assertThat(eval(s, 31, Duration.ofMinutes(10))).isEqualTo(OPEN);
    }

    @Test
    @DisplayName("Duración cero abre en la primera lectura")
    void zeroDurationOpensImmediately() {
        RuleSpec instant = new RuleSpec(Metric.TEMPERATURE, Operator.GT, 30, 28, Duration.ZERO);
        assertThat(RuleEngine.evaluate(instant, new RuleState(), 30.1, T0)).isEqualTo(OPEN);
    }

    @Test
    @DisplayName("Operador '<' (humedad baja) con su propia histéresis")
    void lowerThan() {
        RuleSpec dry = new RuleSpec(Metric.HUMIDITY, Operator.LT, 25, 30, Duration.ofMinutes(1));
        RuleState s = new RuleState();
        assertThat(RuleEngine.evaluate(dry, s, 24, T0)).isEqualTo(NONE);
        assertThat(RuleEngine.evaluate(dry, s, 23, T0.plusSeconds(60))).isEqualTo(OPEN);
        assertThat(RuleEngine.evaluate(dry, s, 29, T0.plusSeconds(70))).isEqualTo(NONE);
        assertThat(RuleEngine.evaluate(dry, s, 31, T0.plusSeconds(80))).isEqualTo(CLOSE);
    }

    @Test
    @DisplayName("Una lectura antigua (reenviada desde el buffer) no altera el estado")
    void ignoresOutOfOrderReadings() {
        RuleState s = new RuleState();
        eval(s, 31, Duration.ofMinutes(10));
        assertThat(eval(s, 20, Duration.ofMinutes(2))).isEqualTo(NONE);
        assertThat(eval(s, 31, Duration.ofMinutes(15))).isEqualTo(OPEN);
    }

    @Test
    @DisplayName("Rechaza histéresis incoherente")
    void rejectsInvalidHysteresis() {
        assertThatThrownBy(() -> new RuleSpec(Metric.TEMPERATURE, Operator.GT, 30, 32, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleSpec(Metric.HUMIDITY, Operator.LT, 30, 20, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleSpec(Metric.HUMIDITY, Operator.LT, 30, 40, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
