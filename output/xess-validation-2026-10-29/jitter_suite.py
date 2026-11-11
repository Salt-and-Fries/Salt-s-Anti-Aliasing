from pathlib import Path
import sys,json,time
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
log=c.EVIDENCE/'launch04.log';deadline=time.monotonic()+90
while time.monotonic()<deadline:
    if log.exists() and 'AUDIT complete xess-first' in log.read_text(errors='replace'):break
    time.sleep(1)
for x,y in [(1,1),(1,-1),(-1,1),(-1,-1)]:
    c.request(f'jitter-{x}-{y}',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='NATIVE_AA',retry=True),count=8,warmup=180,fps=60,hud=False,jitterSignX=x,jitterSignY=y)
c.request('jitter-reference-ssaa',dict(mode='SSAA',ssaa='X400'),count=1,warmup=120,fps=60,hud=False,jitterSignX=1,jitterSignY=1)
