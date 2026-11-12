// The MIT License(MIT)
//
// Copyright(c) 2022-2023 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy of
// this software and associated documentation files(the "Software"), to deal in
// the Software without restriction, including without limitation the rights to
// use, copy, modify, merge, publish, distribute, sublicense, and / or sell copies of
// the Software, and to permit persons to whom the Software is furnished to do so,
// subject to the following conditions :
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
// FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE AUTHORS OR
// COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
// IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
// CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

// SDR FP32 scaling subset adapted from NVIDIA Image Scaling SDK v1.0.3.
// https://github.com/NVIDIAGameWorks/NVIDIAImageScaling/blob/main/NIS/NIS_Scaler.h
// Arithmetic and coefficient bank retain the reference scaling behavior. The
// integrated USM is omitted so the mod's separate sharpness control applies once.
#define NVF float
#define NVF4 vec4
#define NVI int
#define NVB bool
#define NIS_UNROLL
#define lerp mix
const float kDetectRatio = 2.0 * 1127.0 / 1024.0;
const float kDetectThres = 64.0 / 1024.0;
const float nisScalerCoefficients[384] = float[384](
0.0,     0.0,    1.0000, 0.0,     0.0,    0.0,0.0029, -0.0127, 1.0000, 0.0132, -0.0034, 0.0,0.0063, -0.0249, 0.9985, 0.0269, -0.0068, 0.0,0.0088, -0.0361, 0.9956, 0.0415, -0.0103, 0.0005,0.0117, -0.0474, 0.9932, 0.0562, -0.0142, 0.0005,0.0142, -0.0576, 0.9897, 0.0713, -0.0181, 0.0005,0.0166, -0.0674, 0.9844, 0.0874, -0.0220, 0.0010,0.0186, -0.0762, 0.9785, 0.1040, -0.0264, 0.0015,0.0205, -0.0850, 0.9727, 0.1206, -0.0308, 0.0020,0.0225, -0.0928, 0.9648, 0.1382, -0.0352, 0.0024,0.0239, -0.1006, 0.9575, 0.1558, -0.0396, 0.0029,0.0254, -0.1074, 0.9487, 0.1738, -0.0439, 0.0034,0.0264, -0.1138, 0.9390, 0.1929, -0.0488, 0.0044,0.0278, -0.1191, 0.9282, 0.2119, -0.0537, 0.0049,0.0288, -0.1245, 0.9170, 0.2310, -0.0581, 0.0059,0.0293, -0.1294, 0.9058, 0.2510, -0.0630, 0.0063,0.0303, -0.1333, 0.8926, 0.2710, -0.0679, 0.0073,0.0308, -0.1367, 0.8789, 0.2915, -0.0728, 0.0083,0.0308, -0.1401, 0.8657, 0.3120, -0.0776, 0.0093,0.0313, -0.1426, 0.8506, 0.3330, -0.0825, 0.0103,0.0313, -0.1445, 0.8354, 0.3540, -0.0874, 0.0112,0.0313, -0.1460, 0.8193, 0.3755, -0.0923, 0.0122,0.0313, -0.1470, 0.8022, 0.3965, -0.0967, 0.0137,0.0308, -0.1479, 0.7856, 0.4185, -0.1016, 0.0146,0.0303, -0.1479, 0.7681, 0.4399, -0.1060, 0.0156,0.0298, -0.1479, 0.7505, 0.4614, -0.1104, 0.0166,0.0293, -0.1470, 0.7314, 0.4829, -0.1147, 0.0181,0.0288, -0.1460, 0.7119, 0.5049, -0.1187, 0.0190,0.0278, -0.1445, 0.6929, 0.5264, -0.1226, 0.0200,0.0273, -0.1431, 0.6724, 0.5479, -0.1260, 0.0215,0.0264, -0.1411, 0.6528, 0.5693, -0.1299, 0.0225,0.0254, -0.1387, 0.6323, 0.5903, -0.1328, 0.0234,0.0244, -0.1357, 0.6113, 0.6113, -0.1357, 0.0244,0.0234, -0.1328, 0.5903, 0.6323, -0.1387, 0.0254,0.0225, -0.1299, 0.5693, 0.6528, -0.1411, 0.0264,0.0215, -0.1260, 0.5479, 0.6724, -0.1431, 0.0273,0.0200, -0.1226, 0.5264, 0.6929, -0.1445, 0.0278,0.0190, -0.1187, 0.5049, 0.7119, -0.1460, 0.0288,0.0181, -0.1147, 0.4829, 0.7314, -0.1470, 0.0293,0.0166, -0.1104, 0.4614, 0.7505, -0.1479, 0.0298,0.0156, -0.1060, 0.4399, 0.7681, -0.1479, 0.0303,0.0146, -0.1016, 0.4185, 0.7856, -0.1479, 0.0308,0.0137, -0.0967, 0.3965, 0.8022, -0.1470, 0.0313,0.0122, -0.0923, 0.3755, 0.8193, -0.1460, 0.0313,0.0112, -0.0874, 0.3540, 0.8354, -0.1445, 0.0313,0.0103, -0.0825, 0.3330, 0.8506, -0.1426, 0.0313,0.0093, -0.0776, 0.3120, 0.8657, -0.1401, 0.0308,0.0083, -0.0728, 0.2915, 0.8789, -0.1367, 0.0308,0.0073, -0.0679, 0.2710, 0.8926, -0.1333, 0.0303,0.0063, -0.0630, 0.2510, 0.9058, -0.1294, 0.0293,0.0059, -0.0581, 0.2310, 0.9170, -0.1245, 0.0288,0.0049, -0.0537, 0.2119, 0.9282, -0.1191, 0.0278,0.0044, -0.0488, 0.1929, 0.9390, -0.1138, 0.0264,0.0034, -0.0439, 0.1738, 0.9487, -0.1074, 0.0254,0.0029, -0.0396, 0.1558, 0.9575, -0.1006, 0.0239,0.0024, -0.0352, 0.1382, 0.9648, -0.0928, 0.0225,0.0020, -0.0308, 0.1206, 0.9727, -0.0850, 0.0205,0.0015, -0.0264, 0.1040, 0.9785, -0.0762, 0.0186,0.0010, -0.0220, 0.0874, 0.9844, -0.0674, 0.0166,0.0005, -0.0181, 0.0713, 0.9897, -0.0576, 0.0142,0.0005, -0.0142, 0.0562, 0.9932, -0.0474, 0.0117,0.0005, -0.0103, 0.0415, 0.9956, -0.0361, 0.0088,0.0, -0.0068, 0.0269, 0.9985, -0.0249, 0.0063,0.0, -0.0034, 0.0132, 1.0000, -0.0127, 0.0029);
NVF4 GetEdgeMap(NVF p[4][4], NVI i, NVI j)
{
    const NVF g_0 = abs(p[0 + i][0 + j] + p[0 + i][1 + j] + p[0 + i][2 + j] - p[2 + i][0 + j] - p[2 + i][1 + j] - p[2 + i][2 + j]);
    const NVF g_45 = abs(p[1 + i][0 + j] + p[0 + i][0 + j] + p[0 + i][1 + j] - p[2 + i][1 + j] - p[2 + i][2 + j] - p[1 + i][2 + j]);
    const NVF g_90 = abs(p[0 + i][0 + j] + p[1 + i][0 + j] + p[2 + i][0 + j] - p[0 + i][2 + j] - p[1 + i][2 + j] - p[2 + i][2 + j]);
    const NVF g_135 = abs(p[1 + i][0 + j] + p[2 + i][0 + j] + p[2 + i][1 + j] - p[0 + i][1 + j] - p[0 + i][2 + j] - p[1 + i][2 + j]);

    const NVF g_0_90_max = max(g_0, g_90);
    const NVF g_0_90_min = min(g_0, g_90);
    const NVF g_45_135_max = max(g_45, g_135);
    const NVF g_45_135_min = min(g_45, g_135);

    NVF e_0_90 = 0;
    NVF e_45_135 = 0;

    if (g_0_90_max + g_45_135_max == 0)
    {
        return NVF4(0, 0, 0, 0);
    }

    e_0_90 = min(g_0_90_max / (g_0_90_max + g_45_135_max), 1.0);
    e_45_135 = 1.0 - e_0_90;

    NVB c_0_90 = (g_0_90_max > (g_0_90_min * kDetectRatio)) && (g_0_90_max > kDetectThres) && (g_0_90_max > g_45_135_min);
    NVB c_45_135 = (g_45_135_max > (g_45_135_min * kDetectRatio)) && (g_45_135_max > kDetectThres) && (g_45_135_max > g_0_90_min);
    NVB c_g_0_90 = g_0_90_max == g_0;
    NVB c_g_45_135 = g_45_135_max == g_45;

    NVF f_e_0_90 = (c_0_90 && c_45_135) ? e_0_90 : 1.0;
    NVF f_e_45_135 = (c_0_90 && c_45_135) ? e_45_135 : 1.0;

    NVF weight_0 = (c_0_90 && c_g_0_90) ? f_e_0_90 : 0.0;
    NVF weight_90 = (c_0_90 && !c_g_0_90) ? f_e_0_90 : 0.0;
    NVF weight_45 = (c_45_135 && c_g_45_135) ? f_e_45_135 : 0.0;
    NVF weight_135 = (c_45_135 && !c_g_45_135) ? f_e_45_135 : 0.0;

    return NVF4(weight_0, weight_90, weight_45, weight_135);
}

