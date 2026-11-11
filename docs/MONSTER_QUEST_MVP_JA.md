# Monster Quest（独立APK）MVP仕様

## 構成
同じGitHubリポジトリの`monsterquest/`は独立したAndroidアプリモジュール。
アプリIDは`dev.glyphlife.monsterquest`。Glyph Lifeとは別APKとしてインストール。

## 初期ゲーム機能
- オフラインで4×4ランダム部屋を5階層探索。上下左右ボタンで1セルずつ移動。
- 必ずスタートから敵まで到達できる経路を確保する。
- 敵（モコ、フレア、アクア、ギア、ルーン）と戦闘し、攻撃・属性魔法・捕獲できる。
- 水→火、火→草、草→水、光↔闇の属性優位。ゲームロジックにAI不使用。
- HP、撃破数、捕獲リスト、冒険報酬をローカルに保存。
- Glyph Life未導入時は体験用ペットでプレイ可能。

## 2アプリ間連携（実装）
1. Glyph Life「連携」から「ペットデータを共有」を押す。
2. Android共有先としてMonster Questを選択（`ACTION_SEND/application/json`）。
3. Monster QuestはJSON v1のUUID、属性、レベル、13×13データを検証後に受け取る。
4. 1体の相棒で5部屋探索。途中終了・クリア後、報酬を確定。
5. 「報酬をGlyph Lifeへ送る」からJSONを共有（同じイベントIDを繰り返し利用）。
6. Glyph Lifeは受信したイベントをペットIDで照合し、経験値・幸福度を反映し二重受取を禁止する。

## 注意
- Monster Questの実際の画面・APK動作は端末未検証。自動CIはKotlin/JVMの単体テストとAPKビルドが可能。
- AOD / 実Glyph表示はGlyph Life側専用。
- Monster Questの敵は手続き生成で、生成AIモデルや画像生成はまだ未導入。
- 競争ランキングなど改ざん耐性が必要なサービスに現在のJSON交換方式を使わない。
