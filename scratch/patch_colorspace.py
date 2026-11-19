import zipfile
import struct
import os

jar_path = r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\lib\android-all-10.jar'
out_path = r'C:\Users\Noval\.gemini\antigravity\scratch\android-runner\bin\android\graphics\ColorSpace$Rgb.class'
os.makedirs(os.path.dirname(out_path), exist_ok=True)

with zipfile.ZipFile(jar_path, 'r') as z:
    for name in z.namelist():
        if name.endswith('ColorSpace$Rgb.class'):
            data = bytearray(z.read(name))
            break

# Construct Code attribute bytes returning 1L (lconst_1; lreturn)
# 525 is 'Code'
code_attr = struct.pack('>H I H H I B B H H',
    525,       # attribute_name_index ('Code')
    14,        # attribute_length (2+2+4+2+2+2 = 14)
    2,         # max_stack
    8,         # max_locals
    2,         # code_length
    0x0a,      # lconst_1
    0xad,      # lreturn
    0,         # exception_table_length
    0          # attributes_count
)

# Parse CP to reach methods
cp_count = struct.unpack('>H', data[8:10])[0]
offset = 10
cp_strings = {}
i = 1
while i < cp_count:
    tag = data[offset]
    if tag == 1:
        l = struct.unpack('>H', data[offset+1:offset+3])[0]
        val = data[offset+3:offset+3+l].decode('utf-8', 'ignore')
        cp_strings[i] = val
        offset += 3 + l
    elif tag in (7, 8, 16): offset += 3
    elif tag in (3, 4): offset += 5
    elif tag in (5, 6): offset += 9; i += 1
    elif tag in (9, 10, 11, 12, 18): offset += 5
    elif tag == 15: offset += 4
    i += 1

access_flags, this_c, super_c, ifaces_count = struct.unpack('>HHHH', data[offset:offset+8])
offset += 8 + 2 * ifaces_count

# Skip fields
fields_count = struct.unpack('>H', data[offset:offset+2])[0]
offset += 2
for f in range(fields_count):
    f_acc, f_name, f_desc, f_attrs_count = struct.unpack('>HHHH', data[offset:offset+8])
    offset += 8
    for fa in range(f_attrs_count):
        a_name, a_len = struct.unpack('>HI', data[offset:offset+6])
        offset += 6 + a_len

methods_count = struct.unpack('>H', data[offset:offset+2])[0]
offset += 2

new_data = bytearray(data[:offset])
tail_data = data[offset:]

tail_offset = 0
for m in range(methods_count):
    m_acc, m_name, m_desc, m_attrs_count = struct.unpack('>HHHH', tail_data[tail_offset:tail_offset+8])
    tail_offset += 8
    
    m_name_str = cp_strings.get(m_name, '')
    if m_acc & 0x0100:
        print(f"Patching native method {m_name_str} to pure Java!")
        # Clear ACC_NATIVE
        new_acc = m_acc & ~0x0100
        new_data.extend(struct.pack('>HHHH', new_acc, m_name, m_desc, 1))
        new_data.extend(code_attr)
        # Skip existing attributes if any
        for ma in range(m_attrs_count):
            a_name, a_len = struct.unpack('>HI', tail_data[tail_offset:tail_offset+6])
            tail_offset += 6 + a_len
    else:
        new_data.extend(struct.pack('>HHHH', m_acc, m_name, m_desc, m_attrs_count))
        # Copy existing attributes
        for ma in range(m_attrs_count):
            a_name, a_len = struct.unpack('>HI', tail_data[tail_offset:tail_offset+6])
            new_data.extend(tail_data[tail_offset:tail_offset+6+a_len])
            tail_offset += 6 + a_len

# Append remaining class attributes
new_data.extend(tail_data[tail_offset:])

with open(out_path, 'wb') as f:
    f.write(new_data)

print(f"Saved patched ColorSpace$Rgb to {out_path}, original size: {len(data)}, new size: {len(new_data)}")
