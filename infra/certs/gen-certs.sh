#!/bin/sh
# Crea una CA propia y un certificado de servidor para Mosquitto (solo entorno local).
# Uso: sh gen-certs.sh <directorio-salida> [nombres-extra separados por coma]
# Si los certificados ya existen no hace nada (para no invalidar los nodos ya flasheados).
set -eu
OUT=${1:-./generated}
EXTRA=${2:-}
mkdir -p "$OUT"

if [ -f "$OUT/ca.crt" ] && [ -f "$OUT/server.crt" ]; then
  echo "Certificados existentes en $OUT; no se regeneran."
  exit 0
fi

command -v openssl >/dev/null 2>&1 || apk add --no-cache openssl >/dev/null

SAN="DNS:mosquitto,DNS:localhost,IP:127.0.0.1"
if [ -n "$EXTRA" ]; then
  for n in $(echo "$EXTRA" | tr ',' ' '); do
    case "$n" in
      *[!0-9.]*) SAN="$SAN,DNS:$n" ;;
      *)         SAN="$SAN,IP:$n" ;;
    esac
  done
fi

openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
  -keyout "$OUT/ca.key" -out "$OUT/ca.crt" -subj "/CN=SensoNet Local CA"

openssl req -newkey rsa:2048 -nodes \
  -keyout "$OUT/server.key" -out "$OUT/server.csr" -subj "/CN=mosquitto"

printf "subjectAltName=%s\nextendedKeyUsage=serverAuth\n" "$SAN" > "$OUT/server.ext"
openssl x509 -req -in "$OUT/server.csr" -CA "$OUT/ca.crt" -CAkey "$OUT/ca.key" \
  -CAcreateserial -out "$OUT/server.crt" -days 825 -extfile "$OUT/server.ext"

rm -f "$OUT/server.csr" "$OUT/server.ext" "$OUT/ca.srl"
# Mosquitto corre como usuario sin privilegios: debe poder leer la llave (solo en local).
chmod 644 "$OUT/server.key" "$OUT/server.crt" "$OUT/ca.crt"
chmod 600 "$OUT/ca.key"
echo "Certificados creados en $OUT (SAN: $SAN)"
