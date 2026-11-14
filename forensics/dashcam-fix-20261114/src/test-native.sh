#!/bin/sh
# Standalone AArch64 tests; no ADB, HAL, vehicle property or service access.
set -eu
cd "$(dirname "$0")/.."
mkdir -p out
cd out
as -o proxy-stubs.o ../src/proxy-stubs.S
as -o test-proxy.o ../src/test-proxy.S
ld.bfd -T ../src/test-proxy.ld --defsym=proxy_camera_vptr=0x1f7d0 -o test-proxy test-proxy.o proxy-stubs.o
./test-proxy
as -o daemon-stub.o ../src/daemon-stub.S
as -o test-daemon.o ../src/test-daemon.S
ld.bfd -T ../src/test-daemon.ld --defsym=retry_counter=0x35b20 -o test-daemon test-daemon.o daemon-stub.o
./test-daemon
