from pathlib import Path
import sys,json,re
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29'
cases=[('off',dict(mode='OFF')),('ssaa4-reference',dict(mode='SSAA',ssaa='X400'))]
for version,mode in [(1,'SGSR1_UPSCALE'),(2,'SGSR2_SUPER_RESOLUTION')]:
 for quality in ['NATIVE','QUALITY','BALANCED','PERFORMANCE']:
  cases.append((f'sgsr{version}-{quality.lower()}',dict(mode=mode,sgsrQuality=quality)))
cases += [('sgsr1-directional',dict(mode='SGSR1_UPSCALE',sgsrQuality='QUALITY',tuning=dict(sgsr1EdgeDirection=True))),('sgsr1-raw',dict(mode='SGSR1_UPSCALE',tuning=dict(spatialAa='OFF',sgsr1EdgeDirection=False))),('sgsr1-fxaa',dict(mode='SGSR1_UPSCALE',tuning=dict(spatialAa='FXAA'))),('sgsr2-return-native',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='NATIVE'))]
results=[]
for label,qa in cases:
 d=c.request('fixed-'+label,qa,count=8,warmup=180,fps=60,hud=False)
 results.append(d);(c.EVIDENCE/'static-fixed-results.json').write_text(json.dumps(results,indent=2))
 assert 'fallback' not in d['status'],d['status']
