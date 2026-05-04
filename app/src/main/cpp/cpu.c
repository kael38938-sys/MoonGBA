#include "gba.h"

static inline uint32_t read_u16(GbaMemory* mem, uint32_t addr) {
    return (gba_read_u32(mem, addr) & 0xFFFF);
}

static inline void set_nz(GbaCPU* c, uint32_t val) {
    c->cpsr = (c->cpsr & ~0xC0000000) | (val == 0 ? 0x40000000 : 0) | (val & 0x80000000);
}

void gba_cpu_reset(GbaCPU* cpu) {
    for (int i = 0; i < 16; i++) cpu->r[i] = 0;
    cpu->cpsr = 0x0000001F;
    cpu->thumb = true; // GBA boots in THUMB
    cpu->cycles = 0;
    cpu->r[15] = 0x08000000;
}

void gba_cpu_step(GbaCPU* cpu, GbaMemory* mem) {
    uint32_t pc = cpu->r[15];
    uint16_t op = read_u16(mem, pc);
    cpu->r[15] += 2;
    cpu->cycles += 1;

    uint32_t rn, rm, rs, rd, val;
    uint8_t imm, type;

    switch ((op >> 12) & 0xF) {
        case 0x0: // Shifts by register
            rm = (op >> 3) & 7; rs = (op >> 6) & 7; rd = op & 7;
            type = (op >> 4) & 3; imm = cpu->r[rs] & 0xFF;
            val = cpu->r[rm];
            if (type == 0) val <<= imm;
            else if (type == 1) val >>= imm;
            else if (type == 2) val = (int32_t)val >> imm;
            cpu->r[rd] = val; set_nz(cpu, val);
            break;

        case 0x1: // Add/Sub immediate
            rd = (op >> 8) & 7; rm = (op >> 6) & 7; imm = op & 0xFF;
            val = (op >> 9) & 1 ? cpu->r[rm] - imm : cpu->r[rm] + imm;
            cpu->r[rd] = val; set_nz(cpu, val);
            break;

        case 0x2: // Add/Sub register
            rd = (op >> 8) & 7; rs = (op >> 6) & 7; rm = op & 7;
            val = (op >> 9) & 1 ? cpu->r[rs] - cpu->r[rm] : cpu->r[rs] + cpu->r[rm];
            cpu->r[rd] = val; set_nz(cpu, val);
            break;
        case 0x3: // ALU ops (MOV, CMP, ADD, SUB)
            imm = op & 0xFF; rn = (op >> 8) & 7;
            switch ((op >> 9) & 3) {
                case 0: cpu->r[rn] = imm; set_nz(cpu, imm); break; // MOV
                case 1: val = cpu->r[rn] - imm; set_nz(cpu, val); break; // CMP
                case 2: cpu->r[rn] += imm; set_nz(cpu, cpu->r[rn]); break; // ADD
                case 3: cpu->r[rn] -= imm; set_nz(cpu, cpu->r[rn]); break; // SUB
            }
            break;

        case 0x4: // Register ALU
            rm = (op >> 3) & 7; rn = (op >> 6) & 7; rd = op & 7;
            type = (op >> 8) & 0xF;
            switch (type) {
                case 0: val = cpu->r[rn] & cpu->r[rm]; break;
                case 1: val = cpu->r[rn] ^ cpu->r[rm]; break;
                case 2: val = cpu->r[rn] >> cpu->r[rm]; break;
                case 3: val = cpu->r[rn] << cpu->r[rm]; break;
                default: val = cpu->r[rm]; break; // MOV
            }
            cpu->r[rd] = val; set_nz(cpu, val);
            break;

        case 0x5: // Load from PC/LDR literal
            rd = (op >> 8) & 7; imm = op & 0xFF;
            cpu->r[rd] = gba_read_u32(mem, (cpu->r[15] & ~3) + (imm << 2));
            break;

        case 0x6: // LDR/STR offset
            rm = (op >> 3) & 7; rn = (op >> 6) & 7; rd = op & 7;
            val = cpu->r[rn] + ((op >> 6) & 1 ? 0 : -(int32_t)(rm * 4));
            if (op & 0x0800) cpu->r[rd] = gba_read_u32(mem, val);
            else gba_write_u32(mem, val, cpu->r[rd]);
            break;

        case 0x7: // LDR/STR halfword/byte
            rm = (op >> 3) & 7; rn = (op >> 6) & 7; rd = op & 7;
            val = cpu->r[rn] + rm;
            if (op & 0x0800) {
                if (op & 0x0400) cpu->r[rd] = gba_read_u32(mem, val);
                else cpu->r[rd] = (uint32_t)(int8_t)gba_read_u32(mem, val);
            } else {
                if (op & 0x0400) gba_write_u32(mem, val, cpu->r[rd]);
                else gba_write_u32(mem, val, cpu->r[rd] & 0xFF);
            }
            break;

        case 0x8: // LDR/STR immediate
            rd = (op >> 8) & 7; rn = (op >> 6) & 7; imm = op & 0x1F;
            val = cpu->r[rn] + (imm << 2);            if (op & 0x0800) cpu->r[rd] = gba_read_u32(mem, val);
            else gba_write_u32(mem, val, cpu->r[rd]);
            break;

        case 0x9: // Load/Store halfword
            rd = (op >> 8) & 7; rn = (op >> 6) & 7; imm = op & 0x1F;
            val = cpu->r[rn] + (imm << 1);
            if (op & 0x0800) cpu->r[rd] = gba_read_u32(mem, val) & 0xFFFF;
            else gba_write_u32(mem, val, cpu->r[rd]);
            break;

        case 0xA: // Load/Store byte
            rd = (op >> 8) & 7; rn = (op >> 6) & 7; imm = op & 0xFF;
            val = cpu->r[rn] + imm;
            if (op & 0x0800) cpu->r[rd] = (uint32_t)(int8_t)gba_read_u32(mem, val);
            else gba_write_u32(mem, val, cpu->r[rd]);
            break;

        case 0xB: // Sign-extend / Add to SP
            if (op & 0x0800) {
                rn = (op >> 8) & 7; imm = op & 0xFF;
                cpu->r[rn] = (int32_t)(int16_t)imm;
            } else {
                rd = (op >> 8) & 7; imm = op & 0x7F;
                cpu->r[rd] = cpu->r[13] + (imm << 2);
            }
            break;

        case 0xC: // Multiple load/store
            rn = (op >> 8) & 7; imm = op & 0xFF;
            for (int i = 0; i < 8; i++) {
                if (imm & (1 << i)) {
                    if (op & 0x0800) cpu->r[i] = gba_read_u32(mem, cpu->r[rn]);
                    else gba_write_u32(mem, cpu->r[rn], cpu->r[i]);
                    cpu->r[rn] += 4;
                }
            }
            break;

        case 0xD: // Conditional branch
            rn = (op >> 8) & 0xF; imm = (op & 0xFF) << 1;
            bool cond = false;
            switch (rn) {
                case 0: cond = (cpu->cpsr & 0x80000000) != 0; break;
                case 1: cond = (cpu->cpsr & 0x80000000) == 0; break;
                case 2: cond = (cpu->cpsr & 0x40000000) != 0; break;
                case 3: cond = (cpu->cpsr & 0x40000000) == 0; break;
                case 4: cond = (cpu->cpsr & 0x80000000) != 0; break;
                case 5: cond = (cpu->cpsr & 0x80000000) == 0; break;
                case 6: cond = (cpu->cpsr & 0x08000000) != 0; break;                case 7: cond = (cpu->cpsr & 0x08000000) == 0; break;
                case 8: cond = true; break;
                default: cond = false;
            }
            if (cond) cpu->r[15] = (cpu->r[15] & ~1) + ((int16_t)imm);
            break;

        case 0xE: // Branch & Exchange / Long branch
            if (op & 0x0800) { // BLX / BL
                imm = ((op & 0x07FF) << 12) | ((read_u16(mem, pc+2) & 0x07FF) << 1);
                imm = (int32_t)(imm << 11) >> 11;
                cpu->r[14] = cpu->r[15] | 1;
                cpu->r[15] += imm;
            } else { // BX
                rn = (op >> 3) & 0xF;
                cpu->r[15] = cpu->r[rn];
                cpu->thumb = (cpu->r[15] & 1) != 0;
                cpu->r[15] &= ~1;
            }
            break;

        case 0xF: // Long branch with link
            imm = (op & 0x07FF) << 12;
            cpu->r[14] = cpu->r[15] + (imm >> 11);
            break;
    }
}
