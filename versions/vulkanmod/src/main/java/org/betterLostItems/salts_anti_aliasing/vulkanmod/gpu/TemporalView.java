package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Camera-local reprojection in a single reverse-Z, positive-viewport coordinate convention. */
public final class TemporalView {
    private final Matrix4f current = new Matrix4f(), previous = new Matrix4f(), inverseJittered = new Matrix4f();
    private final Matrix4f canonical = new Matrix4f().m11(-1).m22(-1).m32(1);
    private final Matrix4f projection = new Matrix4f(), viewRotation = new Matrix4f();
    private final Matrix4f nativeJitteredProjection = new Matrix4f();
    private float near = .05f, far = 256, depthScale = 1;
    private Object world, perspective, cameraEntity;
    private AntiAliasingMode mode;
    private int width, height, phases, phase;
    private float jitterX, jitterY, previousJitterX, previousJitterY;
    private double x, y, z;
    private float rotationX, rotationY, fovY, cameraMotion;
    private float translationX, translationY, translationZ;
    private boolean initialized, pendingReset = true, reset = true, captured, historyValid;
    private long lastCaptureNanos, frameIndex;

    public void prepare(Object world, Object perspective, Object cameraEntity, AntiAliasingConfig config,
                        int width, int height, int outputWidth) {
        int nextPhases = config.mode == AntiAliasingMode.TAA ? config.tuning.taaJitterPhases
                : config.mode.usesSgsr2QualityControl() ? 32
                : Math.max(8, Math.min(128, Math.round(8f * outputWidth * outputWidth / ((float) width * width))));
        if (world != this.world || perspective != this.perspective || cameraEntity != this.cameraEntity || config.mode != mode
                || width != this.width || height != this.height || phases != nextPhases) invalidate();
        this.world = world; this.perspective = perspective; this.cameraEntity = cameraEntity;
        this.mode = config.mode; this.width = width; this.height = height; this.phases = nextPhases;
        captured = false;
        previousJitterX = jitterX; previousJitterY = jitterY;
        jitterX = halton(phase + 1, 2) - 0.5f; jitterY = halton(phase + 1, 3) - 0.5f;
        phase = (phase + 1) % phases;
    }

    public Matrix4f capture(Matrix4fc projection, Matrix4fc viewRotation, double x, double y, double z,
                            float rotationX, float rotationY, float fovY) {
        if (!usable(projection) || !usable(viewRotation)) {
            invalidate(); return new Matrix4f(projection);
        }
        long now = System.nanoTime();
        double dx = x - this.x, dy = y - this.y, dz = z - this.z;
        float rotation = Math.abs(rotationX - this.rotationX) + Math.abs(wrapDegrees(rotationY - this.rotationY));
        reset = pendingReset || !initialized || dx * dx + dy * dy + dz * dz > 16 || rotation > 45
                || Math.abs(fovY - this.fovY) > Math.toRadians(1)
                || lastCaptureNanos != 0 && now - lastCaptureNanos > 1_000_000_000L;
        pendingReset = false;
        if (initialized && !reset) previous.set(current).translate((float) dx, (float) dy, (float) dz);
        // VulkanMod uses negative-height viewports and forward, zero-to-one depth.
        // Translate in its native coordinates; canonical conversion flips Y and Z together.
        var jittered = new Matrix4f().translation(2 * jitterX / width, 2 * jitterY / height, 0).mul(projection);
        nativeJitteredProjection.set(jittered);
        current.set(canonical).mul(projection).mul(viewRotation);
        this.projection.set(canonical).mul(projection); this.viewRotation.set(viewRotation);
        inverseJittered.set(canonical).mul(jittered).mul(viewRotation).invert();
        if (reset) previous.set(current);
        translationX = reset ? 0 : (float) -dx;
        translationY = reset ? 0 : (float) -dy;
        translationZ = reset ? 0 : (float) -dz;
        float positionMotion = clamp01(((float) Math.sqrt(dx * dx + dy * dy + dz * dz) - 0.02f) / 0.30f);
        float rotationMotion = clamp01((rotation - 0.6f) / 8);
        cameraMotion = reset ? 0 : Math.max(positionMotion * 0.85f, rotationMotion);
        this.x = x; this.y = y; this.z = z; this.rotationX = rotationX; this.rotationY = rotationY; this.fovY = fovY;
        initialized = captured = true; lastCaptureNanos = now; frameIndex++;
        return jittered;
    }

    public void invalidate() {
        pendingReset = true; historyValid = false; initialized = false; phase = 0;
        jitterX = jitterY = previousJitterX = previousJitterY = 0;
    }