NVF4 GetInterpEdgeMap(const NVF4 edge[2][2], NVF phase_frac_x, NVF phase_frac_y)
{
    NVF4 h0 = lerp(edge[0][0], edge[0][1], phase_frac_x);
    NVF4 h1 = lerp(edge[1][0], edge[1][1], phase_frac_x);
    return lerp(h0, h1, phase_frac_y);
}

float EvalPoly6(const float pxl[6], int phase) {
    float y = 0.0;
    for (int i = 0; i < 6; ++i) y += nisScalerCoefficients[clamp(phase, 0, 63) * 6 + i] * pxl[i];
    return y;
}NVF FilterNormal(const NVF p[6][6], NVI phase_x_frac_int, NVI phase_y_frac_int)
{
    NVF h_acc = 0.0;
    NIS_UNROLL
    for (NVI j = 0; j < 6; ++j)
    {
        NVF v_acc = 0.0;
        NIS_UNROLL
        for (NVI i = 0; i < 6; ++i)
        {
            v_acc += p[i][j] * nisScalerCoefficients[phase_y_frac_int * 6 + i];
        }
        h_acc += v_acc * nisScalerCoefficients[phase_x_frac_int * 6 + j];
    }

    // let's return the sum unpacked -> we can accumulate it later
    return h_acc;
}

