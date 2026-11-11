from pathlib import Path
import json,hashlib,shutil,zipfile,re,xml.etree.ElementTree as ET
import numpy as np
root=Path.cwd();out=root/'output/xess-validation-2026-10-29';game=root/'run/xess-validation-20261029'
jar=root/'build/libs/salts-anti-aliasing-mc26.3-1.1.jar'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
qualification={}
for index in [3,4,6,11]:
    path=out/f'launch{index:02d}.log';text=path.read_text(errors='replace')
    qualification[path.name]=dict(vulkanVuidCount=text.count('VUID-'),xessEvaluationFailureCount=text.count('XeSS evaluate failed'),cleanShutdown='Saved performance metrics report' in text)
    assert qualification[path.name]['vulkanVuidCount']==0,(path.name,'Vulkan error')
    assert qualification[path.name]['xessEvaluationFailureCount']==0,(path.name,'XeSS failure')
    assert qualification[path.name]['cleanShutdown'],(path.name,'shutdown pending')
testSuites=[ET.parse(p).getroot() for p in (root/'build/test-results/test').glob('TEST-*.xml')]
tests={key:sum(int(s.attrib.get(key,0)) for s in testSuites) for key in ['tests','failures','errors','skipped']}
assert tests['failures']==tests['errors']==0
performance={}
for preset in ['native_aa','quality','performance']:
    d=json.loads((game/f'audit-soak1080-{preset}.json').read_text());v=np.array(d['frameIntervalsMs'])
    performance[preset]=dict(samples=len(v),meanMs=float(v.mean()),p95Ms=float(np.percentile(v,95)),p99Ms=float(np.percentile(v,99)),meanFps=float(1000/v.mean()))
selected=json.loads((game/'audit-ui2-xess-selected.json').read_text());assert selected['mode']=='XESS_SUPER_RESOLUTION'
failed=json.loads((game/'audit-failure-injected.json').read_text());recovered=json.loads((game/'audit-failure-recovered.json').read_text())
assert 'fallback' in failed['status'] and 'fallback' not in recovered['status']
assert failed['sodiumProbe']['jitterX']==failed['sodiumProbe']['jitterY']==0
regression=json.loads((out/'regression2-results.json').read_text());assert len(regression)==5
assert all(re.search(r' - \d+ x \d+$',d['status']) for d in regression)
with zipfile.ZipFile(jar) as z:
    names=z.namelist();assert not any(n.startswith('salts/qa/') for n in names)
    assert not any(Path(n).name.startswith(('nvngx_','sl.')) and n.endswith('.dll') for n in names)
screens=out/'screenshots';screens.mkdir(exist_ok=True)
for label,index in [('qualified2-xess-native',3),('qualified2-xess-quality',3),('final-hand',8),('ui2-xess-selected',0),('ui2-dropdown-xess-visible',0),('menu-xess-license',0),('pan-native_aa',12),('underwater',12),('sodium-quality',7)]:
    source=game/f'screenshots/{label}-{index:03d}.png'
    shutil.copy2(source,screens/source.name)
shutil.copy2(jar,out/jar.name)
contract=root/'build/test-results/test/TEST-org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessIntegrationContractTest.xml'
shutil.copy2(contract,out/'xess-contract-tests.xml')
shutil.copy2(game/'logs/salts_anti_aliasing_metrics_latest.json',out/'qualified-runtime-metrics.json')
runtime=json.loads((out/'qualified-runtime-metrics.json').read_text())
fg=[e['details'] for e in runtime['diagnostics']['events'] if e['category']=='fsr.frame_generation' and e['event']=='present_counts']
assert any(d['displayPresentCounter']>d['gameFrames']>0 for d in fg)
(out/'frame-generation-regression.json').write_text(json.dumps(fg,indent=2))
sourcePaths=list((root/'native/xess_bridge').rglob('*'))+list((root/'src/client/java/org/betterLostItems/salts_anti_aliasing/client/render/vulkan/xess').glob('*.java'))
sources={str(p.relative_to(root)).replace('\\','/'):sha(p) for p in sourcePaths if p.is_file()}
result=dict(date='2026-10-29',timezone='America/Phoenix',hardware='NVIDIA GeForce RTX 3060 Ti',driver='591.86',minecraft='26.3',java='25.0.2',sodium='0.9.2+mc26.3',sdk=dict(version='2.0.2',commit='de0fb9c1c510661c571164e1418ceca8101dab69',runtimeSha256='251659dd84a3e84de67c886a4186e01f3eca49b00641906fe38bb6b807e5d5b7'),package=dict(file=jar.name,sha256=sha(jar),sizeBytes=jar.stat().st_size),tests=tests,qualification=qualification,steady1080p=performance,uiSelectionPassed=True,injectedFailureRecovered=True,fallbackJitter=[0,0],regression=[dict(label=d['label'],mode=d['mode'],status=d['status']) for d in regression],sourceHashes=sources,limits=['One Windows x64 GPU/driver tested; other GPUs and drivers are not certified.','Performance is for the frozen natural test scene without shader packs, with metrics and the disposable QA harness.','Low input resolutions retain the expected loss of fine detail from Intel cross-vendor XeSS reconstruction.','Resource reload/window resize produced recoverable Minecraft out-of-date-swapchain warnings.','The fault-injection error is intentional; offline Realms/auth and host counter warnings are unrelated to XeSS.','Initial development attempts found and then fixed Vulkan API-version and Streamline privateData requirements.','An initial UI harness attempt used obsolete key/button constants; corrected SDL input selected XeSS successfully.'])
(out/'validation.json').write_text(json.dumps(result,indent=2))
print(json.dumps(dict(package=result['package'],tests=tests,qualification=qualification,performance=performance),indent=2))
