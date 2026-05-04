package com.moonlight.moongba

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
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

    // D-Pad
    private lateinit var btnUp: Button
    private lateinit var btnDown: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button

    // Face buttons
    private lateinit var btnA: Button
    private lateinit var btnB: Button

    // Shoulder + system
    private lateinit var btnL: Button
    private lateinit var btnR: Button
    private lateinit var btnSelect: Button
    private lateinit var btnStart: Button

    // ── State ────────────────────────────────────────────────────────────────

    private var currentRomUri: Uri? = null
    private var currentRomName: String = ""
    private var keyStates: Int = 0

    // ── ROM Picker ───────────────────────────────────────────────────────────

    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadRom(it) }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        EmuCore.nativeInit()
        setupEmulatorCore()
        setupButtons()
        setupVirtualKeypad()
    }

    override fun onPause() {
        super.onPause()
        // Auto-save on pause like Gameboid
        if (currentRomUri != null && emulatorView.isRunning()) {
            autoSaveState()
        }
        EmuCore.nativePause()
        emulatorView.pause()
    }

    override fun onResume() {
        super.onResume()
        EmuCore.nativeResume()
        emulatorView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        emulatorView.stop()
    }

    override fun onBackPressed() {
        if (currentRomUri != null) {
            showExitDialog()
        } else {
            super.onBackPressed()
        }
    }

    // ── Setup ────────────────────────────────────────────────────────────────

    private fun bindViews() {
        emulatorView  = findViewById(R.id.emulatorView)
        tvRomName     = findViewById(R.id.tvRomName)
        btnLoadRom    = findViewById(R.id.btnLoadRom)
        btnStartStop  = findViewById(R.id.btnStartStop)
        btnMenu       = findViewById(R.id.btnMenu)
        virtualKeypad = findViewById(R.id.virtualKeypad)

        btnUp     = findViewById(R.id.btnUp)
        btnDown   = findViewById(R.id.btnDown)
        btnLeft   = findViewById(R.id.btnLeft)
        btnRight  = findViewById(R.id.btnRight)
        btnA      = findViewById(R.id.btnA)
        btnB      = findViewById(R.id.btnB)
        btnL      = findViewById(R.id.btnL)
        btnR      = findViewById(R.id.btnR)
        btnSelect = findViewById(R.id.btnSelect)
        btnStart  = findViewById(R.id.btnStart)
    }

    private fun setupEmulatorCore() {
        emulatorView.setEmulatorCore(object : EmulatorView.EmulatorCoreInterface {
            override fun stepFrame(): ByteArray = EmuCore.nativeStepFrame()
        })
    }

    private fun setupButtons() {
        btnLoadRom.setOnClickListener {
            romPickerLauncher.launch("*/*")
        }

        btnStartStop.setOnClickListener {
            if (emulatorView.isRunning()) {
                emulatorView.stop()
                EmuCore.nativePause()
                btnStartStop.text = "Start"
            } else {
                if (currentRomUri == null) {
                    Toast.makeText(this, "Load a ROM first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                EmuCore.nativeResume()
                emulatorView.start()
                btnStartStop.text = "Stop"
            }
        }

        btnMenu.setOnClickListener {
            showGameMenu()
        }
    }

    // ── Virtual Keypad ───────────────────────────────────────────────────────

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
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    keyStates = keyStates or keyMask
                    EmuCore.nativeSetKeyStates(keyStates)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    keyStates = keyStates and keyMask.inv()
                    EmuCore.nativeSetKeyStates(keyStates)
                }
            }
            true
        }
    }

    // ── ROM Loading ──────────────────────────────────────────────────────────

    private fun loadRom(uri: Uri) {
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                Toast.makeText(this, "Cannot read ROM file", Toast.LENGTH_SHORT).show()
                return
            }
            if (!EmuCore.nativeLoadRom(bytes)) {
                Toast.makeText(this, "Failed to load ROM", Toast.LENGTH_SHORT).show()
                return
            }

            // Stop any running game first
            if (emulatorView.isRunning()) {
                emulatorView.stop()
            }

            currentRomUri = uri
            currentRomName = getRomName(uri)
            tvRomName.text = "Loaded: $currentRomName"

            // Auto-load last save state if exists
            val stateFile = getStateFile(0)
            if (stateFile.exists()) {
                EmuCore.nativeLoadState(stateFile.absolutePath)
            }

            EmuCore.nativeResume()
            emulatorView.start()
            btnStartStop.text = "Stop"

            Toast.makeText(this, "ROM loaded!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Save / Load State ────────────────────────────────────────────────────

    private fun getStateFile(slot: Int): File {
        val name = currentRomName.substringBeforeLast(".")
        return File(filesDir, "$name.ss$slot")
    }

    private fun autoSaveState() {
        val file = getStateFile(0) // slot 0 = auto save
        EmuCore.nativeSaveState(file.absolutePath)
    }

    private fun showSaveStateDialog() {
        val slots = arrayOf("Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5")
        AlertDialog.Builder(this)
            .setTitle("Save State")
            .setItems(slots) { _, which ->
                val file = getStateFile(which + 1)
                if (EmuCore.nativeSaveState(file.absolutePath)) {
                    Toast.makeText(this, "State saved to Slot ${which + 1}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoadStateDialog() {
        val slots = arrayOf("Slot 1", "Slot 2", "Slot 3", "Slot 4", "Slot 5")
        AlertDialog.Builder(this)
            .setTitle("Load State")
            .setItems(slots) { _, which ->
                val file = getStateFile(which + 1)
                if (!file.exists()) {
                    Toast.makeText(this, "No save in Slot ${which + 1}", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                if (EmuCore.nativeLoadState(file.absolutePath)) {
                    Toast.makeText(this, "State loaded from Slot ${which + 1}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Load failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Menus & Dialogs ──────────────────────────────────────────────────────

    private fun showGameMenu() {
        val options = mutableListOf<String>()

        if (currentRomUri != null) {
            options += "Save State"
            options += "Load State"
            options += "Reset Game"
        }
        options += "Load ROM"
        options += "Cancel"

        AlertDialog.Builder(this)
            .setTitle("MoonGBA")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Save State"  -> showSaveStateDialog()
                    "Load State"  -> showLoadStateDialog()
                    "Reset Game"  -> confirmReset()
                    "Load ROM"    -> romPickerLauncher.launch("*/*")
                    "Cancel"      -> { /* dismiss */ }
                }
            }
            .show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Reset Game")
            .setMessage("Reset ${currentRomName}?")
            .setPositiveButton("Reset") { _, _ ->
                EmuCore.nativeReset()
                Toast.makeText(this, "Game reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExitDialog() {
        val options = arrayOf(
            "Resume Game",
            "Load New ROM",
            "Save & Quit",
            "Quit Without Saving"
        )
        AlertDialog.Builder(this)
            .setTitle("Quit Game?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { /* resume — just dismiss */ }
                    1 -> romPickerLauncher.launch("*/*")
                    2 -> {
                        autoSaveState()
                        finish()
                    }
                    3 -> finish()
                }
            }
            .show()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun getRomName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (idx >= 0) cursor.getString(idx) else null
        } ?: uri.lastPathSegment ?: "unknown.gba"
    }
}
