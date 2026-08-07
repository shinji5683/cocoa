$ProgressPreference = 'SilentlyContinue'
$g = "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
$z = Join-Path $env:TEMP "gradle-8.5-bin.zip"
$d = Join-Path $env:TEMP "gradle85_extracted"

if (-not (Test-Path $d)) {
    Write-Host "Downloading Gradle 8.5 with curl..."
    curl.exe -fsSL $g -o $z
    Write-Host "Extracting Gradle 8.5..."
    Expand-Archive -Path $z -DestinationPath $d -Force
}

$gradleBin = Join-Path $d "gradle-8.5\bin\gradle.bat"
Write-Host "Running Gradle Wrapper initialization..."
& $gradleBin wrapper --gradle-version 8.5
