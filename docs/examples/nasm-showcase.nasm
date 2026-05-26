; NASM x86-64 instruction & register showcase (Advanced Assembly plugin)
; Hover any instruction or register for its documentation popup; Ctrl/Cmd-click
; a label to jump to its definition. This file exercises the mnemonic and
; register coverage the plugin understands.

section .data
    msg     db "hello", 10
    msg_len equ $ - msg
    value   dq 42
    arr     dq 1, 2, 3, 4, 5

section .bss
    buf     resb 64
    result  resq 1

section .text
    global _start

; ─── Data Movement ───────────────────────────────────────────────────────────
_start:
    mov     rax, 0
    mov     rbx, rsp
    mov     rcx, [value]
    mov     [result], rax
    movsx   rax, byte [msg]
    movzx   rbx, byte [msg]
    movsxd  rax, dword [value]
    lea     rsi, [msg]
    lea     rdi, [buf]
    push    rbp
    pop     rbp
    xchg    rax, rbx
    cbw
    cwd
    cdq
    cqo
    bswap   eax

; ─── Arithmetic ──────────────────────────────────────────────────────────────
arith:
    add     rax, rbx
    add     rax, 10
    add     [result], rax
    adc     rax, rbx
    sub     rax, rcx
    sbb     rdx, r8
    inc     rax
    dec     rbx
    neg     rcx
    mul     rbx
    imul    rax, rbx
    imul    rax, rbx, 7
    xor     edx, edx
    div     rbx
    cdq
    idiv    rbx
    popcnt  rax, rbx
    lzcnt   rax, rbx
    tzcnt   rax, rbx

; ─── Bitwise & Shifts ────────────────────────────────────────────────────────
bitwise:
    and     rax, 0xFF
    or      rax, 0x01
    xor     rax, rax
    not     rbx
    shl     rax, 3
    shl     rax, cl
    shr     rbx, 1
    sar     rcx, cl
    sal     rdx, 2
    rol     rax, 1
    ror     rbx, cl
    rcl     rax, 1
    rcr     rbx, cl

; ─── Bit Operations ──────────────────────────────────────────────────────────
bitops:
    bt      rax, 5
    bts     rax, rbx
    btr     rax, rcx
    btc     [result], rax
    bsf     rax, rbx
    bsr     rcx, rdx

; ─── Compare & Test ──────────────────────────────────────────────────────────
compare:
    cmp     rax, rbx
    cmp     rax, 0
    test    rax, rax
    test    rcx, 0x0F

; ─── Conditional Move (cmovcc) ───────────────────────────────────────────────
cmov_demo:
    ; signed
    cmove   rax, rbx
    cmovne  rax, rbx
    cmovl   rax, rbx
    cmovle  rax, rbx
    cmovg   rax, rbx
    cmovge  rax, rbx
    cmovnge rax, rbx
    cmovnl  rax, rbx
    ; unsigned
    cmova   rax, rbx
    cmovae  rax, rbx
    cmovb   rax, rbx
    cmovbe  rax, rbx
    cmovnbe rax, rbx
    cmovnb  rax, rbx
    cmovnae rax, rbx
    cmovna  rax, rbx
    ; aliased forms
    cmovz   rax, rbx
    cmovnz  rax, rbx
    cmovc   rax, rbx
    cmovnc  rax, rbx
    ; other flags
    cmovs   rax, rbx
    cmovns  rax, rbx
    cmovo   rax, rbx
    cmovno  rax, rbx
    cmovp   rax, rbx
    cmovnp  rax, rbx

; ─── Set Byte (setcc) ────────────────────────────────────────────────────────
setcc_demo:
    ; signed
    sete    al
    setne   bl
    setl    cl
    setle   dl
    setg    r8b
    setge   r9b
    setnge  r10b
    setnl   r11b
    ; unsigned
    seta    al
    setae   bl
    setb    cl
    setbe   dl
    setnbe  al
    setnb   bl
    setnae  cl
    setna   dl
    ; aliased forms
    setz    al
    setnz   bl
    setc    cl
    setnc   dl
    ; other flags
    sets    al
    setns   bl
    seto    cl
    setno   dl
    setp    al
    setnp   bl

; ─── Jumps ───────────────────────────────────────────────────────────────────
jumps:
    jmp     done
    ; signed
    je      done
    jne     done
    jl      done
    jle     done
    jg      done
    jge     done
    jnge    done
    jng     done
    jnl     done
    jnle    done
    ; unsigned
    ja      done
    jae     done
    jb      done
    jbe     done
    jnbe    done
    jnb     done
    jnc     done
    jc      done
    jnae    done
    jna     done
    ; other
    jo      done
    jno     done
    js      done
    jns     done
    jp      done
    jpe     done
    jnp     done
    jpo     done
    jrcxz   done
    jecxz   done

; ─── Loop & Call ─────────────────────────────────────────────────────────────
    mov     rcx, 5
.loop_top:
    loop    .loop_top
    loope   .loop_top
    loopz   .loop_top
    loopne  .loop_top
    loopnz  .loop_top

    call    my_func
    ret

my_func:
    push    rbp
    mov     rbp, rsp
    sub     rsp, 32
    leave
    ret

