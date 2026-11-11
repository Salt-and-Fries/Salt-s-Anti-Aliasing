from pathlib import Path
from PIL import Image,ImageDraw
import numpy as np,json
root=Path.cwd();out=root/'output/xess-validation-2026-10-29';shots=root/'run/xess-validation-20261029/screenshots'
labels=['native-off','reference-ssaa4','static-native_aa','static-quality','static-ultra_performance','dlaa-reference']
ref=np.asarray(Image.open(shots/'reference-ssaa4-005.png').convert('RGB'),dtype=float)
data={}
for label in labels:
    stack=np.stack([np.asarray(Image.open(shots/f'{label}-{i:03d}.png').convert('RGB'),dtype=float) for i in range(6)])
    data[label]=dict(maeVsSsaa=float(np.abs(stack.mean(axis=0)-ref).mean()),frameVariation=float(stack.std(axis=0).mean()),edgeVariation=float(stack[:,200:600,500:1000].std(axis=0).mean()))
canvas=Image.new('RGB',(3*600,2*370),'#171b24');draw=ImageDraw.Draw(canvas)
for index,label in enumerate(labels):
    x=(index%3)*600;y=(index//3)*370;draw.text((x+12,y+8),label,fill='white')
    image=Image.open(shots/f'{label}-005.png').convert('RGB').crop((570,210,1170,550))
    canvas.paste(image,(x,y+28))
canvas.save(out/'static-comparison.png');(out/'static-analysis.json').write_text(json.dumps(data,indent=2));print(json.dumps(data,indent=2))
