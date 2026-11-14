ENTRY(_start)
SECTIONS {
  .text 0x1b000 : { test-daemon.o(.text) }
  .stub 0x2c9e0 : { daemon-stub.o(.text) }
  .data 0x30000 : { test-daemon.o(.data) }
  .counter 0x35b20 : { test-daemon.o(.counter) }
}
