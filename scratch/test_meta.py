import subprocess, re

aapt2 = r'C:\Users\Noval\AppData\Local\Android\Sdk\build-tools\35.0.0\aapt2.exe'
apk = r'C:\Users\Noval\.gemini\antigravity\scratch\headless-android\base.apk'
out = subprocess.check_output([aapt2, 'dump', 'xmltree', '--file', 'AndroidManifest.xml', apk]).decode('utf-8', errors='ignore')

lines = out.splitlines()
element_stack = []
app_metadata = {}
comp_metadata = {}
cur_comp = None

re_element = re.compile(r'^(\s*)E:\s*([\w-]+)')
re_name = re.compile(r'android:name\([^\)]+\)="([^"]+)"')
re_val = re.compile(r'android:value\([^\)]+\)=(.*)')
re_res = re.compile(r'android:resource\([^\)]+\)=@?(0x[0-9a-fA-F]+|\d+)')

meta_name = None
meta_val = None

for line in lines:
    m_el = re_element.match(line)
    if m_el:
        indent = len(m_el.group(1))
        tag = m_el.group(2)
        while element_stack and element_stack[-1][0] >= indent:
            element_stack.pop()
        element_stack.append((indent, tag))
        if tag in ('activity', 'service', 'provider', 'receiver'):
            cur_comp = None
        if tag == 'meta-data':
            meta_name = None
            meta_val = None

    if element_stack and element_stack[-1][1] in ('activity', 'service', 'provider', 'receiver'):
        m_n = re_name.search(line)
        if m_n and not cur_comp:
            cur_comp = m_n.group(1)

    if element_stack and element_stack[-1][1] == 'meta-data':
        m_n = re_name.search(line)
        if m_n:
            meta_name = m_n.group(1)
        m_v = re_val.search(line)
        if m_v:
            v_raw = m_v.group(1).strip()
            if v_raw.startswith('"'):
                meta_val = v_raw.strip('"')
                if '(Raw: ' in line:
                    m_raw = re.search(r'\(Raw:\s*"([^"]+)"\)', line)
                    if m_raw: meta_val = m_raw.group(1)
            elif v_raw in ('true', '0x1'):
                meta_val = True
            elif v_raw in ('false', '0x0'):
                meta_val = False
            elif v_raw.startswith('@0x'):
                meta_val = int(v_raw[1:], 16)
            elif v_raw.startswith('0x'):
                meta_val = int(v_raw, 16)
            else:
                try: meta_val = int(v_raw)
                except: meta_val = v_raw
        m_r = re_res.search(line)
        if m_r and meta_val is None:
            r_str = m_r.group(1)
            meta_val = int(r_str, 16) if r_str.startswith('0x') else int(r_str)

        if meta_name and meta_val is not None:
            parent_tag = None
            for ind, t in reversed(element_stack[:-1]):
                if t in ('activity', 'service', 'provider', 'receiver', 'application'):
                    parent_tag = t
                    break
            if parent_tag == 'application':
                app_metadata[meta_name] = meta_val
            elif cur_comp:
                if cur_comp not in comp_metadata: comp_metadata[cur_comp] = {}
                comp_metadata[cur_comp][meta_name] = meta_val
            meta_name = None
            meta_val = None

print('Application metadata entries count:', len(app_metadata))
for k, v in list(app_metadata.items())[:15]:
    print('  ', k, '->', v)
print('Component metadata count:', len(comp_metadata))
for c, m in list(comp_metadata.items())[:5]:
    print('  Component:', c, '->', m)
