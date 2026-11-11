from pathlib import Path
import sys,json
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
def take(label,qa,**kw):
 d=c.request(label,qa,count=16,warmup=180,fps=60,hud=False,**kw);results.append(d);(c.EVIDENCE/'temporal-results.json').write_text(json.dumps(results,indent=2));assert 'fallback' not in d['status'],d['status'];return d
for quality in ['NATIVE','QUALITY','BALANCED','PERFORMANCE']:
 take('temporal-'+quality.lower(),dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality=quality,sweep=False,commands=['tick freeze','tp @a 125 91 95 180 8'],yaw=180,pitch=8))
for x,y in [(1,1),(1,-1),(-1,1),(-1,-1)]:
 take(f'sign-{x}-{y}',dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality='NATIVE',retry=True),jitterSignX=x,jitterSignY=y)
take('temporal-reference',dict(mode='SSAA',ssaa='X400'),jitterSignX=1,jitterSignY=1)
for quality in ['NATIVE','QUALITY','PERFORMANCE']:
 take('pan-'+quality.lower(),dict(mode='SGSR2_SUPER_RESOLUTION',sgsrQuality=quality,yaw=180,pitch=8,probe='sgsr2-'+quality,probeDelay=80,commands=['tp @a 125 91 95 180 8']),stepYaw=.1)
