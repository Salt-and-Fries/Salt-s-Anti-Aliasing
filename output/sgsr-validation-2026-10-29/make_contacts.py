from pathlib import Path
from PIL import Image,ImageDraw
root=Path.cwd();shots=root/'run/sgsr-validation-20261029/screenshots';out=root/'output/sgsr-validation-2026-10-29'
names=['fixed-off','fixed-ssaa4-reference','fixed-sgsr1-native','fixed-sgsr1-quality','fixed-sgsr2-native','fixed-sgsr2-quality','fixed-sgsr1-directional','fixed-sgsr2-performance']
canvas=Image.new('RGB',(1280,4*385),(24,24,24));draw=ImageDraw.Draw(canvas)
for i,name in enumerate(names):
 im=Image.open(shots/(name+'-003.png')).convert('RGB');im.thumbnail((640,360))
 x=(i%2)*640;y=(i//2)*385;canvas.paste(im,(x,y+25));draw.text((x+8,y+6),name,fill='white')
canvas.save(out/'static-contact.jpg',quality=92)
names=['fixed-ssaa4-reference','fixed-sgsr1-quality','fixed-sgsr1-directional','fixed-sgsr2-quality']
canvas=Image.new('RGB',(1280,385),(24,24,24));draw=ImageDraw.Draw(canvas)
for i,name in enumerate(names):
 im=Image.open(shots/(name+'-003.png')).convert('RGB').crop((440,140,760,500))
 canvas.paste(im,(i*320,25));draw.text((i*320+4,6),name,fill='white')
canvas.save(out/'detail-contact.png')
