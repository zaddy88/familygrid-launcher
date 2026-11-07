# Builds the Google Play bundle (out\FamilyGrid.aab) without Gradle and signs it with the upload key.
#
#   .\build-aab.ps1
#
# Key location:  $env:FAMILYGRID_KEYSTORE  (default: ..\familygrid-release\familygrid-upload.jks)
# Key alias:     $env:FAMILYGRID_KEY_ALIAS (default: upload)
# Password:      $env:FAMILYGRID_KS_PASS, otherwise you are asked for it (input is hidden).
#                The password is never written to disk or passed on the command line.
$ErrorActionPreference = "Stop"
$sdk   = "E:\Learning\android-modernization\sdk"
$proj  = "E:\Learning\android-modernization\parenthub"
$jdk   = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
$bt    = "$sdk\build-tools\36.0.0"
$plat  = "$sdk\platforms\android-36\android.jar"
$btool = "E:\Learning\android-modernization\tools\bundletool-all-1.18.3.jar"
$ks    = if ($env:FAMILYGRID_KEYSTORE) { $env:FAMILYGRID_KEYSTORE } else { "E:\Learning\android-modernization\familygrid-release\familygrid-upload.jks" }
$alias = if ($env:FAMILYGRID_KEY_ALIAS) { $env:FAMILYGRID_KEY_ALIAS } else { "upload" }

if (-not (Test-Path $ks)) { throw "No upload key at $ks. Create one first: tools\new-release-key.ps1" }
if (-not (Test-Path $btool)) { throw "bundletool not found at $btool (download it from github.com/google/bundletool/releases)" }

Write-Output "== 1) normal build (compiles code + resources)"
& "$proj\build.ps1" | Out-Null
Set-Location $proj

Write-Output "== 2) link resources in bundle (proto) format"
$b = "$proj\obj\bundle"
Remove-Item $b -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $b | Out-Null
& "$bt\aapt2.exe" link --proto-format -o "$b\proto.zip" -I $plat --manifest "$proj\AndroidManifest.xml" "$proj\obj\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 proto link failed" }

Write-Output "== 3) arrange the base module (manifest/, dex/, res/, resources.pb)"
Expand-Archive "$b\proto.zip" "$b\extract"
$base = "$b\base"
New-Item -ItemType Directory -Force "$base\manifest","$base\dex" | Out-Null
Move-Item "$b\extract\AndroidManifest.xml" "$base\manifest\AndroidManifest.xml"
Move-Item "$b\extract\resources.pb" "$base\resources.pb"
if (Test-Path "$b\extract\res") { Move-Item "$b\extract\res" "$base\res" }
Copy-Item "$proj\out\classes.dex" "$base\dex\classes.dex"
& "$jdk\bin\jar.exe" cMf "$b\base.zip" -C $base .

Write-Output "== 4) bundletool build-bundle"
Remove-Item "$proj\out\FamilyGrid.aab" -ErrorAction SilentlyContinue
& "$jdk\bin\java.exe" -jar $btool build-bundle --modules="$b\base.zip" --output="$proj\out\FamilyGrid.aab"
if ($LASTEXITCODE -ne 0) { throw "bundletool build-bundle failed" }
& "$jdk\bin\java.exe" -jar $btool validate --bundle="$proj\out\FamilyGrid.aab" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "bundle validation failed" }

Write-Output "== 5) sign with the upload key ($alias)"
$setHere = $false
if (-not $env:FAMILYGRID_KS_PASS) {
    $sec = Read-Host -AsSecureString "Upload key password"
    $env:FAMILYGRID_KS_PASS = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
    $setHere = $true
}
try {
    & "$jdk\bin\jarsigner.exe" -keystore $ks -storepass:env FAMILYGRID_KS_PASS -keypass:env FAMILYGRID_KS_PASS `
        -sigalg SHA256withRSA -digestalg SHA-256 "$proj\out\FamilyGrid.aab" $alias | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "jarsigner failed (wrong password or alias?)" }
} finally {
    if ($setHere) { Remove-Item Env:FAMILYGRID_KS_PASS }
}
& "$jdk\bin\jarsigner.exe" -verify "$proj\out\FamilyGrid.aab" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "signature verification failed" }

$size = [math]::Round((Get-Item "$proj\out\FamilyGrid.aab").Length / 1KB)
Write-Output "DONE: $proj\out\FamilyGrid.aab ($size KB) - upload this file in Play Console"
