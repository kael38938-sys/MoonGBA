package com.moonlight.moongba

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    // ── Views ────────────────────────────────────────────────────────────────

    private lateinit var emulatorView: EmulatorView
    private lateinit var tvRomName: TextView
    private lateinit var btnLoadRom: Button
    private lateinit var btnStartStop: Button
    private lateinit var btnMenu: Button
    private lateinit var virtualKeypad: View

    private lateinit var btnUp: Button
    private lateinit var btnDown: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnA: Button
    private lateinit var btnB: Button
    private lateinit var btnL: Button
    private lateinit var btnR: Button
    private lateinit var btnSelect: Button
    private lateinit var btnStart: Button

    // ── State ────────────────────────────────────────────────────────────────

    private var currentRomUri: Uri? = null
    private var currentRomName: String = ""
    private var keyStates: Int = 0
    private var isRunning: Boolean = false
    private var isPaused: Boolean = false

    // FPS counter
    private var frameCount: Int = 0
    private var lastFpsTime: Long = 0L
    private val fpsHandler = Handler(Looper.getMainLooper())
    private val fpsRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val elapsed = now - lastFpsTime
            if (elapsed >= 1000 && currentRomName.isNotEmpty()) {
                val fps = frameCount * 1000f / elapsed
                tvRomName.text = "$currentRomName  |  ${fps.toInt()} FPS"
                frameCount = 0
                lastFpsTime = now
            }
            fpsHandler.postDelayed(this, 500)
        }
    }

    // ── ROM Picker ───────────────────────────────────────────────────────────

    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadRom(it) }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setImmersiveMode()
        setContentView(R.layout.activity_main)
        bindViews()
        initCore()
        setupButtons()
        setupVirtualKeypad()
        emulatorView.setFrameCallback { frameCount++ }
    }

    override fun onPause() {
        super.onPause()
        if (isRunning && currentRomUri != null) autoSaveState()
        pauseEmulation()
        fpsHandler.removeCallbacks(fpsRunnable)
    }

    override fun onResume() {
        super.onResume()
        setImmersiveMode()
        if (isRunning && isPaused) {
            resumeEmulation()
            lastFpsTime = System.currentTimeMillis()
            fpsHandler.post(fpsRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fpsHandler.removeCallbacks(fpsRunnable)
        emulatorView.stopEmulation()
    }

    override fun onBackPressed() {
        if (currentRomUri != null) showExitDialog()
        else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("romName", currentRomName)
    }

    override fun onRestoreInstanceState(saved: Bundle) {
        super.onRestoreInstanceState(saved)
        currentRomName = saved.getString("romName", "")
        if (currentRomName.isNotEmpty()) tvRomName.text = currentRomName
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
    }

    private fun bindViews() {
        emulatorView  = findViewById(R.id.emulatorView)
        tvRomName     = findViewById(R.id.tvRomName)
        btnLoadRom    = findViewById(R.id.btnLoadRom)
        btnStartStop  = findViewById(R.id.btnStartStop)
        btnMenu       = findViewById(R.id.btnMenu)
        virtualKeypad = findViewById(R.id.virtualKeypad)
        btnUp         = findViewById(R.id.btnUp)
        btnDown       = findViewById(R.id.btnDown)
        btnLeft       = findViewById(R.id.btnLeft)
        btnRight      = findViewById(R.id.btnRight)
        btnA          = findViewById(R.id.btnA)
        btnB          = findViewById(R.id.btnB)
        btnL          = findViewById(R.id.btnL)
        btnR          = findViewById(R.id.btnR)
        btnSelect     = findViewById(R.id.btnSelect)
        btnStart      = findViewById(R.id.btnStart)
    }

    private fun initCore() {
        if (!EmuCore.nativeInit()) {
            Toast.makeText(this, "Core init failed!", Toast.LENGTH_LONG).show()
        }
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        btnLoadRom.setOnClickListener {
            if (isRunning) pauseEmulation()
            romPickerLauncher.launch("*/*")
        }

        btnStartStop.setOnClickListener {
            when {
                currentRomUri == null -> {
                    Toast.makeText(this, "Load a ROM first!", Toast.LENGTH_SHORT).show()
                }
                isRunning && !isPaused -> {
                    pauseEmulation()
                    btnStartStop.text = "Resume"
                }
                else -> {
                    resumeEmulation()
                    btnStartStop.text = "Pause"
                }
            }
        }

        btnMenu.setOnClickListener { showGameMenu() }
    }

    // ── Virtual Keypad ────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupVirtualKeypad() {
        setupKey(btnUp,     EmuCore.KEY_UP)
        setupKey(btnDown,   EmuCore.KEY_DOWN)
        setupKey(btnLeft,   EmuCore.KEY_LEFT)
        setupKey(btnRight,  EmuCore.KEY_RIGHT)
        setupKey(btnA,      EmuCore.KEY_A)
        setupKey(btnB,      EmuCore.KEY_B)
        setupKey(btnL,      EmuCore.KEY_L)
        setupKey(btnR,      EmuCore.KEY_R)
        setupKey(btnSelect, EmuCore.KEY_SELECT)
        setupKey(btnStart,  EmuCore.KEY_START)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKey(button: Button, keyMask: Int) {
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN -> {
                    keyStates = keyStates or keyMask
                    EmuCore.nativeSetKeyStates(keyStates)
                    v.isPressed = true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_POINTER_UP,
                MotionEvent.ACTION_CANCEL -> {
                    keyStates = keyStates and keyMask.inv()
                    EmuCore.nativeSetKeyStates(keyStates)
                    v.isPressed = false
                }
            }
            true
        }
    }

    // ── ROM Loading ───────────────────────────────────────────────────────────

    private fun loadRom(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null || bytes.isEmpty()) {
                Toast.makeText(this, "Cannot read ROM file", Toast.LENGTH_SHORT).show()
                return
            }
            if (bytes.size < 0xC0) {
                Toast.makeText(this, "File too small — not a valid GBA ROM", Toast.LENGTH_SHORT).show()
                return
            }

            // Stop current game
            if (isRunning) {
                emulatorView.stopEmulation()
                isRunning = false
                isPaused  = false
            }

            if (!EmuCore.nativeLoadRom(bytes)) {
                Toast.makeText(this, "Failed to load ROM", Toast.LENGTH_SHORT).show()
                return
            }

            currentRomUri  = uri
            currentRomName = getRomName(uri)
            tvRomName.text = currentRomName

            // Auto-restore previous session
            val autoState = getStateFile(0)
            if (autoState.exists()) {
                if (EmuCore.nativeLoadState(autoState.absolutePath)) {
                    Toast.makeText(this, "Previous session restored", Toast.LENGTH_SHORT).show()
                }
            }

            startEmulation()
            btnStartStop.text = "Pause"
            lastFpsTime = System.currentTimeMillis()
            fpsHandler.post(fpsRunnable)

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Emulation Control ─────────────────────────────────────────────────────

    private fun startEmulation() {
        EmuCore.nativeResume()
        emulatorView.startEmulation()
        isRunning = true
        isPaused  = false
    }

    private fun pauseEmulation() {
        if (!isRunning) return
        EmuCore.nativePause()
        emulatorView.pauseEmulation()
        isPaused = true
    }

    private fun resumeEmulation() {
        if (!isRunning) return
        EmuCore.nativeResume()
        emulatorView.resumeEmulation()
        isPaused = false
    }

    private fun resetGame() {
        EmuCore.nativeReset()
        keyStates = 0
        EmuCore.nativeSetKeyStates(0)
        if (isPaused) resumeEmulation()
        Toast.makeText(this, "Game reset", Toast.LENGTH_SHORT).show()
    }

    // ── Save / Load State ─────────────────────────────────────────────────────

    private fun getStateFile(slot: Int): File {
        val safeName = currentRomName
            .substringBeforeLast(".")
            .replace(" ", "_")
            .replace("[^a-zA-Z0-9_-]".toRegex(), "")
        return File(filesDir, "$safeName.ss$slot")
    }

    private fun autoSaveState() {
        if (currentRomName.isEmpty()) return
        EmuCore.nativeSaveState(getStateFile(0).absolutePath)
    }

    private fun showSaveStateDialog() {
        val slots = Array(5) { i ->
            val f = getStateFile(i + 1)
            if (f.exists()) "Slot ${i + 1}  ✓  ${getFileDate(f)}" else "Slot ${i + 1}  —  empty"
        }
        AlertDialog.Builder(this)
            .setTitle("Save State")
            .setItems(slots) { _, which ->
                val file = getStateFile(which + 1)
                if (EmuCore.nativeSaveState(file.absolutePath)) {
                    Toast.makeText(this, "Saved to Slot ${which + 1}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoadStateDialog() {
        val slots = Array(5) { i ->
            val f = getStateFile(i + 1)
            if (f.exists()) "Slot ${i + 1}  ✓  ${getFileDate(f)}" else "Slot ${i + 1}  —  empty"
        }
        AlertDialog.Builder(this)
            .setTitle("Load State")
            .setItems(slots) { _, which ->
                val file = getStateFile(which + 1)
                if (!file.exists()) {
                    Toast.makeText(this, "Slot ${which + 1} is empty", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                if (EmuCore.nativeLoadState(file.absolutePath)) {
                    Toast.makeText(this, "Loaded Slot ${which + 1}", Toast.LENGTH_SHORT).show()
                    if (isPaused) resumeEmulation()
                } else {
                    Toast.makeText(this, "Load failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Game Menu ─────────────────────────────────────────────────────────────

    private fun showGameMenu() {
        val wasPaused = isPaused
        if (isRunning && !isPaused) pauseEmulation()

        val options = buildList {
            if (currentRomUri != null) {
                add("Save State")
                add("Load State")
                add("Reset Game")
                add("Screenshot")
            }
            add("Load ROM")
            add("Toggle Keypad")
            add("Cancel")
        }

        AlertDialog.Builder(this)
            .setTitle("MoonGBA Menu")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Save State"    -> showSaveStateDialog()
                    "Load State"    -> showLoadStateDialog()
                    "Reset Game"    -> confirmReset()
                    "Screenshot"    -> takeScreenshot()
                    "Load ROM"      -> romPickerLauncher.launch("*/*")
                    "Toggle Keypad" -> toggleKeypad()
                    "Cancel"        -> if (!wasPaused) resumeEmulation()
                }
            }
            .setOnCancelListener { if (!wasPaused) resumeEmulation() }
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset Game")
            .setMessage("Reset \"$currentRomName\"?\nUnsaved progress will be lost.")
            .setPositiveButton("Reset") { _, _ -> resetGame() }
            .setNegativeButton("Cancel") { _, _ -> if (isPaused) resumeEmulation() }
            .show()
    }

    private fun showExitDialog() {
        val wasPaused = isPaused
        if (isRunning && !isPaused) pauseEmulation()

        val options = arrayOf(
            "Resume Game",
            "Load New ROM",
            "Save & Quit",
            "Quit Without Saving"
        )

        AlertDialog.Builder(this)
            .setTitle("Quit \"$currentRomName\"?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> if (!wasPaused) resumeEmulation()
                    1 -> romPickerLauncher.launch("*/*")
                    2 -> { autoSaveState(); finish() }
                    3 -> finish()
                }
            }
            .setOnCancelListener { if (!wasPaused) resumeEmulation() }
            .show()
    }

    // ── Extras ────────────────────────────────────────────────────────────────

    private fun toggleKeypad() {
        virtualKeypad.visibility =
            if (virtualKeypad.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        if (isPaused) resumeEmulation()
    }

    private fun takeScreenshot() {
        try {
            val bitmap = emulatorView.getLastFrame()
            if (bitmap == null) {
                Toast.makeText(this, "No frame available", Toast.LENGTH_SHORT).show()
                return
            }
            val name = currentRomName.substringBeforeLast(".")
                .replace(" ", "_")
                .replace("[^a-zA-Z0-9_]".toRegex(), "")
            val file = File(
                getExternalFilesDir(null),
                "${name}_${System.currentTimeMillis()}.png"
            )
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            Toast.makeText(this, "Screenshot: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Screenshot failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        if (isPaused) resumeEmulation()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getRomName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "unknown.gba"
    }

    private fun getFileDate(file: File): String {
        val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(file.lastModified()))
    }
}
