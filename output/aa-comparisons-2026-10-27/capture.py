from pathlib import Path
import sys,json,time
sys.path.insert(0,str(Path('build/upscaler-fixes-20261024').resolve())); import control as c
c.GAME=Path('run/aa-comparisons-20261027').resolve();c.EVIDENCE=Path('output/aa-comparisons-2026-10-27').resolve()
cases=[
('off',dict(mode='OFF',commands=['tick freeze','time set noon','weather clear','tp @a 155 100 115 215 14'],yaw=215,pitch=14,sharpness=0)),
('fxaa-default',dict(mode='FXAA')),
('msaa-2x',dict(mode='MSAA',msaa='X2',atoc=False)),('msaa-16x',dict(mode='MSAA',msaa='X16')),
('ssaa-125',dict(mode='SSAA',ssaa='X125')),('ssaa-800',dict(mode='SSAA',ssaa='X800')),
('smaa-low',dict(mode='SMAA',tuning=dict(smaaQuality='LOW'))),('smaa-ultra',dict(mode='SMAA',tuning=dict(smaaQuality='ULTRA'))),
('taa-8-phase',dict(mode='TAA',tuning=dict(taaJitterPhases=8))),('taa-32-phase',dict(mode='TAA',tuning=dict(taaJitterPhases=32))),
('nis-ultra-performance',dict(mode='NIS_UPSCALE',spatialQuality='ULTRA_PERFORMANCE')),('nis-quality',dict(mode='NIS_UPSCALE',spatialQuality='QUALITY')),
('fsr1-ultra-performance',dict(mode='FSR1_UPSCALE',spatialQuality='ULTRA_PERFORMANCE')),('fsr1-quality',dict(mode='FSR1_UPSCALE',spatialQuality='QUALITY')),
('dlss-ultra-performance',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='ULTRA_PERFORMANCE')),('dlss-dlaa',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='DLAA')),
('fsr2-ultra-performance',dict(mode='FSR2_SUPER_RESOLUTION',fsrQuality='ULTRA_PERFORMANCE')),('fsr2-native-aa',dict(mode='FSR2_SUPER_RESOLUTION',fsrQuality='NATIVE_AA')),
('fsr3-ultra-performance',dict(mode='FSR3_SUPER_RESOLUTION',fsrQuality='ULTRA_PERFORMANCE')),('fsr3-native-aa',dict(mode='FSR3_SUPER_RESOLUTION',fsrQuality='NATIVE_AA')),
('off-end',dict(mode='OFF'))]
results=[]
for label,qa in cases:
 try:
  d=c.request('compare-'+label,qa,count=1,warmup=32 if label=='ssaa-800' else 120,hud=False,fps=60)
  results.append(dict(label=label,result=d))
 except Exception as e:
  results.append(dict(label=label,error=str(e)));print(label,'ERROR',e,flush=True)
 (c.EVIDENCE/'capture-results.json').write_text(json.dumps(results,indent=2))
