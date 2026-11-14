/* Known live UB954, bus7 address0x3d. Exactly 13 register-address/read
 * pairs in ONE I2C_RDWR adapter-locked transfer; no control-data writes.
 * Registers: 00 03 4c 58 5b 5c f0 f1 f2 f3 f4 f5 4c.
 * No status/clear-on-read registers; no port-selection, alias or GPIO writes.
 * stdout is 13 raw bytes only on complete success. Exit1 on any failure.
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
 cmp x0,#26
 b.ne fail
 mov x0,x19
 mov x8,#57
 svc #0
 mov x0,#1
 adr x1,values
 mov x2,#13
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
 .word 26,0
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
readreg 3
readreg 4
readreg 5
readreg 6
readreg 7
readreg 8
readreg 9
readreg 10
readreg 11
readreg 12
registers: .byte 0,3,0x4c,0x58,0x5b,0x5c,0xf0,0xf1,0xf2,0xf3,0xf4,0xf5,0x4c
values: .space 13
