package com.sensonet.security;

import tools.jackson.databind.ObjectMapper;
import com.sensonet.common.ApiExceptionHandler;
import com.sensonet.config.SensonetProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] VIEW = {"VIEWER", "ADMIN"};

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, RateLimitFilter rateLimitFilter,
                                           ObjectMapper mapper) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API sin cookies: el JWT viaja en el encabezado Authorization
            .cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'"))
                .frameOptions(f -> f.deny()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/ws/**").permitAll() // la autenticación se hace en el CONNECT de STOMP
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/settings/**", "/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(VIEW)
                .requestMatchers("/api/**").hasRole("ADMIN")
                .anyRequest().denyAll())
            .oauth2ResourceServer(rs -> rs
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
                .authenticationEntryPoint((req, res, ex) -> writeProblem(res, mapper, HttpStatus.UNAUTHORIZED,
                        "No autenticado", "Inicia sesión para continuar", "unauthorized"))
                .accessDeniedHandler((req, res, ex) -> writeProblem(res, mapper, HttpStatus.FORBIDDEN,
                        "Prohibido", "Tu rol no permite esta acción", "forbidden")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> writeProblem(res, mapper, HttpStatus.UNAUTHORIZED,
                        "No autenticado", "Inicia sesión para continuar", "unauthorized"))
                .accessDeniedHandler((req, res, ex) -> writeProblem(res, mapper, HttpStatus.FORBIDDEN,
                        "Prohibido", "Tu rol no permite esta acción", "forbidden")))
            .addFilterBefore(rateLimitFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService.decoder();
    }

    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SensonetProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = props.cors() != null && props.cors().allowedOrigins() != null
                ? props.cors().allowedOrigins() : List.of();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        source.registerCorsConfiguration("/ws/**", cfg);
        return source;
    }

    static void writeProblem(HttpServletResponse res, ObjectMapper mapper, HttpStatus status,
                             String title, String detail, String type) throws IOException {
        ProblemDetail pd = ApiExceptionHandler.problem(status, title, detail, type);
        res.setStatus(status.value());
        res.setContentType("application/problem+json");
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getOutputStream(), pd);
    }
}
