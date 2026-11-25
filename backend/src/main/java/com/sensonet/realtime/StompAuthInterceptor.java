package com.sensonet.realtime;

import org.springframework.core.convert.converter.Converter;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Autentica la sesión STOMP con el JWT enviado en el encabezado {@code Authorization} del frame
 * CONNECT. Los navegadores no permiten encabezados en el handshake de WebSocket, por eso se valida aquí.
 * Solo se permite suscribirse a {@code /topic/**}; los clientes no pueden enviar mensajes.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder decoder;
    private final Converter<Jwt, AbstractAuthenticationToken> converter;

    public StompAuthInterceptor(JwtDecoder decoder, Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter) {
        this.decoder = decoder;
        this.converter = jwtAuthConverter;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) {
            return message;
        }
        StompCommand cmd = acc.getCommand();
        if (cmd == StompCommand.CONNECT) {
            String header = acc.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Falta el token JWT");
            }
            try {
                Jwt jwt = decoder.decode(header.substring(7));
                acc.setUser(converter.convert(jwt));
            } catch (JwtException e) {
                throw new MessagingException("Token JWT inválido o vencido");
            }
        } else if (cmd == StompCommand.SUBSCRIBE) {
            if (acc.getUser() == null) {
                throw new MessagingException("No autenticado");
            }
            String dest = acc.getDestination();
            if (dest == null || !dest.startsWith("/topic/")) {
                throw new MessagingException("Destino no permitido");
            }
        } else if (cmd == StompCommand.SEND) {
            throw new MessagingException("Los clientes no pueden enviar mensajes");
        }
        return message;
    }
}
