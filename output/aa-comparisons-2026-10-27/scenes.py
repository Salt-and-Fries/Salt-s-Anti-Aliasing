from pathlib import Path
import sys
sys.path.insert(0,str(Path('build/upscaler-fixes-20261024').resolve())); import control as c
c.GAME=Path('run/aa-comparisons-20261027').resolve();c.EVIDENCE=Path('output/aa-comparisons-2026-10-27').resolve()
for label,coords in [('scene-b',(125,91,95,180,8)),('scene-c',(125,91,95,110,8)),('scene-d',(155,100,115,175,8))]:
 x,y,z,yaw,pitch=coords
 c.request(label,dict(commands=[f'tp @a {x} {y} {z} {yaw} {pitch}'],yaw=yaw,pitch=pitch),warmup=120,hud=False,fps=60)
