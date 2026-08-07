package com.shinji.cocoa

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.shinji.cocoa.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var localTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(CocoaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)

        initLocalTts()
        setupStatusSection()
        setupTtsControls()
        setupTestBench()
    }

    private fun initLocalTts() {
        localTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTts?.setLanguage(Locale.JAPANESE)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusDisplay()
    }

    private fun setupStatusSection() {
        binding.btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "インストール済みサービスから「cocoa スクリーンリーダー」を有効化してください", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateServiceStatusDisplay() {
        val isEnabled = isAccessibilityServiceEnabled(this, CocoaScreenReaderService::class.java)
        if (isEnabled) {
            binding.tvStatus.text = getString(R.string.status_enabled)
            binding.tvStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvStatus.text = getString(R.string.status_disabled)
            binding.tvStatus.setTextColor(getColor(R.color.status_red))
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun setupTtsControls() {
        val currentRate = prefs.getFloat(CocoaScreenReaderService.KEY_SPEECH_RATE, 1.0f)
        val currentPitch = prefs.getFloat(CocoaScreenReaderService.KEY_SPEECH_PITCH, 1.0f)

        binding.sliderSpeed.value = currentRate
        binding.sliderPitch.value = currentPitch
        binding.tvSpeedLabel.text = "読み上げ速度: ${String.format("%.1f", currentRate)}x"
        binding.tvPitchLabel.text = "音声ピッチ: ${String.format("%.1f", currentPitch)}x"

        binding.sliderSpeed.addOnChangeListener { _, value, _ ->
            binding.tvSpeedLabel.text = "読み上げ速度: ${String.format("%.1f", value)}x"
            prefs.edit().putFloat(CocoaScreenReaderService.KEY_SPEECH_RATE, value).apply()
            CocoaScreenReaderService.instance?.updateTtsSettings()
            localTts?.setSpeechRate(value)
        }

        binding.sliderPitch.addOnChangeListener { _, value, _ ->
            binding.tvPitchLabel.text = "音声ピッチ: ${String.format("%.1f", value)}x"
            prefs.edit().putFloat(CocoaScreenReaderService.KEY_SPEECH_PITCH, value).apply()
            CocoaScreenReaderService.instance?.updateTtsSettings()
            localTts?.setPitch(value)
        }

        binding.btnTestSpeech.setOnClickListener {
            val sampleText = "cocoa スクリーンリーダーの音声テストです。速度 ${String.format("%.1f", binding.sliderSpeed.value)} 倍速で再生中。"
            if (CocoaScreenReaderService.isServiceRunning()) {
                CocoaScreenReaderService.instance?.speak(sampleText, TextToSpeech.QUEUE_FLUSH)
            } else {
                localTts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "testUtterance")
            }
        }
    }

    private fun setupTestBench() {
        binding.btnTestA.setOnClickListener {
            Toast.makeText(this, "テストボタンAが押されました", Toast.LENGTH_SHORT).show()
        }

        binding.btnTestB.setOnClickListener {
            Toast.makeText(this, "テストボタンBが押されました", Toast.LENGTH_SHORT).show()
        }

        binding.switchDetailMode.setOnCheckedChangeListener { _, isChecked ->
            val modeStr = if (isChecked) "詳細アナウンスモード" else "標準モード"
            Toast.makeText(this, "$modeStr に切り替えました", Toast.LENGTH_SHORT).show()
        }

        binding.btnTriggerMenu.setOnClickListener {
            if (CocoaScreenReaderService.isServiceRunning()) {
                CocoaScreenReaderService.instance?.triggerCocoaMenu()
            } else {
                Toast.makeText(this, "先にサービスを有効化してください", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        localTts?.shutdown()
        localTts = null
    }
}
