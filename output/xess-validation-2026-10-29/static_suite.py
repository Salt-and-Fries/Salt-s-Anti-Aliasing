from pathlib import Path
import sys,json
root=Path.cwd();sys.path.insert(0,str(root/'build/upscaler-fixes-20261024'));import control as c
c.GAME=root/'run/xess-validation-20261029';c.EVIDENCE=root/'output/xess-validation-2026-10-29'
results=[]
cases=[('native-off',dict(mode='OFF')),('reference-ssaa4',dict(mode='SSAA',ssaa='X400'))]
cases += [('static-'+q.lower(),dict(mode='XESS_SUPER_RESOLUTION',xessQuality=q)) for q in ['NATIVE_AA','ULTRA_QUALITY_PLUS','ULTRA_QUALITY','QUALITY','BALANCED','PERFORMANCE','ULTRA_PERFORMANCE']]
cases += [('dlaa-reference',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='DLAA')),('xess-return-native',dict(mode='XESS_SUPER_RESOLUTION',xessQuality='NATIVE_AA'))]
for label,qa in cases:
    try:
        d=c.request(label,qa,count=6,warmup=120,fps=60,hud=False)
        results.append(d)
    except Exception as e:results.append(dict(label=label,error=str(e))); print(label,e,flush=True)
    (c.EVIDENCE/'static-results.json').write_text(json.dumps(results,indent=2))
