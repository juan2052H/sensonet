# Despliegue de la demo pública

La demo pública funciona siempre con el simulador. Estos pasos requieren cuentas propias; no están automatizados porque necesitan tus credenciales. Los planes gratuitos cambian con frecuencia: revisa sus condiciones antes de elegir.

| Componente | Opción sugerida | Notas |
|---|---|---|
| Broker MQTT | HiveMQ Cloud o EMQX Cloud (plan gratuito) | Incluyen TLS. Crea un usuario `backend` y usuarios para los nodos simulados; configura permisos equivalentes a `infra/mosquitto/acl` si el plan lo permite |
| Base de datos | PostgreSQL gestionado (Neon, Render…) | Si no ofrece TimescaleDB, usa `SENSONET_DB_FLAVOR=postgres` (ver ADR 0004) |
| Backend | Render (Web Service con Docker, carpeta `backend/`) | Se duerme sin tráfico; el simulador lo mantiene activo |
| Frontend | GitHub Pages | El workflow `frontend.yml` lo publica si defines la variable del repositorio `SENSONET_API_URL` |
| Simulador | Worker de Render, o tu PC cuando muestres la demo | `python sim.py --host <broker> --port 8883 --ca <ca del proveedor>` |

## Variables del backend

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>/<db>?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | credenciales de la base |
| `SENSONET_DB_FLAVOR` | `timescale` o `postgres` |
| `SENSONET_JWT_SECRET` | `openssl rand -base64 48` |
| `SENSONET_MQTT_URL` | `ssl://<broker>:8883` |
| `SENSONET_MQTT_USERNAME` / `_PASSWORD` | usuario `backend` del broker |
| `SENSONET_MQTT_CA_FILE` | vacío si el broker usa un certificado público (Let's Encrypt) |
| `SENSONET_CORS_ORIGINS` | `https://<usuario>.github.io` |
| `SENSONET_SEED_ADMIN_EMAIL` / `_PASSWORD` | administrador inicial |
| `SENSONET_SEED_VIEWER_EMAIL` / `_PASSWORD` | usuario de solo lectura para compartir en el README |
| `SENSONET_TELEGRAM_BOT_TOKEN` / `_CHAT_ID` | opcionales |

## Pasos

1. Crea la base de datos y el broker y anota sus credenciales.
2. En Render, crea un Web Service apuntando a `backend/` (Dockerfile) con las variables de arriba. Health check: `/actuator/health`.
3. En GitHub: Settings → Pages → Source: GitHub Actions. En Settings → Variables, crea `SENSONET_API_URL=https://<servicio>.onrender.com`.
4. Haz push a `main`: el workflow compila el dashboard con esa URL y lo publica.
5. Arranca el simulador apuntando al broker en la nube con `--escenario ola-de-calor`, para que la demo muestre alertas.
6. Comparte en el README el usuario `VIEWER`, nunca el `ADMIN`.

## Frontend en un dominio distinto al backend

Cuando el dashboard está en GitHub Pages y el backend en Render, el navegador hace peticiones de otro origen. `SENSONET_CORS_ORIGINS` debe contener exactamente el origen del dashboard. El WebSocket usa `wss://<backend>/ws` y valida el mismo origen.
