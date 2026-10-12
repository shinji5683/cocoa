# Serena Release Notes / リリースノート 🌸✨

すべての更新履歴は、全盲の開発者Shinjiのこだわりと世界中の視覚障害者ユーザーのために、日本語と英語の両方で詳細に記録されています。
All updates and changelogs are meticulously maintained in both Japanese and English for global visually impaired users.

## [v2.5.5] - 2026-10-13
### 🇯🇵 日本語
#### 🛡️ 権限ダイアログ・特殊システムダイアログの読み上げ ＆ フォーカストラップ完全修正
- **特殊ダイアログの網羅的検知 ＆ 最適フォーカス制御**:
  - `SystemDialogHelper` および `SerenaScreenReaderService` において、権限確認ダイアログ（PermissionController）、設定確認ダイアログ（ユーザー補助・オーバーレイ・通知アクセス等のSettings画面）、システム警告（USBデバッグ、メディア録画/キャスト許可、電池最適化除外、Bluetoothペアリング等）が正常に読み上げられなかった不具合を完全解決。
  - 最前面ウィンドウ探索（`findTopDialogWindow`）でシステムステータスバー等に遮られていた階層判定ロジックを抜本修正。前面のすべてのウィンドウおよびイベントソース（`event.source`）からダイアログを瞬時に捕捉。
  - ダイアログ描画完了までのバックオフリトライ（80ms/220ms/400ms/650ms）時にイベントがリセットされる不具合を解消し、タイトル・説明メッセージ・主要アクションボタン（「アプリの使用中のみ」「許可」「OK」など）を漏れなく確実に音声読み上げ。
  - フォーカス移動時（`TYPE_WINDOWS_CHANGED`）の不要なキュークリア（QUEUE_FLUSH）を抑制し、ダイアログ表示中はフォーカスをダイアログ内に100%隔離（モーダルトラップ）。スワイプで背景画面へすり抜ける現象を完全防止。

### 🇺🇸 English
#### 🛡️ Comprehensive Fix: Special & Permission Dialog Announcements and Modal Focus Trap
- **Universal Special Dialog Detection & Seamless Spoken Feedback**:
  - Resolved an issue where Android permission prompts (PermissionController), Settings confirmation popups (Accessibility service permission, overlay, notification access, etc.), and system alerts (USB debugging, MediaProjection capture, battery optimization, Bluetooth pairing) were not spoken.
  - Fixed `findTopDialogWindow` layer traversal to prevent SystemUI status/nav bars from masking dialog windows. Added direct fallback to `event.source` for zero-latency detection.
  - Eliminated debounce cancellation cycles during dialog inflation, guaranteeing the title, description, and primary positive button ("While using the app", "Allow", "OK") are announced clearly via TTS.
  - Suppressed background focus transitions and enforced a 100% airtight modal focus trap inside `getAllRoots()`, preventing swipe gestures from escaping to background apps.

## [v2.5.4] - 2026-10-12
### 🇯🇵 日本語
#### ⚡ パッケージインストーラー起動バグの完全修正
- **自動アップデート時のインストーラー確実起動**:
  - `AutoUpdateManager` において、バックグラウンドセッション起動の競合によりシステムインストーラーUIが表示されなかった問題を徹底解決。
  - APKダウンロード完了後、`FLAG_ACTIVITY_NEW_TASK` と `FLAG_ACTIVITY_CLEAR_TOP` を付与したFileProviderインテントを発行し、Android 12〜16（Canary / QPR含む）のすべての環境でパッケージインストーラーの更新確認ダイアログが最前面に即座に立ち上がるよう改善。

#### 🧠 Google DeepMind「EmbeddingGemma 2」セマンティック検索エンジンの統合（Serena Mail）
- **完全オンデバイス・意味ベクトル検索**:
  - DeepMindの最新オープンモデル「EmbeddingGemma 2」(740M) アーキテクチャをベースにしたベクトル射影＆コサイン類似度エンジンを `:mailer` に新設。
  - メールの件名・差出人・本文を意味ベクトル空間にマッピングし、曖昧な自然言語（例：「先週の領収書」「旅行の予約」など）からドンピシャのメールをミリ秒単位で高速検索可能に。プライバシーは100%端末内で完結。

