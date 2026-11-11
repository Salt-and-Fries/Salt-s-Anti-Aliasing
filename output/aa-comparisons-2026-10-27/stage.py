from pathlib import Path
import shutil,json,re,time,subprocess,os
root=Path.cwd(); out=root/'output/aa-comparisons-2026-10-27'; game=root/'run/aa-comparisons-20261027'; source=root/'run/sodium-compat-fixes-20261025'
game.mkdir(exist_ok=True)
for n in ['config','saves','mods']:
    if not (game/n).exists(): shutil.copytree(source/n,game/n)
shutil.copy2(source/'options.txt',game/'options.txt')
p=game/'options.txt'; s=p.read_text(); s=re.sub(r'^renderClouds:.*$', 'renderClouds:"false"',s,flags=re.M); s=re.sub(r'^startedCleanly:.*$', 'startedCleanly:true',s,flags=re.M);s=re.sub(r'^preferredGraphicsBackend:.*$', 'preferredGraphicsBackend:"vulkan"',s,flags=re.M);p.write_text(s)
p=game/'config/salts_anti_aliasing.json';c=json.loads(p.read_text());c.update(mode='OFF',recordMetrics=False,sharpenStrength=0,dlssLogPath='');p.write_text(json.dumps(c,indent=2))
args=(root/'build/sodium-compat-fixes-20261025/launch.args').read_text();args=args.replace(str(source).replace('\\','/'),str(game).replace('\\','/')); lines=args.splitlines();lines[lines.index('--width')+1]='480';lines[lines.index('--height')+1]='270';(out/'launch.args').write_text('\n'.join(lines)+'\n')
i=time.time_ns()//1000000
(game/'qa-control.json').write_text(json.dumps(dict(id=i,mode='OFF',particleTest=False,handSwing=False,sweep=False,commands=['tick freeze','time set noon','weather clear','tp @a 100.5 105 107.5 180 10'],yaw=180,pitch=10)))
(game/'audit-control.json').write_text(json.dumps(dict(id=i,capture='scene-test',count=1,warmup=240,fps=60,hud=False,size=[480,270])))
log=(out/'launch.log').open('wb');p=subprocess.Popen([r'C:\Users\texca\.jdks\ms-25.0.2\bin\java.exe','@'+str(out/'launch.args')],cwd=root,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW);print('pid',p.pid)
