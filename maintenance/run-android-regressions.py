#!/usr/bin/env python3
"""Run fixed Android regression groups; omissions/assumptions are failures, not passes."""
import json,pathlib,re,subprocess,time
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
old=json.loads((root/'maintenance/2026-11-08-pending-review.json').read_text())
core=list(dict.fromkeys(c['class'] for c in old['validation']['core_android']['cases']))
ui=[prefix+s for s in ['data.pinyin.CustomShuangpinPreservationTest','data.prefs.LegacyChineseEnglishSpacingTest','input.config.DirectionalSwipeMigrationFileTest','input.predict.LlmCompletionProtocolTest','ui.main.settings.SettingsSearchNavigationTest','ui.main.settings.behavior.TextKeyboardLayoutEditorActivityTest','ui.main.settings.behavior.TextKeyboardLayoutProfileManagerActivityTest','data.theme.ThemePoolMembershipTest','ui.main.settings.theme.CustomThemeActivityTest','ui.main.settings.theme.ThemeSelectionUiTest','input.clipboard.ClipboardCategoryButtonsTest','input.dialog.RimeSchemaPickerTest']]
new=[prefix+'maintenance.'+s for s in ['MaintenanceInputTest','LayoutTransferRegressionTest','RimeSelectionRegressionTest']]
# Activity fixtures have independent lifecycles. A stall in one class must
# leave diagnostics and allow the remaining regressions to run.
input_class = new[0]
input_methods = [
 "finishCommitsLastPartialAndLateCallbacksCannotWriteToReplacementEditor",
 "bothSymbolPanelStylesCommitThroughRealEditor",
 "javascriptRequestPartialDisplayAndFinalCommitKeepSpacesAndEmoji",
 "deniedBinderFallsBackToExternalFloatingServiceAndCommitsTokenBoundResult",
 "floatingKeyboardResizesRotatesAndKeepsEditorInput",
]
groups = [("core", core)]
groups += [("ui-" + cls.rsplit(".", 1)[-1], [cls]) for cls in ui]
groups += [("input-" + method, [input_class + "#" + method]) for method in input_methods]
groups += [(cls.rsplit(".", 1)[-1], [cls]) for cls in new[1:]]
groups += [("missing-plugin", [prefix + "input.dialog.LanguageInputMethodPickerTest"])]
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
  if group=='missing-plugin':
   adb('shell','pm','uninstall','-k',rime)
   assert rime not in adb('shell','pm','list','packages',rime).splitlines(), 'Rime package remains installed'
   assert not any(x.strip()=='package:'+rime for x in adb('shell','pm','list','packages',rime).splitlines())
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
 finally:
  if group=='missing-plugin':
   try: adb('install','-r','-t',rime_apk)
   except Exception as error: failed=True;report['restore_error']=str(error)
 report['seconds']=round(time.monotonic()-started,1);reports.append(report)
 (out/'android.json').write_text(json.dumps({'source_sha':(out/'source-sha.txt').read_text().strip(),'groups':reports},indent=2)+'\n')
 print(json.dumps({k:v for k,v in report.items() if k!='cases'}),flush=True)
 if report['status']=='failed':
  try:
   (out/(group+'-logcat.log')).write_text(adb('logcat','-d','-t','2500',timeout=20))
  except (subprocess.SubprocessError, OSError) as error:
   print(f'Could not capture logcat for {group}: {error}',flush=True)
  print(log[-6000:],flush=True)
assert (out/'lint-exit-code.txt').read_text().strip()=='0','Lint failed; see retained XML/logs'
assert not failed,'Android regression group failed or was skipped/incomplete'
