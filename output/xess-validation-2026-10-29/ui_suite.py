from pathlib import Path
import sys,json,time
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
log=c.EVIDENCE/'launch08.log';deadline=time.monotonic()+90
while time.monotonic()<deadline:
    if log.exists() and 'AUDIT complete xess-first' in log.read_text(errors='replace'):break
    time.sleep(1)
results=[]
def take(label,qa=None,**kw):
    d=c.request(label,qa,count=1,warmup=60,fps=60,hud=True,**kw);results.append(d);(c.EVIDENCE/'ui-results.json').write_text(json.dumps(results,indent=2));return d
try:
    take('ui-before-selection',dict(mode='OFF'),size=[1920,1080],menu='aa')
    take('ui-dropdown-open',menu='keep',click='Anti-Aliasing:')
    take('ui-dropdown-xess-visible',dict(dropdownDown=3),menu='keep')
    selected=take('ui-xess-selected',dict(dropdownRow=4),menu='keep')
    assert selected['mode']=='XESS_SUPER_RESOLUTION',selected['mode']
    take('ui-xess-version',menu='keep',click='Advanced controls',scroll=350)
    take('ui-final-world',menu='none')
finally:
    c.atomic(c.GAME/'audit-control.json',dict(id=time.time_ns()//1000000,stop=True))
    print('UI smoke check finished; requested normal shutdown',flush=True)
