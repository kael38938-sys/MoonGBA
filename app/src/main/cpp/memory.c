#include "gba.h"
#include <string.h>
#include <android/log.h>

bool gba_load_rom(GbaMemory* mem, const uint8_t* data, uint32_t size) {
    if (size < 0xC0 || size > sizeof(mem->rom)) return false;
    memcpy(mem->rom, data, size);
    mem->rom_size = size;
    __android_log_print(ANDROID_LOG_INFO, "GBA", "ROM loaded: %u bytes", size);
    return true;
}

uint32_t gba_read_u32(GbaMemory* mem, uint32_t addr) {
    addr &= 0x0FFFFFFF; // GBA mirrors upper bits
    if (addr >= 0x08000000) {
        uint32_t off = addr & 0x1FFFFFF;
        if (off + 3 < mem->rom_size) return gba_read32(&mem->rom[off]);
    }
    if (addr >= 0x07000000) return gba_read32(&mem->oam[addr & 0x3FF]);
    if (addr >= 0x06000000) return gba_read32(&mem->vram[addr & 0x17FFF]);
    if (addr >= 0x05000000) return gba_read32(&mem->palette[addr & 0x3FF]);
    if (addr >= 0x04000000) return gba_read32(&mem->io[addr & 0x3FF]);
    if (addr >= 0x03000000) return gba_read32(&mem->iwram[addr & 0x7FFF]);
    if (addr >= 0x02000000) return gba_read32(&mem->ewram[addr & 0x3FFFF]);
    return 0; // BIOS (stub)
}

void gba_write_u32(GbaMemory* mem, uint32_t addr, uint32_t val) {
    addr &= 0x0FFFFFFF;
    if (addr >= 0x07000000) { gba_write32(&mem->oam[addr & 0x3FF], val); return; }
    if (addr >= 0x06000000) { gba_write32(&mem->vram[addr & 0x17FFF], val); return; }
    if (addr >= 0x05000000) { gba_write32(&mem->palette[addr & 0x3FF], val); return; }
    if (addr >= 0x04000000) { gba_write32(&mem->io[addr & 0x3FF], val); return; }
    if (addr >= 0x03000000) { gba_write32(&mem->iwram[addr & 0x7FFF], val); return; }
    if (addr >= 0x02000000) { gba_write32(&mem->ewram[addr & 0x3FFFF], val); return; }
    // ROM & BIOS are read-only
}
