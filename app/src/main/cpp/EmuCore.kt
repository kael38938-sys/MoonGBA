package com.moonlight.moongba

object EmuCore {
    init { System.loadLibrary("gba_core") }

    @JvmStatic external fun nativeInit(): Boolean
    @JvmStatic external fun nativeLoadRom(rom: ByteArray): Boolean
    @JvmStatic external fun nativeStepFrame(): ByteArray
}
