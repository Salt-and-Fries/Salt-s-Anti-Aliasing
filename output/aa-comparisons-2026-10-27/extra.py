from pathlib import Path
import sys,json
sys.path.insert(0,str(Path('build/upscaler-fixes-20261024').resolve()));import control as c
c.GAME=Path('run/aa-comparisons-20261027').resolve();c.EVIDENCE=Path('output/aa-comparisons-2026-10-27').resolve()
results=json.loads((c.EVIDENCE/'capture-results.json').read_text())
cases=[('fxaa-subpixel-0',dict(mode='FXAA',tuning=dict(fxaaSubpixel=0))),('fxaa-subpixel-1',dict(mode='FXAA',tuning=dict(fxaaSubpixel=1))),('fsr3-fg-ultra-performance',dict(mode='FSR3_SUPER_RESOLUTION_FRAME_GENERATION',fsrQuality='ULTRA_PERFORMANCE')),('fsr3-fg-native-aa',dict(mode='FSR3_SUPER_RESOLUTION_FRAME_GENERATION',fsrQuality='NATIVE_AA')),('off-final',dict(mode='OFF'))]
for label,qa in cases:
 try:
  d=c.request('compare-'+label,qa,count=1,warmup=120,hud=False,fps=60)
  results.append(dict(label=label,result=d))
 except Exception as e:
  results.append(dict(label=label,error=str(e)));print(label,'ERROR',e,flush=True)
 (c.EVIDENCE/'capture-results.json').write_text(json.dumps(results,indent=2))
