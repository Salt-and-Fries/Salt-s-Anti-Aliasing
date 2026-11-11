from pathlib import Path
import json,numpy as np,hashlib,re
from PIL import Image,ImageDraw,ImageFont
R=Path('.').resolve();B=R/'build/sodium-compat-audit-20261025';G=R/'run/sodium-compat-audit-20261025';F=B/'figures';F.mkdir(exist_ok=True)
def events(label):return [json.loads(s) for s in (G/f'inputs-{label}.jsonl').read_text().splitlines()]
def buf(label,i,name):
 p=G/'buffers'/f'{label}-{i:03d}-{name}';meta=json.loads(p.with_suffix('.json').read_text());fmt=meta['format'];channels=2 if fmt=='RG16_FLOAT' else 1 if fmt=='D32_FLOAT' else 4
 dt=np.float32 if fmt=='D32_FLOAT' else np.float16 if 'FLOAT' in fmt else np.uint8
 return np.fromfile(p.with_suffix('.bin'),dtype=dt).reshape(meta['height'],meta['width'],channels).astype(float)
A={}
for label in ['s92-camera-yaw','s92-camera-pitch','s92-camera-strafe']:
 errors=[];projectionErrors=[]
 for i,e in enumerate(events(label)):
  depth=buf(label,i,'depth')[...,0];actual=buf(label,i,'motionVectorTarget');h,w=depth.shape;yy,xx=np.mgrid[4:h:8,4:w:8];d=depth[yy,xx]
  def mat(k):return np.array(e[k]).reshape(4,4).T
  points=np.stack([(xx+.5)/w*2-1,(yy+.5)/h*2-1,d,np.ones_like(d)],axis=-1)
  world=points@mat('jitteredClipToWorld').T;valid=np.abs(world[...,3])>1e-5;world/=world[...,3:4]
  curr=world@mat('currentViewProjection').T;prev=world@mat('previousViewProjection').T;valid&=(curr[...,3]>1e-5)&(prev[...,3]>1e-5)&(d>1e-7)
  expected=(prev[...,:2]/prev[...,3:4]-curr[...,:2]/curr[...,3:4])*.5*np.array([w,h]);delta=np.linalg.norm(actual[yy,xx]-expected,axis=-1);errors.extend(delta[valid].tolist())
  probe=e['sodiumProbe'];aa=np.array(probe['aaProjection']).reshape(4,4).T;s=np.array(probe['sodiumProjection']).reshape(4,4).T;t=np.eye(4);t[0,3]=e['jitterX']*2/w;t[1,3]=-e['jitterY']*2/h;projectionErrors.append(float(np.max(abs(s-t@aa))))
 A[label]=dict(frames=len(events(label)),samples=len(errors),p95InputPixelError=float(np.percentile(errors,95)),p99InputPixelError=float(np.percentile(errors,99)),maxProjectionDifference=max(projectionErrors))
for label in ['s92-entity-motion','s92-particles','s92-particles-oit']:
 e=events(label);nonzero=[]
 for i,row in enumerate(e):
  a=np.flipud(buf(label,i,'motionVectorTarget'));h,w=a.shape[:2];roi=a[int(h*.38):int(h*.62),int(w*.31):int(w*.7)]
  nonzero.append(int((np.linalg.norm(roi,axis=-1)>.001).sum()))
 A[label]=dict(frames=len(e),minEntityDrawVertices=min(x['entityMotionVertices'] for x in e),maxEntityDrawVertices=max(x['entityMotionVertices'] for x in e),minParticleQuads=min(x['particleMotionQuads'] for x in e),maxParticleQuads=max(x['particleMotionQuads'] for x in e),roiNonzeroMin=min(nonzero),roiNonzeroMax=max(nonzero))
 if label=='s92-particles':
  a=np.flipud(buf(label,12,'motionVectorTarget'));heat=np.clip(np.linalg.norm(a,axis=-1)/1,0,1);rgb=np.stack([heat*255,heat*220,heat*0],axis=-1).astype('uint8');Image.fromarray(rgb).resize((1280,720),Image.Resampling.NEAREST).save(F/'particle-flow.png')
A['captures']={}
for p in G.glob('audit-*.json'):
 d=json.loads(p.read_text());
 if 'label' not in d:continue
 A['captures'][d['label']]=dict(status=d['status'],mode=d['mode'],installed=d.get('sodiumProbe',{}).get('installed'),version=d.get('sodiumProbe',{}).get('version'),screenshots=len(list((G/'screenshots').glob(d['label']+'-*.png'))))
A['validation']={}
for name in ['vanilla','sodium092','dynamic','sodium093']:
 p=B/f'launch-{name}.log'
 if not p.exists():continue
 s=p.read_text(errors='replace');A['validation'][name]=dict(vuidReferences=s.count('VUID-'),syncHazards=len(re.findall('SYNC-HAZARD|hazard detected',s,re.I)),vertexWriterWarning='does not support optimized vertex writing code paths' in s,hasSodium='\n\t- sodium ' in s)
A['normalConfigPreserved']=(B/'normal-salts_anti_aliasing.json').read_bytes()==(R/'run/26.3/config/salts_anti_aliasing.json').read_bytes();A['normalOptionsPreserved']=(B/'normal-options.txt').read_bytes()==(R/'run/26.3/options.txt').read_bytes()
A['modCommit']='21b0de753ee11ad936159042887bba12538e7b5b';A['modJarSha256']=hashlib.sha256((R/'build/libs/salts-anti-aliasing-mc26.3-1.1.jar').read_bytes()).hexdigest()
(B/'analysis.json').write_text(json.dumps(A,indent=2));print(json.dumps({k:v for k,v in A.items() if k!='captures'},indent=2));print('Capture cases',len(A['captures']))
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22)
for prefix in ['vanilla','sodium092']:
 labels=['off','fxaa','smaa','msaa4','ssaa200','dlss','dlaa','fsr3'];panel=Image.new('RGB',(1200,8*175),'#eef2f5');draw=ImageDraw.Draw(panel)
 for i,label in enumerate(labels):
  p=G/'screenshots'/f'{prefix}-{label}-004.png'
  if not p.exists():continue
  im=Image.open(p).convert('RGB');im=im.crop((440,310,840,420)).resize((1000,140),Image.Resampling.NEAREST);panel.paste(im,(180,i*175+27));draw.text((10,i*175+57),label,fill='#133243',font=font)
 panel.save(F/(prefix+'-edge-comparison.png'))
for label in ['s92-entity-motion','s92-particles','s92-glass-oit-msaa','s92-glass-oit-dlss_super_resolution']:
 p=G/'screenshots'/f'{label}-006.png'
 if p.exists():Image.open(p).crop((250,200,1030,600)).resize((1170,600),Image.Resampling.NEAREST).save(F/(label+'-zoom.png'))
