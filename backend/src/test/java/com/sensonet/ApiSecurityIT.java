package com.sensonet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ApiSecurityIT extends AbstractIT {

    @Test
    void loginReturnsJwtWithRole() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Credenciales incorrectas devuelven 401 en formato ProblemDetail")
    void badCredentials() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN + "\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Credenciales inválidas"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@test.local\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestsWithoutTokenGet401() throws Exception {
        mvc.perform(get("/api/nodes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/nodes").header("Authorization", "Bearer token.falso.xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un usuario VIEWER recibe 403 al intentar encender el relé")
    void viewerCannotControlRelay() throws Exception {
        String code = uniqueCode("sec");
        sendTelemetry(code, telemetry(1, Instant.now(), 22, 50, null));
        long id = nodeId(code);
        String viewer = token(VIEWER);
        mvc.perform(get("/api/nodes").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/nodes/" + id + "/commands/relay").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"on\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/rules").header("Authorization", viewer)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", viewer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/settings/system").header("Authorization", viewer)).andExpect(status().isForbidden());
    }

    @Test
    void validationErrorsAreDescribed() throws Exception {
        mvc.perform(post("/api/rules").header("Authorization", token(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"metric\":\"TEMPERATURE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.operator").exists());
        // Histéresis incoherente → 400 con explicación
        mvc.perform(post("/api/rules").header("Authorization", token(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"metric\":\"TEMPERATURE\",\"operator\":\"GT\",\"threshold\":30,"
                                + "\"recovery\":35,\"durationSeconds\":60,\"severity\":\"INFO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("recuperación")));
    }

    @Test
    void unknownNodeIs404() throws Exception {
        mvc.perform(get("/api/nodes/999999").header("Authorization", token(VIEWER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("No encontrado"));
    }

    @Test
    void adminManagesUsers() throws Exception {
        String admin = token(ADMIN);
        String created = mvc.perform(post("/api/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nuevo@test.local\",\"password\":\"una-clave-larga\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json(created).get("id").asLong();
        mvc.perform(post("/api/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nuevo@test.local\",\"password\":\"una-clave-larga\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/users/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        String me = mvc.perform(get("/api/auth/me").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json(me).get("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void publicEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("Authorization", token(ADMIN))).andExpect(status().isOk());
    }

    @Test
    void settingsForAdmin() throws Exception {
        String admin = token(ADMIN);
        mvc.perform(get("/api/settings/system").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dbFlavor").value("timescale"))
                .andExpect(jsonPath("$.telegramEnabled").value(false));
        mvc.perform(get("/api/settings/invalid-readings").header("Authorization", admin))
                .andExpect(status().isOk());
        mvc.perform(post("/api/settings/notifications/test").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delivered").value(false));
    }
}
