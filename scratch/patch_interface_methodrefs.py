import zipfile, struct, os, shutil

def patch_jar(jar_path):
    print(f"Opening {jar_path}...")
    temp_jar = jar_path + ".tmp"
    
    # 1. Discover all interfaces in the jar
    interfaces = set()
    with zipfile.ZipFile(jar_path, 'r') as z:
        for name in z.namelist():
            if name.endswith('.class'):
                data = z.read(name)
                if len(data) > 10:
                    magic, minor, major, cp_count = struct.unpack('>IHHH', data[:10])
                    if magic == 0xcafebabe:
                        idx = 10
                        cp = [None] * cp_count
                        i = 1
                        while i < cp_count:
                            tag = data[idx]
                            if tag == 1:
                                l, = struct.unpack('>H', data[idx+1:idx+3])
                                val = data[idx+3:idx+3+l].decode('latin1', errors='ignore')
                                cp[i] = val
                                idx += 3 + l
                            elif tag == 7:
                                cp[i] = struct.unpack('>H', data[idx+1:idx+3])[0]
                                idx += 3
                            elif tag in (9, 10, 11): idx += 5
                            elif tag == 12: idx += 5
                            elif tag in (3, 4): idx += 5
                            elif tag in (5, 6): idx += 9; i += 1
                            elif tag == 8: idx += 3
                            elif tag in (15, 16, 18): idx += 4 if tag == 15 else 3 if tag == 16 else 5
                            else: break
                            i += 1
                        if idx + 4 <= len(data):
                            access_flags, this_class = struct.unpack('>HH', data[idx:idx+4])
                            if access_flags & 0x0200: # ACC_INTERFACE
                                if this_class < cp_count and isinstance(cp[this_class], int):
                                    name_idx = cp[this_class]
                                    if name_idx < cp_count and isinstance(cp[name_idx], str):
                                        interfaces.add(cp[name_idx])
    
    print(f"Found {len(interfaces)} interface classes.")
    
    # 2. Iterate and patch any tag=10 (Methodref) pointing to an interface -> change to tag=11 (InterfaceMethodref)
    patched_classes = 0
    total_refs_patched = 0
    
    with zipfile.ZipFile(jar_path, 'r') as z_in, zipfile.ZipFile(temp_jar, 'w', compression=zipfile.ZIP_DEFLATED) as z_out:
        for item in z_in.infolist():
            data = bytearray(z_in.read(item.filename))
            if item.filename.endswith('.class') and len(data) > 10:
                magic, minor, major, cp_count = struct.unpack('>IHHH', data[:10])
                if magic == 0xcafebabe:
                    idx = 10
                    cp = [None] * cp_count
                    ref_entries = [] # (index, offset, cls_idx, nt_idx)
                    i = 1
                    while i < cp_count:
                        tag = data[idx]
                        if tag == 1:
                            l, = struct.unpack('>H', data[idx+1:idx+3])
                            val = data[idx+3:idx+3+l].decode('latin1', errors='ignore')
                            cp[i] = val
                            idx += 3 + l
                        elif tag == 7:
                            cp[i] = ('Class', struct.unpack('>H', data[idx+1:idx+3])[0])
                            idx += 3
                        elif tag == 10: # Methodref
                            c_idx, nt_idx = struct.unpack('>HH', data[idx+1:idx+5])
                            ref_entries.append((i, idx, c_idx, nt_idx))
                            idx += 5
                        elif tag in (9, 11): idx += 5
                        elif tag == 12: idx += 5
                        elif tag in (3, 4): idx += 5
                        elif tag in (5, 6): idx += 9; i += 1
                        elif tag == 8: idx += 3
                        elif tag in (15, 16, 18): idx += 4 if tag == 15 else 3 if tag == 16 else 5
                        else: break
                        i += 1
                    
                    class_modified = False
                    for (ref_i, offset, c_idx, nt_idx) in ref_entries:
                        if c_idx < cp_count and isinstance(cp[c_idx], tuple) and cp[c_idx][0] == 'Class':
                            name_idx = cp[c_idx][1]
                            if name_idx < cp_count and isinstance(cp[name_idx], str):
                                target_class = cp[name_idx]
                                if target_class in interfaces:
                                    # Change tag 10 to tag 11
                                    data[offset] = 11
                                    class_modified = True
                                    total_refs_patched += 1
                                    if 'StartStopTokens' in target_class or total_refs_patched <= 20:
                                        print(f"Patched {item.filename}: Methodref -> InterfaceMethodref for {target_class}")
                    
                    if class_modified:
                        patched_classes += 1
            z_out.writestr(item, bytes(data))
            
    print(f"Patching complete! Patched {total_refs_patched} references across {patched_classes} classes.")
    
    # Backup original jar
    bak = jar_path + ".bak"
    if not os.path.exists(bak):
        shutil.copyfile(jar_path, bak)
        print(f"Backup saved to {bak}")
        
    shutil.move(temp_jar, jar_path)
    print(f"Successfully updated {jar_path}!")

if __name__ == "__main__":
    patch_jar("app.jar")
