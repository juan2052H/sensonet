package com.sensonet.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sensonet.config.SensonetProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Límite de peticiones por IP en ventanas de un minuto: uno estricto para el inicio de
 * sesión (contra fuerza bruta) y otro general para el resto de la API.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final FixedWindowLimiter loginLimiter;
    private final FixedWindowLimiter apiLimiter;
    private final ObjectMapper mapper;

    public RateLimitFilter(SensonetProperties props, Clock clock, ObjectMapper mapper) {
        this.loginLimiter = new FixedWindowLimiter(props.rateLimit().loginPerMinute(), 60_000, clock);
        this.apiLimiter = new FixedWindowLimiter(props.rateLimit().apiPerMinute(), 60_000, clock);
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String ip = req.getRemoteAddr();
        boolean login = req.getRequestURI().equals("/api/auth/login");
        FixedWindowLimiter limiter = login ? loginLimiter : apiLimiter;
        if (!limiter.tryAcquire(ip)) {
            res.setHeader("Retry-After", "60");
            SecurityConfig.writeProblem(res, mapper, HttpStatus.TOO_MANY_REQUESTS, "Demasiadas solicitudes",
                    login ? "Demasiados intentos de inicio de sesión; espera un minuto"
                          : "Has superado el límite de solicitudes por minuto", "rate-limit");
            return;
        }
        chain.doFilter(req, res);
    }
}
