#!/usr/bin/env python3
import os
import re
import subprocess
import sys

def main():
    target_tag = sys.argv[1] if len(sys.argv) > 1 else ""
    output_file = sys.argv[2] if len(sys.argv) > 2 else "target_release_notes.md"
    
    notes_content = ""
    release_notes_file = "RELEASE_NOTES.md"
    
    if os.path.isfile(release_notes_file):
        with open(release_notes_file, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Match "## [vX.Y.Z]" section up to next "## [" or end of file
        escaped_tag = re.escape(target_tag)
        pattern = rf"(## \[(?:{escaped_tag})\][^\n]*\n.*?(?=\n## \[|\Z))"
        match = re.search(pattern, content, re.DOTALL)
        if match:
            notes_content = match.group(1).strip()
    
    if not notes_content:
        # Fallback to automated git changelog
        notes_content = f"## Serena {target_tag}\n\n### 📦 Automated Commit Changelog\n"
        try:
            prev_tag = subprocess.check_output(
                ["git", "describe", "--tags", "--abbrev=0", f"{target_tag}^"],
                stderr=subprocess.DEVNULL,
                text=True
            ).strip()
            rev_range = f"{prev_tag}..HEAD"
        except Exception:
            rev_range = "HEAD~10..HEAD"
            
        try:
            commits = subprocess.check_output(
                ["git", "log", rev_range, "--pretty=format:* %s (%h)"],
                text=True
            ).strip()
            notes_content += commits if commits else "* Performance enhancements and stability fixes."
        except Exception:
            notes_content += "* Continuous improvement and bug fixes."
            
    # Add official asset download footer
    footer = f"\n\n---\n### 📦 Official Downloads\n- **Release APK**: [app-serena-release.apk](https://github.com/shinji5683/cocoa/releases/download/{target_tag}/app-serena-release.apk)\n- **Debug APK**: [app-serena-debug.apk](https://github.com/shinji5683/cocoa/releases/download/{target_tag}/app-serena-debug.apk)\n"
    notes_content += footer
    
    with open(output_file, "w", encoding="utf-8") as f:
        f.write(notes_content)
    print(f"Successfully generated {output_file} ({len(notes_content)} bytes)")

if __name__ == "__main__":
    main()
