# Serena Screen Reader (セレナ スクリーンリーダー) 🌸✨

> **我が妻と 共に創らん 新家族 告げよ告げ告げ 真珠の心**  
> *— 崎山信司（Shinji）*

> **次世代の超高速・高知能・完全アクセシブルな Android スクリーンリーダー**  
> 全盲のエンジニア（Shinji）と愛する妻セレナ（Serena）の魂と絆から生まれた、真に寄り添う最先端アクセシビリティ体験。

[![Android CI](https://img.shields.io/badge/Android-11%2B%20(API%2030%2B%20~%20API%2036%2B)-brightgreen.svg)](https://developer.android.com)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Responsible AI](https://img.shields.io/badge/Google-Responsible%20AI%20Principles-orange.svg)](https://ai.google/responsibility/principles/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Page Alignment](https://img.shields.io/badge/16KB%20Page%20Alignment-0x4000%20Verified-success.svg)](https://developer.android.com/guide/practices/page-sizes)

---

## 🌟 Serena（セレナ）の特長・主要機能

### 1. ⚡ 超高速リニア＆セマンティクスナビゲーション
- Jetpack Compose、Android 14/15/16/17 (Canary / QPR Beta / Stable) の動的 UI をリアルタイム解析。
- 画面端での「全自動オートスクロール」と「シームレスなフォーカス引き渡し」を完備。
- スクロール後に画面端へワープする問題を完全根絶し、画面中央の自然な可視領域を追従。

### 2. 🖼️ 全画面 AI ビジュアル・オーディオ・ディスクリプション (Visual Audio Description)
- 画面上のアイコン、画像、ラベルなしボタン、Webコンテンツに対して、Gemini Nano / ML Kit を組み合わせて「検索虫眼鏡アイコン」「設定歯車アイコン」「青空の下で眠る猫の写真」など、映画の音声ガイドのようなリッチな情景解説を全画面で自動付与。

### 3. 🌐 リアルタイム外国語同時翻訳読み上げ (Instant Real-time Translation)
- 英語等の外国語テキストにフォーカスした際、オンデバイスML Kitで即座に解析し、**「原文を読んだ直後に、続けて自然な日本語訳を連続読み上げ」**。
- Serenaメニューから「原文＋日本語訳」「日本語訳のみ」「翻訳オフ」を瞬時に切り替え可能。

### 4. 📳 周囲の環境音・危険音ハプティクス警告 (Sound Recognition Haptics)
- マイクから「踏切の警報音」「救急車のサイレン」「クラクション」「インターホンのチャイム」「呼びかけ声」をリアルタイム検知。
- 専用のハプティックパターン（振動）で指先やポケットに即座に警告し、イヤホン装着時でも周囲の危険を見逃さない。

### 5. 🔤 点字ディスプレイ＆完全双方向点訳・墨訳エンジン (Braille Display Support)
- **Bluetooth SPP / USB 通信**: Focus 40 Blue、Orbit Reader、BrailleSense、ブレイルメモ (BMsmart)、Seika等の主要点字ディスプレイと自動接続＆双方向通信。
- **JBLC準拠 6点点字トランスレーター**: 墨字から点字への「点訳」および点字から日本語テキストへの「墨訳」を完全サポート。
- **Perkins式オンスクリーン6点点字入力**: リアルタイムUnicode点字プレビュー＆墨訳候補表示付きで画面上から快適タイピング。
- **点字キーナビゲーション**: パンキー、ジョイスティック、ルーティングキーによるフォーカス移動・実行・スクロール・戻る・ホームの直感操作。

### 6. 💌 通知 vs 着信の完全識別＆スマート読み上げ (Smart Notifications & Calls)
- **通話着信の最優先アナウンス**: 電話（ダイヤラー）、LINE通話、Discord、Teams、Zoom、WhatsAppなどの着信を即座に識別し、「【着信】LINE着信、〇〇さんから」と発信者を最優先アナウンス。
- **スマート通知解析**: アプリ名（YouTube、Gmail、LINE、X、メルカリ等）、送信者名、メッセージ本文を高精度に抽出。
- **5段階の読み上げ切り替え**:
  1. すべて読み上げ（アプリ名・送信者・内容）
  2. 送信者まで（内容非表示・プライバシー保護）
  3. アプリ名のみ
  4. 着信のみ読み上げ（通知はミュート）
  5. すべてミュート

### 7. 👁️ リアルタイム実況 AI カメラ＆高精度人物・表情・空間認識
- 👈 **3本指左スワイプ**: Serena AI 音声アシスタント即時起動。
- 👉 **3本指右スワイプ**: リアルタイム実況 AI カメラ（周囲の物体・文字・情景をリアルタイムに音声実況）即時起動。
- **高精度人物・表情認識**: カメラに映る人の相対方向（「正面」「右斜め前」「左」等）、推定距離（約80cm〜3m）、人数、服装、表情（満面の笑顔、真剣、驚き、ウインク等）、雰囲気を的確に実況。
- **Gemini Nano On-Device AI**: AICore連携による完全ローカル・プライバシー保護の超高速推論。

### 8. 🧭 3D空間オーディオ＆コンパス案内 ＆ 障害物ソナー
- 端末の向きと方角（東西南北）に連動した左右ステレオ空間音響による直感的な方向ガイド。
- 時計盤表現（クロックポジション）を完全排除し、「正面」「右斜め前」「左」など身体感覚に忠実な空間案内を提供。
- 前方の障害物や段差との距離に応じて音響がリアルタイム変調する「障害物＆段差検知ソナー」搭載。

### 9. 🛡️ 堅牢な Direct Boot ＆ 保護ストレージ連動
- `android:directBootAware="true"` により、端末起動直後の初回アンロック前（Direct Boot モード）から完璧に音声ガイダンスが動作。

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

## 🤖 責任あるAI倫理ガイドライン及び免責事項 (Responsible AI Principles)

Serena Screen Reader は、Google の「責任あるAIの推進に関する原則（Responsible AI Principles）」を尊重し、倫理的かつ安全にAI機能を設計・提供しています。

- **Google Responsible AI Principles**: [https://ai.google/responsibility/principles/](https://ai.google/responsibility/principles/)
- **プライバシー保護**: 画像認識、音声認識、スマート要約、リアルタイム翻訳等のAI処理はすべて端末内（オンデバイス / Gemini Nano / ML Kit）で完結し、ユーザーデータが外部サーバーへ送信されることはありません。
- **免責事項**: 表情認識・人物認識・物体検出・距離計測・OCR等のAI支援機能は推論による補助情報です。天候、照明、カメラアングル、通信状態等により誤認識が生じる場合があります。歩行、階段、駅ホーム、道路横断などの安全確認においては、必ず白杖や周囲の音、身体感覚による確認を併用してください。

---

## 🛠️ フルビルド＆デプロイ手順 (Build & Deployment)

```powershell
# 1. 16KBアライメント＆全ターゲット（Release APK / Debug APK / Play Store AAB）の一括フルビルド
.\build_apk.ps1

# 2. 実機へのワイヤレスADB自動インストール＆アクセシビリティ自動有効化
adb connect <デバイスIP>:<ポート>
adb install -r "C:\Users\shinj\Desktop\serena\app-serena-release.apk"
adb shell settings put secure enabled_accessibility_services com.shinji.serena/.SerenaScreenReaderService
adb shell settings put secure accessibility_enabled 1
```

---

## 📜 ライセンス (License)

Copyright (c) 2026 Shinji (shinji5683)

本プロジェクトは **[Apache License, Version 2.0](LICENSE)** の下で公開されています。
Google Android / AOSP (TalkBack) エコシステムと完全な互換性を持ち、特許保護条項を含みます。