    public boolean captured() { return captured; }
    public float[] nativeJitteredProjection() { return nativeJitteredProjection.get(new float[16]); }
    public boolean resetHistory() { return reset || !historyValid; }
    public void resolved() { historyValid = true; }
    public float jitterX() { return jitterX; }
    public float jitterY() { return jitterY; }
    public float fovY() { return fovY; }
    public long frameIndex() { return frameIndex; }
    public void depthRange(float near, float sourceFar) {
        float finiteFar = Float.isFinite(sourceFar) ? sourceFar : 8192;
        float scale = Float.isFinite(sourceFar) ? 1 : finiteFar / (finiteFar - near);
        if (this.near != near || this.far != finiteFar) pendingReset = true;
        this.near = near; this.far = finiteFar; this.depthScale = scale;
        canonical.m22(-scale);
    }
    public float depthScale() { return depthScale; }
    public float[] currentArray() { return current.get(new float[16]); }
    public float[] previousArray() { return previous.get(new float[16]); }
    public float[] cameraTranslation() { return new float[]{translationX, translationY, translationZ}; }
    public ByteBuffer geometryUniforms() {
        var data = buffer(160);
        matrix(data, current); matrix(data, previous);
        data.putFloat(2 * jitterX / width).putFloat(-2 * jitterY / height).putFloat(width).putFloat(height);
        data.putFloat(translationX).putFloat(translationY).putFloat(translationZ).putFloat(0);
        return data.flip();
    }
    public float[] projectionArray() { return projection.get(new float[16]); }
    public float[] cameraParameters() {
        var inverse = new Matrix4f(viewRotation).invert();
        return new float[]{near, far, fovY, Math.abs(projection.m11() / projection.m00()), 0, 0, 0,
                inverse.m10(), inverse.m11(), inverse.m12(), inverse.m00(), inverse.m01(), inverse.m02(),
                -inverse.m20(), -inverse.m21(), -inverse.m22()};
    }
    public Matrix4f current() { return new Matrix4f(current); }
    public Matrix4f previous() { return new Matrix4f(previous); }

    public ByteBuffer motionUniforms() {
        var data = buffer(224);
        matrix(data, inverseJittered); matrix(data, current); matrix(data, previous);
        data.putFloat(width).putFloat(height).putFloat(0).putFloat(0);
        data.putFloat(translationX).putFloat(translationY).putFloat(translationZ).putFloat(0);
        return data.flip();
    }

    public ByteBuffer taaUniforms(AntiAliasingConfig config) {
        var tuning = config.tuning;
        var data = buffer(208);
        matrix(data, inverseJittered); matrix(data, previous);
        data.putFloat(resetHistory() ? 0 : tuning.taaHistoryWeight).putFloat(tuning.taaLumaRejection)
                .putFloat(tuning.taaDepthRejection).putFloat(tuning.taaVarianceGamma);
        data.putFloat(jitterX).putFloat(jitterY).putFloat(previousJitterX).putFloat(previousJitterY);
        data.putFloat(cameraMotion).putFloat(0).putFloat(0).putFloat(0);
        data.putFloat(translationX).putFloat(translationY).putFloat(translationZ).putFloat(0);
        data.putFloat(tuning.taaHistoryFilter.ordinal()).putFloat(tuning.debugView.ordinal()).putFloat(0).putFloat(0);
        return data.flip();
    }

    public ByteBuffer maskUniforms(AntiAliasingConfig config) {
        var t = config.tuning;
        var data = buffer(48);
        data.putFloat(width).putFloat(height).putFloat(previousJitterX - jitterX).putFloat(jitterY - previousJitterY);
        data.putFloat(t.reactiveStrength).putFloat(t.reactiveThreshold).putFloat(t.reactiveMaximum).putFloat(0);
        data.putFloat(t.fsrTransparencyThreshold).putFloat(t.fsrCompositionStrength).putFloat(t.fsrReactiveScale).putFloat(.01f);
        return data.flip();
    }

    public ByteBuffer sgsrUniforms(int outputWidth, int outputHeight, int stillFrames) {
        var flipZ = new Matrix4f().m22(-1).m32(1);
        var clipToPrevious = new Matrix4f(flipZ).mul(previous).mul(new Matrix4f(current).invert()).mul(flipZ);
        double area = (double) outputWidth * outputHeight / ((double) width * height);
        var data = buffer(128);
        matrix(data, clipToPrevious);
        data.putFloat(width).putFloat(height).putFloat(outputWidth).putFloat(outputHeight);
        data.putFloat(1f / width).putFloat(1f / height).putFloat(1f / outputWidth).putFloat(1f / outputHeight);
        data.putFloat(jitterX).putFloat(-jitterY);
        data.putFloat((float) outputWidth / width).putFloat((float) Math.min(20, area * area * area));
        data.putFloat((float) Math.tan(fovY * .5) * width / height);
        boolean still = cameraStill();
        data.putFloat(stillFrames > 5 ? .3f : 0).putFloat(resetHistory() ? 1 : 0).putInt(still ? 1 : 0);
        return data.flip();
    }

    public boolean cameraStill() {
        float[] a = current.get(new float[16]), b = previous.get(new float[16]);
        double delta = 0;
        for (int i = 0; i < 16; i++) delta += Math.abs(a[i] - b[i]);
        return !resetHistory() && delta < 1e-5;
    }

    private static ByteBuffer buffer(int bytes) { return ByteBuffer.allocate(bytes).order(ByteOrder.nativeOrder()); }
    private static void matrix(ByteBuffer data, Matrix4fc matrix) {
        float[] values = new float[16]; matrix.get(values);
        for (float value : values) data.putFloat(value);
    }
    private static boolean usable(Matrix4fc matrix) {
        float[] values = new float[16]; matrix.get(values);
        for (float value : values) if (!Float.isFinite(value)) return false;
        float determinant = matrix.determinant();
        return Float.isFinite(determinant) && Math.abs(determinant) > 1e-10f;
    }
    private static float clamp01(float value) { return Math.max(0, Math.min(1, value)); }
    private static float wrapDegrees(float value) { return (value % 360 + 540) % 360 - 180; }
    private static float halton(int index, int base) {
        float result = 0, fraction = 1f / base;
        while (index > 0) { result += fraction * (index % base); index /= base; fraction /= base; }
        return result;
    }
}
