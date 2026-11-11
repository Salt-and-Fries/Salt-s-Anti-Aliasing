from pathlib import Path
import sys,json
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/sgsr-validation-20261029';c.EVIDENCE=root/'output/sgsr-validation-2026-10-29';results=[]
def take(label,qa=None,**kw):
 d=c.request('sgsr-ui-'+label,qa,count=1,warmup=60,fps=60,hud=True,**kw)
 results.append(d);(c.EVIDENCE/'ui-results.json').write_text(json.dumps(results,indent=2));return d
take('off',dict(mode='OFF'),size=[1920,1080],menu='aa')
take('dropdown',menu='keep',click='Anti-Aliasing:')
take('choices',dict(dropdownDown=5),menu='keep')
d=take('sgsr1-selected',dict(dropdownRow=3),menu='keep');assert d['mode']=='SGSR1_UPSCALE',d['mode']
take('sgsr1-advanced',menu='keep',click='Advanced controls',scroll=230)
take('sgsr2-start',dict(mode='OFF'),menu='aa')
take('dropdown2',menu='keep',click='Anti-Aliasing:')
take('choices2',dict(dropdownDown=5),menu='keep')
d=take('sgsr2-selected',dict(dropdownRow=4),menu='keep');assert d['mode']=='SGSR2_SUPER_RESOLUTION',d['mode']
take('credits',menu='keep',click='Credits and Licenses')
take('sgsr-license',menu='keep',click='Qualcomm SGSR License')
take('world',menu='none')
