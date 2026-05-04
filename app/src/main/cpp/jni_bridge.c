#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdio.h>
#include "gba.h"

#define LOG_TAG "MoonGBA"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static GbaSystem sys;
static uint8_t frame_buf[FRAME_SIZE];
static volatile int paused = 0;
static volatile uint32_t key_states = 0;

// ─── Init ────────────────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_moonlight_moongba_EmuCore_nativeInit(JNIEnv* env, jobject thiz) {
    gba_cpu_reset(&sys.cpu);
    memset(&sys.mem, 0, sizeof(sys.mem));
    memset(&sys.ppu, 0, sizeof(sys.ppu));
    paused = 0;
    key_states = 0;
    LOGI("Core initialized");
    return JNI_TRUE;
}

// ─── ROM Loading ─────────────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_moonlight_moongba_EmuCore_nativeLoadRom(JNIEnv* env, jobject thiz, jbyteArray rom) {
    jsize len = (*env)->GetArrayLength(env, rom);
    jbyte* bytes = (*env)->GetByteArrayElements(env, rom, NULL);
    bool ok = gba_load_rom(&sys.mem, (const uint8_t*)bytes, (uint32_t)len);
    (*env)->ReleaseByteArrayElements(env, rom, bytes, JNI_ABORT);
    if (ok) {
        gba_cpu_reset(&sys.cpu);
        LOGI("ROM loaded: %d bytes", len);
    } else {
        LOGE("ROM load failed");
    }
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ─── Frame Step ──────────────────────────────────────────────────────────────

JNIEXPORT jbyteArray JNICALL
Java_com_moonlight_moongba_EmuCore_nativeStepFrame(JNIEnv* env, jobject thiz) {
    if (!paused) {
        // GBA runs at ~16.78 MHz, 60fps = ~280896 cycles per frame
        for (int i = 0; i < 280896; i++) {
            gba_cpu_step(&sys.cpu, &sys.mem);
        }
    }

    // PPU: render VRAM to framebuffer (Mode 3 — direct bitmap)
    // Mode 3: 240x160, 16-bit RGB555 stored in VRAM at 0x06000000
    for (int y = 0; y < GBA_H; y++) {
        for (int x = 0; x < GBA_W; x++) {
            int idx = (y * GBA_W + x) * 2;
            uint16_t color555 = sys.mem.vram[idx] | (sys.mem.vram[idx + 1] << 8);

            // Convert RGB555 to RGBA8888
            uint8_t r = ((color555 >>  0) & 0x1F) << 3;
            uint8_t g = ((color555 >>  5) & 0x1F) << 3;
            uint8_t b = ((color555 >> 10) & 0x1F) << 3;

            int out = (y * GBA_W + x) * 4;
            frame_buf[out + 0] = r;
            frame_buf[out + 1] = g;
            frame_buf[out + 2] = b;
            frame_buf[out + 3] = 0xFF; // alpha
        }
    }

    jbyteArray arr = (*env)->NewByteArray(env, FRAME_SIZE);
    (*env)->SetByteArrayRegion(env, arr, 0, FRAME_SIZE, (jbyte*)frame_buf);
    return arr;
}

// ─── Input ───────────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_moonlight_moongba_EmuCore_nativeSetKeyStates(JNIEnv* env, jobject thiz, jint states) {
    key_states = (uint32_t)states;
    // GBA key register is at I/O address 0x04000130 (KEYINPUT)
    // GBA uses active-low logic: 0 = pressed, 1 = released
    // We invert the bitmask and write to IO register
    uint16_t keyinput = (uint16_t)(~states & 0x03FF);
    sys.mem.io[0x130] = keyinput & 0xFF;
    sys.mem.io[0x131] = (keyinput >> 8) & 0xFF;
}

// ─── Game Control ────────────────────────────────────────────────────────────

JNIEXPORT void JNICALL
Java_com_moonlight_moongba_EmuCore_nativeReset(JNIEnv* env, jobject thiz) {
    gba_cpu_reset(&sys.cpu);
    memset(&sys.ppu, 0, sizeof(sys.ppu));
    key_states = 0;
    LOGI("CPU reset");
}

JNIEXPORT void JNICALL
Java_com_moonlight_moongba_EmuCore_nativePause(JNIEnv* env, jobject thiz) {
    paused = 1;
    LOGI("Emulation paused");
}

JNIEXPORT void JNICALL
Java_com_moonlight_moongba_EmuCore_nativeResume(JNIEnv* env, jobject thiz) {
    paused = 0;
    LOGI("Emulation resumed");
}

// ─── Save / Load State ───────────────────────────────────────────────────────

JNIEXPORT jboolean JNICALL
Java_com_moonlight_moongba_EmuCore_nativeSaveState(JNIEnv* env, jobject thiz, jstring path) {
    const char* fpath = (*env)->GetStringUTFChars(env, path, NULL);
    FILE* f = fopen(fpath, "wb");
    (*env)->ReleaseStringUTFChars(env, path, fpath);
    if (!f) {
        LOGE("Save state: cannot open file");
        return JNI_FALSE;
    }
    fwrite(&sys.cpu, sizeof(sys.cpu), 1, f);
    fwrite(&sys.mem.ewram, sizeof(sys.mem.ewram), 1, f);
    fwrite(&sys.mem.iwram, sizeof(sys.mem.iwram), 1, f);
    fwrite(&sys.mem.io,    sizeof(sys.mem.io),    1, f);
    fwrite(&sys.mem.vram,  sizeof(sys.mem.vram),  1, f);
    fwrite(&sys.mem.oam,   sizeof(sys.mem.oam),   1, f);
    fwrite(&sys.mem.palette, sizeof(sys.mem.palette), 1, f);
    fclose(f);
    LOGI("State saved");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_moonlight_moongba_EmuCore_nativeLoadState(JNIEnv* env, jobject thiz, jstring path) {
    const char* fpath = (*env)->GetStringUTFChars(env, path, NULL);
    FILE* f = fopen(fpath, "rb");
    (*env)->ReleaseStringUTFChars(env, path, fpath);
    if (!f) {
        LOGE("Load state: file not found");
        return JNI_FALSE;
    }
    fread(&sys.cpu, sizeof(sys.cpu), 1, f);
    fread(&sys.mem.ewram, sizeof(sys.mem.ewram), 1, f);
    fread(&sys.mem.iwram, sizeof(sys.mem.iwram), 1, f);
    fread(&sys.mem.io,    sizeof(sys.mem.io),    1, f);
    fread(&sys.mem.vram,  sizeof(sys.mem.vram),  1, f);
    fread(&sys.mem.oam,   sizeof(sys.mem.oam),   1, f);
    fread(&sys.mem.palette, sizeof(sys.mem.palette), 1, f);
    fclose(f);
    LOGI("State loaded");
    return JNI_TRUE;
}
