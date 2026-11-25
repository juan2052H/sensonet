package com.sensonet.security;

import com.sensonet.config.SensonetProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Crea los usuarios iniciales desde variables de entorno si la tabla está vacía. */
@Component
@Order(1)
public class UserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);
    private final UserRepository repo;
    private final UserService users;
    private final SensonetProperties.Seed seed;

    public UserSeeder(UserRepository repo, UserService users, SensonetProperties props) {
        this.repo = repo;
        this.users = users;
        this.seed = props.seed();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repo.count() > 0 || seed == null) {
            return;
        }
        if (isBlank(seed.adminEmail()) || isBlank(seed.adminPassword())) {
            log.warn("No hay usuarios y no se definieron SENSONET_SEED_ADMIN_EMAIL/PASSWORD: nadie podrá iniciar sesión");
            return;
        }
        users.create(seed.adminEmail(), seed.adminPassword(), Role.ADMIN);
        log.info("Usuario ADMIN inicial creado: {}", seed.adminEmail());
        if (!isBlank(seed.viewerEmail()) && !isBlank(seed.viewerPassword())) {
            users.create(seed.viewerEmail(), seed.viewerPassword(), Role.VIEWER);
            log.info("Usuario VIEWER inicial creado: {}", seed.viewerEmail());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
