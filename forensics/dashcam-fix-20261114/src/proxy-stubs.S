/* Only avm (video6cif) is multiplexed. Other IDs pass through.
 * Entry hooks run before the original stack frame is created.
 * AArch64 x8 is the C++ return-value storage pointer and must survive dispatch.
 */
.text
.global patch_open, patch_close
.type patch_open,%function
patch_open:
    stp x29, x30, [sp, #-48]!
    mov x29, sp
    stp x0, x1, [sp, #16]
    str x8, [sp, #32]
    mov x0, x1
    bl hidl_c_str
    adr x1, selected_id
    bl strcmp_plt
    cbnz w0, .Lopen_vendor
    ldp x0, x1, [sp, #16]
    ldr x8, [sp, #32]
    ldp x29, x30, [sp], #48
    /* Replaced first instruction, then continue original function. */
    sub sp, sp, #0xe0
    b original_open_rest
.Lopen_vendor:
    ldp x0, x1, [sp, #16]
    ldr x8, [sp, #32]
    ldp x29, x30, [sp], #48
    ldr x0, [x0, #8]    /* ProxyEnumerator::backend */
    ldr x0, [x0, #8]    /* DirectBackend::vendor enumerator */
    ldr x9, [x0]
    ldr x9, [x9, #112] /* IEvsEnumerator::openCamera */
    br x9
.size patch_open, .-patch_open

.type patch_close,%function
patch_close:
    ldr x9, [x1]
    cbz x9, .Lclose_proxy
    ldr x10, [x9]
    adr x11, proxy_camera_vptr
    cmp x10, x11
    b.eq .Lclose_proxy
    ldr x0, [x0, #8]
    ldr x0, [x0, #8]
    ldr x9, [x0]
    ldr x9, [x9, #120] /* IEvsEnumerator::closeCamera */
    br x9
.Lclose_proxy:
    sub sp, sp, #0x50
    b original_close_rest
.size patch_close, .-patch_close
selected_id:
    .asciz "avm"
