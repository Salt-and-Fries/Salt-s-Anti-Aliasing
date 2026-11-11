from pathlib import Path
import sys,json,time,numpy as np
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
deadline=time.monotonic()+90
while 'AUDIT complete sgsr-first' not in (c.EVIDENCE/'launch05.log').read_text(errors='replace'):
 if time.monotonic()>deadline:raise RuntimeError('Startup incomplete')
 time.sleep(1)
for mode,quality,count in [('SGSR2_SUPER_RESOLUTION','NATIVE',60000),('SGSR1_UPSCALE','QUALITY',30000)]:
 d=c.request('soak-'+mode.lower(),dict(mode=mode,sgsrQuality=quality,sweep=True,amplitude=15,period=160,yaw=180,pitch=8,tuning=dict(mipBiasMode='OFF',spatialAa='SMAA',sgsr1EdgeDirection=False),commands=['gamemode spectator @a','tick unfreeze','tp @a 125 91 95 180 8']),count=count,warmup=360,measure=True,fps=260,hud=False,size=[1920,1080])
 assert 'fallback' not in d['status'].lower(),d['status']
 results.append(d);(c.EVIDENCE/'soak-results.json').write_text(json.dumps(results,indent=2))
 t=np.array(d['frameIntervalsMs']);print(mode,dict(frames=len(t),seconds=float(t.sum()/1000),mean=float(t.mean()),p99=float(np.percentile(t,99)),max=float(t.max()),over50=int((t>50).sum())),flush=True)
c.request('soak-final-image',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='QUALITY',sweep=False,yaw=180,pitch=8),count=4,warmup=120,fps=60,hud=False)
c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
