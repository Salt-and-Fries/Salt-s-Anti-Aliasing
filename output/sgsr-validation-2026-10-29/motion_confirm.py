from pathlib import Path
import sys,json
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
for quality in ['NATIVE','QUALITY','PERFORMANCE']:
 d=c.request('confirmed-pan-'+quality.lower(),dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality=quality,yaw=180,pitch=8,probe='confirmed-pan-'+quality,probeDelay=60,commands=['tp @a 125 91 95 180 8']),count=180,warmup=60,measure=True,fps=60,hud=False,menu='none',stepYaw=.1)
 results.append(d)
 (c.EVIDENCE/'motion-confirm-results.json').write_text(json.dumps(results,indent=2))
d=c.request('confirmed-translation',dict(yaw=180,pitch=8,probe='confirmed-translation',probeDelay=60),count=180,warmup=60,measure=True,fps=60,hud=False,stepX=.005)
results.append(d);(c.EVIDENCE/'motion-confirm-results.json').write_text(json.dumps(results,indent=2))
