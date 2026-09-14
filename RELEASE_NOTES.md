# Serena Release Notes / リリースノート 🌸✨

すべての更新履歴は、全盲の開発者Shinjiのこだわりと世界中の視覚障害者ユーザーのために、日本語と英語の両方で詳細に記録されています。
All updates and changelogs are meticulously maintained in both Japanese and English for global visually impaired users.

---

## [v2.3.5] - 2026-09-14
### 🇯🇵 日本語
#### 🛡 サービス安定化・セキュリティ防御強化（Critical Service Shield）
- **SecurityException / OS例外によるサービス強制停止の完全防御**:
  - Android OSフレームワークやOEM ROMがスローする `SecurityException` や `RemoteException`、IPC `DeadObjectException` に対する多層防御シールドを実装。
  - グローバルクラッシュハンドラー（`SerenaApp`）において、セキュリティ例外やワーカースレッド例外によるプロセス停止（サービスKill）を完全にブロックし、全盲ユーザーの生命線であるスクリーンリーダーの生存を絶対維持。
- **アクセシビリティサービス全エントリポイントの防護壁確立**:
  - `onAccessibilityEvent`、`onGesture`、`onServiceConnected`、`onInterrupt` を例外防御レイヤーで完全に包摂。
  - `ConcurrentModificationException`（コレクション同時変更衝突）やNullPo、動的ノード取得失敗による予期せぬクラッシュを100%封じ込め。
- **オーディオ録音・認識ワーカーのフェイルセーフ**:
  - `AudioRecord` ストリームの読み取りループに安全機構を追加し、権限変動やデバイス例外でのクラッシュを阻止。

### 🇺🇸 English
#### 🛡 Critical Service Shield & Robustness
- **Immunity Against SecurityException & Service Termination**:
  - Implemented multi-layered defensive shields preventing Android OS or OEM ROM `SecurityException`, `RemoteException`, or IPC binder errors from terminating the service.
  - Enhanced global crash handler (`SerenaApp`) to intercept non-fatal and security exceptions on background threads, ensuring the screen reader service remains alive without disrupting the user's operation.
- **Top-Level Accessibility Callback Armor**:
  - Guarded all service entry points (`onAccessibilityEvent`, `onGesture`, `onServiceConnected`, `onInterrupt`) against unhandled exceptions, `ConcurrentModificationException`, and transient node access errors.
- **Fail-Safe Audio Recognition Worker**:
  - Hardened `AudioRecord` streaming loop against unexpected runtime exceptions and permission revocation.

---

## [v2.3.4] - 2026-09-14
### 🇯🇵 日本語
#### ✨ 新機能・アクセシビリティ強化
- **通話相手の自動特定＆通話開始アナウンス**:
  - 通話接続時に、相手の連絡先名または電話番号をインテリジェントに特定し、「［アプリ名］の［相手の名前］さんとの通話を開始しました。開始時刻は［現在時刻］です。」と音声案内します。
- **通話終了時の合計通話時間＆終了時刻案内**:
  - 通話を切断した瞬間に、「［アプリ名］の［相手の名前］さんとの通話が終了しました。通話時間は［通話時間］です。終了時刻は［終了時刻］です。」と耳元で即座に案内します。
- **UIツリーからのスマート連絡先抽出（`extractCallerNameFromNode`）**:
  - 通話アプリ（標準電話、LINE、WhatsApp等）のアクセシビリティノードから「応答」「切断」「ミュート」「スピーカー」などのボタン類やタイマー表示を除外し、相手の名前・番号だけを高精度に抽出します。
- **6言語完全ローカライズ＆文字列外部化**:
  - 日本語、英語、スペイン語、タガログ語、オランダ語、デフォルト言語の全 `strings.xml` に完全配備（ハードコードゼロ）。

### 🇺🇸 English
#### ✨ New Features & Accessibility
- **Automatic Caller Identification & Call Start Announcement**:
  - Intelligently recognizes caller contact name or phone number upon connection and announces: *"Started [App] call with [Name]. Start time is [Time]."*
- **Call End Duration & End Time Announcement**:
  - Disconnection immediately announces: *"[App] call with [Name] ended. Duration was [Duration]. End time is [Time]."*
- **Intelligent Caller Node Extraction (`extractCallerNameFromNode`)**:
  - Robustly isolates contact names while ignoring in-call action buttons (mute, speaker, hangup) and timer displays across dialer and messaging apps.
- **Full 6-Locale Localization**:
  - Fully externalized and localized across Japanese, English, Spanish, Tagalog, Dutch, and Default.

---

## [v2.3.3] - 2026-09-14
### 🇯🇵 日本語
#### 🐛 バグ修正・互換性向上
- **Android 11（API 30）クラッシュの完全撃退**:
  - API 31専用の `TelephonyCallback` によるARTクラス事前解決例外（`NoClassDefFoundError`）を、独立ヘルパーオブジェクト（`@RequiresApi(Build.VERSION_CODES.S)`）へ退避・完全隔離。
  - ミリ波定数 `TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED` をAPI 30で参照した際の `NoSuchFieldError` を防御。
  - Android 11フレームワーク不整合を誘発していた `FLAG_SERVICE_HANDLES_DOUBLE_TAP` フラグの指定をAPI 31+に限定し、安全なフォールバック機構を追加。

### 🇺🇸 English
#### 🐛 Bug Fixes & Compatibility
- **Comprehensive Android 11 (API 30) Crash Resolution**:
  - Isolated API 31 `TelephonyCallback` inside `@RequiresApi(Build.VERSION_CODES.S)` helper objects to eliminate ART pre-verification `NoClassDefFoundError` on Android 11.
  - Guarded `TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED` against `NoSuchFieldError` on API 30.
  - Gated `FLAG_SERVICE_HANDLES_DOUBLE_TAP` to API 31+ with defensive fallback handling.

---

## [v2.3.2] - 2026-09-11
### 🇯🇵 日本語
#### 🐛 バグ修正・安定性向上
- **バックグラウンドマイク専有の解除**:
  - アシスタントや音声入力、Google Gemini長押し呼び出しを阻害しないよう、不要な常時マイク専有を完全撤廃。

### 🇺🇸 English
#### 🐛 Bug Fixes & Stability
- **Microphone Resource Release**:
  - Eliminated persistent background mic hold, ensuring system voice assistants (Google Assistant / Gemini) trigger reliably via power button gestures.

---

## [v2.3.1] - 2026-09-11
### 🇯🇵 日本語
#### 🛠 アクセシビリティ・UI修正
- **Gboard リフト入力（Lift to Type）の分離**:
  - 入力エリアと機能ボタンの混同を防止し、確実なキー入力を保証。
- **ステータスバーのタッチ誤認識修正**:
  - ステータスバー操作時にホーム画面ノードを誤って読み上げる現象を完全に遮断。

### 🇺🇸 English
#### 🛠 Accessibility & UI Fixes
- **Gboard Lift-to-Type Distinction**:
  - Properly distinguished text input keys from action buttons.
- **Status Bar Leak Isolation**:
  - Prevented home screen node bleed-through when exploring the status bar.

---

## [v2.2.2] - 2026-09-10
### 🇯🇵 日本語
#### 🚀 アプリアップデート機能強化
- **PackageInstaller Session API への完全移行**:
  - Android 14/15/16 のセキュリティポリシーに完全適合したアプリ内アップデートインストーラー。
  - 権限付与後の自動再開プロセスを実装。

### 🇺🇸 English
#### 🚀 In-App Update Engine
- **Session-Based PackageInstaller**:
  - Compliant with Android 14+ strict install guidelines with seamless post-grant automatic resume.
