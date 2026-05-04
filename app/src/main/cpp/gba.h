#pragma once
#include <stdint.h>
#include <stdbool.h>

#define GBA_W 240
#define GBA_H 160
#define FRAME_SIZE (GBA_W * GBA_H * 4) // RGBA32

typedef struct {
    uint32_t r[16];   // R0-R15 (R15 = PC)
    uint32_t cpsr;
    bool   thumb;
    uint32_t cycles;
} GbaCPU;

typedef struct {
    uint8_t rom[16 * 1024 * 1024];
    uint8_t ewram[256 * 1024];
    uint8_t iwram[32 * 1024];
    uint8_t io[2048];
    uint8_t vram[96 * 1024];
    uint8_t oam[1024];
    uint8_t palette[1024];
    uint32_t rom_size;
} GbaMemory;

typedef struct {
    uint32_t buffer[FRAME_SIZE / 4];
} GbaPPU;

typedef struct {
    GbaCPU  cpu;
    GbaMemory mem;
    GbaPPU  ppu;
} GbaSystem;

// Safe unaligned reads (ARM emulators rely on this)
static inline uint32_t gba_read32(const uint8_t* p) {
    return p[0] | (p[1] << 8) | (p[2] << 16) | (p[3] << 24);
}
static inline void gba_write32(uint8_t* p, uint32_t val) {
    p[0] = val; p[1] = val >> 8; p[2] = val >> 16; p[3] = val >> 24;
}
