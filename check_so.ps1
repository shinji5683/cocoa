Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead("app/build/outputs/apk/debug/app-debug.apk")
foreach ($entry in $zip.Entries) {
    if ($entry.FullName.EndsWith(".so")) {
        Write-Host "Found SO in APK: $($entry.FullName)"
        $stream = $entry.Open()
        $mem = New-Object System.IO.MemoryStream
        $stream.CopyTo($mem)
        $bytes = $mem.ToArray()
        $stream.Close()
        $mem.Close()
        if ($bytes[0] -eq 0x7f -and $bytes[1] -eq 0x45 -and $bytes[2] -eq 0x4c -and $bytes[3] -eq 0x46) {
            $is64 = ($bytes[4] -eq 2)
            Write-Host "  Is64: $is64, Length: $($bytes.Length)"
            if ($is64) {
                $e_phoff = [BitConverter]::ToUInt64($bytes, 32)
                $e_phentsize = [BitConverter]::ToUInt16($bytes, 54)
                $e_phnum = [BitConverter]::ToUInt16($bytes, 56)
                Write-Host "  e_phoff: $e_phoff, e_phentsize: $e_phentsize, e_phnum: $e_phnum"
                for ($i = 0; $i -lt $e_phnum; $i++) {
                    $phOffset = $e_phoff + ($i * $e_phentsize)
                    $p_type = [BitConverter]::ToUInt32($bytes, $phOffset)
                    if ($p_type -eq 1) {
                        $p_align = [BitConverter]::ToUInt64($bytes, $phOffset + 48)
                        Write-Host "    PT_LOAD ${i}: p_align = $p_align"
                    }
                }
            }
        }
    }
}
$zip.Dispose()
