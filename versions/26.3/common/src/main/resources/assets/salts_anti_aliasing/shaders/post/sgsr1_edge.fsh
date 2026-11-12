#version 450
precision mediump float;
precision highp int;
#define UseEdgeDirection
uniform mediump sampler2D ps0;
layout(std140) uniform Sgsr1Config { highp vec4 ViewportInfo; vec4 Sgsr1Controls; };
layout(location=0) in highp vec2 texCoord;
layout(location=0) out vec4 fragColor;
#include <salts_anti_aliasing:sgsr/sgsr1_edge_reference.glsl>
void main() { fragColor=sgsr1Filter(texCoord); }
