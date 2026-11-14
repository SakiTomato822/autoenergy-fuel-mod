ENTRY(_start)
SECTIONS {
  .text 0x10000 : { test-proxy.o(.text) }
  .stub 0x1b500 : { proxy-stubs.o(.text) }
  .vptr 0x1f7d0 : { test-proxy.o(.vptr) }
  .data 0x20000 : { test-proxy.o(.data) }
}
