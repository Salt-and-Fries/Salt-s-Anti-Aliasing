from pathlib import Path
import sys,json,time
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
deadline=time.monotonic()+90
while 'AUDIT complete sgsr-first' not in (c.EVIDENCE/'launch06.log').read_text(errors='replace'):
 if time.monotonic()>deadline:raise RuntimeError('Startup incomplete')
 time.sleep(1)
for label,qa,extra in [
 ('teleport',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='QUALITY',commands=['tp @a 0.5 100 0.5 0 2'],yaw=0,pitch=2),{}),
 ('preset',dict(sgsrQuality='PERFORMANCE'),{}),
 ('resize',dict(sgsrQuality='QUALITY'),dict(size=[1279,719])),
 ('reload',dict(reload=True),{}),
 ('mode',dict(mode='SGSR1_UPSCALE',sgsrQuality='QUALITY'),{}),
 ('return',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='NATIVE'),{})]:
 d=c.request('transition-'+label,qa,count=32,warmup=0,qaDelay=0,fps=60,hud=False,**extra)
 results.append(d);assert 'fallback' not in d['status'].lower(),d['status']
(c.EVIDENCE/'transition-results.json').write_text(json.dumps(results,indent=2))
c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
