/* One known combined chip-ID register read over the already enabled GPIO bus.
 * No scan, slave-force, GPIO, bind, init-table or control-register writes.
 * aarch64 Linux raw syscalls; first I2C msg only sets register address 0x000d.
 */
.text
.global _start
_start:
    mov x0, #-100
    adr x1, bus_path
    mov x2, #2
    mov x3, #0
    mov x8, #56
    svc #0
    cmp x0, #0
    b.lt fail_open
    mov x19, x0
    mov x1, #0x705  /* I2C_FUNCS: adapter capability query, no bus traffic */
    adr x2, funcs
    mov x8, #29
    svc #0
    cmp x0, #0
    b.lt fail_ioctl
    adr x9, funcs
    ldr x9, [x9]
    tbz x9, #0, fail_ioctl
    mov x0, x19
    mov x1, #0x707  /* I2C_RDWR: exactly two messages in one transfer */
    adr x2, request
    mov x8, #29
    svc #0
    cmp x0, #2
    b.ne fail_ioctl
    adr x9, id_byte
    ldrb w10, [x9]
    adr x11, hex
    lsr w12, w10, #4
    ldrb w12, [x11, x12]
    adr x13, id_digits
    strb w12, [x13]
    and w10, w10, #15
    ldrb w12, [x11, x10]
    strb w12, [x13, #1]
    mov x0, x19
    mov x8, #57
    svc #0
    mov x0, #1
    adr x1, success
    mov x2, #success_len
    mov x8, #64
    svc #0
    mov x0, #0
    b finish
fail_open:
    mov x19, #-1
    adr x20, open_error
    mov x21, #open_error_len
    b print_fail
fail_ioctl:
    /* Preserve raw negative errno for process exit status below. */
    neg x22, x0
    adr x11, hex
    adr x13, errno_digits
    mov x14, #12
1:  lsr x15, x22, x14
    and x15, x15, #15
    ldrb w12, [x11, x15]
    strb w12, [x13], #1
    subs x14, x14, #4
    b.pl 1b
    mov x0, x19
    mov x8, #57
    svc #0
    adr x20, read_error
    mov x21, #read_error_len
print_fail:
    mov x0, #1
    mov x1, x20
    mov x2, x21
    mov x8, #64
    svc #0
    mov x0, #1
finish:
    mov x8, #93
    svc #0
bus_path: .asciz "/dev/i2c-31"
hex: .ascii "0123456789abcdef"
.data
.balign 8
funcs: .quad 0
request: .quad messages
    .word 2,0
messages:
    .hword 0x6b,0,2,0
    .quad reg_addr
    .hword 0x6b,1,1,0
    .quad id_byte
reg_addr: .byte 0,13
id_byte: .byte 0
success: .ascii "bus31 addr=0x6b reg=0x000d id=0x"
id_digits: .ascii "00\n"
.equ success_len, .-success
open_error: .ascii "OPEN_FAILED\n"
.equ open_error_len, .-open_error
read_error: .ascii "KNOWN_ID_READ_NO_RESULT errno_low16=0x"
errno_digits: .ascii "0000\n"
.equ read_error_len, .-read_error