; ─── String Operations ───────────────────────────────────────────────────────
strings:
    cld
    rep     movsb
    rep     movsw
    rep     movsd
    rep     movsq
    rep     stosb
    rep     stosw
    rep     stosd
    rep     stosq
    lodsb
    lodsw
    lodsd
    lodsq
    repe    cmpsb
    repe    cmpsw
    repne   scasb
    repne   scasw

; ─── Flags ───────────────────────────────────────────────────────────────────
flags:
    clc
    stc
    cmc
    cld
    std
    lahf
    sahf
    pushfq
    popfq
    pushfd
    popfd
    pushf
    popf

; ─── All 64-bit GPRs ─────────────────────────────────────────────────────────
registers:
    xor     rax, rax
    xor     rbx, rbx
    xor     rcx, rcx
    xor     rdx, rdx
    xor     rsi, rsi
    xor     rdi, rdi
    xor     r8,  r8
    xor     r9,  r9
    xor     r10, r10
    xor     r11, r11
    xor     r12, r12
    xor     r13, r13
    xor     r14, r14
    xor     r15, r15

; 32-bit sub-registers
    xor     eax,  eax
    xor     ebx,  ebx
    xor     ecx,  ecx
    xor     edx,  edx
    xor     esi,  esi
    xor     edi,  edi
    xor     r8d,  r8d
    xor     r9d,  r9d
    xor     r10d, r10d
    xor     r11d, r11d

; 16-bit sub-registers
    xor     ax,  ax
    xor     bx,  bx
    xor     cx,  cx
    xor     dx,  dx
    xor     si,  si
    xor     di,  di
    xor     r8w, r8w

; 8-bit sub-registers
    xor     al, al
    xor     bl, bl
    xor     cl, cl
    xor     dl, dl
    xor     sil, sil
    xor     dil, dil
    xor     r8b, r8b
    xor     r9b, r9b
    mov     ah, 0
    mov     bh, 0
    mov     ch, 0
    mov     dh, 0

; ─── Segment Registers (read only in 64-bit) ─────────────────────────────────
    mov     ax, cs
    mov     ax, ds
    mov     ax, es
    mov     ax, fs
    mov     ax, gs
    mov     ax, ss

; ─── SIMD Registers (XMM / YMM) ──────────────────────────────────────────────
simd:
    xorps   xmm0, xmm0
    xorps   xmm1, xmm1
    xorps   xmm7, xmm7
    xorps   xmm8, xmm8
    xorps   xmm15, xmm15

; ─── System & Misc ───────────────────────────────────────────────────────────
system:
    nop
    pause
    ud2

    cpuid

    rdtsc
    rdtscp

    lfence
    sfence
    mfence

    xlatb

    ; Linux write(1, msg, msg_len)
    mov     rax, 1
    mov     rdi, 1
    lea     rsi, [msg]
    mov     rdx, msg_len
    syscall

    ; Linux exit(0)
    mov     rax, 60
    xor     rdi, rdi
    syscall

    int     0x80
    int3

; ─── x87 FPU ─────────────────────────────────────────────────────────────────
fpu:
    fld     qword [value]
    fst     qword [result]
    fstp    qword [result]
    fadd    st0, st1
    fsub    st0, st1
    fmul    st0, st1
    fdiv    st0, st1
    fsqrt
    fsin
    fcos
    fldz
    fld1

; ─── SSE / SSE2 (scalar + packed) ─────────────────────────────────────────────
sse:
    movaps   xmm0, xmm1
    movups   xmm0, [buf]
    movss    xmm0, [value]
    movsd    xmm0, [value]
    addps    xmm0, xmm1
    addss    xmm0, xmm1
    mulps    xmm0, xmm1
    mulsd    xmm0, xmm1
    divss    xmm0, xmm1
    sqrtps   xmm0, xmm1
    cvtsi2ss xmm0, eax
    cvtss2si eax, xmm0

; ─── SSE4 ─────────────────────────────────────────────────────────────────────
sse4:
    roundps   xmm0, xmm1, 0
    pcmpestri xmm0, xmm1, 0

; ─── AVX / AVX2 (VEX-encoded) ─────────────────────────────────────────────────
avx:
    vzeroupper
    vmovaps      ymm0, ymm1
    vaddps       ymm0, ymm1, ymm2
    vfmadd213ss  xmm0, xmm1, xmm2
    vpbroadcastd ymm0, xmm1

; ─── BMI2 ─────────────────────────────────────────────────────────────────────
bmi2:
    mulx    rax, rdx, rbx
    rorx    rax, rbx, 8
    pdep    rax, rbx, rcx
    pext    rax, rbx, rcx

; ─── AES-NI ───────────────────────────────────────────────────────────────────
aes:
    aesenc          xmm0, xmm1
    aesenclast      xmm0, xmm1
    aeskeygenassist xmm0, xmm1, 1

; ─── Extended SIMD & opmask registers (AVX-512) ───────────────────────────────
extregs:
    vmovaps zmm0, zmm1
    vmovaps zmm31, zmm30
    vmovaps xmm16, xmm17
    vmovaps ymm20, ymm21
    kmovq   k1, k2

done:
    hlt