# ADR 0002: Broker: Eclipse Mosquitto con TLS y ACL

Estado: aceptada · Fecha: 2026-11-24

## Decisión
Eclipse Mosquitto 2 en Docker, con un único listener TLS (8883), `allow_anonymous false`, archivo de
contraseñas generado al arrancar desde variables de entorno y ACL con `pattern ... %u`.

## Alternativas
EMQX o HiveMQ: más funciones (panel, clúster, autenticación por API), pero más pesados. Sus planes en la
nube se usan para la demo pública (DESPLIEGUE.md).

## Consecuencias
- Cada nodo solo puede escribir en sus tópicos: robar las credenciales de un nodo no permite suplantar a otro.
- Sin puerto sin cifrar, la depuración usa `mosquitto_sub --cafile`.
- El archivo de contraseñas no se versiona: se genera con hash PBKDF2 en `entrypoint.sh`.
