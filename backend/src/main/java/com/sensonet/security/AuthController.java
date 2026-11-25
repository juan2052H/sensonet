package com.sensonet.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Autenticación y usuarios")
public class AuthController {

    private final UserService users;
    private final JwtService jwt;

    public AuthController(UserService users, JwtService jwt) {
        this.users = users;
        this.jwt = jwt;
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record LoginResponse(String token, Instant expiresAt, String email, Role role) {}

    public record UserDto(Long id, String email, Role role, Instant createdAt) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(@NotBlank @Email String email,
                                    @NotBlank @Size(min = 10, max = 72, message = "Entre 10 y 72 caracteres") String password,
                                    Role role) {}

    @Operation(summary = "Inicia sesión y devuelve un JWT")
    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        User user = users.authenticate(req.email(), req.password());
        JwtService.IssuedToken token = jwt.issue(user);
        return new LoginResponse(token.token(), token.expiresAt(), user.getEmail(), user.getRole());
    }

    @Operation(summary = "Datos del usuario autenticado")
    @GetMapping("/api/auth/me")
    public MeResponse me(@AuthenticationPrincipal Jwt principal) {
        List<String> roles = principal.getClaimAsStringList("roles");
        return new MeResponse(principal.getSubject(), Role.valueOf(roles.get(0)));
    }

    public record MeResponse(String email, Role role) {}

    @Operation(summary = "Lista de usuarios (ADMIN)")
    @GetMapping("/api/users")
    public List<UserDto> list() {
        return users.list().stream().map(UserDto::of).toList();
    }

    @Operation(summary = "Crea un usuario (ADMIN)")
    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return UserDto.of(users.create(req.email(), req.password(), req.role() == null ? Role.VIEWER : req.role()));
    }

    @Operation(summary = "Elimina un usuario (ADMIN)")
    @DeleteMapping("/api/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @AuthenticationPrincipal Jwt principal) {
        users.delete(id, principal.getSubject());
    }
}
