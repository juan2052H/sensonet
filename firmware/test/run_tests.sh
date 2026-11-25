#!/bin/sh
# Compila y ejecuta las pruebas nativas de la lógica pura del firmware (requiere g++).
set -eu
cd "$(dirname "$0")"
mkdir -p build
g++ -std=c++17 -Wall -Wextra -Werror -I../sensonet_node \
  test_main.cpp ../sensonet_node/telemetry_codec.cpp ../sensonet_node/offline_ring.cpp \
  -o build/firmware_tests
./build/firmware_tests
