package com.shinji.serena

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.shinji.serena.databinding.ActivityMainBinding
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var localTts: TextToSpeech? = null

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "通知の受信許可を受け取りました", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "通知の受信が許可されませんでした。着信・メッセージ通知が制限されます", Toast.LENGTH_LONG).show()
        }
        updateServiceStatusDisplay()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)

        initLocalTts()
        setupStatusSection()
        setupSecuritySection()
        setupPermissionsSection()
        setupTtsControls()
        setupHourlyChimeSection()
        setupDeveloperCallSection()
        setupTestBench()
        setupTelemetrySection()

        checkPermissionsOnStart()
        checkTelemetryConsentOnStart()
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
            Toast.makeText(this, "インストール済みサービスから「serena スクリーンリーダー」を有効化してください", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupSecuritySection() {
        checkSecurityStatus()
        binding.btnRecheckSecurity.setOnClickListener {
            checkSecurityStatus()
            Toast.makeText(this, "セキュリティ再診断を実施しました", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkSecurityStatus() {
        val securityHelper = DeviceSecurityHelper(this)
        val result = securityHelper.checkDeviceSecurity()

        binding.tvSecurityStatus.text = result.message
        binding.tvSecurityDetail.text = result.detailMessage

        when (result.status) {
            DeviceSecurityHelper.SecurityStatus.SECURE_OFFICIAL -> {
                binding.tvSecurityStatus.setTextColor(getColor(R.color.status_green))
            }
            DeviceSecurityHelper.SecurityStatus.UPDATE_RECOMMENDED -> {
                binding.tvSecurityStatus.setTextColor(getColor(R.color.cocoa_secondary))
            }
            DeviceSecurityHelper.SecurityStatus.MODIFIED_ENVIRONMENT -> {
                binding.tvSecurityStatus.setTextColor(getColor(R.color.status_red))
            }
        }
    }

    private fun setupPermissionsSection() {
        binding.btnRequestOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "このOSバージョンでは重ねて表示権限は不要です", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRequestNotification.setOnClickListener {
            requestNotificationPermission()
        }
    }

    private fun checkPermissionsOnStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Toast.makeText(this, "このOSバージョンでは通知許可は有効です", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateServiceStatusDisplay() {
        val isEnabled = isAccessibilityServiceEnabled(this, SerenaScreenReaderService::class.java)
        if (isEnabled) {
            binding.tvStatus.text = getString(R.string.status_enabled)
            binding.tvStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvStatus.text = getString(R.string.status_disabled)
            binding.tvStatus.setTextColor(getColor(R.color.status_red))
        }

        // Overlay status check
        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (canDrawOverlays) {
            binding.tvOverlayStatus.text = getString(R.string.status_perm_granted)
            binding.tvOverlayStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvOverlayStatus.text = getString(R.string.status_perm_denied)
            binding.tvOverlayStatus.setTextColor(getColor(R.color.status_red))
        }

        // Notification permission status check
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (notificationGranted) {
            binding.tvNotificationStatus.text = getString(R.string.status_perm_granted)
            binding.tvNotificationStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvNotificationStatus.text = getString(R.string.status_perm_denied)
            binding.tvNotificationStatus.setTextColor(getColor(R.color.status_red))
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        if (SerenaScreenReaderService.isServiceRunning()) return true

        val expectedFull = "${context.packageName}/${service.name}"
        val expectedShort = "${context.packageName}/.${service.simpleName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedFull, ignoreCase = true) ||
                componentName.equals(expectedShort, ignoreCase = true) ||
                componentName.contains(service.simpleName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun setupTtsControls() {
        val currentRate = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_RATE, 1.0f)
        val currentPitch = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_PITCH, 1.0f)

        binding.sliderSpeed.value = currentRate
        binding.sliderPitch.value = currentPitch
        binding.tvSpeedLabel.text = "読み上げ速度: ${String.format("%.1f", currentRate)}x"
        binding.tvPitchLabel.text = "音声ピッチ: ${String.format("%.1f", currentPitch)}x"

        binding.sliderSpeed.addOnChangeListener { _, value, _ ->
            binding.tvSpeedLabel.text = "読み上げ速度: ${String.format("%.1f", value)}x"
            prefs.edit().putFloat(SerenaScreenReaderService.KEY_SPEECH_RATE, value).apply()
            SerenaScreenReaderService.instance?.updateTtsSettings()
            localTts?.setSpeechRate(value)
        }

        binding.sliderPitch.addOnChangeListener { _, value, _ ->
            binding.tvPitchLabel.text = "音声ピッチ: ${String.format("%.1f", value)}x"
            prefs.edit().putFloat(SerenaScreenReaderService.KEY_SPEECH_PITCH, value).apply()
            SerenaScreenReaderService.instance?.updateTtsSettings()
            localTts?.setPitch(value)
        }

        binding.btnTestSpeech.setOnClickListener {
            val sampleText = "serena スクリーンリーダーの音声テストです。速度 ${String.format("%.1f", binding.sliderSpeed.value)} 倍速で再生中。"
            if (SerenaScreenReaderService.isServiceRunning()) {
                SerenaScreenReaderService.instance?.speak(sampleText, TextToSpeech.QUEUE_FLUSH)
            } else {
                localTts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "testUtterance")
            }
        }

        binding.btnOpenTtsSettings.setOnClickListener {
            try {
                val intent = Intent("com.android.settings.TTS_SETTINGS")
                startActivity(intent)
                Toast.makeText(this, "「音声データ」からフィリピン語(Filipino/Tagalog)や英語の音声パッケージを追加できます", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun setupHourlyChimeSection() {
        val isEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_HOURLY_CHIME_ENABLED, true)
        binding.switchHourlyChime.isChecked = isEnabled

        binding.switchHourlyChime.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(SerenaScreenReaderService.KEY_HOURLY_CHIME_ENABLED, isChecked).apply()
            val statusStr = if (isChecked) "時報機能を有効にしました" else "時報機能を無効にしました"
            Toast.makeText(this, statusStr, Toast.LENGTH_SHORT).show()
        }

        binding.btnTestHourlyChime.setOnClickListener {
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            if (SerenaScreenReaderService.isServiceRunning()) {
                SerenaScreenReaderService.instance?.triggerHourlyAnnouncement(currentHour)
            } else {
                val isAm = currentHour < 12
                val displayHour = when {
                    currentHour == 0 -> 12
                    currentHour > 12 -> currentHour - 12
                    else -> currentHour
                }
                val periodStr = if (isAm) "午前" else "午後"
                val sampleText = "${periodStr}${displayHour}時をお知らせします。（テスト再生）"
                localTts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "testHourlyChime")
            }
        }
    }

    private fun setupDeveloperCallSection() {
        val phoneNumber = BuildConfig.DEVELOPER_PHONE
        val developerName = BuildConfig.DEVELOPER_NAME
        binding.btnCallDeveloper.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                startActivity(intent)
                Toast.makeText(this, "開発者(${developerName})への電話アプリを起動します", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "電話アプリの起動に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
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
            if (SerenaScreenReaderService.isServiceRunning()) {
                SerenaScreenReaderService.instance?.triggerSerenaMenu()
            } else {
                Toast.makeText(this, "先にサービスを有効化してください", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTelemetrySection() {
        val telemetry = AlphaTelemetryHelper.getInstance(this)

        binding.switchTelemetryOptIn.isChecked = telemetry.isConsentGranted()
        binding.switchTelemetryOptIn.setOnCheckedChangeListener { _, isChecked ->
            telemetry.setConsentStatus(isChecked)
            val msg = if (isChecked) "動作改善レポート送信を許可しました" else "動作改善レポート送信を停止しました"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnSendTelemetry.setOnClickListener {
            if (!telemetry.isConsentGranted()) {
                showTelemetryConsentDialog()
            } else {
                telemetry.sendReportViaEmail(this)
            }
        }

        binding.btnCopyTelemetry.setOnClickListener {
            telemetry.copyReportToClipboard(this)
        }
    }

    private fun checkTelemetryConsentOnStart() {
        val telemetry = AlphaTelemetryHelper.getInstance(this)
        if (!telemetry.isConsentAsked()) {
            showTelemetryConsentDialog()
        }
    }

    private fun showTelemetryConsentDialog() {
        val telemetry = AlphaTelemetryHelper.getInstance(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_telemetry_title)
            .setMessage(R.string.dialog_telemetry_message)
            .setPositiveButton(R.string.dialog_telemetry_agree) { dialog, _ ->
                telemetry.setConsentStatus(true)
                binding.switchTelemetryOptIn.isChecked = true
                Toast.makeText(this, "ご協力ありがとうございます！動作改善レポート送信が許可されました", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_telemetry_refuse) { dialog, _ ->
                telemetry.setConsentStatus(false)
                binding.switchTelemetryOptIn.isChecked = false
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        localTts?.shutdown()
        localTts = null
    }
}


