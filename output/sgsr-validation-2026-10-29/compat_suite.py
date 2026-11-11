from pathlib import Path
import sys,json,time,re
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
deadline=time.monotonic()+90
while 'AUDIT complete sgsr-first' not in (c.EVIDENCE/'launch03.log').read_text(errors='replace'):
 if time.monotonic()>deadline:raise RuntimeError('Startup incomplete')
 time.sleep(1)
def take(label,qa,**kw):
 d=c.request('compat-'+label,qa,count=8,warmup=150,fps=60,hud=False,**kw)
 results.append(d);(c.EVIDENCE/'compat-results.json').write_text(json.dumps(results,indent=2))
 assert re.search(r' - \d+ x \d+$',d['status']),d['status'];return d
for mode in ['SGSR1_UPSCALE','SGSR2_SUPER_RESOLUTION']:
 for quality in ['NATIVE','QUALITY','PERFORMANCE']:
  d=take(mode+'-'+quality,dict(mode=mode,sgsrQuality=quality,improvedTransparency=True,tuning=dict(mipBiasMode='AUTOMATIC'),commands=['gamemode spectator @a','tick freeze','tp @a 125 91 95 180 8'],yaw=180,pitch=8),size=[1920,1080],stepYaw=.1)
  assert d['sodiumProbe']['installed'];assert d['sodiumProbe']['pipelines']
take('sgsr2-reload',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='QUALITY',reload=True),size=[1279,719])
take('sgsr2-objects',dict(entityMotion=True,geometryMotion=True,particleTest=True,commands=['gamemode creative @a','tick unfreeze','tp @a 0.5 100 0.5 0 2'],yaw=0,pitch=2),size=[1280,720])
take('dlss-m',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='DLAA',tuning=dict(dlssModel='M')))
take('dlss-l',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='QUALITY',tuning=dict(dlssModel='L')))
take('fsr3-fg',dict(mode='FSR3_SUPER_RESOLUTION_FRAME_GENERATION',fsrQuality='QUALITY'))
take('xess',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='NATIVE_AA'))
take('sgsr1-return',dict(mode='SGSR1_UPSCALE',sgsrQuality='QUALITY',tuning=dict(sgsr1EdgeDirection=True)))
take('sgsr2-underwater',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='QUALITY',particleTest=False,entityMotion=False,geometryMotion=False,commands=['fill 48 98 48 54 98 57 minecraft:stone','fill 49 99 49 53 103 53 minecraft:water','fill 48 99 56 54 107 56 minecraft:white_concrete','fill 50 100 56 50 106 56 minecraft:black_concrete','fill 52 100 56 52 106 56 minecraft:black_concrete','tp @a 50.5 100 49.5 0 0','tick freeze'],yaw=0,pitch=0))
take('sgsr2-teleport',dict(sgsrQuality='NATIVE',commands=['tp @a 125 91 95 180 8'],yaw=180,pitch=8))
c.atomic(c.GAME/'qa-control.json',dict(id=time.time_ns()//1000000,unload=True));time.sleep(3)
ident=time.time_ns()//1000000;c.atomic(c.GAME/'audit-control.json',dict(id=ident,lifecycle=True));time.sleep(2)
life=json.loads((c.GAME/f'audit-lifecycle-{ident}.json').read_text());print('UNLOAD',life,flush=True)
assert not life['hasWorld'] and life['ownedTargetBytes']==0,life
(c.EVIDENCE/'world-unload.json').write_text(json.dumps(life,indent=2))
c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
