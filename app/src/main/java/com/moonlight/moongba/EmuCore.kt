package com.moonlight.moongba

object EmuCore {
    init {
        System.loadLibrary("gba_core")
    }

    @JvmStatic external fun nativeInit(): Boolean
    @JvmStatic external fun nativeLoadRom(rom: ByteArray): Boolean
    @JvmStatic external fun nativeStepFrame(): ByteArray

    // Input — bitmask of pressed buttons
    @JvmStatic external fun nativeSetKeyStates(states: Int)

    // Game control
    @JvmStatic external fun nativeReset()
    @JvmStatic external fun nativePause()
    @JvmStatic external fun nativeResume()

    // Save / Load state
    @JvmStatic external fun nativeSaveState(path: String): Boolean
    @JvmStatic external fun nativeLoadState(path: String): Boolean

    // GBA button bitmasks (from Gameboid Keycodes.java)
    const val KEY_A      = 0x0001
    const val KEY_B      = 0x0002
    const val KEY_SELECT = 0x0004
    const val KEY_START  = 0x0008
    const val KEY_RIGHT  = 0x0010
    const val KEY_LEFT   = 0x0020
    const val KEY_UP     = 0x0040
    const val KEY_DOWN   = 0x0080
    const val KEY_R      = 0x0100
    const val KEY_L      = 0x0200
}