NVF AddDirFilters(NVF p[6][6], NVF phase_x_frac, NVF phase_y_frac, NVI phase_x_frac_int, NVI phase_y_frac_int, NVF4 w)
{
    NVF f = 0;
    if (w.x > 0.0)
    {
        // 0 deg filter
        NVF interp0Deg[6];
        {
            NIS_UNROLL
                for (NVI i = 0; i < 6; ++i)
                {
                    interp0Deg[i] = lerp(p[i][2], p[i][3], phase_x_frac);
                }
        }
        f += EvalPoly6(interp0Deg, phase_y_frac_int) * w.x;
    }
    if (w.y > 0.0)
    {
        // 90 deg filter
        NVF interp90Deg[6];
        {
            NIS_UNROLL
                for (NVI i = 0; i < 6; ++i)
                {
                    interp90Deg[i] = lerp(p[2][i], p[3][i], phase_y_frac);
                }
        }

        f += EvalPoly6(interp90Deg, phase_x_frac_int) * w.y;
    }
    if (w.z > 0.0)
    {
        //45 deg filter
        NVF pphase_b45 = 0.5 + 0.5 * (phase_x_frac - phase_y_frac);

        NVF temp_interp45Deg[7];
        temp_interp45Deg[1] = lerp(p[2][1], p[1][2], pphase_b45);
        temp_interp45Deg[3] = lerp(p[3][2], p[2][3], pphase_b45);
        temp_interp45Deg[5] = lerp(p[4][3], p[3][4], pphase_b45);
        {
            pphase_b45 = pphase_b45 - 0.5;
            NVF a = (pphase_b45 >= 0.) ? p[0][2] : p[2][0];
            NVF b = (pphase_b45 >= 0.) ? p[1][3] : p[3][1];
            NVF c = (pphase_b45 >= 0.) ? p[2][4] : p[4][2];
            NVF d = (pphase_b45 >= 0.) ? p[3][5] : p[5][3];
            temp_interp45Deg[0] = lerp(p[1][1], a, abs(pphase_b45));
            temp_interp45Deg[2] = lerp(p[2][2], b, abs(pphase_b45));
            temp_interp45Deg[4] = lerp(p[3][3], c, abs(pphase_b45));
            temp_interp45Deg[6] = lerp(p[4][4], d, abs(pphase_b45));
        }

        NVF interp45Deg[6];
        NVF pphase_p45 = phase_x_frac + phase_y_frac;
        if (pphase_p45 >= 1)
        {
            NIS_UNROLL
                for (NVI i = 0; i < 6; i++)
                {
                    interp45Deg[i] = temp_interp45Deg[i + 1];
                }
            pphase_p45 = pphase_p45 - 1;
        }
        else
        {
            NIS_UNROLL
                for (NVI i = 0; i < 6; i++)
                {
                    interp45Deg[i] = temp_interp45Deg[i];
                }
        }

        f += EvalPoly6(interp45Deg, NVI(pphase_p45 * 64)) * w.z;
    }
    if (w.w > 0.0)
    {
        //135 deg filter
        NVF pphase_b135 = 0.5 * (phase_x_frac + phase_y_frac);

        NVF temp_interp135Deg[7];
        temp_interp135Deg[1] = lerp(p[3][1], p[4][2], pphase_b135);
        temp_interp135Deg[3] = lerp(p[2][2], p[3][3], pphase_b135);
        temp_interp135Deg[5] = lerp(p[1][3], p[2][4], pphase_b135);
        {
            pphase_b135 = pphase_b135 - 0.5;
            NVF a = (pphase_b135 >= 0.) ? p[5][2] : p[3][0];
            NVF b = (pphase_b135 >= 0.) ? p[4][3] : p[2][1];
            NVF c = (pphase_b135 >= 0.) ? p[3][4] : p[1][2];
            NVF d = (pphase_b135 >= 0.) ? p[2][5] : p[0][3];
            temp_interp135Deg[0] = lerp(p[4][1], a, abs(pphase_b135));
            temp_interp135Deg[2] = lerp(p[3][2], b, abs(pphase_b135));
            temp_interp135Deg[4] = lerp(p[2][3], c, abs(pphase_b135));
            temp_interp135Deg[6] = lerp(p[1][4], d, abs(pphase_b135));
        }

        NVF interp135Deg[6];
        NVF pphase_p135 = 1 + (phase_x_frac - phase_y_frac);
        if (pphase_p135 >= 1)
        {
            NIS_UNROLL
                for (NVI i = 0; i < 6; ++i)
                {
                    interp135Deg[i] = temp_interp135Deg[i + 1];
                }
            pphase_p135 = pphase_p135 - 1;
        }
        else
        {
            NIS_UNROLL
                for (NVI i = 0; i < 6; ++i)
                {
                    interp135Deg[i] = temp_interp135Deg[i];
                }
        }

        f += EvalPoly6(interp135Deg, NVI(pphase_p135 * 64)) * w.w;
    }
    return f;
}


//-----------------------------------------------------------------------------------------------
// NVScaler
//-----------------------------------------------------------------------------------------------

