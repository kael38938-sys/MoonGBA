void gba_cpu_step(GbaCPU* cpu, GbaMemory* mem) {
    uint32_t pc = cpu->r[15] & ~1;   // THUMB PC must be halfword aligned
    uint16_t op = gba_read_u32(mem, pc) & 0xFFFF;
    cpu->r[15] += 2;
    cpu->cycles += 1;

    uint32_t rn, rm, rs, rd, val;
    uint8_t imm, type;

    switch ((op >> 12) & 0xF) {
        // ... cases 0x0-0x4 stay the same ...

        case 0x5: // Load from PC/LDR literal
            rd = (op >> 8) & 7; imm = op & 0xFF;
            // PC points 4 bytes ahead in THUMB (already advanced by 2, so +2 more)
            cpu->r[rd] = gba_read_u32(mem, ((pc + 2) & ~3) + (imm << 2));
            break;

        // ... cases 0x6-0xC stay the same ...

        case 0xD: // Conditional branch
            rn = (op >> 8) & 0xF; imm = (op & 0xFF) << 1;
            bool cond = false;
            switch (rn) {
                case 0: cond = (cpu->cpsr & 0x80000000) != 0; break; // MI (N=1)
                case 1: cond = (cpu->cpsr & 0x80000000) == 0; break; // PL (N=0)
                case 2: cond = (cpu->cpsr & 0x40000000) != 0; break; // Z=1 (EQ)
                case 3: cond = (cpu->cpsr & 0x40000000) == 0; break; // Z=0 (NE)
                case 4: cond = (cpu->cpsr & 0x10000000) != 0; break; // VS (V=1)  FIXED
                case 5: cond = (cpu->cpsr & 0x10000000) == 0; break; // VC (V=0)  FIXED
                case 6: cond = (cpu->cpsr & 0x08000000) != 0; break; // C=1 (CS/HS)
                case 7: cond = (cpu->cpsr & 0x08000000) == 0; break; // C=0 (CC/LO)
                case 8: cond = true; break; // AL (always)
                default: cond = false;
            }
            if (cond) cpu->r[15] = (cpu->r[15] & ~1) + ((int16_t)imm);
            break;

        // ... cases 0xE-0xF stay the same ...
    }
}
