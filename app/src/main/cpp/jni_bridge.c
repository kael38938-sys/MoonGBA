#include <jni.h>
#include <android/log.h>
#include <string.h>
#include "gba.h"

static GbaSystem sys;

JNIEXPORT jboolean JNICALL
Java_com_moonlight_moongba_EmuCore_nativeInit(JNIEnv* env, jobject thiz) {
    gba_cpu_reset(&sys.cpu);
    memset(&sys.mem, 0, sizeof(sys.mem));
    memset(&sys.ppu, 0, sizeof(sys.ppu));
    __android_log_print(ANDROID_LOG_INFO, "GBA-JNI", "Core initialized");
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

JNIEXPORT jintArray JNICALL
Java_com_moonlight_moongba_EmuCore_nativeStepFrame(JNIEnv* env, jobject thiz) {
    // Run CPU (simplified)
    for (int i = 0; i < 1000; i++) {
        gba_cpu_step(&sys.cpu, &sys.mem);
    }

    // Create 240x160 IntArray (RGB565 format)
    jintArray arr = (*env)->NewIntArray(env, 240 * 160);
    jint pixels[240 * 160];

    // Test pattern: checkerboard
    for (int y = 0; y < 160; y++) {
        for (int x = 0; x < 240; x++) {
            int block_x = x / 40;
            int block_y = y / 40;
            
            uint16_t color565;
            if ((block_x + block_y) % 2 == 0) {
                // Red: R=31, G=0, B=0  →  0xF800
                color565 = 0xF800;
            } else {
                // Blue: R=0, G=0, B=31  →  0x001F
                color565 = 0x001F;
            }
            pixels[y * 240 + x] = (jint)color565;
        }
    }

    (*env)->SetIntArrayRegion(env, arr, 0, 240 * 160, pixels);
    return arr;
}
