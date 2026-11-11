"""Reproduce the vendored SGSR adaptation from its pinned, checksum-verified upstream sources.
Run with Python 3 from any directory; no third-party packages are required.
"""
from pathlib import Path
import re,json,hashlib,shutil,urllib.request
root=Path(__file__).resolve().parents[1]
sdk=root/'build/sgsr-sdk';assets=root/'src/main/resources/assets/salts_anti_aliasing'
manifest=json.loads((assets/'licenses/sgsr/source-manifest.json').read_text())
commit=manifest['commit']
# Download only pinned upstream inputs. Fail before changing packaged shaders if any digest differs.
for name in ['LICENSE',*manifest['files']]:
    target=sdk/name
    if not target.exists():
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(urllib.request.urlopen(f'https://raw.githubusercontent.com/SnapdragonGameStudios/snapdragon-gsr/{commit}/{name}',timeout=30).read())
    if name in manifest['files'] and hashlib.sha256(target.read_bytes()).hexdigest()!=manifest['files'][name]:
        raise RuntimeError('Pinned SGSR source checksum mismatch: '+name)
(sdk/'commit.txt').write_text(commit)

shaders=assets/'shaders'; vendor=shaders/'include/sgsr';vendor.mkdir(parents=True,exist_ok=True)
def source(path):return (sdk/path).read_text(encoding='utf-8-sig')
def common(text):
    text=re.sub(r'^#version .*$', '', text, count=1, flags=re.M)
    text=re.sub(r'layout\(set\s*=\s*0,\s*binding\s*=\s*\d+\)\s*', '', text)
    text=re.sub(r'layout\(std140,\s*set\s*=\s*0,\s*binding\s*=\s*0\)', 'layout(std140)', text)
    text=text.replace('uniform readonly Params','uniform Params')
    return text

# Preserve the reference filter body. Only resource declarations and input/output names change.
for filename,label in [('sgsr1_shader_mobile.frag','sgsr1_reference.glsl'),('sgsr1_shader_mobile_edge_direction.frag','sgsr1_edge_reference.glsl')]:
    s=source('sgsr/v1/include/glsl/'+filename)
    s=s[s.index('float fastLanczos2('):]
    s=s.replace('void main()', 'vec4 sgsr1Filter(vec2 sgsrUv)')
    s=s.replace('in_TEXCOORD0.xy','sgsrUv').replace('in_TEXCOORD0.x','sgsrUv.x').replace('in_TEXCOORD0.y','sgsrUv.y')
    s=s.replace('ViewportInfo[0]', 'ViewportInfo').replace('OperationMode','1')
    s=s.replace('EdgeThreshold','Sgsr1Controls.x').replace('EdgeSharpness','Sgsr1Controls.y')
    s=s.replace('out_Target0.xyzw = color;', 'return color;').replace('int mode = 1;', 'const int mode = 1;')
    # ES reference puts precision qualifiers on constructors; desktop GLSL only permits declarations.
    s=re.sub(r'\b(highp|mediump|lowp)\s+(?=(?:[biud]?vec[234]|float|int|uint)\s*\()', '', s)
    header='// Copyright (c) 2025, Qualcomm Innovation Center, Inc. All rights reserved.\n// SPDX-License-Identifier: BSD-3-Clause\n// Adapted resource interface; reference filter math preserved.\n'
    (vendor/label).write_text(header+s,encoding='utf-8')
    mode='edge' if 'edge_' in label else 'standard'
    wrapper='''#version 450
precision mediump float;
precision highp int;
'''+('#define UseEdgeDirection\n' if mode=='edge' else '')+'''uniform mediump sampler2D ps0;
layout(std140) uniform Sgsr1Config { highp vec4 ViewportInfo; vec4 Sgsr1Controls; };
layout(location=0) in highp vec2 texCoord;
layout(location=0) out vec4 fragColor;
#include <salts_anti_aliasing:sgsr/'''+label+'''>
void main() { fragColor=sgsr1Filter(texCoord); }
'''
    (shaders/'post'/f'sgsr1_{mode}.fsh').write_text(wrapper)

s=common(source('sgsr/v2/include/glsl_2_pass_fs/sgsr2_convert.fs'))
# Read standard Z while retaining the reference's nearest-depth and depth-separation math.
s=s.replace('textureGather(InputDepth,', 'sgsrDepthGather(InputDepth,')
helper='''// The reference fragment path uses conventional depth. Its clip matrix is conjugated by the same Z flip.
vec4 sgsrDepthGather(sampler2D image, vec2 uv, int component) {
    return vec4(1.0)-textureGather(image,uv,0);
}
'''
s=s.replace('void main()',helper+'\nvoid main()')
(shaders/'post/sgsr2_convert.fsh').write_text('#version 450\n'+s)
s=common(source('sgsr/v2/include/glsl_2_pass_fs/sgsr2_upscale.fs'))
helper='''// Defined edge replication: texelFetch is not covered by sampler clamp and otherwise reads out of bounds.
vec4 sgsrColorFetch(sampler2D image, ivec2 pixel, int lod) {
    return texelFetch(image,clamp(pixel,ivec2(0),textureSize(image,lod)-1),lod);
}
'''
s=s.replace('texelFetch(InputColor,','sgsrColorFetch(InputColor,')
s=s.replace('void main()',helper+'\nvoid main()')
s=s.replace('Output = vec4(Upsampledcw.xyz, 0.0);','Output = vec4(Upsampledcw.xyz, 1.0);')
(shaders/'post/sgsr2_upscale.fsh').write_text('#version 450\n'+s)

for kind in ['entity','particle']:
    text=(shaders/'post'/f'{kind}_motion.fsh').read_text()
    text=text.replace('vec2 motion=(previousClip.xy/previousClip.w-currentClip.xy/currentClip.w)*0.5*JitterAndSize.zw;',
                      'vec2 motion=currentClip.xy/currentClip.w-previousClip.xy/previousClip.w;')
    text=text.replace('fragColor=vec4(clamp(motion,-JitterAndSize.zw,JitterAndSize.zw),0.0,1.0);',
                      'fragColor=vec4(clamp(motion,vec2(-2.0),vec2(2.0))*0.2495+vec2(32767.0/65535.0),0.0,1.0);')
    (shaders/'post'/f'sgsr2_{kind}_motion.fsh').write_text(text)

notices=assets/'licenses/sgsr';notices.mkdir(parents=True,exist_ok=True)
shutil.copy2(sdk/'LICENSE',notices/'LICENSE-SGSR.txt')
manifest['repository']='https://github.com/SnapdragonGameStudios/snapdragon-gsr'
(notices/'source-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Imported pinned SGSR shaders and notices')
