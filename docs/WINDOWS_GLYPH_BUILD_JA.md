# Windows PCでNothing Phone (4a) Pro用APKを作る

目的: 所有するNothing Phone (4a) ProでGlyph Lifeの背面LEDを実際に検証するための個人テストAPKを作る。

## 事前条件
- Windows 10/11、Android StudioまたはAndroid SDK Platform 35 + Build Tools 35、JDK 17以上。
- USBインストールする場合はAndroid Platform Tools（adb）と端末のUSBデバッグ。
- Nothing公式SDK EULAを確認。SDKは非公開ソースで、再配布・商用利用に制限がある。
- SDK付きのGlyph LifeはAPIレベル33以上が必要。SDKなしビルドはAPI29以上。

## 使い方
PowerShellからリポジトリ直下で実行:

```powershell
.\scripts\build_glyph_windows.ps1
```

スクリプトは公式NothingリポジトリからAARを取得し、公式Gradle 8.14配布をSHA-256検証後にセットアップしてデバッグAPKを作る。

端末にUSBでインストールも行うなら:

```powershell
.\scripts\build_glyph_windows.ps1 -InstallToPhone
```

APKは `app/build/outputs/apk/debug/app-debug.apk`。他人へ配布しない。SDKのライセンスを遵守する。

## 注意事項
- このスクリプトはGitHub ActionsのSDKコンパイル検証から進めるための手順。Windows実機での実行・APKインストールはまだ未検証。
- 端末で動作確認するまで、Glyphの実表示、AOD設定での検出、動作FPS、NFCリング読み取りを成功と判定しない。
- 過去のSDKなしデバッグAPKと同じアプリIDを使うので、署名が異なる環境では上書きできない場合がある。その場合は保存データをバックアップしてから再インストールする。
