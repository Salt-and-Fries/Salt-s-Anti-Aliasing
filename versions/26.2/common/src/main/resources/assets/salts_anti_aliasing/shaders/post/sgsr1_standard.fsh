#version 450
precision mediump float;
precision highp int;
uniform mediump sampler2D ps0;
layout(std140) uniform Sgsr1Config { highp vec4 ViewportInfo; vec4 Sgsr1Controls; };
layout(location=0) in highp vec2 texCoord;
layout(location=0) out vec4 fragColor;
#moj_import <salts_anti_aliasing:sgsr/sgsr1_reference.glsl>
void main() { fragColor=sgsr1Filter(texCoord); }
