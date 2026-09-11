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

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, getString(R.string.main_bg_location_granted), Toast.LENGTH_SHORT).show()
        }
        updateServiceStatusDisplay()
    }

    private val requestAllPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val grantedCount = permissions.values.count { it }
        Toast.makeText(this, getString(R.string.main_permissions_updated_fmt, grantedCount, permissions.size), Toast.LENGTH_SHORT).show()
        updateServiceStatusDisplay()

        // 位置情報が許可された場合、Android 10+ で「常に許可」のバックグラウンド権限を案内＆リクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasBg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

            if ((hasFine || hasCoarse) && !hasBg) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_bg_location_dialog_title))
                    .setMessage(getString(R.string.main_bg_location_dialog_msg))
                    .setPositiveButton(getString(R.string.main_bg_location_dialog_positive)) { _, _ ->
                        requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton(getString(R.string.main_bg_location_dialog_negative), null)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error inflating layout: ${e.message}", e)
            return
        }

        prefs = getSafeSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)

        safeRun("initLocalTts") { initLocalTts() }
        safeRun("setupStatusSection") { setupStatusSection() }
        safeRun("setupSecuritySection") { setupSecuritySection() }
        safeRun("setupPermissionsSection") { setupPermissionsSection() }
        safeRun("setupTtsControls") { setupTtsControls() }
        safeRun("setupHourlyChimeSection") { setupHourlyChimeSection() }
        safeRun("setupCallAssistantSection") { setupCallAssistantSection() }
        safeRun("setupOperationGuideSection") { setupOperationGuideSection() }
        safeRun("setupShakeSensitivitySection") { setupShakeSensitivitySection() }
        safeRun("setupDeveloperCallSection") { setupDeveloperCallSection() }
        safeRun("setupTestBench") { setupTestBench() }
        safeRun("setupTelemetrySection") { setupTelemetrySection() }
        safeRun("setupTranslationSection") { setupTranslationSection() }

        safeRun("checkPreviousCrashOnStart") { checkPreviousCrashOnStart() }
        safeRun("checkPermissionsOnStart") { checkPermissionsOnStart() }
        safeRun("checkTelemetryConsentOnStart") { checkTelemetryConsentOnStart() }
        safeRun("handleGemmaDownloadIntent") { handleGemmaDownloadIntent(intent) }
        safeRun("checkForUpdatesOnStart") {
            com.shinji.serena.update.AutoUpdateManager.getInstance(this).checkForUpdate { info ->
                if (info != null && info.isUpdateAvailable) {
                    com.shinji.serena.update.AutoUpdateManager.getInstance(this).showUpdateDialog(this, info)
                }
            }
        }
    }

    private inline fun safeRun(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "safeRun error in $tag: ${e.message}", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGemmaDownloadIntent(intent)
    }

    private fun handleGemmaDownloadIntent(intent: Intent?) {
        if (intent != null && (intent.action == "com.shinji.serena.ACTION_DOWNLOAD_GEMMA" || intent.getBooleanExtra("DOWNLOAD_GEMMA", false))) {
            val success = com.shinji.serena.ai.GeminiNanoEngine.isAvailable(this)
            if (success) {
                Toast.makeText(this, "Gemini Nano on-device AIが利用可能です！", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Gemini Nano AIの準備中または非対応端末です。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var isLocalTtsReady = false

    private fun initLocalTts() {
        try {
            localTts?.shutdown()
            isLocalTtsReady = false
            localTts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val defaultLocale = Locale.getDefault()
                    val langRes = localTts?.setLanguage(defaultLocale)
                    if (langRes == TextToSpeech.LANG_MISSING_DATA || langRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                        localTts?.language = Locale.ENGLISH
                    }
                    val rate = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_RATE, 1.0f).coerceIn(0.5f, 2.0f)
                    val pitch = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_PITCH, 1.0f).coerceIn(0.5f, 2.0f)
                    localTts?.setSpeechRate(rate)
                    localTts?.setPitch(pitch)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val audioAttributes = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                        localTts?.setAudioAttributes(audioAttributes)
                    }
                    isLocalTtsReady = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "initLocalTts error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            localTts?.stop()
            localTts?.shutdown()
            localTts = null
            isLocalTtsReady = false
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            try {
                updateServiceStatusDisplay()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in updateServiceStatusDisplay: ${e.message}")
            }
        }
        safeRun("checkAndResumePendingInstall") {
            com.shinji.serena.update.AutoUpdateManager.getInstance(this).checkAndResumePendingInstall()
        }
    }

    private fun setupStatusSection() {
        binding.tvAppVersion.text = "v${BuildConfig.VERSION_NAME} (Alpha)"
        binding.btnOpenAccessibility.setOnClickListener {
            showAccessibilityDisclosureDialog()
        }
        binding.btnOpenWelcomeGuide.setOnClickListener {
            showWelcomeDialog(isUserTriggered = true)
        }
        binding.btnCheckUpdate.setOnClickListener {
            Toast.makeText(this, getString(R.string.update_checking), Toast.LENGTH_SHORT).show()
            com.shinji.serena.update.AutoUpdateManager.getInstance(this).checkForUpdate { info ->
                if (info != null && info.isUpdateAvailable) {
                    com.shinji.serena.update.AutoUpdateManager.getInstance(this).showUpdateDialog(this, info)
                } else if (info != null) {
                    Toast.makeText(this, getString(R.string.update_already_latest, BuildConfig.VERSION_NAME), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.update_download_failed, "Network error"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAccessibilityDisclosureDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.disclosure_title)
            .setMessage(R.string.disclosure_message)
            .setPositiveButton(R.string.disclosure_agree) { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(R.string.disclosure_cancel, null)
            .show()
    }

    private fun setupSecuritySection() {
        checkSecurityStatus()
        binding.btnRecheckSecurity.setOnClickListener {
            showTermsAndAiEthicsDialog()
        }
    }

    private fun showTermsAndAiEthicsDialog() {
        AlertDialog.Builder(this)
            .setTitle("🛡️ Googleセキュリティ規約 ＆ 責任あるAI倫理ガイドライン")
            .setMessage(
                "【開発者公式署名 ＆ Google Play App Signing】\n" +
                "本アプリの公式署名権限および著作権は開発者 Shinji に単独帰属し、AES-256暗号化により保護されています。第三者による自己署名バイナリの偽装配布は利用規約および国際法により固く禁じられています。\n\n" +
                "【ユーザー主導の権限同意 (User-Driven Consent)】\n" +
                "アクセシビリティ、カメラ、マイク、位置情報のすべての権限は、ユーザー本人の明示的な同意によってのみ有効化されます。\n\n" +
                "【Google 責任あるAI倫理原則の完全準拠 (Responsible AI)】\n" +
                "① 社会的価値と視覚障害者アクセシビリティ支援\n" +
                "② 不公平なバイアスや差別の完全排除\n" +
                "③ 安全性とフェイルセーフ設計の徹底\n" +
                "④ 完全オンデバイス（Zero Cloud Architecture）による100%のプライバシー保護\n\n" +
                "すべてのAI推論（Gemini Nano / ML Kit）は端末内NPUで完結し、外部への画像・音声・テキスト送信は一切行いません。"
            )
            .setPositiveButton("確認・同意する", null)
            .show()
    }

    private fun checkPreviousCrashOnStart() {
        try {
            val crashReport = SerenaApp.getLastCrashReport(this)
            if (crashReport != null) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.main_crash_dialog_title))
                    .setMessage(
                        getString(
                            R.string.main_crash_dialog_msg_fmt,
                            crashReport.take(400) + (if (crashReport.length > 400) getString(R.string.main_crash_dialog_more) else "")
                        )
                    )
                    .setPositiveButton(getString(R.string.main_crash_btn_copy)) { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Serena Crash Report", crashReport)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, getString(R.string.main_crash_toast_copied), Toast.LENGTH_LONG).show()
                        SerenaApp.clearCrashReport(this)
                    }
                    .setNeutralButton(getString(R.string.main_crash_btn_mail)) { _, _ ->
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.DEVELOPER_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.main_crash_mail_subject))
                            putExtra(Intent.EXTRA_TEXT, crashReport)
                        }
                        try {
                            startActivity(Intent.createChooser(intent, getString(R.string.main_crash_mail_chooser)))
                        } catch (_: Exception) {}
                        SerenaApp.clearCrashReport(this)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        SerenaApp.clearCrashReport(this)
                    }
                    .setOnDismissListener {
                        SerenaApp.clearCrashReport(this)
                    }
                    .show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking crash report: ${e.message}")
        }
    }

    private fun checkSecurityStatus() {
        val securityHelper = DeviceSecurityHelper(this)
        val result = securityHelper.checkDeviceSecurity()

        binding.tvSecurityStatus.text = result.message
        binding.tvSecurityDetail.text = result.detailMessage

        when (result.status) {
            DeviceSecurityHelper.SecurityStatus.SECURE_OFFICIAL -> {
                binding.tvSecurityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            }
            DeviceSecurityHelper.SecurityStatus.UPDATE_RECOMMENDED -> {
                binding.tvSecurityStatus.setTextColor(ContextCompat.getColor(this, R.color.serena_secondary))
            }
            DeviceSecurityHelper.SecurityStatus.MODIFIED_ENVIRONMENT -> {
                binding.tvSecurityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
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
                Toast.makeText(this, getString(R.string.main_overlay_not_needed), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRequestNotification.setOnClickListener {
            requestNotificationPermission()
        }
    }

    private fun checkPermissionsOnStart() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissionsToRequest.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val ungranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            requestAllPermissionsLauncher.launch(ungranted.toTypedArray())
        }
    }

    private fun requestNotificationPermission() {
        checkPermissionsOnStart()
    }

    private fun updateServiceStatusDisplay() {
        val isEnabled = isAccessibilityServiceEnabled(this, SerenaScreenReaderService::class.java)
        if (isEnabled) {
            binding.tvStatus.text = getString(R.string.status_enabled)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvStatus.text = getString(R.string.status_disabled)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }

        // Overlay status check
        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (canDrawOverlays) {
            binding.tvOverlayStatus.text = getString(R.string.status_perm_granted)
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvOverlayStatus.text = getString(R.string.status_perm_denied)
            binding.tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }

        // Notification permission status check
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        if (notificationGranted) {
            binding.tvNotificationStatus.text = getString(R.string.status_perm_granted)
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvNotificationStatus.text = getString(R.string.status_perm_denied)
            binding.tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }
    }

    private fun requestAllPermissionsAtOnce() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        }

        val ungranted = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            requestAllPermissionsLauncher.launch(ungranted.toTypedArray())
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
        val currentRate = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_RATE, 1.0f).coerceIn(0.5f, 2.0f)
        val currentPitch = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_PITCH, 1.0f).coerceIn(0.5f, 2.0f)

        try {
            val stepIndex = Math.round((currentRate - 0.5f) / 0.1f).coerceIn(0, 15)
            val snappedRate = (0.5f + stepIndex * 0.1f).coerceIn(0.5f, 2.0f)
            binding.sliderSpeed.value = String.format(Locale.US, "%.1f", snappedRate).toFloat()
        } catch (_: Exception) {
            binding.sliderSpeed.value = 1.0f
        }

        try {
            val stepIndex = Math.round((currentPitch - 0.5f) / 0.1f).coerceIn(0, 15)
            val snappedPitch = (0.5f + stepIndex * 0.1f).coerceIn(0.5f, 2.0f)
            binding.sliderPitch.value = String.format(Locale.US, "%.1f", snappedPitch).toFloat()
        } catch (_: Exception) {
            binding.sliderPitch.value = 1.0f
        }

        binding.tvSpeedLabel.text = getString(R.string.label_speech_rate_format, binding.sliderSpeed.value)
        binding.tvPitchLabel.text = getString(R.string.label_pitch_format, binding.sliderPitch.value)

        binding.sliderSpeed.addOnChangeListener { _, value, _ ->
            val snapped = String.format(Locale.US, "%.1f", value).toFloat()
            binding.tvSpeedLabel.text = getString(R.string.label_speech_rate_format, snapped)
            prefs.edit().putFloat(SerenaScreenReaderService.KEY_SPEECH_RATE, snapped).apply()
            SerenaScreenReaderService.instance?.updateTtsSettings()
            localTts?.setSpeechRate(snapped)
        }

        binding.sliderPitch.addOnChangeListener { _, value, _ ->
            val snapped = String.format(Locale.US, "%.1f", value).toFloat()
            binding.tvPitchLabel.text = getString(R.string.label_pitch_format, snapped)
            prefs.edit().putFloat(SerenaScreenReaderService.KEY_SPEECH_PITCH, snapped).apply()
            SerenaScreenReaderService.instance?.updateTtsSettings()
            localTts?.setPitch(snapped)
        }

        binding.btnTestSpeech.setOnClickListener {
            val sampleText = getString(R.string.main_tts_test_speech_fmt, binding.sliderSpeed.value)
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
                Toast.makeText(this, getString(R.string.main_tts_filipino_guide), Toast.LENGTH_LONG).show()
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
            val statusStr = if (isChecked) getString(R.string.main_hourly_chime_enabled) else getString(R.string.main_hourly_chime_disabled)
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
                val periodStr = if (isAm) getString(R.string.main_time_am) else getString(R.string.main_time_pm)
                val sampleText = getString(R.string.main_hourly_chime_test_fmt, periodStr, displayHour)
                localTts?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "testHourlyChime")
            }
        }
    }

    private fun setupCallAssistantSection() {
        val isPeriodicEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_CALL_PERIODIC_ANNOUNCE, false)
        binding.switchCallPeriodicAnnounce.isChecked = isPeriodicEnabled

        binding.switchCallPeriodicAnnounce.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(SerenaScreenReaderService.KEY_CALL_PERIODIC_ANNOUNCE, isChecked).apply()
            val statusStr = if (isChecked) getString(R.string.main_call_timer_enabled) else getString(R.string.main_call_timer_disabled)
            Toast.makeText(this, statusStr, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOperationGuideSection() {
        val isEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_ANNOUNCE_OPERATION_ACTIONS, false)
        binding.switchAnnounceOperationActions.isChecked = isEnabled

        binding.switchAnnounceOperationActions.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(SerenaScreenReaderService.KEY_ANNOUNCE_OPERATION_ACTIONS, isChecked).apply()
            val statusStr = if (isChecked) getString(R.string.main_guide_enabled) else getString(R.string.main_guide_disabled)
            Toast.makeText(this, statusStr, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupShakeSensitivitySection() {
        val currentThreshold = prefs.getFloat(SerenaScreenReaderService.KEY_SHAKE_THRESHOLD, 2.3f)
        when (currentThreshold) {
            2.3f -> binding.rbShakeSensitive.isChecked = true
            3.5f -> binding.rbShakeNormal.isChecked = true
            4.3f -> binding.rbShakeFirm.isChecked = true
            else -> binding.rbShakeSensitive.isChecked = true
        }

        binding.rgShakeSensitivity.setOnCheckedChangeListener { _, checkedId ->
            val newThreshold = when (checkedId) {
                R.id.rbShakeSensitive -> 2.3f
                R.id.rbShakeNormal -> 3.5f
                R.id.rbShakeFirm -> 4.3f
                else -> 2.3f
            }
            prefs.edit().putFloat(SerenaScreenReaderService.KEY_SHAKE_THRESHOLD, newThreshold).apply()
            
            val label = when (newThreshold) {
                2.3f -> getString(R.string.main_shake_light)
                3.5f -> getString(R.string.main_shake_standard)
                4.3f -> getString(R.string.main_shake_firm)
                else -> getString(R.string.main_shake_light)
            }
            Toast.makeText(this, getString(R.string.main_shake_sensitivity_toast_fmt, label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupDeveloperCallSection() {
        val phoneNumber = "08094959134"
        val developerEmail = "shinjisakiyama@gmail.com"
        val developerName = "Shinji"

        // 1. 電話サポート（直接ダイヤル起動）
        binding.btnCallDeveloper.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
                startActivity(intent)
                Toast.makeText(this, getString(R.string.main_support_call_toast_fmt, developerName), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.main_support_call_fail_fmt, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }

        // 2. メールサポート（Gmail / 標準メーラー起動）
        binding.btnEmailDeveloper.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$developerEmail")
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.main_support_mail_subject))
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.main_support_mail_body))
                }
                startActivity(intent)
                Toast.makeText(this, getString(R.string.main_support_mail_toast), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.main_support_mail_fail_fmt, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }

        // 3. LINE / WhatsApp / SMS 等のメッセージサポートダイアログ
        binding.btnMessageDeveloper.setOnClickListener {
            val options = arrayOf(
                getString(R.string.main_support_msg_wa),
                getString(R.string.main_support_msg_sms),
                getString(R.string.main_support_msg_line),
                getString(R.string.main_support_msg_other)
            )
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.main_support_msg_dialog_title))
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // WhatsApp 直通リンク
                            try {
                                val url = "https://wa.me/818094959134?text=" + Uri.encode(getString(R.string.main_support_mail_body))
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                                Toast.makeText(this, getString(R.string.main_support_wa_toast), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this, getString(R.string.main_support_wa_fail_fmt, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }
                        1 -> {
                            // SMS 直通
                            try {
                                val smsUri = Uri.parse("smsto:$phoneNumber")
                                val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                    putExtra("sms_body", getString(R.string.main_support_mail_body))
                                }
                                startActivity(intent)
                                Toast.makeText(this, getString(R.string.main_support_sms_toast), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this, getString(R.string.main_support_sms_fail_fmt, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }
                        2 -> {
                            // LINE 電話番号検索サポート
                            try {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Shinji Phone", phoneNumber)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this, getString(R.string.main_support_line_toast_fmt, phoneNumber), Toast.LENGTH_LONG).show()

                                val lineIntent = packageManager.getLaunchIntentForPackage("jp.naver.line.android")
                                if (lineIntent != null) {
                                    startActivity(lineIntent)
                                } else {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://line.me/")))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this, getString(R.string.main_support_line_fail_fmt, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        }
                        3 -> {
                            // その他共有
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, getString(R.string.main_support_mail_body))
                            }
                            val chooser = Intent.createChooser(sendIntent, getString(R.string.main_support_other_chooser))
                            startActivity(chooser)
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // 4. スペイン語翻訳貢献者 (Luis Carlos) へのメール連絡
        binding.btnEmailLuis.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:luiscarlosgonzalezmorales655@gmail.com")
                    putExtra(Intent.EXTRA_SUBJECT, "[Serena Screen Reader] Spanish Feedback & Community")
                    putExtra(Intent.EXTRA_TEXT, "Hi Luis Carlos,\n\nThank you for translating Serena Screen Reader into Spanish!\n\n")
                }
                startActivity(intent)
                Toast.makeText(this, getString(R.string.main_support_luis_toast), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.main_support_mail_fail_fmt, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupTestBench() {
        binding.btnTestA.setOnClickListener {
            Toast.makeText(this, getString(R.string.main_test_btn_a_toast), Toast.LENGTH_SHORT).show()
        }

        binding.btnTestB.setOnClickListener {
            Toast.makeText(this, getString(R.string.main_test_btn_b_toast), Toast.LENGTH_SHORT).show()
        }

        binding.switchDetailMode.setOnCheckedChangeListener { _, isChecked ->
            val modeStr = if (isChecked) getString(R.string.main_mode_detailed) else getString(R.string.main_mode_standard)
            Toast.makeText(this, getString(R.string.main_test_bench_mode_fmt, modeStr), Toast.LENGTH_SHORT).show()
        }

        binding.btnTriggerMenu.setOnClickListener {
            if (SerenaScreenReaderService.isServiceRunning()) {
                SerenaScreenReaderService.instance?.triggerSerenaMenu()
            } else {
                Toast.makeText(this, getString(R.string.main_service_already_enabled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTelemetrySection() {
        val telemetry = AlphaTelemetryHelper.getInstance(this)

        binding.switchTelemetryOptIn.isChecked = telemetry.isConsentGranted()
        binding.switchTelemetryOptIn.setOnCheckedChangeListener { _, isChecked ->
            telemetry.setConsentStatus(isChecked)
            val msg = if (isChecked) getString(R.string.main_telemetry_enabled) else getString(R.string.main_telemetry_disabled)
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
        } else {
            checkAiEthicsConsentOnStart()
        }
    }

    private fun checkAiEthicsConsentOnStart() {
        val hasConsented = prefs.getBoolean(KEY_AI_ETHICS_CONSENT, false)
        if (!hasConsented) {
            showAiEthicsConsentDialog()
        } else {
            checkWelcomeGuideOnStart()
        }
    }

    private fun checkWelcomeGuideOnStart() {
        val hideWelcome = prefs.getBoolean(KEY_HIDE_WELCOME_DIALOG, false)
        if (!hideWelcome) {
            showWelcomeDialog(isUserTriggered = false)
        }
    }

    private var isWelcomeTtsPlaying = false

    private fun showWelcomeDialog(isUserTriggered: Boolean = false) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_welcome_serena, null)
        val cbDontShowAgain = dialogView.findViewById<android.widget.CheckBox>(R.id.cbDontShowAgain)
        val btnPlayTts = dialogView.findViewById<android.widget.Button>(R.id.btnPlayWelcomeTts)
        val btnDismiss = dialogView.findViewById<android.widget.Button>(R.id.btnDismissWelcome)

        cbDontShowAgain.isChecked = prefs.getBoolean(KEY_HIDE_WELCOME_DIALOG, false)

        val welcomeGuideText = getString(R.string.welcome_tts_guide_text)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        isWelcomeTtsPlaying = false

        btnPlayTts.setOnClickListener {
            if (isWelcomeTtsPlaying) {
                try { localTts?.stop() } catch (_: Exception) {}
                if (SerenaScreenReaderService.isServiceRunning()) {
                    SerenaScreenReaderService.instance?.speak(getString(R.string.welcome_btn_stop_tts), TextToSpeech.QUEUE_FLUSH)
                }
                btnPlayTts.text = getString(R.string.welcome_btn_play_tts)
                btnPlayTts.setBackgroundColor(ContextCompat.getColor(this, R.color.serena_accent))
                isWelcomeTtsPlaying = false
            } else {
                isWelcomeTtsPlaying = true
                btnPlayTts.text = getString(R.string.welcome_btn_stop_tts)
                btnPlayTts.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red))

                val rate = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_RATE, 1.0f).coerceIn(0.5f, 2.0f)
                val pitch = prefs.getFloat(SerenaScreenReaderService.KEY_SPEECH_PITCH, 1.0f).coerceIn(0.5f, 2.0f)

                localTts?.setSpeechRate(rate)
                localTts?.setPitch(pitch)

                localTts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            btnPlayTts.text = getString(R.string.welcome_btn_play_tts)
                            btnPlayTts.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.serena_accent))
                            isWelcomeTtsPlaying = false
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            btnPlayTts.text = getString(R.string.welcome_btn_play_tts)
                            btnPlayTts.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.serena_accent))
                            isWelcomeTtsPlaying = false
                        }
                    }
                })

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }

                if (SerenaScreenReaderService.isServiceRunning() && SerenaScreenReaderService.instance != null) {
                    SerenaScreenReaderService.instance?.speak(welcomeGuideText, TextToSpeech.QUEUE_FLUSH)
                } else if (isLocalTtsReady && localTts != null) {
                    localTts?.speak(welcomeGuideText, TextToSpeech.QUEUE_FLUSH, params, "welcome_guide_utterance")
                } else {
                    localTts = TextToSpeech(applicationContext) { status ->
                        if (status == TextToSpeech.SUCCESS) {
                            localTts?.language = Locale.getDefault()
                            localTts?.setSpeechRate(rate)
                            localTts?.setPitch(pitch)
                            isLocalTtsReady = true
                            localTts?.speak(welcomeGuideText, TextToSpeech.QUEUE_FLUSH, params, "welcome_guide_utterance")
                        }
                    }
                }
            }
        }

        cbDontShowAgain.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_HIDE_WELCOME_DIALOG, isChecked).apply()
        }

        btnDismiss.setOnClickListener {
            if (isWelcomeTtsPlaying) {
                localTts?.stop()
                isWelcomeTtsPlaying = false
            }
            prefs.edit().putBoolean(KEY_HIDE_WELCOME_DIALOG, cbDontShowAgain.isChecked).apply()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            if (isWelcomeTtsPlaying) {
                localTts?.stop()
                isWelcomeTtsPlaying = false
            }
        }

        dialog.show()
    }

    private fun showAiEthicsConsentDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.ai_ethics_title)
            .setMessage(R.string.ai_ethics_message)
            .setPositiveButton(R.string.ai_ethics_agree) { dialog, _ ->
                prefs.edit().putBoolean(KEY_AI_ETHICS_CONSENT, true).apply()
                dialog.dismiss()
                checkWelcomeGuideOnStart()
            }
            .setNeutralButton(R.string.ai_ethics_view_google) { _, _ ->
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google/responsibility/principles/"))
                    startActivity(browserIntent)
                } catch (_: Exception) {}
            }
            .setNegativeButton(R.string.ai_ethics_disagree) { dialog, _ ->
                prefs.edit().putBoolean(KEY_AI_ETHICS_CONSENT, false).apply()
                dialog.dismiss()
                checkWelcomeGuideOnStart()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        const val KEY_AI_ETHICS_CONSENT = "key_ai_ethics_consent_agreed"
        const val KEY_HIDE_WELCOME_DIALOG = "key_hide_welcome_dialog"
    }

    private fun showTelemetryConsentDialog() {
        val telemetry = AlphaTelemetryHelper.getInstance(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_telemetry_title)
            .setMessage(R.string.dialog_telemetry_message)
            .setPositiveButton(R.string.dialog_telemetry_agree) { dialog, _ ->
                telemetry.setConsentStatus(true)
                binding.switchTelemetryOptIn.isChecked = true
                Toast.makeText(this, getString(R.string.main_telemetry_thanks), Toast.LENGTH_SHORT).show()
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

    private fun setupTranslationSection() {
        val helper = com.shinji.serena.translation.InstantTranslationHelper(this)
        val targetLangName = if (helper.targetLanguageCode == "ja") "日本語 (ja)" else Locale.getDefault().displayLanguage
        binding.tvTranslationTargetInfo.text = getString(R.string.translation_target_format, targetLangName)

        when (helper.mode) {
            com.shinji.serena.translation.TranslationMode.ORIGINAL_THEN_TRANSLATION -> binding.rbTransOriginalThenTrans.isChecked = true
            com.shinji.serena.translation.TranslationMode.TRANSLATION_ONLY -> binding.rbTransOnly.isChecked = true
            com.shinji.serena.translation.TranslationMode.OFF -> binding.rbTransOff.isChecked = true
        }

        binding.rgTranslationMode.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.rbTransOriginalThenTrans -> com.shinji.serena.translation.TranslationMode.ORIGINAL_THEN_TRANSLATION
                R.id.rbTransOnly -> com.shinji.serena.translation.TranslationMode.TRANSLATION_ONLY
                else -> com.shinji.serena.translation.TranslationMode.OFF
            }
            helper.mode = newMode
            Toast.makeText(this, getString(R.string.main_translation_mode_toast_fmt, newMode.displayName), Toast.LENGTH_SHORT).show()
        }
    }
}


