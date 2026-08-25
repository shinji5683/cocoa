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
            Toast.makeText(this, "バックグラウンド位置情報（常に許可）が有効になりました", Toast.LENGTH_SHORT).show()
        }
        updateServiceStatusDisplay()
    }

    private val requestAllPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val grantedCount = permissions.values.count { it }
        Toast.makeText(this, "権限を更新しました (${grantedCount}/${permissions.size})", Toast.LENGTH_SHORT).show()
        updateServiceStatusDisplay()

        // 位置情報が許可された場合、Android 10+ で「常に許可」のバックグラウンド権限を案内＆リクエスト
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasBg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

            if ((hasFine || hasCoarse) && !hasBg) {
                AlertDialog.Builder(this)
                    .setTitle("📍 バックグラウンド位置情報の許可")
                    .setMessage("端末をシェイクしたときにいつでも現在地住所を読み上げるため、次の画面で『常に許可』を選択してください。")
                    .setPositiveButton("許可に進む") { _, _ ->
                        requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("後で", null)
                    .show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            prefs = getSafeSharedPreferences(SerenaScreenReaderService.PREFS_NAME, Context.MODE_PRIVATE)

            initLocalTts()
            setupStatusSection()
            setupSecuritySection()
            setupPermissionsSection()
            setupTtsControls()
            setupHourlyChimeSection()
            setupCallAssistantSection()
            setupShakeSensitivitySection()
            setupDeveloperCallSection()
            setupTestBench()
            setupTelemetrySection()
            setupTranslationSection()

            checkPreviousCrashOnStart()
            checkPermissionsOnStart()
            checkTelemetryConsentOnStart()
            handleGemmaDownloadIntent(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
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
            showAccessibilityDisclosureDialog()
        }
        binding.btnOpenWelcomeGuide.setOnClickListener {
            showWelcomeDialog(isUserTriggered = true)
        }
    }

    private fun showAccessibilityDisclosureDialog() {
        AlertDialog.Builder(this)
            .setTitle("🌸 Serena アクセシビリティ機能について")
            .setMessage(
                "Serena スクリーンリーダーは、視覚障害者および操作補助を必要とする方のために以下の機能を提供します：\n\n" +
                "・画面上の文字やボタンのリアルタイム音声読み上げ\n" +
                "・電話やLINE等の着信相手・通話時間アナウンス\n" +
                "・高精度徒歩ナビゲーション（正面・右斜め前等の直感案内）とカメラ情景認識\n\n" +
                "【プライバシー保護方針】\n" +
                "画面の内容や入力データはすべて端末内（オンデバイス）でのみ処理され、外部サーバーへ送信・収集・共有されることは一切ありません。\n\n" +
                "サービスを有効にするには、次の画面で「Serena」を選択してオンにしてください。"
            )
            .setPositiveButton("同意して設定を開く") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "インストール済みアプリから「Serena」をオンにしてください", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("キャンセル", null)
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
                    .setTitle("⚠️ 前回の異常終了（クラッシュ）診断ログ")
                    .setMessage(
                        "前回の起動時またはサービス実行中に発生したエラーを記録しました。\n" +
                        "『ログをコピー』を押して開発者Shinjiに共有することで、迅速に原因を特定・修正できます。\n\n" +
                        crashReport.take(400) + (if (crashReport.length > 400) "\n...(以下省略)" else "")
                    )
                    .setPositiveButton("📋 診断ログをコピー") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Serena Crash Report", crashReport)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "クラッシュログをクリップボードにコピーしました！", Toast.LENGTH_LONG).show()
                    }
                    .setNeutralButton("📧 メール送信") { _, _ ->
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.DEVELOPER_EMAIL))
                            putExtra(Intent.EXTRA_SUBJECT, "【Serena クラッシュ診断ログ】")
                            putExtra(Intent.EXTRA_TEXT, crashReport)
                        }
                        try {
                            startActivity(Intent.createChooser(intent, "エラーログ送信"))
                        } catch (_: Exception) {}
                    }
                    .setNegativeButton("閉じる", null)
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
                Toast.makeText(this, "このOSバージョンでは重ねて表示権限は不要です", Toast.LENGTH_SHORT).show()
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

    private fun setupCallAssistantSection() {
        val isPeriodicEnabled = prefs.getBoolean(SerenaScreenReaderService.KEY_CALL_PERIODIC_ANNOUNCE, false)
        binding.switchCallPeriodicAnnounce.isChecked = isPeriodicEnabled

        binding.switchCallPeriodicAnnounce.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(SerenaScreenReaderService.KEY_CALL_PERIODIC_ANNOUNCE, isChecked).apply()
            val statusStr = if (isChecked) "通話中の経過時間定期読み上げを有効にしました" else "通話中の経過時間定期読み上げを無効にしました"
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
                2.3f -> "敏感 (2.3f)"
                3.5f -> "標準 (3.5f)"
                4.3f -> "しっかり (4.3f)"
                else -> "敏感 (2.3f)"
            }
            Toast.makeText(this, "シェイク感度を${label}に設定しました", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "開発者(${developerName})への電話アプリを起動します", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "電話アプリの起動に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // 2. メールサポート（Gmail / 標準メーラー起動）
        binding.btnEmailDeveloper.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:$developerEmail")
                    putExtra(Intent.EXTRA_SUBJECT, "【Serena Screen Reader】お問い合わせ・フィードバック")
                    putExtra(Intent.EXTRA_TEXT, "Shinjiさん、こんにちは！\n\n【お問い合わせ内容】\n\n")
                }
                startActivity(intent)
                Toast.makeText(this, "メールアプリを起動します", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "メールアプリの起動に失敗しました: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // 3. LINE / WhatsApp / SMS 等のメッセージサポートダイアログ
        binding.btnMessageDeveloper.setOnClickListener {
            val options = arrayOf(
                "🟢 WhatsApp で直通チャットを開く",
                "💬 SMS (ショートメッセージ) で直接送信",
                "🟢 LINE で電話番号検索 (番号を自動コピーしてLINE起動)",
                "📤 その他のアプリで共有送信"
            )
            AlertDialog.Builder(this)
                .setTitle("💬 メッセージサポート窓口の選択")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // WhatsApp 直通リンク
                            try {
                                val url = "https://wa.me/818094959134?text=" + Uri.encode("【Serena Screen Reader サポート相談】\nShinjiさん、こんにちは！\n")
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                startActivity(intent)
                                Toast.makeText(this, "WhatsAppを起動します", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this, "WhatsAppの起動に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        1 -> {
                            // SMS 直通
                            try {
                                val smsUri = Uri.parse("smsto:$phoneNumber")
                                val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                    putExtra("sms_body", "【Serena サポート相談】\nShinjiさん、こんにちは！\n")
                                }
                                startActivity(intent)
                                Toast.makeText(this, "SMSメッセージアプリを起動します", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(this, "SMSアプリの起動に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        2 -> {
                            // LINE 電話番号検索サポート
                            try {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Shinji Phone", phoneNumber)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(this, "電話番号 ($phoneNumber) をコピーしました。LINEの友だち追加で電話番号検索してください", Toast.LENGTH_LONG).show()

                                val lineIntent = packageManager.getLaunchIntentForPackage("jp.naver.line.android")
                                if (lineIntent != null) {
                                    startActivity(lineIntent)
                                } else {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://line.me/")))
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this, "LINEの起動に失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        3 -> {
                            // その他共有
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "【Serena Screen Reader サポート相談】\nShinjiさん宛て\n\n")
                            }
                            val chooser = Intent.createChooser(sendIntent, "相談するアプリを選択")
                            startActivity(chooser)
                        }
                    }
                }
                .setNegativeButton("キャンセル", null)
                .show()
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

        val welcomeGuideText = "Serena スクリーンリーダーへようこそ！全盲の開発者 Shinji が創り上げた、世界でいちばん優しく賢い次世代スクリーンリーダーです。" +
                "主な特徴をご紹介します。" +
                "1、直感的なタッチ探索とTalkBackモードの切替。" +
                "2、着信相手や通話時間を自動で知らせるスマート通話アシスタント。" +
                "3、毎時0分の時報チャイムと、端末を振るだけで状態がわかるシェイク通知。" +
                "4、正面や右斜め前など直感的な表現で案内する高精度歩行ナビ。" +
                "5、画面の外国語を自動で日本語に訳すリアルタイム翻訳と、タガログ語への対応。" +
                "6、オンデバイスAIによる情景認識と、同音異義語も迷わない漢字の詳細な組み合わせ説明。" +
                "Serena と一緒に、もっと自由で快適なスマートフォン体験をお楽しみください！"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        isWelcomeTtsPlaying = false

        btnPlayTts.setOnClickListener {
            if (isWelcomeTtsPlaying) {
                localTts?.stop()
                if (SerenaScreenReaderService.isServiceRunning()) {
                    SerenaScreenReaderService.instance?.speak("音声ガイドを停止しました", TextToSpeech.QUEUE_FLUSH)
                }
                btnPlayTts.text = "🔊 音声ガイドを聞く (TTS読み上げ)"
                btnPlayTts.setBackgroundColor(ContextCompat.getColor(this, R.color.serena_accent))
                isWelcomeTtsPlaying = false
            } else {
                if (SerenaScreenReaderService.isServiceRunning()) {
                    SerenaScreenReaderService.instance?.speak(welcomeGuideText, TextToSpeech.QUEUE_FLUSH)
                } else {
                    localTts?.speak(welcomeGuideText, TextToSpeech.QUEUE_FLUSH, null, "welcome_guide")
                }
                btnPlayTts.text = "⏹️ 音声ガイドを停止"
                btnPlayTts.setBackgroundColor(ContextCompat.getColor(this, R.color.status_red))
                isWelcomeTtsPlaying = true
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
            .setTitle("🤖 Google 責任あるAI倫理ガイドライン及びAI機能の同意")
            .setMessage(
                "Serena Screen Reader は、オンデバイスAI（Gemini Nano）や機械学習を活用した空間案内・情景認識・表情認識・OCR・スマート要約機能を提供します。\n\n" +
                "【1. Google 責任あるAI倫理原則の遵守】\n" +
                "本アプリのAI機能は、Google の責任あるAI倫理原則 (Responsible AI Principles: https://ai.google/responsibility/principles/) に基づいて設計されています。\n\n" +
                "【2. AI推論の特性と安全に関する免責事項】\n" +
                "・表情認識、人物認識、物体認識、距離・位置推定などのAI機能は推論による補助情報であり、環境（暗所、逆光、カメラ角度、電波状態等）により誤認識や誤差が生じる場合があります。\n" +
                "・横断歩道、階段、駅のホーム、道路など安全が最優先される場面では、必ず白杖や周囲の音、身体感覚による安全確認を併用してください。\n\n" +
                "【3. 完全オンデバイス・プライバシー保護】\n" +
                "AI画像解析や音声認識はすべて端末内（オンデバイス）で完結し、カメラ映像や個人データが外部サーバーに送信・収集されることはありません。\n\n" +
                "上記ガイドラインおよび利用規約に同意して利用を開始しますか？"
            )
            .setPositiveButton("同意して利用する") { dialog, _ ->
                prefs.edit().putBoolean(KEY_AI_ETHICS_CONSENT, true).apply()
                Toast.makeText(this, "AI倫理ガイドライン及び免責事項に同意しました", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                checkWelcomeGuideOnStart()
            }
            .setNeutralButton("Google AI倫理原則を確認") { _, _ ->
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ai.google/responsibility/principles/"))
                    startActivity(browserIntent)
                } catch (_: Exception) {
                    Toast.makeText(this, "ブラウザを開けませんでした", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("同意しない") { dialog, _ ->
                prefs.edit().putBoolean(KEY_AI_ETHICS_CONSENT, false).apply()
                Toast.makeText(this, "AI支援機能の精度と安全確認に十分ご注意ください", Toast.LENGTH_LONG).show()
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

    private fun setupTranslationSection() {
        val helper = com.shinji.serena.translation.InstantTranslationHelper(this)
        val targetLangName = if (helper.targetLanguageCode == "ja") "日本語 (ja)" else Locale.getDefault().displayLanguage
        binding.tvTranslationTargetInfo.text = "翻訳先言語: $targetLangName へ自動適応"

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
            Toast.makeText(this, "リアルタイム翻訳: ${newMode.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        localTts?.shutdown()
        localTts = null
    }
}


