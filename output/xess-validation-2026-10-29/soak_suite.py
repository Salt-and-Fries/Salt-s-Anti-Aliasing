from pathlib import Path
import sys,json,time
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
log=c.EVIDENCE/'launch07.log';deadline=time.monotonic()+90
while time.monotonic()<deadline:
    if log.exists() and 'AUDIT complete xess-first' in log.read_text(errors='replace'):break
    time.sleep(1)
results=[]
def take(label,qa=None,**kw):
    d=c.request(label,qa,**kw);results.append(d);(c.EVIDENCE/'soak-results.json').write_text(json.dumps(results,indent=2));return d
try:
    for q in ['NATIVE_AA','QUALITY','PERFORMANCE']:
        take('soak1080-'+q.lower(),dict(mode='XESS_SUPER_RESOLUTION',xessQuality=q,tuning=dict(mipBiasMode='OFF'),yaw=180,pitch=8,commands=['gamemode spectator @a','tick freeze','tp @a 125 91 95 180 8']),size=[1920,1080],count=1800,warmup=300,measure=True,fps=260,hud=False)
    take('failure-injected',dict(injectXessFailure=True),count=2,warmup=60,fps=60,hud=False)
    take('failure-recovered',dict(retry=True,xessQuality='NATIVE_AA'),count=4,warmup=120,fps=60,hud=False)
    take('menu-xess',count=1,warmup=60,fps=60,hud=True,menu='aa')
    take('menu-xess-advanced',count=1,warmup=60,fps=60,hud=True,menu='keep',click='Advanced controls')
    take('menu-xess-dropdown',count=1,warmup=60,fps=60,hud=True,menu='keep',click='Intel XeSS Super Resolution')
    take('menu-xess-dropdown-closed',count=1,warmup=60,fps=60,hud=True,menu='keep',click='Intel XeSS Super Resolution')
    take('menu-xess-credits',count=1,warmup=60,fps=60,hud=True,menu='keep',click='Credits and Licenses')
    take('menu-xess-license',count=1,warmup=60,fps=60,hud=True,menu='keep',click='Intel XeSS License')
    take('soak-final-world',count=4,warmup=120,fps=60,hud=False,menu='none')
except Exception as e:
    results.append(dict(error=str(e)));(c.EVIDENCE/'soak-results.json').write_text(json.dumps(results,indent=2));raise
finally:
    c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
    print('Requested clean shutdown of final qualification client',flush=True)
