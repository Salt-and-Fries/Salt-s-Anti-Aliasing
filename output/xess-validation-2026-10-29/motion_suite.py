from pathlib import Path
import sys,json
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
results=[]
def request(label,qa,**kw):
    qa=dict(mode='XESS_SUPER_RESOLUTION',sharpness=0,**qa)
    d=c.request(label,qa,count=24,warmup=180,fps=60,hud=False,jitterSignX=1,jitterSignY=1,**kw)
    results.append(d);(c.EVIDENCE/'motion-results.json').write_text(json.dumps(results,indent=2));return d
for q in ['NATIVE_AA','QUALITY','ULTRA_PERFORMANCE']:
    request('pan-'+q.lower(),dict(xessQuality=q,sweep=False,yaw=180,pitch=8,probe='pan-'+q.lower(),probeDelay=80,commands=['gamemode spectator @a','tick freeze','tp @a 125 91 95 180 8']),stepYaw=.1)
request('translation-quality',dict(xessQuality='QUALITY',yaw=135,pitch=25,commands=['tp @a 160 105 160 135 25']),stepX=.015,stepY=.008,stepZ=.012)
request('resize-1080-native',dict(xessQuality='NATIVE_AA',yaw=180,pitch=8,commands=['tp @a 125 91 95 180 8']),size=[1920,1080])
request('resize-odd-quality',dict(xessQuality='QUALITY'),size=[1279,719])
request('resource-reload',dict(xessQuality='QUALITY',reload=True),size=[1280,720])
request('oit-on',dict(xessQuality='QUALITY',improvedTransparency=True,yaw=0,pitch=2,commands=['gamemode creative @a','tp @a 0.5 100 0.5 0 2','give @a minecraft:diamond_sword']))
request('oit-off',dict(xessQuality='QUALITY',improvedTransparency=False))
request('particles-hand',dict(xessQuality='NATIVE_AA',particleTest=True,handSwing=True,entityMotion=True,commands=['tick unfreeze','summon minecraft:pig 0 100 5 {Tags:["salts_qa_motion"],NoAI:1b,NoGravity:1b,Silent:1b}']),stepYaw=.03)
request('underwater',dict(xessQuality='QUALITY',particleTest=False,handSwing=False,entityMotion=False,yaw=0,pitch=0,commands=['fill 48 98 48 54 98 57 minecraft:stone','fill 49 99 49 53 103 53 minecraft:water','fill 48 99 56 54 107 56 minecraft:white_concrete','fill 50 100 56 50 106 56 minecraft:black_concrete','fill 52 100 56 52 106 56 minecraft:black_concrete','tp @a 50.5 100 49.5 0 0','tick freeze']))
request('teleport-recovery',dict(xessQuality='NATIVE_AA',yaw=180,pitch=8,commands=['gamemode spectator @a','tp @a 125 91 95 180 8']))
