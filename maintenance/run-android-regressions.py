#!/usr/bin/env python3
"""Run fixed Android regression groups; omissions/assumptions are failures, not passes."""
import json,pathlib,re,subprocess,time
root=pathlib.Path(__file__).resolve().parents[1]; out=root/'maintenance/validation-results';out.mkdir(parents=True,exist_ok=True)
app='org.ulinoyaped.fcitx5.android.debug'
runner=app+'.test/androidx.test.runner.AndroidJUnitRunner'; prefix='org.fxboomk.fcitx5.android.'
def adb(*args,timeout=180):
 return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT,timeout=timeout)
def latest(pattern):
 files=list(root.glob(pattern)); assert files,pattern;return str(max(files,key=lambda p:p.stat().st_mtime_ns))
def install(pattern):
 p=latest(pattern);print(adb('install','-r','-t',p));return p
install('app/build/outputs/apk/debug/*.apk')
install('app/build/outputs/apk/androidTest/debug/*.apk')
install('plugin/rime/build/outputs/apk/debug/*.apk')
install('plugin/anthy/build/outputs/apk/debug/*.apk')
adb('shell','pm','grant',app,'android.permission.RECORD_AUDIO')
groups = [
    ("native-english-pair", [prefix + "maintenance.EnglishPairRegressionTest"]),
    ("layout-copy-storage", [prefix + "maintenance.LayoutCopyFileRegressionTest"]),
    ("layout-management", [prefix + "ui.main.settings.behavior.TextKeyboardLayoutProfileManagerActivityTest"]),
    ("compose-shift", [prefix + "input.keyboard.ComposeShiftPresentationTest"]),
]
reports=[];failed=False
for group,classes in groups:
 expected = 0
 for target in classes:
  cls, _, method = target.partition("#")
  source = (root/'app/src/androidTest/java'/pathlib.Path(cls.replace('.','/')).with_suffix('.kt')).read_text()
  if method:
   assert re.search(r'@Test\s+fun\s+' + re.escape(method) + r'\s*\(', source), target
   expected += 1
  else:
   expected += len(re.findall(r'@Test\b', source))
 started=time.monotonic(); log=''
 try:
  adb('shell','am','force-stop',app)
  adb('logcat','-c')
  command=['adb','shell','am','instrument','-w','-r','-e','class',','.join(classes),runner]
  completed=subprocess.run(command,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=300)
  log=completed.stdout
  (out/(group+'-android.log')).write_text(log)
  match=re.search(r'OK \((\d+) tests?\)',log)
  cases=[];current={}
  for line in log.splitlines():
   if line.startswith('INSTRUMENTATION_STATUS: '):
    key,_,value=line[len('INSTRUMENTATION_STATUS: '):].partition('=');current[key]=value
   elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
    code=int(line.rsplit(':',1)[1]);
    if code<=0 and 'test' in current: cases.append({'class':current.get('class'),'test':current['test'],'code':code})
    current={}
  success=(completed.returncode==0 and match is not None and int(match[1])==expected and len(cases)==expected and all(c['code']==0 for c in cases))
  report={'name':group,'expected':expected,'tests':int(match[1]) if match else None,'status':'passed' if success else 'failed','cases':cases}
  failed|=not success
 except Exception as error:
  partial = getattr(error, 'stdout', None)
  if partial:
   log = partial.decode(errors='replace') if isinstance(partial, bytes) else partial
   (out/(group+'-android.log')).write_text(log)
  report={'name':group,'expected':expected,'status':'failed','error':str(error)};failed=True
 report['seconds']=round(time.monotonic()-started,1);reports.append(report)
 (out/'android.json').write_text(json.dumps({'source_sha':(out/'source-sha.txt').read_text().strip(),'groups':reports},indent=2)+'\n')
 print(json.dumps({k:v for k,v in report.items() if k!='cases'}),flush=True)
 if report['status']=='failed':
  try:
   (out/(group+'-logcat.log')).write_text(adb('logcat','-d','-t','2500',timeout=20))
  except (subprocess.SubprocessError, OSError) as error:
   print(f'Could not capture logcat for {group}: {error}',flush=True)
  print(log[-6000:],flush=True)
assert not failed,'Android regression group failed or was skipped/incomplete'
