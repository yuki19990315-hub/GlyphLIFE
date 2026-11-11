param(
    [switch]$InstallToPhone
)

# Builds an SDK-enabled Glyph Life APK for personal testing on Windows.
# Does not upload or redistribute Nothing's proprietary SDK or the resulting APK.
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $project

$license = 'https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/main/LICENSE.md'
$sdkUrl = 'https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/main/glyph-matrix-sdk-2.0.aar'
$sdkPath = Join-Path $project 'app\libs\glyph-matrix-sdk-2.0.aar'

Write-Host 'Glyph Life: Nothing SDK test build' -ForegroundColor Cyan
Write-Host "SDK license: $license"
Write-Host 'The SDK is closed-source, and commercial usage requires permission from Nothing.'
Write-Host 'Do not share APKs containing the SDK unless its license permits that distribution.'

if (-not (Test-Path $sdkPath)) {
    $accepted = Read-Host 'Have you read and accepted Nothing SDK terms for your own use? [yes/no]'
    if ($accepted -ne 'yes') { throw 'SDK license acceptance required before download.' }
    New-Item -ItemType Directory -Force -Path (Split-Path $sdkPath) | Out-Null
    Write-Host 'Downloading Glyph Matrix SDK from the official Nothing repository...'
    Invoke-WebRequest -Uri $sdkUrl -OutFile $sdkPath
}
if ((Get-Item $sdkPath).Length -lt 10000) { throw 'SDK AAR download appears incomplete.' }

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $studioJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJbr 'bin\java.exe')) {
        $env:JAVA_HOME = $studioJbr
        $env:Path = (Join-Path $studioJbr 'bin') + ';' + $env:Path
    } else {
        throw 'Java was not found. Install Android Studio/JDK 17+ or set JAVA_HOME.'
    }
}

if (-not $env:ANDROID_HOME) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (-not (Test-Path $defaultSdk)) {
        throw 'Android SDK not found. Install Android Studio SDK Platform 35 and Build Tools 35.'
    }
    $env:ANDROID_HOME = $defaultSdk
}

$gradleVersion = '8.14'
$gradleHome = Join-Path $env:USERPROFILE ".glyphlife-tools\gradle-$gradleVersion"
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'

if (-not (Test-Path $gradleExe)) {
    $cacheRoot = Join-Path $env:USERPROFILE '.glyphlife-tools'
    New-Item -ItemType Directory -Force -Path $cacheRoot | Out-Null
    $archive = Join-Path $cacheRoot "gradle-$gradleVersion-bin.zip"
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Host "Downloading Gradle $gradleVersion from Gradle's official distribution..."
    Invoke-WebRequest -Uri $url -OutFile $archive
    $checksum = (Invoke-WebRequest -Uri ($url + '.sha256')).Content.Trim().Split(' ')[0].ToLower()
    $actual = (Get-FileHash -Algorithm SHA256 -Path $archive).Hash.ToLower()
    if ($actual -ne $checksum) {
        Remove-Item $archive -Force
        throw 'Gradle download SHA-256 mismatch.'
    }
    Expand-Archive -Path $archive -DestinationPath $cacheRoot -Force
}
if (-not (Test-Path $gradleExe)) { throw 'Gradle could not be prepared.' }

Write-Host 'Building Glyph Life with Nothing SDK, Android API 33 minimum...'
& $gradleExe --no-daemon :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed. Inspect the errors above.' }

$apk = Join-Path $project 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw 'Expected debug APK not found.' }

Write-Host 'SDK-enabled personal-test APK built successfully:' -ForegroundColor Green
Write-Host $apk

if ($InstallToPhone) {
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    if (-not (Test-Path $adb)) { throw 'adb was not found. Install Android SDK Platform Tools.' }
    Write-Host 'Installing via ADB to the connected Android device...'
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'ADB install failed. Check USB debugging and device access.' }
    Write-Host 'Installation command succeeded. Glyph hardware display still requires a device test.'
}
