# Serena Screen Reader - Developer Digital Signatures

## 1. Git Commit & Tag Signing (SSH Signing)
- **Developer Name**: Shinji
- **Developer Email**: shinjisakiyama@gmail.com
- **SSH Public Key**: `ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIH6EaV5EwFf7pB1W+yP2PZqQ1jK3Q6R9v8T0b5YxZ9p/ shinjisakiyama@gmail.com`
- **Git GPG Format**: `ssh`
- **Signing Status**: Enabled for all Git commits and tags (`commit.gpgsign=true`, `tag.gpgsign=true`).

---

## 2. Android APK Release Signing Certificate
- **Keystore**: `app/serena-release-key.jks`
- **Key Alias**: `serena_key`
- **Owner / Issuer**: `CN=Shinji, OU=Serena, O=Shinji, L=Tokyo, C=JP`
- **Key Algorithm**: 2048-bit RSA with SHA384withRSA
- **Signature Schemes**: APK Signature Scheme v2, v3, v4 enabled
- **Validity**: Until January 1, 2054
- **Certificate Fingerprints**:
  - **SHA-256**: `AC:07:CC:97:47:6B:9F:51:C9:C4:9D:48:3E:55:40:E4:76:B5:76:69:26:BD:F7:32:75:D3:9C:39:AD:94:D8:05`
  - **SHA-1**: `A7:68:AA:57:E8:02:8C:61:D2:EE:5E:DE:B9:C8:E2:63:06:07:C8:FE`
