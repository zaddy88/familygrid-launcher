# Builds FamilyGrid Launcher without Gradle: aapt2 -> javac -> d8 -> jar -> zipalign -> apksigner
$ErrorActionPreference = "Stop"
$sdk   = "E:\Learning\android-modernization\sdk"
$proj  = "E:\Learning\android-modernization\parenthub"
$jdk   = "C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
$bt    = "$sdk\build-tools\34.0.0"
$plat  = "$sdk\platforms\android-34\android.jar"

Set-Location $proj
Remove-Item "$proj\obj","$proj\out" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force "$proj\obj" | Out-Null
New-Item -ItemType Directory -Force "$proj\out" | Out-Null

Write-Output "1) compile + link resources"
& "$bt\aapt2.exe" compile --dir "$proj\res" -o "$proj\obj\res.zip"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }
New-Item -ItemType Directory -Force "$proj\obj\gen" | Out-Null
& "$bt\aapt2.exe" link -o "$proj\out\base.apk" -I $plat --manifest "$proj\AndroidManifest.xml" "$proj\obj\res.zip" --java "$proj\obj\gen"
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Output "2) javac"
$oldPref = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& "$jdk\bin\javac.exe" -encoding utf-8 --release 11 -classpath $plat -d "$proj\obj" ((Get-ChildItem "$proj\src\dev\fluxnova\familygrid\*.java").FullName + (Get-ChildItem "$proj\obj\gen" -Recurse -Filter *.java).FullName) 2>&1 | ForEach-Object { Write-Output $_ }
$ErrorActionPreference = $oldPref
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Output "3) d8 dex"
& "$bt\d8.bat" --release --min-api 24 --lib $plat --output "$proj\out" (Get-ChildItem "$proj\obj\dev\fluxnova\familygrid\*.class").FullName
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Output "4) inject classes.dex"
& "$jdk\bin\jar.exe" -uf "$proj\out\base.apk" -C "$proj\out" classes.dex

Write-Output "5) zipalign"
& "$bt\zipalign.exe" -f 4 "$proj\out\base.apk" "$proj\out\aligned.apk"

Write-Output "6) key + sign"
if (-not (Test-Path "$proj\debug.keystore")) {
    try {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'SilentlyContinue'
        & "$jdk\bin\keytool.exe" -genkeypair -keystore "$proj\debug.keystore" -alias familygrid `
          -storepass familygrid -keypass familygrid -keyalg RSA -keysize 2048 -validity 10000 `
          -dname "CN=FamilyGrid,O=Fluxnova,C=US" 2>&1 | Out-Null
        $ErrorActionPreference = $prev
    } catch {}
}
try {
    $prev2 = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    & "$bt\apksigner.bat" sign --ks "$proj\debug.keystore" --ks-pass pass:familygrid `
      --out "$proj\out\FamilyGrid.apk" "$proj\out\aligned.apk" 2>&1 | Out-Null
    & "$bt\apksigner.bat" verify "$proj\out\FamilyGrid.apk" 2>&1 | Out-Null
    $ErrorActionPreference = $prev2
} catch {}
Write-Output "DONE: $proj\out\FamilyGrid.apk"
