from pathlib import Path
import sys,json,time,re
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
log=c.EVIDENCE/'launch11.log';deadline=time.monotonic()+90
while time.monotonic()<deadline:
    if log.exists() and 'AUDIT complete xess-first' in log.read_text(errors='replace'):break
    time.sleep(1)
results=[]
cases=[('qualified2-dlss-m',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='DLAA',tuning=dict(dlssModel='M'))),
       ('qualified2-dlss-l',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='QUALITY',tuning=dict(dlssModel='L'))),
       ('qualified2-fsr3-fg',dict(mode='FSR3_SUPER_RESOLUTION_FRAME_GENERATION',fsrQuality='QUALITY')),
       ('qualified2-xess-native',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='NATIVE_AA')),
       ('qualified2-xess-quality',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='QUALITY'))]
try:
    for label,qa in cases:
        qa.update(yaw=180,pitch=8,commands=['gamemode spectator @a','tick freeze','tp @a 125 91 95 180 8'])
        d=c.request(label,qa,count=4,warmup=120,fps=60,hud=False,size=[1920,1080]);results.append(d)
        (c.EVIDENCE/'regression2-results.json').write_text(json.dumps(results,indent=2))
        assert re.search(r' - \d+ x \d+$',d['status']),d['status']
finally:
    c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
    print('Final shared Vulkan regression complete; requested clean shutdown',flush=True)
