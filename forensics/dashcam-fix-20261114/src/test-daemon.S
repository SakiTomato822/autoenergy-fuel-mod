.text
.global _start, sleep_plt, original_start_call, original_cleanup, inc_strong, return_status_destructor
_start:
    sub sp, sp, #128
    ldr x8, =fake_sink
    str x8, [sp, #24]
    mov x19, #0
    mov x20, #0
    mov x21, #0
    mov x22, #0
    mov x23, #0
    mov x24, #0
1:  adr x28, 2f
    b patch_retry
2:  add x20, x20, #1
    cmp x20, #61
    b.ne 1b
    cmp x21, #60
    b.ne fail
    cmp x22, #60
    b.ne fail
    cmp w19, #4
    b.ne fail
    cmp x23, #60
    b.ne fail
    cmp x24, #60
    b.ne fail
    mov x0, #1
    ldr x1, =message
    mov x2, #message_len
    mov x8, #64
    svc #0
    mov x0, #0
    mov x8, #93
    svc #0
fail:
    mov x0, #43
    mov x8, #93
    svc #0
.ltorg
.org 0x1060
original_start_call:
    add x21, x21, #1
    br x28
.org 0x11b0
original_cleanup:
    br x28
.org 0x11290
sleep_plt:
    cmp w0, #2
    b.ne fail
    add x22, x22, #1
    ret
.org 0x112c0
inc_strong:
    add x23, x23, #1
    ret
.org 0x112d0
return_status_destructor:
    add x24, x24, #1
    ret
.data
.balign 8
fake_vtable_header: .quad 0, 0, 0
fake_vtable: .quad 0
fake_sink: .quad fake_vtable
message: .ascii "PASS: 60 retries then bounded failure\n"
.equ message_len, .-message
.section .counter,"aw",%nobits
.balign 8
.global retry_counter
retry_counter: .space 16
