.text
.global _start, original_open_rest, original_close_rest, hidl_c_str, strcmp_plt
.macro open_test id, expected
    ldr x0, =enumerator
    ldr x1, =\id
    ldr x8, =result
    bl original_open_entry
    ldr x9, =result
    ldr w9, [x9]
    cmp w9, #\expected
    b.ne fail
    cmp sp, x20
    b.ne fail
    cmp x19, #0x191
    b.ne fail
.endm
.macro close_test ref, expected
    ldr x0, =enumerator
    ldr x1, =\ref
    ldr x8, =result
    bl original_close_entry
    ldr x9, =result
    ldr w9, [x9]
    cmp w9, #\expected
    b.ne fail
    cmp sp, x20
    b.ne fail
.endm
_start:
    mov x19, #0x191
    mov x20, sp
    open_test avm, 1
    open_test apa, 2
    open_test unknown, 2
    close_test raw_ref, 3
    close_test proxy_ref, 4
    close_test null_ref, 4
    mov x0, #1
    ldr x1, =message
    mov x2, #message_len
    mov x8, #64
    svc #0
    mov x0, #0
    mov x8, #93
    svc #0
fail:
    mov x0, #42
    mov x8, #93
    svc #0
vendor_open:
    ldr x9, =vendor
    cmp x0, x9
    b.ne fail
    ldr x9, =result
    cmp x8, x9
    b.ne fail
    mov w9, #2
    str w9, [x8]
    ret
vendor_close:
    ldr x9, =vendor
    cmp x0, x9
    b.ne fail
    ldr x9, =raw_ref
    cmp x1, x9
    b.ne fail
    mov w9, #3
    str w9, [x8]
    ret
.ltorg
.org 0x68e0
original_open_entry:
    b patch_open
original_open_rest:
    mov w9, #1
    str w9, [x8]
    add sp, sp, #0xe0
    ret
.org 0x6eac
original_close_entry:
    b patch_close
original_close_rest:
    mov w9, #4
    str w9, [x8]
    add sp, sp, #0x50
    ret
.org 0xaf80
hidl_c_str:
    mov x8, #0 /* Prove dispatch preserves the caller's sret across calls. */
    ret
.org 0xb2b0
strcmp_plt:
    mov x8, #0
1:  ldrb w2, [x0], #1
    ldrb w3, [x1], #1
    subs w4, w2, w3
    b.ne 2f
    cbnz w2, 1b
2:  mov w0, w4
    ret
.section .vptr,"a"
.global proxy_camera_vptr
proxy_camera_vptr: .space 64
.data
.balign 8
enumerator: .quad 0, backend
backend: .quad 0, vendor
vendor: .quad vendor_vtable
vendor_vtable: .space 112
    .quad vendor_open, vendor_close
raw_camera: .quad vendor_vtable
proxy_camera: .quad proxy_camera_vptr
raw_ref: .quad raw_camera
proxy_ref: .quad proxy_camera
null_ref: .quad 0
result: .quad 0
avm: .asciz "avm"
apa: .asciz "apa"
unknown: .asciz "unsupported"
message: .ascii "PASS: proxy dispatch, close and ABI checks\n"
.equ message_len, .-message
