#include <jni.h>
#include <android/log.h>
#include <string.h>      // ADD THIS - needed for memset/memcpy
#include "gba.h"

static GbaSystem sys;
static uint8_t frame_buf[FRAME_SIZE];

JNIEXPORT jboolean JNICALL
Java_com_moonlight_moongba_EmuCore_nativeInit(JNIEnv* env, jobject thiz) {
    gba_cpu_reset(&sys.cpu);
    memset(&sys.mem, 0, sizeof(sys.mem));
    memset(&sys.ppu, 0, sizeof(sys.ppu));
    __android_log_print(ANDROID_LOG_INFO, "GBA-JNI", "Core ready");
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_moonlight_moongba_EmuCore_nativeLoadRom(JNIEnv* env, jobject thiz, jbyteArray rom) {
    jsize len = (*env)->GetArrayLength(env, rom);
    jbyte* bytes = (*env)->GetByteArrayElements(env, rom, NULL);
    bool ok = gba_load_rom(&sys.mem, (const uint8_t*)bytes, len);
    (*env)->ReleaseByteArrayElements(env, rom, bytes, JNI_ABORT);
    __android_log_print(ANDROID_LOG_INFO, "GBA-JNI", "ROM loaded: %d bytes, ok=%d", len, ok);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_moonlight_moongba_EmuCore_nativeStepFrame(JNIEnv* env, jobject thiz) {
    // Run CPU for one frame (simplified)
    for (int i = 0; i < 16000; i++) {
        gba_cpu_step(&sys.cpu, &sys.mem);
    }

    // Test pattern: checkerboard with CORRECT byte order for little-endian
    // We want Kotlin to read: [B, G, R, A] from memory
    // So uint32_t value = (A<<24)|(R<<16)|(G<<8)|B
    for (int y = 0; y < GBA_H; y++) {
        for (int x = 0; x < GBA_W; x++) {
            int block_x = x / 20;
            int block_y = y / 20;
            
            uint32_t color;
            if ((block_x + block_y) % 2 == 0) {
                // Bright RED: A=255, R=255, G=0, B=0
                color = (0xFFu << 24) | (0xFFu << 16) | (0x00u << 8) | 0x00u;
            } else {
                // Bright BLUE: A=255, R=0, G=0, B=255
                color = (0xFFu << 24) | (0x00u << 16) | (0x00u << 8) | 0xFFu;
            }
            sys.ppu.buffer[y * GBA_W + x] = color;
        }
    }

    memcpy(frame_buf, sys.ppu.buffer, FRAME_SIZE);

    jbyteArray arr = (*env)->NewByteArray(env, FRAME_SIZE);
    (*env)->SetByteArrayRegion(env, arr, 0, FRAME_SIZE, (jbyte*)frame_buf);
    return arr;
}
