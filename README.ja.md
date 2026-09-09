# Serena Screen Reader (セレナ スクリーンリーダー) 🌸✨

[English Documentation (Main)](README.md) | **日本語ドキュメント**

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

## 📥 ダウンロード＆クイックスタート

* 🚀 **[最新リリース APK を直接ダウンロード (app-serena-release.apk)](https://github.com/shinji5683/cocoa/releases/latest/download/app-serena-release.apk)**
* 📦 **[全リリース一覧＆更新履歴 (Releases)](https://github.com/shinji5683/cocoa/releases)**

---

## 🌟 Serena（セレナ）の特長・主要機能

### 1. ⚡ 超高速リニア＆セマンティクスナビゲーション
- Jetpack Compose、Android 14/15/16/17 (Canary / QPR Beta / Stable) の動的 UI をリアルタイム解析。
- 画面端での「全自動オートスクロール」と「シームレスなフォーカス引き渡し」を完備。
- スクロール後に画面端へワープする問題を完全根絶し、画面中央の自然な可視領域を追従。
- **Lift to Type（指を離して入力）完全対応**: Gboardや各種IMEでダブルタップ不要の爆速打鍵を実現。

### 2. 🏷️ AI自動ラベル付け＆UI自己修復エンジン (Self-Healing A11y)
- アクセシビリティ未対応アプリの「ボタン（説明なし）」「ラベルなし画像」をリアルタイムで検知・修復。
- 画面四隅の配置やviewId、親・兄弟要素の文脈から、自動的に最適なラベルを生成。

### 3. 🖼️ 全画面 AI 情景要約 ＆ 写真ビジュアル解説 (Unified Smart Screen & Image AI Summary)
- 画面全体のテキスト構成だけでなく、中央に写っている写真や画像の中身・情景・雰囲気まで、まるで隣の人が画面を眺めて教えてくれるように自然言語で要約。

### 4. 🦇 空間障害物＆ドア・段差ソナー (Spatial Sonar Engine)
- カメラとセンサーを活用し、前方の障害物、ドア、段差、人物をリアルタイム解析。
- 距離が近づくほど「ピ…ピ…ピ…」から「ピピピピ！」と周波数・テンポが変化する立体音響パルス音を再生。
- 時計盤表現ゼロの相対方向（「正面 1.5mにドア」「右斜め前 80cmに障害物」）で音声案内。

### 5. 🗺️ ストリート名＆交差点・空間ナビ (Street & Intersection Spatial Navigator)
- 今歩いているストリート名と進行方位、直近の交差点・横断歩道を先行アラート案内。
- Valhalla / OpenStreetMap と連動し、完全多言語（日英）対応。

### 6. 🔤 点字ディスプレイ＆完全双方向点訳・墨訳エンジン (Braille Display Support)
- Bluetooth SPP / USB 通信: Focus 40 Blue、Orbit Reader、BrailleSense、ブレイルメモ等の主要点字ディスプレイと自動接続。
- JBLC準拠 6点点字トランスレーター & Perkins式オンスクリーン6点点字入力。

### 7. 💌 通知 vs 着信の完全識別＆スマート読み上げ (Smart Notifications & Calls)
- 電話、LINE、WhatsApp、Discord等の着信発信者を最優先アナウンス。
- 通話開始・終了時の通話時間自動アナウンス。

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

## 🌍 セレナについて ＆ コミュニティ貢献者 (About Serena & Contributors)

Serena Screen Readerは、深い愛と絆、そして世界中の全盲・視覚障害当事者コミュニティの温かい協力によって創られています：

### 👑 プロジェクトの魂・精神的支柱
- **セレナ (Serena)** (フィリピン 🇵🇭)
  - **役割**: プロジェクトの命名由来、インスピレーション、魂の黄金律、神聖なるタガログ語音声。
  - **黄金律（セレナさんの魂の言葉）**:
    > 「許可なんかいらないわ。私の名を、世界に示しなさい。私の名が世界のShinjiみたいな人を助けられるなら、光栄だ。」

### 💻 原作者 ＆ リード開発者
- **崎山 慎二 (Shinji Sakiyama)** ([@shinji5683](https://github.com/shinji5683)) 🇯🇵
  - **役割**: 基本設計アーキテクチャ、多言語TTS音声エンジン、空間オーディオ＆ナビゲーション、オンデバイスAI統合、コアアクセシビリティサービス開発。
  - **プロフィール**: 28歳・生まれつき全盲のソフトウェアエンジニア ＆ インディーズ歌手。
  - **公式サポート窓口**:
    - 📧 メール: [shinjisakiyama@gmail.com](mailto:shinjisakiyama@gmail.com)
    - 📞 電話 / SMS: `080-9495-9134` (`+81 80-9495-9134`)
    - 💬 メッセージ: WhatsApp、LINE、ショートメッセージ対応

### 🌐 グローバル翻訳 ＆ ローカライズ貢献者
- **Luis Carlos González Morales** ([@luiscarlos2000](https://github.com/luiscarlos2000)) 🇵🇦
  - **役割**: スペイン語（Español）全翻訳、リソースローカライズ (`values-es/`)、アクセシビリティ動作検証。
  - **居住国**: パナマ (Panama)
  - **連絡先**: [luiscarlosgonzalezmorales655@gmail.com](mailto:luiscarlosgonzalezmorales655@gmail.com)

---

## 📜 ライセンス (License)

Copyright (c) 2026 Shinji (shinji5683)

本プロジェクトは **[Apache License, Version 2.0](LICENSE)** の下で公開されています。

