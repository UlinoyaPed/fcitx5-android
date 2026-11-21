#!/usr/bin/env python3
"""Run fixed Android regression groups; omissions/assumptions are failures, not passes."""
import glob,json,pathlib,re,subprocess,time
root=pathlib.Path(__file__).resolve().parents[1]; out=root/'maintenance/validation-results';out.mkdir(parents=True,exist_ok=True)
app='org.ulinoyaped.fcitx5.android.debug';rime='org.ulinoyaped.fcitx5.android.plugin.rime.debug'
runner=app+'.test/androidx.test.runner.AndroidJUnitRunner'; prefix='org.fxboomk.fcitx5.android.'
def adb(*args,timeout=180):
 return subprocess.check_output(['adb',*args],text=True,stderr=subprocess.STDOUT,timeout=timeout)
def latest(pattern):
 files=list(root.glob(pattern)); assert files,pattern;return str(max(files,key=lambda p:p.stat().st_mtime_ns))
def install(pattern):
 p=latest(pattern);print(adb('install','-r','-t',p));return p
install('app/build/outputs/apk/debug/*.apk')
install('app/build/outputs/apk/androidTest/debug/*.apk')
rime_apk=install('plugin/rime/build/outputs/apk/debug/*.apk')
adb('shell','pm','grant',app,'android.permission.RECORD_AUDIO')
record = json.loads((root / "maintenance/2026-11-13-genai-review.json").read_text())
cases = record["validation"]["android_instrumentation"]["cases"]
groups = [("personal-core", list(dict.fromkeys(case["class"] for case in cases)))]
reports=[];failed=False
for group,classes in groups:
 expected=sum(len(re.findall(r'@Test\b',(root/'app/src/androidTest/java'/pathlib.Path(c.replace('.','/')).with_suffix('.kt')).read_text())) for c in classes)
 started=time.monotonic(); log=''
 try:
  if group=='missing-plugin':
   adb('shell','am','force-stop',app)
   adb('shell','pm','uninstall','-k',rime)
   assert rime not in adb('shell','pm','list','packages',rime).splitlines(), 'Rime package remains installed'
   assert not any(x.strip()=='package:'+rime for x in adb('shell','pm','list','packages',rime).splitlines())
  command=['adb','shell','am','instrument','-w','-r','-e','class',','.join(classes),runner]
  completed=subprocess.run(command,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=1500)
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
  report={'name':group,'expected':expected,'status':'failed','error':str(error)};failed=True
 finally:
  if group=='missing-plugin':
   try: adb('install','-r','-t',rime_apk)
   except Exception as error: failed=True;report['restore_error']=str(error)
 report['seconds']=round(time.monotonic()-started,1);reports.append(report)
 (out/'android.json').write_text(json.dumps({'source_sha':(out/'source-sha.txt').read_text().strip(),'groups':reports},indent=2)+'\n')
 print(json.dumps({k:v for k,v in report.items() if k!='cases'}),flush=True)
 if report['status']=='failed': print(log[-6000:],flush=True)
assert (out/'lint-exit-code.txt').read_text().strip()=='0','Lint failed; see retained XML/logs'
assert not failed,'Android regression group failed or was skipped/incomplete'
