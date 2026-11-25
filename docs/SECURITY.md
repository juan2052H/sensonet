# Seguridad

Casi todos los proyectos IoT de ejemplo usan MQTT sin cifrar y sin autenticación. SensoNet parte de lo contrario: el broker **solo** acepta TLS y cada nodo tiene credenciales y permisos propios.

## Modelo de amenazas

| Amenaza | Ejemplo | Mitigación | Dónde |
|---|---|---|---|
| Suplantación de nodo | Alguien publica lecturas falsas | Usuario y contraseña únicos por nodo; ACL `pattern write sensonet/v1/%u/...` | `infra/mosquitto/acl` |
| Escucha de tráfico | Captura de lecturas o credenciales | TLS ≥ 1.2 con CA propia; no hay listener sin cifrar. El backend y el simulador verifican el certificado y el nombre del servidor | `mosquitto.conf`, `SslSocketFactories` |
| Comandos no autorizados | Encender el relé desde fuera | Solo el usuario `backend` puede publicar en `cmd/relay` y `config`; en la API, JWT y rol `ADMIN` | ACL, `SecurityConfig` |
| Datos maliciosos | JSON enorme o valores absurdos | `max_packet_size 4096` en el broker, límite de tamaño en el backend, validación de esquema y de rango físico; los mensajes rechazados se registran | `IngestionService`, `TelemetryValidator` |
| Inyección en el nodo | Un id de comando que rompe el JSON del ACK | El firmware deja solo caracteres `[0-9A-Za-z-]` en los ids y limita los mensajes a 512 bytes | `telemetry_codec.cpp` |
| Fuga de secretos | Contraseñas en GitHub | `.env` y `secrets.h` en `.gitignore`; plantillas `.example`; el backend no registra el token de Telegram; gitleaks en CI | `.gitignore`, `security.yml` |
| Fuerza bruta | Adivinar la contraseña de un usuario | 10 intentos por minuto y por IP; BCrypt coste 12; tiempo de respuesta igual exista o no el usuario | `RateLimitFilter`, `UserService` |
| Denegación de servicio | Inundar el broker o la API | `max_connections`, `max_inflight_messages`, `max_queued_messages`; 600 peticiones por minuto y por IP en la API | `mosquitto.conf`, `RateLimitFilter` |
| Robo del token | XSS que lee el JWT | CSP estricta en nginx (`script-src 'self'`), React escapa el contenido, el token caduca en 8 h | `nginx.conf` |
| WebSocket abierto | Suscribirse sin sesión | El frame `CONNECT` de STOMP exige un JWT válido; solo se permite suscribirse a `/topic/**` y los clientes no pueden enviar mensajes | `StompAuthInterceptor` |
| Firmware alterado | OTA con un binario malicioso | OTA fuera del alcance actual. Si se agrega: solo por HTTPS y con firma verificada | — |

## Verificaciones hechas contra el sistema real

- `sim-lab`, con sus propias credenciales válidas, publicó en `sensonet/v1/sim-sala/telemetry`. El broker descartó el mensaje (ACL) y no se guardó ninguna lectura falsa.
- Conexión anónima: rechazada (`not authorised`).
- Conexión sin TLS al puerto 8883: rechazada (error de protocolo).
- Petición a la API desde un origen no permitido: 403 (CORS).
- Un `VIEWER` que intenta encender el relé: 403 (prueba automatizada `ApiSecurityIT`).

## Certificados

`infra/certs/gen-certs.sh` crea una CA propia y un certificado de servidor con SAN `mosquitto`, `localhost` y `127.0.0.1`, y con las IP o nombres extra que indiques en `MQTT_EXTRA_SAN`. El contenedor `certs` lo ejecuta en cada `docker compose up` y no regenera los certificados si ya existen, para no invalidar los nodos ya configurados. La llave de la CA queda en `infra/certs/generated/`, que está en `.gitignore`.

En la demo pública se usa un broker gestionado con TLS (HiveMQ Cloud o EMQX Cloud) y credenciales propias del simulador, nunca las de un nodo real.

## Limitaciones conocidas

- El límite de peticiones vive en memoria: sirve para una sola instancia del backend.
- Las credenciales de los nodos se definen en `.env` (`MQTT_NODES`). Un sistema en producción usaría certificados de cliente por dispositivo (mTLS) o un servicio de aprovisionamiento.
- El JWT se guarda en `localStorage`. Una cookie `HttpOnly` reduciría el impacto de un XSS, a cambio de tener que proteger contra CSRF.

## Reportar una vulnerabilidad

Abre un issue privado (Security advisory) en el repositorio de GitHub.
