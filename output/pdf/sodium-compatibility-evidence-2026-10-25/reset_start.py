from pathlib import Path
import json,re,sys,time
G=Path('run/sodium-compat-audit-20261025');label=sys.argv[1]
p=G/'options.txt';s=p.read_text();s=re.sub(r'^preferredGraphicsBackend:.*$','preferredGraphicsBackend:"vulkan"',s,flags=re.M);p.write_text(s)
p=G/'config/salts_anti_aliasing.json';c=json.loads(p.read_text());c.update(mode='OFF',sharpenStrength=0);c['tuning'].update(mipBiasMode='OFF',dlssColorInput='AUTO',dlssModel='K');p.write_text(json.dumps(c,indent=2))
ident=time.time_ns()//1000000
(G/'qa-control.json').write_text(json.dumps(dict(id=ident,mode='OFF',particleTest=False,commands=['tp @a 100.5 102 107.5 0 0'],yaw=0,pitch=0)))
(G/'audit-control.json').write_text(json.dumps(dict(id=ident,capture=label+'-initial',count=4,warmup=180,fps=60,hud=False)))
