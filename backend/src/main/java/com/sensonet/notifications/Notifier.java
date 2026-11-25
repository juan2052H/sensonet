package com.sensonet.notifications;

/** Canal de notificación (Telegram, correo...). */
public interface Notifier {

    /** Envía un mensaje con formato HTML básico. Devuelve true si se entregó. */
    boolean send(String html);

    boolean enabled();
}
