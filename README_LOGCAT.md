# Serena Screen Reader - Logcat Utility Document / ログキャッチツール説明書

English and Japanese documentation for the logcat tool.
このドキュメントは、スクリーンリーダー開発およびテスト用のログ取得ツール（`Android_Logcat.ps1`）の解説書です。

---

## 🇺🇸 English Guide

### 📌 Overview
This utility script helps you capture and save Android logcat logs specifically filtered for the Serena Screen Reader application.
It supports multiple filtering modes and automatically saves logs to a file when stopped.

### 🚀 How to Run
Open PowerShell in the project root directory and run:
```powershell
powershell -File .\run_logcat.ps1
```

### ⚙️ Log Modes
When launched, you can choose from the following 3 modes:
1. **`[1] Default Mode`**: Captures only the core accessibility service and gesture dispatcher logs. Recommended for daily testing and checking basic gesture responses without system noise.
2. **`[2] Error Mode`**: Filters only for `Error` and `Fatal` level logs related to `serena`. Perfect for tracking down crashes and critical bugs.
3. **`[3] Full Mode`**: Captures all logs from the `serena` application, regardless of the tags.

### 💾 Auto-Save on Stop (Ctrl+C)
- Pressing **`Ctrl+C`** will stop the logcat session.
- Once stopped, it automatically saves the captured logs into a file in the current directory.
- **Filename Format**: `{ModeName}_yyyyMMdd-HHmmss.log`
  - *Example*: `DefaultMode_20260823-093000.log`
  - *Note*: A hyphen `-` is intentionally inserted between the date and time to ensure compatibility and ease of reading for screen readers (like NVDA).
- It performs synchronous disk writes to guarantee file completeness before exiting.

---

## 🇯🇵 日本語ガイド

### 📌 概要
このツールは、Serenaスクリーンリーダーアプリに関連するデバッグログを簡単かつ綺麗に取得・保存するためのPowerShellスクリプトです。
余計なシステムログをフィルタリングして、必要な情報だけを効率的に集めることができます。

### 🚀 起動方法
プロジェクトのルートディレクトリでPowerShellを開き、以下のコマンドを実行してください：
```powershell
powershell -File .\run_logcat.ps1
```

### ⚙️ ログの3つのモード
スクリプトを起動すると、どのログを取得するか選択メニューが表示されます：
1. **`[1] Default Mode`**: スクリーンリーダーの本体（`SerenaScreenReaderService`）とジェスチャー判定（`SerenaGestureDispatcher`）の基本ログだけを取得します。余計なシステムノイズがないため、日常のジェスチャー動作テストに一番おすすめです。
2. **`[2] Error Mode`**: アプリ（`serena`）で発生したエラー（Error）や深刻なバグ（Fatal）のログのみを狙い撃ちで取得します。アプリがクラッシュした時の原因特定に役立ちます。
3. **`[3] Full Mode`**: アプリ全体のすべてのログ（どのタグから出力されたものでも）を取得します。

### 💾 Ctrl+C での自動保存機能と安心設計
- ログの取得中に **`Ctrl+C`** を押すとログの読み込みが停止します。
- ストップした瞬間に、それまでに画面に流れたログをカレントディレクトリにファイル保存します。
- **ファイル名**: `{モード名}_yyyyMMdd-HHmmss.log`
  - *例*: `DefaultMode_20260823-093000.log`
  - *アクセシビリティ対応*: 日付と時間の間には `-` を挟んでいるため、NVDAなどのスクリーンリーダーが日付の数字を繋げて読み間違えることがないように親切設計にしています。
- プログラムがディスクへの書き込み完了（同期書き込み）を100%保証してからメッセージを出して終了するため、ログが途中で壊れたりする心配もありません。

## 作成に使用したもの

このツールは、AIテクノロジー、およびPowerShell7Xを使用して作成されています。