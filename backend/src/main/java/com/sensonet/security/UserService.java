package com.sensonet.security;

import com.sensonet.common.ConflictException;
import com.sensonet.common.NotFoundException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /** Hash BCrypt de una contraseña aleatoria: igualar el tiempo de respuesta cuando el usuario no existe. */
    private static final String DUMMY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEeO5yWyFQfJcIQeVjHYW0/3l4CQGr1o0BK";

    private final UserRepository repo;
    private final PasswordEncoder encoder;

    public UserService(UserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        User user = repo.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            encoder.matches(password, DUMMY_HASH);
            throw new BadCredentialsException("bad credentials");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("bad credentials");
        }
        return user;
    }

    @Transactional
    public User create(String email, String password, Role role) {
        String normalized = email.trim().toLowerCase();
        if (repo.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException("Ya existe un usuario con ese correo");
        }
        return repo.save(new User(normalized, encoder.encode(password), role));
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return repo.findAll();
    }

    @Transactional
    public void delete(long id, String currentEmail) {
        User user = repo.findById(id).orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (user.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new IllegalArgumentException("No puedes eliminar tu propio usuario");
        }
        if (user.getRole() == Role.ADMIN && repo.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("Debe quedar al menos un administrador");
        }
        repo.delete(user);
    }
}
