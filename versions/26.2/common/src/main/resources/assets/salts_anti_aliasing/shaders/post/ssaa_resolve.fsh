#version 450
// Full source-footprint integration, with explicit reconstruction and color-space controls.
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform SsaaConfig { float LinearLight; float FilterType; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
vec3 decodeSrgb(vec3 c) {
    c=max(c,vec3(0));
    return mix(pow((c+0.055)/1.055,vec3(2.4)),c/12.92,lessThanEqual(c,vec3(0.04045)));
}
vec3 encodeSrgb(vec3 c) {
    c=max(c,vec3(0));
    return mix(1.055*pow(c,vec3(1.0/2.4))-0.055,c*12.92,lessThanEqual(c,vec3(0.0031308)));
}
float tentIntegral(float x,float radius) {
    x=clamp(x,-radius,radius);
    return x<0.0 ? (x+radius)*(x+radius)/(2.0*radius) : radius*0.5+x-x*x/(2.0*radius);
}
float weight(float a,float b,float center,float radius) {
    return tentIntegral(b-center,radius)-tentIntegral(a-center,radius);
}
void main() {
    vec2 scale=InSize/OutSize;
    vec2 start=floor(texCoord*OutSize)*scale;
    vec2 end=min(start+scale,InSize);
    vec2 center=(start+end)*0.5;
    bool tent=FilterType>0.5;
    ivec2 first=ivec2(floor(tent?center-scale:start));
    ivec2 last=ivec2(ceil(tent?center+scale:end));
    vec4 sum=vec4(0);float total=0.0;
    for(int y=first.y;y<last.y;++y) {
        float wy=tent?weight(float(y),float(y+1),center.y,scale.y):max(0.0,min(float(y+1),end.y)-max(float(y),start.y));
        for(int x=first.x;x<last.x;++x) {
            float wx=tent?weight(float(x),float(x+1),center.x,scale.x):max(0.0,min(float(x+1),end.x)-max(float(x),start.x));
            float w=wx*wy;
            vec4 c=texelFetch(InSampler,clamp(ivec2(x,y),ivec2(0),ivec2(InSize)-1),0);
            if(LinearLight>0.5)c.rgb=decodeSrgb(c.rgb);
            sum+=c*w;total+=w;
        }
    }
    sum/=max(total,0.000001);
    if(LinearLight>0.5)sum.rgb=encodeSrgb(sum.rgb);
    fragColor=sum;
}
