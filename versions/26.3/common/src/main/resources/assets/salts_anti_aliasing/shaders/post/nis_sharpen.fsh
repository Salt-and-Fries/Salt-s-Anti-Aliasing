#version 450
// Custom edge-adaptive sharpening. This is not NVIDIA NVSharpen or AMD RCAS.
uniform sampler2D InSampler;
layout(std140) uniform SamplerInfo { vec2 OutSize; vec2 InSize; };
layout(std140) uniform NisSharpenConfig { float Sharpness; float Algorithm; float OvershootLimit; };
layout(location=0) in vec2 texCoord;
layout(location=0) out vec4 fragColor;
float luma(vec3 c) { return dot(c,vec3(0.2126,0.7152,0.0722)); }
void main() {
    vec2 d=1.0/max(InSize,vec2(1.0));
    vec4 source=texture(InSampler,texCoord);
    vec3 c=source.rgb;
    vec3 n=texture(InSampler,texCoord+vec2(0,-d.y)).rgb;
    vec3 s=texture(InSampler,texCoord+vec2(0,d.y)).rgb;
    vec3 w=texture(InSampler,texCoord+vec2(-d.x,0)).rgb;
    vec3 e=texture(InSampler,texCoord+vec2(d.x,0)).rgb;
    vec3 lo=min(c,min(min(n,s),min(w,e)));
    vec3 hi=max(c,max(max(n,s),max(w,e)));
    vec3 detail=c-(n+s+w+e)*0.25;
    float contrast=luma(hi)-luma(lo);
    float gain=0.8*Sharpness*(1.0-smoothstep(0.05,0.5,contrast));
    if(Algorithm>0.5) {
        vec3 nw=texture(InSampler,texCoord+vec2(-d.x,-d.y)).rgb;
        vec3 ne=texture(InSampler,texCoord+vec2(d.x,-d.y)).rgb;
        vec3 sw=texture(InSampler,texCoord+vec2(-d.x,d.y)).rgb;
        vec3 se=texture(InSampler,texCoord+vec2(d.x,d.y)).rgb;
        lo=min(lo,min(min(nw,ne),min(sw,se)));
        hi=max(hi,max(max(nw,ne),max(sw,se)));
        detail=c-(c*0.25+(n+s+w+e)*0.125+(nw+ne+sw+se)*0.0625);
        gain=Sharpness;
    }
    vec3 pad=(hi-lo)*OvershootLimit;
    vec3 result=clamp(c+detail*gain,lo-pad,hi+pad);
    fragColor=vec4(clamp(result,0.0,1.0),source.a);
}
