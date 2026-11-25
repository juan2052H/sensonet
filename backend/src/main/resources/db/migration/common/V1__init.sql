-- SensoNet: esquema base (común a TimescaleDB y PostgreSQL simple)

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(160) UNIQUE NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'VIEWER')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE nodes (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(40) UNIQUE NOT NULL,   -- = usuario MQTT del nodo
    name             VARCHAR(80) NOT NULL,
    location         VARCHAR(120),
    firmware         VARCHAR(40),
    online           BOOLEAN     NOT NULL DEFAULT FALSE,
    last_seen        TIMESTAMPTZ,
    relay_on         BOOLEAN,
    rssi             INTEGER,
    buffered         INTEGER,
    dropped          BIGINT,
    interval_seconds INTEGER     NOT NULL DEFAULT 10,
    last_reading_at  TIMESTAMPTZ,
    last_temperature DOUBLE PRECISION,
    last_humidity    DOUBLE PRECISION,
    last_pressure    DOUBLE PRECISION,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- La clave incluye ts porque el número de secuencia se reinicia si el nodo se reinicia:
-- un reenvío desde el buffer trae el mismo (ts, seq) y se descarta con ON CONFLICT.
CREATE TABLE readings (
    node_id     BIGINT      NOT NULL REFERENCES nodes (id),
    ts          TIMESTAMPTZ NOT NULL,
    seq         BIGINT      NOT NULL,
    temperature REAL CHECK (temperature BETWEEN -40 AND 85),
    humidity    REAL CHECK (humidity BETWEEN 0 AND 100),
    pressure    REAL CHECK (pressure BETWEEN 300 AND 1100),
    rssi        SMALLINT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (node_id, ts, seq)
);

-- Mensajes rechazados (esquema o rango físico inválido): quedan para auditoría.
CREATE TABLE invalid_readings (
    id          BIGSERIAL PRIMARY KEY,
    node_code   VARCHAR(40)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    reason      VARCHAR(200) NOT NULL,
    payload     TEXT
);
CREATE INDEX invalid_readings_received_idx ON invalid_readings (received_at DESC);

CREATE TABLE alert_rules (
    id               BIGSERIAL PRIMARY KEY,
    node_id          BIGINT REFERENCES nodes (id) ON DELETE CASCADE, -- NULL = todos los nodos
    name             VARCHAR(80)      NOT NULL,
    metric           VARCHAR(20)      NOT NULL,
    operator         VARCHAR(4)       NOT NULL,
    threshold        DOUBLE PRECISION NOT NULL,
    recovery         DOUBLE PRECISION NOT NULL,
    duration_seconds INTEGER          NOT NULL CHECK (duration_seconds >= 0),
    severity         VARCHAR(10)      NOT NULL,
    enabled          BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE TABLE alerts (
    id         BIGSERIAL PRIMARY KEY,
    rule_id    BIGINT REFERENCES alert_rules (id) ON DELETE SET NULL,
    rule_name  VARCHAR(80)      NOT NULL,
    node_id    BIGINT           NOT NULL REFERENCES nodes (id),
    metric     VARCHAR(20)      NOT NULL,
    operator   VARCHAR(4)       NOT NULL,
    threshold  DOUBLE PRECISION NOT NULL,
    severity   VARCHAR(10)      NOT NULL,
    opened_at  TIMESTAMPTZ      NOT NULL,
    closed_at  TIMESTAMPTZ,
    open_value DOUBLE PRECISION NOT NULL,
    peak_value DOUBLE PRECISION NOT NULL, -- valor más extremo mientras estuvo abierta
    acked_by   VARCHAR(160),
    acked_at   TIMESTAMPTZ
);
CREATE INDEX alerts_open_idx ON alerts (closed_at) WHERE closed_at IS NULL;
CREATE INDEX alerts_opened_idx ON alerts (opened_at DESC);

CREATE TABLE commands (
    id         UUID PRIMARY KEY,
    node_id    BIGINT      NOT NULL REFERENCES nodes (id),
    type       VARCHAR(20) NOT NULL,
    payload    TEXT        NOT NULL,
    status     VARCHAR(12) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    error      VARCHAR(200),
    created_by VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    acked_at   TIMESTAMPTZ
);
CREATE INDEX commands_pending_idx ON commands (created_at) WHERE status = 'PENDING';
