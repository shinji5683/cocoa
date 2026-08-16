import sys
import struct
import os
import zipfile
import shutil

def patch_elf_page_size(so_path):
    with open(so_path, "rb+") as f:
        data = bytearray(f.read())
        
        if data[:4] != b'\x7fELF':
            return False

        is_64bit = (data[4] == 2)
        endian = data[5]
        fmt_char = "<" if endian == 1 else ">"

        if not is_64bit:
            return False

        e_phoff = struct.unpack_from(fmt_char + "Q", data, 32)[0]
        e_phentsize = struct.unpack_from(fmt_char + "H", data, 54)[0]
        e_phnum = struct.unpack_from(fmt_char + "H", data, 56)[0]

        modified = False
        for i in range(e_phnum):
            ph_offset = e_phoff + i * e_phentsize
            p_type = struct.unpack_from(fmt_char + "I", data, ph_offset)[0]
            
            if p_type == 1:
                align_offset = ph_offset + 48
                p_align = struct.unpack_from(fmt_char + "Q", data, align_offset)[0]
                
                if p_align < 16384:
                    print(f"  Patching {os.path.basename(so_path)} PT_LOAD segment {i}: p_align {p_align} -> 16384 (0x4000)")
                    struct.pack_into(fmt_char + "Q", data, align_offset, 16384)
                    modified = True

        if modified:
            f.seek(0)
            f.write(data)
            f.truncate()
            return True
        else:
            return False

def patch_apk(apk_in, apk_out):
    temp_dir = "scratch/apk_patch_temp"
    if os.path.exists(temp_dir):
        shutil.rmtree(temp_dir)
    os.makedirs(temp_dir, exist_ok=True)

    with zipfile.ZipFile(apk_in, 'r') as zip_ref:
        zip_ref.extractall(temp_dir)

    patched_count = 0
    for root, dirs, files in os.walk(temp_dir):
        for file in files:
            if file.endswith(".so"):
                so_path = os.path.join(root, file)
                if patch_elf_page_size(so_path):
                    patched_count += 1

    print(f"Patched {patched_count} .so files in ELF headers.")

    with zipfile.ZipFile(apk_out, 'w', zipfile.ZIP_DEFLATED) as zip_out:
        for root, dirs, files in os.walk(temp_dir):
            for file in files:
                abs_path = os.path.join(root, file)
                rel_path = os.path.relpath(abs_path, temp_dir)
                
                if file.endswith(".so"):
                    zip_out.write(abs_path, rel_path, compress_type=zipfile.ZIP_STORED)
                else:
                    zip_out.write(abs_path, rel_path, compress_type=zipfile.ZIP_DEFLATED)

    shutil.rmtree(temp_dir, ignore_errors=True)
    print(f"Saved ELF patched APK to {apk_out}")

if __name__ == "__main__":
    if len(sys.argv) > 2:
        patch_apk(sys.argv[1], sys.argv[2])
    else:
        print("Usage: python patch_16k_elf.py input.apk output.apk")
