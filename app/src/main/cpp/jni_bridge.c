#include <jni.h>
#include <android/log.h>
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
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_moonlight_moongba_EmuCore_nativeStepFrame(JNIEnv* env, jobject thiz) {
    for (int i = 0; i < 16000; i++) gba_cpu_step(&sys.cpu, &sys.mem);

    for (int y = 0; y < GBA_H; y++) {
        for (int x = 0; x < GBA_W; x++) {
            uint32_t col = ((x ^ y) & 0x10) ? 0xFF0000FF : 0xFFFF0000;
            sys.ppu.buffer[y * GBA_W + x] = col;
        }
    }
    memcpy(frame_buf, sys.ppu.buffer, FRAME_SIZE);

    jbyteArray arr = (*env)->NewByteArray(env, FRAME_SIZE);
    (*env)->SetByteArrayRegion(env, arr, 0, FRAME_SIZE, (jbyte*)frame_buf);
    return arr;
}
