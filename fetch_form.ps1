$url = "https://support.google.com/googleplay/android-developer/contact/protectappeals?hl=en"
$response = Invoke-WebRequest -Uri $url -UserAgent "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
$content = $response.Content

Write-Host "Form content length: $($content.Length)"

# Save html to scratch file to inspect
$content | Out-File -FilePath "scratch_form.html" -Encoding utf8
Write-Host "Saved to scratch_form.html"
