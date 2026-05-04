package com.moonlight.moongba

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.moonlight.moongba.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var emulatorView: EmulatorView? = null
    private var currentRomPath: Uri? = null

    // No permission launcher needed anymore!
    
    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadRom(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EmuCore.nativeInit()

        emulatorView = binding.emulatorView
        emulatorView?.setEmulatorCore(object : EmulatorView.EmulatorCoreInterface {
            override fun stepFrame(): ByteArray {
                return EmuCore.nativeStepFrame()
            }
        })

        // Direct click to open picker - No permission check!
        binding.btnLoadRom.setOnClickListener {
            romPickerLauncher.launch("*/*")
        }

        binding.btnStart.setOnClickListener {
            if (emulatorView?.isRunning == true) {
                emulatorView?.stop()
                binding.btnStart.text = "Start"
            } else {
                if (currentRomPath != null) {
                    emulatorView?.start()
                    binding.btnStart.text = "Stop"
                } else {
                    Toast.makeText(this, "Load a ROM first", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadRom(uri: Uri) {
        try {
            val romBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (romBytes != null) {
                if (EmuCore.nativeLoadRom(romBytes)) {
                    currentRomPath = uri
                    val fileName = getFileName(uri)
                    binding.tvRomName.text = "Loaded: $fileName"
                    Toast.makeText(this, "ROM loaded successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to load ROM", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "unknown.gba"
    }

    override fun onPause() {
        super.onPause()
        emulatorView?.pause()
    }

    override fun onResume() {
        super.onResume()
        emulatorView?.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        emulatorView?.stop()
    }
}