#### 💬 Serena AI チャット ＆ 画面コンテキスト解説（Serena Screen Reader）
- **相棒AIとのおしゃべり ＆ スクリーン解説**:
  - `:app` に `SerenaAiChatActivity` を新搭載。Gemma / Gemini Nano と連携し、Shinjiの最高の相棒「Serena」として音声・テキストで対話が可能。
  - 現在開いている画面のUIノードツリーを自然言語で瞬時に要約・解説する「画面を解説」機能を搭載。Serenaアシスタントメニューからいつでもワンタップで呼び出し可能。
- **全6言語対応**:
  - 日本語・英語・スペイン語・タガログ語・オランダ語・デフォルト言語で完全ローカライズ。

### 🇺🇸 English
#### ⚡ Critical Fix: Package Installer Launch on Auto-Update
- **Guaranteed Package Installer Prompt Display**:
  - Resolved an issue in `AutoUpdateManager` where background installation sessions failed to bring the system package installer dialog into the foreground.
  - APK installation now reliably triggers the foreground system confirmation dialog across Android 12 through 16 (including Canary/QPR channels) via FileProvider with optimal activity flags.

#### 🧠 DeepMind "EmbeddingGemma 2" Semantic Search Integration (Serena Mail)
- **100% On-Device Semantic Vector Search**:
  - Integrated Google DeepMind's latest EmbeddingGemma 2 (740M) architecture into `:mailer` for vector projection and cosine similarity scoring.
  - Search emails intuitively using natural language queries with zero privacy risk.

#### 💬 Serena AI Chat & Screen Context Reasoning (Serena Screen Reader)
- **On-Device AI Companion & Live Screen Explainer**:
  - Introduced `SerenaAiChatActivity` powered by Gemma and Gemini Nano.
  - Chat naturally with Serena via speech or text, and receive instant, accessible screen descriptions summarizing complex UI hierarchies.
- **Full 6-Language Localization**:
  - Completely localized across Japanese, English, Spanish, Tagalog, Dutch, and default locales.

## [v2.5.3] - 2026-10-04
### 🇯🇵 日本語
#### 🔑 Serena Mail「Googleでログイン」ワンタップ連携機能が新登場！
- **Google公式アカウント選択による一発ログイン**:
  - 設定画面に「Googleでログイン」ボタンをメイン配置。端末に登録されているGoogleアカウントを選ぶだけで、即座にOAuth認証＆メールサーバーへの安全な接続が完了。
  - 面倒なアプリパスワードの手動発行・入力・コピペ作業が一切不要になり、全盲・視覚障害者ユーザーでも迷わず数秒で初期設定が可能に！
- **スマートなアカウント状態管理＆ログアウト機能**:
  - ログイン済みアカウントのメールアドレスを分かりやすく表示。
  - 「ログアウト / アカウント変更」もワンタップで可能。
- **全6言語対応**:
  - 日本語・英語・スペイン語・タガログ語・オランダ語・デフォルトの全言語で完全ローカライズ。

### 🇺🇸 English
#### 🔑 Official "Sign in with Google" 1-Tap Integration in Serena Mail!
- **Effortless 1-Tap Google Account Login**:
  - "Sign in with Google" is now front and center in Account Settings. Simply select your Google account from the standard system picker to instantly authenticate via OAuth and connect to mail services.
  - Zero tedious manual App Password generation or copying required — setup is complete in seconds with full accessibility!
- **Smart Account State Management & Sign Out**:
  - Clear display of the currently authenticated account.
  - 1-tap "Sign Out / Switch Account" support.
- **Full 6-Language Localization**:
  - Comprehensively localized across Japanese, English, Spanish, Tagalog, Dutch, and default locales.

