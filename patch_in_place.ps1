$apkIn = "app\build\outputs\apk\debug\app-debug.apk"
$apkOut = "app\build\outputs\apk\debug\app-debug-elf.apk"

Copy-Item $apkIn $apkOut -Force

Write-Host "Opening APK for in-place ELF 16KB patching..."
$stream = [System.IO.File]::Open($apkOut, [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite)
$bytes = New-Object byte[] $stream.Length
$stream.Read($bytes, 0, $bytes.Length) | Out-Null

$elfMagic = [byte[]](0x7f, 0x45, 0x4c, 0x46)
$patchedCount = 0

for ($idx = 0; $idx -lt ($bytes.Length - 64); $idx++) {
    if ($bytes[$idx] -eq $elfMagic[0] -and $bytes[$idx+1] -eq $elfMagic[1] -and $bytes[$idx+2] -eq $elfMagic[2] -and $bytes[$idx+3] -eq $elfMagic[3]) {
        # Check 64-bit ELF
        if ($bytes[$idx+4] -eq 2) {
            $e_phoff = [BitConverter]::ToUInt64($bytes, $idx + 32)
            $e_phentsize = [BitConverter]::ToUInt16($bytes, $idx + 54)
            $e_phnum = [BitConverter]::ToUInt16($bytes, $idx + 56)

            # Sanity check offsets
            if ($e_phoff -lt $bytes.Length - $idx -and $e_phentsize -eq 56 -and $e_phnum -lt 30) {
                for ($j = 0; $j -lt $e_phnum; $j++) {
                    $phOffset = $idx + $e_phoff + ($j * $e_phentsize)
                    if ($phOffset + 56 -le $bytes.Length) {
                        $p_type = [BitConverter]::ToUInt32($bytes, $phOffset)
                        if ($p_type -eq 1) {
                            $alignOffset = $phOffset + 48
                            $p_align = [BitConverter]::ToUInt64($bytes, $alignOffset)
                            if ($p_align -eq 4096) {
                                Write-Host "  In-place patching ELF PT_LOAD segment at index ${alignOffset}: 4096 -> 16384 (0x4000)"
                                $newAlignBytes = [BitConverter]::GetBytes([UInt64]16384)
                                [Array]::Copy($newAlignBytes, 0, $bytes, $alignOffset, 8)
                                $patchedCount++
                            }
                        }
                    }
                }
            }
        }
    }
}

$stream.Seek(0, [System.IO.SeekOrigin]::Begin) | Out-Null
$stream.Write($bytes, 0, $bytes.Length)
$stream.Close()

Write-Host "In-place patched $patchedCount ELF segment alignments."
