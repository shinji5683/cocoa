# Serena Screen Reader (セレナ スクリーンリーダー) 🌸✨

> **次世代の超高速・高知能・完全アクセシブルな Android スクリーンリーダー**  
> 全盲のエンジニアと AI がゼロから共同設計した、真に寄り添うアクセシビリティ体験。

[![Android CI](https://img.shields.io/badge/Android-11%2B%20(API%2030%2B%20~%20API%2036%2B)-brightgreen.svg)](https://developer.android.com)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)

---

## 🌟 Serena（セレナ）の特長

1. **⚡ 超高速リニア＆セマンティクスナビゲーション**
   - Jetpack Compose、Android 14/15/16/17 (Canary / QPR) の動的 UI をリアルタイム解析。
   - 画面端での「全自動オートスクロール」と「シームレスなフォーカス引き渡し」を完備。
   - スクロール後に画面端へワープする Home/End バグを完全根絶し、画面中央の自然な可視領域を追従。

2. **🖐️ TalkBack 100% 準拠＋拡張マルチフィンガージェスチャー**
   - 1本指・2本指・3本指のマルチタッチジェスチャーを完全サポート。
   - Pixel Launcher や各社ホームアプリの `snapToPage`（ページめくり）に完全適合。
   - ロック画面（Keyguard Bouncer）での 2本指上スワイプによる即時 PIN キーパッド探索＆フォーカス。

3. **👁️ リアルタイム実況 AI カメラ＆音声アシスタント**
   - 👈 **3本指左スワイプ**: Serena AI 音声アシスタント即時起動。
   - 👉 **3本指右スワイプ**: リアルタイム実況 AI カメラ（周囲の物体・文字・情景をリアルタイムに音声実況）即時起動。

4. **🛡️ 堅牢な Direct Boot ＆ 保護ストレージ連動**
   - `android:directBootAware="true"` により、端末起動直後の初回アンロック前（Direct Boot モード）から完璧に音声ガイダンスが動作。

5. **🔤 高精度フォネティック漢字詳細読みエンジン**
   - 1文字ごとの漢字詳細読み（例：「新」＝「新聞のシン」、「治」＝「明治のジ」）を内蔵辞書で瞬時に解説。

---

## 🖐️ ジェスチャー一覧表 (Gesture Reference)

| ジェスチャー | 動作・機能 |
| :--- | :--- |
| **1本指 右スワイプ** | 次の項目へフォーカス移動（末尾到達時は次ページへ自動スクロール） |
| **1本指 左スワイプ** | 前の項目へフォーカス移動（先頭到達時は前ページへ自動スクロール） |
| **1本指 上スワイプ** | 選択中の読み上げコントロール（文字/単語/見出し等）に従って前へ移動 |
| **1本指 下スワイプ** | 選択中の読み上げコントロール（文字/単語/見出し等）に従って次へ移動 |
| **1本指 ダブルタップ** | フォーカス中要素のクリック（タップ実行） |
| **1本指 ダブルタップ＆ホールド** | アクション・ショートカットメニュー表示 |
| **2本指 シングルタップ** | 読み上げの一時停止 / 再開 |
| **2本指 ダブルタップ** | 通話の応答・終了 / メディアの再生・一時停止 |
| **2本指 左スワイプ** | 次のページへ（横スクロール進む / ホーム画面めくり） |
| **2本指 右スワイプ** | 前のページへ（横スクロール戻る / ホーム画面めくり） |
| **2本指 上スワイプ** | ロック画面時：画面ロック解除（PIN入力へ） / アプリ内：縦スクロール次へ |
| **2本指 下スワイプ** | 縦スクロール前へ（上の内容を表示） |
| **3本指 左スワイプ** | **Serena AI 音声アシスタント即時起動** 🤖🎙️ |
| **3本指 右スワイプ** | **リアルタイム実況 AI カメラ即時起動** 📸✨ |

---

## 🛠️ ビルド手順 (Build & Installation)

### 前提条件 (Prerequisites)
- **OS**: Windows / macOS / Linux
- **JDK**: Java 17 以上
- **Android SDK**: API 34 (Android 14) または API 35 (Android 15)
- **ADB**: Android Debug Bridge が利用可能であること

### 1. リポジトリのクローン
```bash
git clone https://github.com/shinji5683/cocoa.git
cd cocoa
```

### 2. ユニットテストの実行
```bash
# Windows
.\gradlew.bat testDebugUnitTest

# macOS / Linux
./gradlew testDebugUnitTest
```

### 3. デバッグ APK のビルド
```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
ビルドされた APK は `app/build/outputs/apk/debug/app-debug.apk` に生成されます。

### 4. 実機へのインストール＆アクセシビリティ自動有効化 (PowerShell)
```powershell
# 自動ビルド・インストール・サービス有効化スクリプト
.\build_apk.ps1
```
手動でインストールおよび有効化する場合:
```bash
# APK のインストール
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Serena スクリーンリーダー サービスの自動有効化
adb shell settings put secure enabled_accessibility_services com.shinji.serena/.SerenaScreenReaderService
adb shell settings put secure accessibility_enabled 1

# メイン設定画面の起動
adb shell am start -n com.shinji.serena/.MainActivity
```

---

## 🤝 謝辞＆ライセンス帰属 (Acknowledgements & Attributions)

- **あやめキーボード (Ayame Keyboard / IME)**:
  - 本スクリーンリーダーの漢字詳細読み（フォネティック読み）および IME 連携アーキテクチャは、視覚障害者向け高機能日本語入力アプリ「あやめキーボード」の思想およびオープンソース資産（MIT License）を尊重・準拠して開発されています。
  - 心より感謝申し上げます！🌸✨

---

## 📄 ライセンス (License)

本プロジェクトは **Apache License 2.0** および **MIT License** のデュアルライセンス（互換ライセンス）のもとで公開されています。  
詳細は [LICENSE](LICENSE) ファイルをご確認ください。

Copyright (c) 2026 Shinji ([@shinji5683](https://github.com/shinji5683))