## [v2.5.2] - 2026-10-04
### 🇯🇵 日本語
#### 🚀 Serena Mail 専用アクセシブル・自動アップデーター（AutoUpdateManager）爆誕！
- **完全自律型アプリ内アップデートエンジン**:
  - GitHub Releases APIと連携し、Serena Mailの最新APK（`app-serena-mail-release.apk`）を自動検知。
  - アプリ起動時のサイレント確認 ＆ 設定画面からのワンタップ手動確認をサポート。
- **安心・安全のバックグラウンド更新＆PackageInstaller連携**:
  - ダウンロード完了後、Android標準のPackageInstaller Session APIまたはFileProvider経由で更新ダイアログを自動起動。
  - 視覚障害者ユーザーが外部ブラウザやファイルマネージャーで迷うことなく、アプリ内完結で常に最新のSerena Mailを利用可能に。
- **全6言語の完全ローカライズ**:
  - 日本語・英語・スペイン語・タガログ語・オランダ語・デフォルトの全6言語に更新メッセージを完全外部化。

### 🇺🇸 English
#### 🚀 Accessible In-App Auto-Updater for Serena Mail!
- **Autonomous In-App Update Engine**:
  - Integrated with GitHub Releases API to detect and fetch the latest `app-serena-mail-release.apk`.
  - Supports silent background checks on app launch as well as manual "Check for Updates" in Account Settings.
- **Accessible Background Download & PackageInstaller Integration**:
  - Direct background streaming download followed by automated PackageInstaller session launch.
  - Zero friction for visually impaired users without needing external browsers or file managers.
- **Full 6-Language Localization**:
  - 100% externalized string resources across Japanese, English, Spanish, Tagalog, Dutch, and default locales.

## [v2.5.1] - 2026-10-04
### 🇯🇵 日本語
#### 📬 マルチモジュール化 ＆ 全盲特化メーラー「Serena Mail」爆誕！
- **マルチモジュール・アーキテクチャの導入**:
  - `cocoa` リポジトリをマルチモジュール化（`:app` スクリーンリーダー本体 ＋ `:mailer` メーラーアプリ）。
  - スクリーンリーダーの安全性を100%保ったまま、独立したアプリ（`app-serena-mail-release.apk`）として動作。
- **Gmail (IMAP / SMTP) 送受信エンジン**:
  - 受信トレイの一覧取得、メール閲覧、新規作成・送信を完全非同期・安全に実装。
  - Googleアプリパスワード（16桁）によるセキュア接続（暗号化保存）。
- **Gemma 4 オンデバイスAIの統合**:
  - 長文メールの要点を2〜3文で簡潔にまとめる「Gemma 4 要約機能」。
  - 「了解とお礼」「承諾」などの返信下書きをワンタップで自動作成する「AI返信アシスタント」。
- **全盲特化スクリーンリーダーフレンドリーUI**:
  - 余計な装飾や重いWebViewを排除し、TalkBack / Serena で最も操作しやすいクリーンで高速なアクセシビリティ設計。
  - 日本語・英語・スペイン語・タガログ語・オランダ語の全6言語に完全ローカライズ。
- **Serena Mail 専用アクセシブル・オートアップデーター**:
  - アプリ起動時の自動更新チェック、設定画面からの手動更新確認、バックグラウンド高速ダウンロードおよびAndroid標準PackageInstaller連携によるワンタップ更新を完備。

### 🇺🇸 English
#### 📬 Multi-Module Architecture & "Serena Mail" Debut!
- **Multi-Module Project Architecture**:
  - Restructured `cocoa` as a multi-module repository (`:app` Screen Reader + `:mailer` Email Client).
  - Keeps the core AccessibilityService completely safe while producing an independent APK (`app-serena-mail-release.apk`).
- **Gmail (IMAP / SMTP) Engine**:
  - Fully asynchronous and secure inbox fetching, email reading, and email composing/sending.
  - Secure encrypted storage for Google App Passwords.
