from pathlib import Path
import sys,json
sys.path.insert(0,str(Path('build/upscaler-fixes-20261024').resolve()))
import control as c
c.GAME=Path('run/sodium-compat-audit-20261025').resolve();c.EVIDENCE=Path('build/sodium-compat-audit-20261025').resolve()
prefix=sys.argv[1];results=[]
cases=[('off',dict(mode='OFF')),('fxaa',dict(mode='FXAA')),('smaa',dict(mode='SMAA')),('msaa2',dict(mode='MSAA',msaa='X2',atoc=False)),('msaa4',dict(mode='MSAA',msaa='X4')),('msaa4-atoc',dict(atoc=True)),('msaa8',dict(msaa='X8',atoc=False)),('ssaa200',dict(mode='SSAA',ssaa='X200')),('ssaa400',dict(ssaa='X400')),('nis',dict(mode='NIS_UPSCALE',spatialQuality='QUALITY')),('fsr1',dict(mode='FSR1_UPSCALE')),('taa',dict(mode='TAA')),('dlss',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='QUALITY',tuning=dict(dlssModel='K',mipBiasMode='OFF'))),('dlaa',dict(dlssQuality='DLAA')),('dlss-mip',dict(dlssQuality='QUALITY',tuning=dict(mipBiasMode='CUSTOM',mipBias=-2))),('fsr2',dict(mode='FSR2_SUPER_RESOLUTION',fsrQuality='QUALITY',tuning=dict(mipBiasMode='OFF'))),('fsr3',dict(mode='FSR3_SUPER_RESOLUTION')),('fg',dict(mode='FSR3_SUPER_RESOLUTION_FRAME_GENERATION')),('oit-dlss',dict(mode='DLSS_SUPER_RESOLUTION',improvedTransparency=True)),('oit-msaa',dict(mode='MSAA',msaa='X4')),('oit-fsr3',dict(mode='FSR3_SUPER_RESOLUTION'))]
cases=[case for case in cases if case[0]!='fg']
if prefix=='sodium093':cases=[case for case in cases if case[0] in ['off','msaa4','ssaa200','dlss','dlss-mip','fsr3','oit-dlss','oit-msaa','oit-fsr3']]
for suffix,qa in cases:
    qa=dict(particleTest=False,handSwing=False,sweep=False,sharpness=0,**qa)
    if suffix=='off':qa.update(improvedTransparency=False,commands=['tick freeze','tp @a 100.5 102 107.5 0 0','time set noon','weather clear'],yaw=0,pitch=0)
    label=prefix+'-'+suffix
    d=c.request(label,qa,count=8,warmup=100,inputs=False,hud=False,fps=60)
    results.append(dict(label=label,status=d['status'],mode=d['mode'],probe=d.get('sodiumProbe')))
    (c.EVIDENCE/(prefix+'-summary.json')).write_text(json.dumps(results,indent=2))
c.atomic(c.GAME/'audit-control.json',dict(id=1792933000000+len(prefix),stop=True,capture=prefix+'-stop'))
