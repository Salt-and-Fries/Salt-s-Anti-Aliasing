from pathlib import Path
import sys,json
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=json.loads((c.EVIDENCE/'lifecycle-results.json').read_text())
def take(label,qa,**kw):
 d=c.request('life-'+label,qa,count=4,warmup=160,fps=60,**dict(dict(hud=False),**kw))
 results.append(d);(c.EVIDENCE/'lifecycle-results.json').write_text(json.dumps(results,indent=2))
 assert 'fallback' not in d['status'].lower(),d['status']
 return d
take('hand-hud',dict(particleTest=False,geometryMotion=False,chestMotion=False,entityMotion=False,tuning=dict(debugView='NONE'),commands=['gamemode creative','item replace entity @a weapon.mainhand with minecraft:diamond_sword','tp @a 125 91 95 180 8'],yaw=180,pitch=8),hud=True)
# A failed renderer must restore the native, unjittered scene until explicit retry.
d=c.request('life-injected-failure',dict(injectSgsrFailure=True),count=2,warmup=100,fps=60,hud=False)
results.append(d);assert 'fallback' in d['status'].lower() or 'failed' in d['status'].lower(),d['status']
assert d['sodiumProbe']['jitterX']==0 and d['sodiumProbe']['jitterY']==0
take('retry',dict(retry=True))
off=take('off-release',dict(mode='OFF'))
assert off['ownedTargetBytes']==0,off['ownedTargetBytes']
take('return-sgsr2',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='NATIVE'))
(c.EVIDENCE/'lifecycle-results.json').write_text(json.dumps(results,indent=2))
