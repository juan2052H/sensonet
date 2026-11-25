package com.sensonet.devices;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Código único = usuario MQTT del nodo. */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 120)
    private String location;

    @Column(length = 40)
    private String firmware;

    @Column(nullable = false)
    private boolean online;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "relay_on")
    private Boolean relayOn;

    private Integer rssi;
    private Integer buffered;
    private Long dropped;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds = 10;

    @Column(name = "last_reading_at")
    private Instant lastReadingAt;

    @Column(name = "last_temperature")
    private Double lastTemperature;

    @Column(name = "last_humidity")
    private Double lastHumidity;

    @Column(name = "last_pressure")
    private Double lastPressure;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Node() {}

    public Node(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getFirmware() { return firmware; }
    public void setFirmware(String firmware) { this.firmware = firmware; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
    public Boolean getRelayOn() { return relayOn; }
    public void setRelayOn(Boolean relayOn) { this.relayOn = relayOn; }
    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }
    public Integer getBuffered() { return buffered; }
    public void setBuffered(Integer buffered) { this.buffered = buffered; }
    public Long getDropped() { return dropped; }
    public void setDropped(Long dropped) { this.dropped = dropped; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public Instant getLastReadingAt() { return lastReadingAt; }
    public Double getLastTemperature() { return lastTemperature; }
    public Double getLastHumidity() { return lastHumidity; }
    public Double getLastPressure() { return lastPressure; }
    public Instant getCreatedAt() { return createdAt; }

    /** Actualiza la "última lectura" solo si es más reciente (las lecturas del buffer llegan tarde). */
    public boolean applyLatestReading(Instant ts, Double t, Double h, Double p) {
        if (lastReadingAt != null && !ts.isAfter(lastReadingAt)) {
            return false;
        }
        lastReadingAt = ts;
        lastTemperature = t;
        lastHumidity = h;
        lastPressure = p;
        return true;
    }
}
