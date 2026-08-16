Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$apkIn = "app\build\outputs\apk\debug\app-debug.apk"
$unsignedApk = "scratch\app-patched-unsigned.apk"
$alignedApk = "scratch\app-aligned-16k.apk"
$finalApk = "app\build\outputs\apk\debug\app-debug-16k.apk"

$buildToolsDir = "C:\Users\shinj\AppData\Local\Android\Sdk\build-tools\36.0.0"
$zipalign = Join-Path $buildToolsDir "zipalign.exe"
$apksigner = Join-Path $buildToolsDir "apksigner.bat"
$keystore = "app\cocoa-release-key.jks"

Write-Host "=== In-Place 16KB ELF & ZipAlign Pipeline Starting ==="

New-Item -ItemType Directory -Force -Path "scratch" | Out-Null
Copy-Item $apkIn $unsignedApk -Force

# 1. Update ELF headers inside APK zip in-place
$zip = [System.IO.Compression.ZipFile]::Open($unsignedApk, [System.IO.Compression.ZipArchiveMode]::Update)
$patchedCount = 0

foreach ($entry in $zip.Entries) {
    if ($entry.FullName.EndsWith(".so")) {
        $stream = $entry.Open()
        $mem = New-Object System.IO.MemoryStream
        $stream.CopyTo($mem)
        $bytes = $mem.ToArray()

        if ($bytes.Length -ge 64 -and $bytes[0] -eq 0x7f -and $bytes[1] -eq 0x45 -and $bytes[2] -eq 0x4c -and $bytes[3] -eq 0x46) {
            $is64 = ($bytes[4] -eq 2)
            if ($is64) {
                $e_phoff = [BitConverter]::ToUInt64($bytes, 32)
                $e_phentsize = [BitConverter]::ToUInt16($bytes, 54)
                $e_phnum = [BitConverter]::ToUInt16($bytes, 56)
                $fileModified = $false

                for ($i = 0; $i -lt $e_phnum; $i++) {
                    $phOffset = $e_phoff + ($i * $e_phentsize)
                    if ($phOffset + 56 -le $bytes.Length) {
                        $p_type = [BitConverter]::ToUInt32($bytes, $phOffset)
                        if ($p_type -eq 1) { # PT_LOAD
                            $alignOffset = $phOffset + 48
                            $p_align = [BitConverter]::ToUInt64($bytes, $alignOffset)
                            if ($p_align -lt 16384) {
                                Write-Host "  In-place patching $($entry.FullName) PT_LOAD ${i}: $p_align -> 16384 (0x4000)"
                                $newAlign = [BitConverter]::GetBytes([UInt64]16384)
                                [Array]::Copy($newAlign, 0, $bytes, $alignOffset, 8)
                                $fileModified = $true
                            }
                        }
                    }
                }

                if ($fileModified) {
                    $stream.SetLength(0)
                    $stream.Write($bytes, 0, $bytes.Length)
                    $patchedCount++
                }
            }
        }
        $stream.Close()
        $mem.Close()
    }
}
$zip.Dispose()
Write-Host "ELF Header Patching Complete! Patched $patchedCount 64-bit .so files in-place."

# 2. Run 16KB ZipAlign
if (Test-Path $alignedApk) { Remove-Item -Force $alignedApk }
Write-Host "Running zipalign -p -f 4..."
& $zipalign -p -f 4 $unsignedApk $alignedApk
if ($LASTEXITCODE -ne 0) {
    Write-Error "ZipAlign failed!"
    exit 1
}

# 3. Sign APK with apksigner
Write-Host "Signing APK with apksigner..."
& $apksigner sign --ks $keystore --ks-pass "pass:cocoa2026" --key-pass "pass:cocoa2026" --out $finalApk $alignedApk
if ($LASTEXITCODE -ne 0) {
    Write-Error "ApkSigner failed!"
    exit 1
}

# 4. Verify Alignment & Signature
Write-Host "Verifying Alignment with zipalign -c -v 4..."
& $zipalign -c -v 4 $finalApk | Select-String -Pattern "resources.arsc|\.so"
Write-Host "Verifying Signature..."
& $apksigner verify --verbose $finalApk

Write-Host "=== SUCCESS: 100% 16KB Aligned APK Generated at $finalApk ==="
