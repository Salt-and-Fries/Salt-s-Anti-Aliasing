from pathlib import Path
import sys,json,time,re
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
log=c.EVIDENCE/'launch08.log';deadline=time.monotonic()+90
while 'AUDIT complete sgsr-first' not in log.read_text(errors='replace'):
 if time.monotonic()>deadline:raise RuntimeError('Startup incomplete')
 time.sleep(1)
for i,mode in enumerate(['SGSR2_SUPER_RESOLUTION','SGSR1_UPSCALE','SGSR2_SUPER_RESOLUTION']):
 c.request(f'reload-ready-{i}',dict(mode=mode,sgsrQuality='QUALITY'),count=1,warmup=60,fps=60,hud=False)
 before=len(log.read_text(errors='replace'));ident=time.time_ns()//1000000
 c.atomic(c.GAME/'qa-control.json',dict(id=ident,reload=True));deadline=time.monotonic()+75
 while 'QA resource reload completed' not in log.read_text(errors='replace')[before:]:
  if time.monotonic()>deadline:raise RuntimeError('Reload incomplete')
  time.sleep(.25)
 d=c.request(f'reload-confirm-{i}',count=8,warmup=0,fps=60,hud=False)
 assert re.search(r' - \d+ x \d+$',d['status']),d['status']
 results.append(d);(c.EVIDENCE/'reload-confirm-results.json').write_text(json.dumps(results,indent=2))
c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
