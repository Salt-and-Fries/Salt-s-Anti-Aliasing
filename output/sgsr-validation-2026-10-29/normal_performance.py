from pathlib import Path
import sys,json,time,numpy as np
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
deadline=time.monotonic()+90
while 'AUDIT complete sgsr-first' not in (c.EVIDENCE/'launch07.log').read_text(errors='replace'):
 if time.monotonic()>deadline:raise RuntimeError('Startup incomplete')
 time.sleep(1)
cases=[('off',dict(mode='OFF'))]
for mode in ['SGSR1_UPSCALE','SGSR2_SUPER_RESOLUTION']:
 for quality in ['NATIVE','QUALITY','BALANCED','PERFORMANCE']:
  cases.append((mode.lower()+'-'+quality.lower(),dict(mode=mode,sgsrQuality=quality)))
cases.append(('sgsr1-directional',dict(mode='SGSR1_UPSCALE',sgsrQuality='QUALITY',tuning=dict(sgsr1EdgeDirection=True))))
for label,qa in cases:
 qa.setdefault('tuning',{}).update(mipBiasMode='OFF',spatialAa='SMAA',sgsr1EdgeDirection=label=='sgsr1-directional')
 qa.update(commands=['gamemode spectator @a','tick freeze','tp @a 125 91 95 180 8'],yaw=180,pitch=8)
 d=c.request('normal-perf-'+label,qa,count=1800,warmup=360,measure=True,fps=260,hud=False,size=[1920,1080])
 assert 'fallback' not in d['status'].lower(),d['status']
 results.append(d);(c.EVIDENCE/'normal-performance-results.json').write_text(json.dumps(results,indent=2))
 t=np.array(d['frameIntervalsMs']);print(label,dict(mean=float(t.mean()),p95=float(np.percentile(t,95)),p99=float(np.percentile(t,99)),max=float(t.max())),flush=True)
summary=[dict(label=d['label'],frames=len(d['frameIntervalsMs']),meanMs=float(np.mean(d['frameIntervalsMs'])),p95Ms=float(np.percentile(d['frameIntervalsMs'],95)),p99Ms=float(np.percentile(d['frameIntervalsMs'],99))) for d in results]
(c.EVIDENCE/'normal-performance-summary.json').write_text(json.dumps(summary,indent=2))
