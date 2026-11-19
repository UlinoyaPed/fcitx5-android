#!/usr/bin/env python3
"""Validate all release packages; static alignment is not a real 16KB device test."""
import hashlib, json, os, pathlib, re, struct, subprocess, sys, xml.etree.ElementTree as ET, zipfile
root=pathlib.Path(__file__).resolve().parents[1]
out=root/'maintenance/validation-results'; out.mkdir(parents=True,exist_ok=True)
xmls=list((root/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')); assert xmls,'No JVM results'
suites=[ET.parse(p).getroot() for p in xmls]
summary={key:sum(int(s.attrib.get(key,0)) for s in suites) for key in ['tests','failures','errors','skipped']}
assert summary['tests']>0 and all(summary[k]==0 for k in ['failures','errors','skipped']),summary
(out/'jvm.json').write_text(json.dumps(summary,indent=2)+'\n')
if sys.argv[1:] == ['--jvm-only']:
 print(json.dumps({'jvm':summary}),flush=True)
 raise SystemExit(0)
assert not sys.argv[1:], 'Unknown verification arguments'
sdk=pathlib.Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ['ANDROID_HOME'])
tools=sdk/'build-tools/36.1.0'
modules=[root/'app']+sorted(p for p in (root/'plugin').iterdir() if (p/'build.gradle.kts').exists())
assert len(modules)==11, modules
reports=[]
for module in modules:
 apks=list((module/'build/outputs/apk/release').glob('*.apk')); assert apks, module
 apk=max(apks,key=lambda p:p.stat().st_mtime_ns); assert 'unsigned' not in apk.name
 cert=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(apk)],text=True)
 digest=re.search(r'Signer #1 certificate SHA-256 digest: (\S+)',cert).group(1)
 subprocess.run([str(tools/'zipalign'),'-c','-P','16','-v','4',str(apk)],check=True,stdout=subprocess.DEVNULL)
 badging=subprocess.check_output([str(tools/'aapt2'),'dump','badging',str(apk)],text=True)
 package=re.search(r"package: name='([^']+)'",badging).group(1)
 assert package.startswith('org.ulinoyaped.fcitx5.android') and not package.endswith('.debug'),package
 native=[]
 with zipfile.ZipFile(apk) as z:
  for name in z.namelist():
   if name.startswith('lib/') and name.endswith('.so'):
    b=z.read(name); assert b[:5]==b'\x7fELF\x02' and b[5]==1, name
    assert struct.unpack_from('<H',b,18)[0]==183,name
    phoff=struct.unpack_from('<Q',b,32)[0]; entsize,count=struct.unpack_from('<HH',b,54)
    alignments=[struct.unpack_from('<Q',b,phoff+i*entsize+48)[0] for i in range(count) if struct.unpack_from('<I',b,phoff+i*entsize)[0]==1]
    assert alignments and min(alignments)>=16384,(name,alignments)
    native.append({'path':name,'load_alignments':alignments})
  if module.name=='app':
   for name,expected in {'assets/usr/share/libime/zh_CN.lm':'df62f5880141a5435334c82fc66db9fd0a2e0d28a02148ec5fb577c9e30e58cd','assets/usr/share/libime/zh_CN.lm.predict':'eb90df84d26b4aace54a61104b04d8acbdde07aa722dbec1741caf6d31e3c3ad'}.items():
    assert hashlib.sha256(z.read(name)).hexdigest()==expected,name
   # Debug fixture names must never enter an installable release.
   manifest=subprocess.check_output([str(tools/'aapt2'),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],text=True)
   assert 'MaintenanceEditorActivity' not in manifest and 'MaintenanceImeFactory' not in manifest
   for name in z.namelist():
    if re.fullmatch(r'classes\d*\.dex',name):
     assert b'MaintenanceEditorActivity' not in z.read(name) and b'MaintenanceImeFactory' not in z.read(name)
 reports.append({'module':module.name,'package':package,'apk':str(apk.relative_to(root)),'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'certificate_sha256':digest,'native':native,'zip_alignment':'passed'})
assert len({r['certificate_sha256'] for r in reports})==1
(out/'apks.json').write_text(json.dumps({'source_sha':(out/'source-sha.txt').read_text().strip(),'packages':reports,'real_16kb_device_test':False},indent=2)+'\n')
print(json.dumps({'apks':len(reports),'jvm':summary}))
