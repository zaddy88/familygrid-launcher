# Creates the Google Play UPLOAD key for FamilyGrid. Run this ONCE, yourself, in a terminal:
#     powershell -ExecutionPolicy Bypass -File tools\new-release-key.ps1
#
# - keytool will ask you for a password and your name/organisation. Choose a strong password
#   and store it in a password manager. Nobody else (and no AI agent) needs to see it.
# - The key is saved OUTSIDE this repository so it can never be committed.
# - BACK IT UP (e.g. a USB stick + cloud drive). With Play App Signing, Google can reset a lost
#   upload key, but it is a slow manual process.
$ErrorActionPreference = "Stop"
$jdk = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
$dir = if ($env:FAMILYGRID_KEY_DIR) { $env:FAMILYGRID_KEY_DIR } else { "E:\Learning\android-modernization\familygrid-release" }
$ks  = Join-Path $dir "familygrid-upload.jks"

if (Test-Path $ks) { Write-Output "A key already exists at $ks - not overwriting it."; exit 1 }
New-Item -ItemType Directory -Force $dir | Out-Null

& "$jdk\bin\keytool.exe" -genkeypair -v -keystore $ks -storetype PKCS12 -alias upload `
    -keyalg RSA -keysize 4096 -validity 10000

if ($LASTEXITCODE -eq 0) {
    Write-Output ""
    Write-Output "Upload key created: $ks"
    Write-Output "Next: back it up, then build the bundle with:  .\build-aab.ps1"
}
