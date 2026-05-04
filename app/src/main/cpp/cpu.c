#include "gba.h"

void gba_cpu_reset(GbaCPU* cpu) {
    for (int i = 0; i < 16; i++) cpu->r[i] = 0;
    cpu->cpsr = 0x0000001F; // User mode, IRQ/FIQ disabled
    cpu->thumb = false;
    cpu->cycles = 0;
    cpu->r[15] = 0x08000000; // Direct ROM boot (skip BIOS for v1)
}

void gba_cpu_step(GbaCPU* cpu, GbaMemory* mem) {
    uint32_t pc = cpu->r[15];
    uint32_t opcode = gba_read_u32(mem, pc);
    cpu->r[15] += cpu->thumb ? 2 : 4;
    cpu->cycles += 1;

    // 🔧 TODO: Replace with real THUMB/ARM decoder
    // For now, safely loop without crashing
    (void)opcode;
}
