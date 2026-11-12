// Copyright (c) 2021 Advanced Micro Devices, Inc. All rights reserved.
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files(the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions :
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

// GLSL FP32 subset of AMD ffx_a.h used by EASU.
// https://github.com/GPUOpen-Effects/FidelityFX-FSR/blob/master/ffx-fsr/ffx_a.h
#define A_STATIC
#define AF1 float
#define AF2 vec2
#define AF3 vec3
#define AF4 vec4
#define AU1 uint
#define AU2 uvec2
#define AU4 uvec4
#define AP1 bool
#define outAU4 out AU4
#define AF1_(a) float(a)
#define AF2_(a) vec2(a)
#define AF3_(a) vec3(a)
#define AF4_(a) vec4(a)
#define AU1_(a) uint(a)
#define AF1_AU1(a) uintBitsToFloat(a)
#define AF2_AU2(a) uintBitsToFloat(a)
#define AU1_AF1(a) floatBitsToUint(a)
AF1 ARcpF1(AF1 a) { return 1.0/a; }
AF1 ASatF1(AF1 a) { return clamp(a,0.0,1.0); }
AF3 AMin3F3(AF3 x,AF3 y,AF3 z) { return min(x,min(y,z)); }
AF3 AMax3F3(AF3 x,AF3 y,AF3 z) { return max(x,max(y,z)); }
AF1 APrxLoRcpF1(AF1 a) { return AF1_AU1(AU1_(0x7ef07ebb)-AU1_AF1(a)); }
AF1 APrxLoRsqF1(AF1 a) { return AF1_AU1(AU1_(0x5f347d74)-(AU1_AF1(a)>>AU1_(1))); }
