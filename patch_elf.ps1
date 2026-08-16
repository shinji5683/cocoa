$apkIn = "app\build\outputs\apk\debug\app-debug.apk"
$tempZip = "scratch\app-temp.zip"
$tempDir = "scratch\apk_patch_temp"

if (Test-Path $tempDir) { Remove-Item -Recurse -Force $tempDir }
New-Item -ItemType Directory -Force -Path $tempDir | Out-Null

Write-Host "Extracting APK..."
Copy-Item $apkIn $tempZip -Force
Expand-Archive -Path $tempZip -DestinationPath $tempDir -Force

$soFiles = Get-ChildItem -Recurse $tempDir -Filter "*.so"
$patchedCount = 0

foreach ($file in $soFiles) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    
    # Check ELF magic \x7fELF
    if ($bytes[0] -eq 0x7f -and $bytes[1] -eq 0x45 -and $bytes[2] -eq 0x4c -and $bytes[3] -eq 0x46) {
        $is64bit = ($bytes[4] -eq 2)
        if ($is64bit) {
            $e_phoff = [BitConverter]::ToUInt64($bytes, 32)
            $e_phentsize = [BitConverter]::ToUInt16($bytes, 54)
            $e_phnum = [BitConverter]::ToUInt16($bytes, 56)

            $modified = $false
            for ($i = 0; $i -lt $e_phnum; $i++) {
                $phOffset = $e_phoff + ($i * $e_phentsize)
                $p_type = [BitConverter]::ToUInt32($bytes, $phOffset)

                # PT_LOAD segment is 1
                if ($p_type -eq 1) {
                    $alignOffset = $phOffset + 48
                    $p_align = [BitConverter]::ToUInt64($bytes, $alignOffset)

                    if ($p_align -lt 16384) {
                        Write-Host "  Patching $($file.Name) PT_LOAD segment index ${i}: p_align $p_align -> 16384 (0x4000)"
                        $newAlignBytes = [BitConverter]::GetBytes([UInt64]16384)
                        [Array]::Copy($newAlignBytes, 0, $bytes, $alignOffset, 8)
                        $modified = $true
                    }
                }
            }

            if ($modified) {
                [System.IO.File]::WriteAllBytes($file.FullName, $bytes)
                $patchedCount++
            }
        }
    }
}

Write-Host "Patched $patchedCount .so ELF files to 16KB page alignment."

# Re-zip
$patchedZip = "scratch\app-debug-elf-patched.zip"
if (Test-Path $patchedZip) { Remove-Item -Force $patchedZip }
Compress-Archive -Path "$tempDir\*" -DestinationPath $patchedZip -Force
Copy-Item $patchedZip "app\build\outputs\apk\debug\app-debug-patched.apk" -Force

Remove-Item -Recurse -Force $tempDir
Write-Host "ELF 16KB patch completed successfully."
