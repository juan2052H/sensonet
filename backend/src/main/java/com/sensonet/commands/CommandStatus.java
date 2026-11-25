package com.sensonet.commands;

public enum CommandStatus {
    /** Publicado, esperando confirmación del nodo. */
    PENDING,
    /** El nodo respondió en cmd/ack con el mismo id. */
    CONFIRMED,
    /** Sin confirmación a tiempo, broker no disponible o el nodo reportó error. */
    FAILED
}
