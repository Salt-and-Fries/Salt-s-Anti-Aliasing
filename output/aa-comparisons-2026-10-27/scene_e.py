from pathlib import Path
import sys
sys.path.insert(0,str(Path('build/upscaler-fixes-20261024').resolve())); import control as c
c.GAME=Path('run/aa-comparisons-20261027').resolve();c.EVIDENCE=Path('output/aa-comparisons-2026-10-27').resolve()
c.request('scene-e',dict(mode='OFF',commands=['tp @a 155 100 115 215 14'],yaw=215,pitch=14),warmup=120,hud=False,fps=60)
