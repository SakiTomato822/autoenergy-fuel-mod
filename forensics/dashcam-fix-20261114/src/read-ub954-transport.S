/* Follow-up: resolve current FPD-Link input transport, not chip ID repeat.
 * Known bus7 addr3d; one adapter-locked I2C_RDWR reads 4c,6d,4c only.
 * No control-data/port/GPIO writes or clear-on-read registers.
 * stdout three raw bytes only on full success.
 */
.text
.global _start
_start:
 mov x0,#-100
 adr x1,path
 mov x2,#2
 mov x3,#0
 mov x8,#56
 svc #0
 cmp x0,#0
 b.lt fail
 mov x19,x0
 mov x1,#0x705
 adr x2,funcs
 mov x8,#29
 svc #0
 cmp x0,#0
 b.lt fail
 adr x9,funcs
 ldr x9,[x9]
 tbz x9,#0,fail
 mov x0,x19
 mov x1,#0x707
 adr x2,request
 mov x8,#29
 svc #0
 cmp x0,#6
 b.ne fail
 mov x0,x19
 mov x8,#57
 svc #0
 mov x0,#1
 adr x1,values
 mov x2,#3
 mov x8,#64
 svc #0
 mov x0,#0
 b done
fail:
 mov x0,#1
done:
 mov x8,#93
 svc #0
path: .asciz "/dev/i2c-7"
.data
.balign 8
funcs: .quad 0
request: .quad messages
 .word 6,0
messages:
.macro readreg idx
 .hword 0x3d,0,1,0
 .quad registers+\idx
 .hword 0x3d,1,1,0
 .quad values+\idx
.endm
readreg 0
readreg 1
readreg 2
registers: .byte 0x4c,0x6d,0x4c
values: .space 3
