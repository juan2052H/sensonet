package com.sensonet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AppConfig {

    /** Reloj inyectable: las pruebas usan un reloj fijo para validar tiempos de espera y duraciones. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Hilo dedicado al procesamiento de mensajes MQTT (orden de llegada preservado). */
    @Bean(name = "mqttInboundExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor mqttInboundExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(10_000);
        ex.setThreadNamePrefix("mqtt-in-");
        ex.initialize();
        return ex;
    }

    /** Ejecutor para @Async (notificaciones), para no bloquear la ingesta. */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("async-");
        ex.initialize();
        return ex;
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SensoNet API")
                        .version("v1")
                        .description("Monitor IoT en tiempo real: nodos, lecturas, reglas, alertas y comandos.")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
