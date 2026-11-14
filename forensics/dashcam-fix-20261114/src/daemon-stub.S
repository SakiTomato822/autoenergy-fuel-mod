/* On startVideoStream failure, allow 60 retries, two seconds apart.
 * Retry the existing main-loop call with its original camera and Sink.
 * No vendor camera configuration is forced by the recorder.
 */
.text
.global patch_retry
.type patch_retry,%function
patch_retry:
    adr x9, retry_counter
    ldr w10, [x9]
    cmp w10, #60
    b.hs .Lgive_up
    add w10, w10, #1
    str w10, [x9]
    add x0, sp, #0x48
    bl return_status_destructor
    mov w0, #2
    bl sleep_plt
    /* main releases the temporary strong reference after each stream call.
     * Re-create it for each retry; otherwise the second retry frees the Sink.
     */
    ldr x8, [sp, #24]
    str x8, [sp, #56]
    cbz x8, .Lretry
    ldr x9, [x8]
    ldur x9, [x9, #-24]
    add x0, x8, x9
    add x1, sp, #56
    bl inc_strong
.Lretry:
    b original_start_call
.Lgive_up:
    mov w19, #4
    b original_cleanup
.size patch_retry, .-patch_retry
