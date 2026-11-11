from pathlib import Path
import sys,json
sys.path.insert(0,str(Path('build/upscaler-fixes-20261024').resolve()))
import control as c
c.GAME=Path('run/sodium-compat-audit-20261025').resolve();c.EVIDENCE=Path('build/sodium-compat-audit-20261025').resolve()
def run(label,qa=None,count=24,warmup=120,**kw):return c.request(label,qa,count=count,warmup=warmup,inputs=True,buffers=['motionVectorTarget','depth'],hud=False,fps=60,**kw)
run('s92-camera-yaw',dict(mode='DLSS_SUPER_RESOLUTION',dlssQuality='QUALITY',improvedTransparency=False,tuning=dict(mipBiasMode='OFF'),commands=['tp @a 100.5 102 107.5 0 0'],yaw=0,pitch=0),stepYaw=.15)
run('s92-camera-pitch',dict(yaw=0,pitch=0),stepPitch=.1)
run('s92-camera-strafe',dict(yaw=0,pitch=0),stepX=.02)
setup=['tick unfreeze','fill 2 101 4 4 103 4 glass','fill -5 100 3 -3 103 5 glass','fill -5 101 3 -4 102 4 water','tp @a 0 101 -2 0 0']
run('s92-entity-motion',dict(fixture=True,entityMotion=True,entityMotionY=101,commands=setup,yaw=0,pitch=0))
run('s92-particles',dict(entityMotion=False,particleTest=True,particleKind='flame',particleZ=5))
run('s92-particles-oit',dict(improvedTransparency=True))
for mode in ['MSAA','DLSS_SUPER_RESOLUTION','FSR3_SUPER_RESOLUTION']:
 c.request('s92-glass-oit-'+mode.lower(),dict(particleTest=False,mode=mode,msaa='X4'),count=12,warmup=120,hud=False,fps=60)
run('s92-linear-target',dict(mode='DLSS_SUPER_RESOLUTION',tuning=dict(dlssColorInput='LINEAR_HDR')),count=6)
run('s92-sdr-target',dict(tuning=dict(dlssColorInput='AUTO')),count=6)
run('s92-reload',dict(reload=True),count=6,warmup=400)
c.request('s92-sodium-menu',dict(particleTest=False),count=1,warmup=30,menu='sodium',hud=False,fps=60)
