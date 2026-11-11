from pathlib import Path
import sys,json,time
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
log=c.EVIDENCE/'launch05.log';deadline=time.monotonic()+90
while time.monotonic()<deadline:
    if log.exists() and 'AUDIT complete xess-first' in log.read_text(errors='replace'):break
    time.sleep(1)
results=[]
def take(label,qa,**kwargs):
    d=c.request(label,qa,**kwargs);results.append(d);(c.EVIDENCE/'final-results.json').write_text(json.dumps(results,indent=2));return d
for q in ['NATIVE_AA','ULTRA_QUALITY_PLUS','ULTRA_QUALITY','QUALITY','BALANCED','PERFORMANCE','ULTRA_PERFORMANCE']:
    take('final-'+q.lower(),dict(mode='XESS_SUPER_RESOLUTION',xessQuality=q,sweep=False,sharpness=0,commands=['gamemode spectator @a','tick freeze','tp @a 125 91 95 180 8'],yaw=180,pitch=8),size=[1920,1080],count=4,warmup=120,fps=60,hud=False)
for q in ['OFF','NATIVE_AA','QUALITY','PERFORMANCE']:
    take('perf1080-'+q.lower(),dict(mode='OFF' if q=='OFF' else 'XESS_SUPER_RESOLUTION',**({} if q=='OFF' else dict(xessQuality=q))),count=600,warmup=240,measure=True,fps=260,hud=False)
take('final-hand',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='QUALITY',yaw=0,pitch=2,commands=['gamemode creative @a','tp @a 0.5 100 0.5 0 2','item replace entity @a weapon.mainhand with minecraft:diamond_sword']),count=12,warmup=180,fps=60,hud=True,stepYaw=.03)
take('final-mip-auto',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='QUALITY',yaw=180,pitch=8,tuning=dict(mipBiasMode='AUTOMATIC'),commands=['gamemode spectator @a','tp @a 125 91 95 180 8']),count=4,warmup=120,fps=60,hud=False)
take('final-fsr2-regression',dict(mode='FSR2_SUPER_RESOLUTION',fsrQuality='QUALITY',tuning=dict(mipBiasMode='OFF')),count=4,warmup=120,fps=60,hud=False)
take('final-fsr3-regression',dict(mode='FSR3_SUPER_RESOLUTION',fsrQuality='QUALITY'),count=4,warmup=120,fps=60,hud=False)
take('final-reenter-xess',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='NATIVE_AA'),count=4,warmup=120,fps=60,hud=False)
# Exercise history invalidation and native retirement on world unload, then stop normally.
c.atomic(c.GAME/'qa-control.json',dict(id=time.time_ns()//1000000,unload=True))
time.sleep(2)
c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,capture='final-title',count=1,warmup=180,fps=60,hud=True))
deadline=time.monotonic()+30
while time.monotonic()<deadline:
    if (c.GAME/'screenshots/final-title-000.png').exists():break
    time.sleep(.3)
c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
print('Final suite complete, requested clean shutdown',flush=True)
