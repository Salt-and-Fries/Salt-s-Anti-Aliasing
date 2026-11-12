package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.betterLostItems.salts_anti_aliasing.client.compat.sodium.SodiumMotionVertexConsumer;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SodiumMotionVertexConsumerTest {
    static class Captured implements MotionCapturingVertexConsumer.Positions {
        final List<Float> values = new ArrayList<>();
        boolean invalid;
        public void vertex(float x,float y,float z) { values.add(x);values.add(y);values.add(z); }
        public void invalidate() { invalid=true; }
    }
    static class BulkDelegate extends MotionCapturingVertexConsumer implements VertexBufferWriter {
        int pushes, scalarVertices, renderedVertices;
        boolean intrinsics = true;
        BulkDelegate() { super(null,null); }
        @Override public boolean canUseIntrinsics() { return intrinsics; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x,float y,float z) {
            scalarVertices++; return this;
        }
        public void push(MemoryStack stack,long ptr,int count,VertexFormat format) {
            pushes++;
            renderedVertices += count;
            if(count>0 && ptr!=0) MemoryUtil.memSet(ptr,0,count*(long)format.getVertexSize());
        }
    }
    @Test void readsTheActualStrideAndOffsetBeforeTheDelegateOverwritesItsInput() {
        var format=VertexFormat.builder(0).addAttribute("Color",GpuFormat.RGBA8_UNORM)
                .addAttribute("Position",GpuFormat.RGB32_FLOAT).addAttribute("UV0",GpuFormat.RG32_FLOAT).build();
        var capture=new Captured();var delegate=new BulkDelegate();
        var consumer=new SodiumMotionVertexConsumer(delegate,capture);
        try(var stack=MemoryStack.stackPush()) {
            long ptr=stack.ncalloc(4,2,format.getVertexSize());
            for(int i=0;i<2;i++) {
                long vertex=ptr+format.getElement("Position").offset()+i*(long)format.getVertexSize();
                MemoryUtil.memPutFloat(vertex,i+1);MemoryUtil.memPutFloat(vertex+4,i+2);MemoryUtil.memPutFloat(vertex+8,i+3);
            }
            consumer.push(stack,ptr,2,format);
            assertEquals(0,MemoryUtil.memGetInt(ptr+4));
        }
        consumer.addVertex(7,8,9);
        assertEquals(List.of(1f,2f,3f,2f,3f,4f,7f,8f,9f),capture.values);
        assertEquals(1,delegate.pushes);assertEquals(1,delegate.scalarVertices);assertFalse(capture.invalid);
    }
    @Test void unfamiliarPackedPositionsKeepRenderingWithoutInventingGeometry() {
        var format=VertexFormat.builder(0).addAttribute("Position",GpuFormat.RGBA16_UINT).build();
        var capture=new Captured();var delegate=new BulkDelegate();
        try(var stack=MemoryStack.stackPush()) {
            new SodiumMotionVertexConsumer(delegate,capture).push(stack,stack.ncalloc(4,1,format.getVertexSize()),1,format);
        }
        assertTrue(capture.invalid);assertTrue(capture.values.isEmpty());assertEquals(1,delegate.pushes);
    }
    @Test void nestedWritersRetainTheirIntrinsicEligibility() {
        var delegate=new BulkDelegate();
        var inner=new SodiumMotionVertexConsumer(delegate,new Captured());
        var outer=new SodiumMotionVertexConsumer(inner,new Captured());
        assertTrue(outer.canUseIntrinsics());
        delegate.intrinsics=false;
        assertFalse(outer.canUseIntrinsics());
    }
    @Test void captureBudgetDoesNotTruncateTheRenderedBulkDraw() {
        var capture=new Captured() { @Override public int bulkLimit(int requested) { return 1; } };
        var delegate=new BulkDelegate();
        var format=VertexFormat.builder(0).addAttribute("Position",GpuFormat.RGB32_FLOAT).build();
        try(var stack=MemoryStack.stackPush()) {
            new SodiumMotionVertexConsumer(delegate,capture).push(stack,stack.ncalloc(4,100,format.getVertexSize()),100,format);
        }
        assertEquals(3,capture.values.size());assertEquals(1,delegate.pushes);
        assertEquals(100,delegate.renderedVertices);
    }
    @Test void emptyPushDoesNotDereferenceNullMemory() {
        var capture=new Captured();var delegate=new BulkDelegate();
        try(var stack=MemoryStack.stackPush()) {
            new SodiumMotionVertexConsumer(delegate,capture).push(stack,0,0,
                    VertexFormat.builder(0).addAttribute("Position",GpuFormat.RGB32_FLOAT).build());
        }
        assertTrue(capture.values.isEmpty());assertFalse(capture.invalid);assertEquals(1,delegate.pushes);
    }
}