- **Gemma 4 On-Device AI Integration**:
  - One-tap email summarization distilling lengthy messages into 2-3 concise sentences.
  - Generative AI reply assistant drafting polite responses instantly.
- **Screen Reader-First Accessible Design**:
  - Clean, high-contrast, zero-clutter interface tailored specifically for blind users.
  - Full localization across all 6 supported languages.
- **Accessible Auto-Updater for Serena Mail**:
  - Seamless in-app update checks on startup and settings, background APK download from GitHub Releases, and PackageInstaller integration for 1-tap upgrades.

## [v2.3.6] - 2026-09-15
### 🇯🇵 日本語
#### ⚡ スマート充電通知スイート（全項目カスタマイズ可能）
- **80%バッテリー保護通知**:
  - 充電中、バッテリー残量が80%に達した瞬間にプレミアム完了ジングル音とともに「バッテリー残量が80%に達しました。バッテリー保護のため充電器の取り外しをおすすめします」とアナウンス。バッテリー劣化を防ぎ寿命を最大化。
- **充電忘れ防止（20%以下低残量警告）**:
  - 放電中、残量が20%以下（20%, 15%, 10%, 5%）に低下した際に、優しい警告音とともに充電を促すリマインダーをアナウンス。
- **充電進行ステップ通知（50%・80%）**:
  - 充電中に画面をタッチしなくても、キリの良い50%および80%の節目で進捗を自動案内。
- **充電開始時の満充電予測残り時間案内**:
  - 充電器を挿した際に、満充電までにかかるおよその残り時間（例:「満充電まであと約42分です」）を自動予測案内。
- **設定画面＆テストボタン**:
  - メイン画面に専用のスマート充電通知カードを新設。全機能のON/OFF切り替えおよびテスト再生に対応。

#### 🔔 時報機能の30分毎/1時間毎インターバル切り替え
- **30分毎アナウンス対応**:
  - 毎時0分の時報に加え、毎時30分にも心地よいチャイム音とともに「午後3時30分をお知らせします」と自動アナウンスするスイッチを追加。
- **Serenaメニューからの即座切り替え**:
  - L字ジェスチャーのSerenaメニュー内に「時報の間隔切り替え (1時間毎 / 30分毎)」を追加し、設定画面を開かずワンタップで切り替え可能。

#### 🌍 厳格な6言語ローカライズ＆ハードコードゼロ
- 日本語・英語・スペイン語・タガログ語・オランダ語・デフォルトの全6言語に全UI・音声リソースを完全同期。

### 🇺🇸 English
#### ⚡ Smart Battery Announcement Suite (Fully Customizable)
- **80% Battery Protection Alert**:
  - Plays a gentle jingle and announces when battery reaches 80% to encourage unplugging and prolong battery health.
- **Low Battery Plug-in Reminder (20% & below)**:
  - Timely audible reminders when battery drops to 20%, 15%, 10%, or 5% to prevent unexpected shutdowns.
- **Charging Milestone Announcements (50%, 80%)**:
  - Hands-free audible status updates when battery crosses 50% and 80% while charging.
- **Estimated Charge Time on Plug In**:
  - Speaks estimated time remaining until full charge right upon connecting the charger.
- **Dedicated Settings & Test Button**:
  - New Smart Battery card in MainActivity with individual toggles and instant announcement preview.

#### 🔔 Hourly Chime: 30-Minute & 1-Hour Interval Toggle
- **Half-Hour Announcement Support**:
  - Option to announce the time at both 0 and 30 minutes past the hour.
- **Instant Toggle via Serena Menu**:
  - Switch between hourly and 30-minute chime directly from the Serena L-gesture menu.

#### 🌍 Strict 6-Locale Synchronization & Zero Hardcoding
- Complete parity across Japanese, English, Spanish, Tagalog, Dutch, and Default locale string resources.

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
