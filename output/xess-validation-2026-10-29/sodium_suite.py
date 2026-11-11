from pathlib import Path
import sys,json,time
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
log=c.EVIDENCE/'launch06.log';deadline=time.monotonic()+90
while time.monotonic()<deadline:
    if log.exists() and 'AUDIT complete xess-first' in log.read_text(errors='replace'):break
    time.sleep(1)
results=[]
def take(label,qa,**kw):
    d=c.request(label,qa,count=8,warmup=180,fps=60,hud=False,**kw);results.append(d);(c.EVIDENCE/'sodium-results.json').write_text(json.dumps(results,indent=2));return d
for q in ['NATIVE_AA','QUALITY','ULTRA_PERFORMANCE']:
    take('sodium-'+q.lower(),dict(mode='XESS_SUPER_RESOLUTION',xessQuality=q,improvedTransparency=True,tuning=dict(mipBiasMode='AUTOMATIC'),yaw=180,pitch=8,commands=['gamemode spectator @a','tick freeze','tp @a 125 91 95 180 8']),size=[1920,1080],stepYaw=.05)
take('sodium-glass',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='QUALITY',improvedTransparency=True,yaw=0,pitch=2,commands=['gamemode creative @a','tp @a 0.5 100 0.5 0 2','item replace entity @a weapon.mainhand with minecraft:diamond_sword']))
take('sodium-reload',dict(mode='XESS_SUPER_RESOLUTION',reload=True,xessQuality='NATIVE_AA',commands=['tick unfreeze','particle minecraft:cloud 0.5 102 6 1 0.5 1 0.03 120 force @a']),size=[1280,720])
take('sodium-fsr3',dict(mode='FSR3_SUPER_RESOLUTION',fsrQuality='QUALITY'))
take('sodium-xess-return',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='NATIVE_AA'))
c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
print('Sodium qualification complete; requested clean shutdown',flush=True)
