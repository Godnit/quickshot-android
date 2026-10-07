package com.example.quickshot;

import java.nio.ByteBuffer;

/** Pack only the visible pixels; the final row need not contain trailing padding. */
final class FramePixels {
    static ByteBuffer packRgba(ByteBuffer input,int width,int height,int rowStride,int pixelStride) {
        if(width<=0 || height<=0 || pixelStride<4 || rowStride<(long)width*pixelStride)
            throw new IllegalArgumentException("Invalid frame strides");
        long required=(long)(height-1)*rowStride+(long)(width-1)*pixelStride+4;
        if(required>input.remaining()) throw new IllegalArgumentException("Incomplete frame");
        ByteBuffer source=input.slice();
        ByteBuffer packed=ByteBuffer.allocate(Math.multiplyExact(Math.multiplyExact(width,height),4));
        for(int row=0;row<height;row++) {
            if(pixelStride==4) {
                ByteBuffer scanline=source.duplicate();
                scanline.position(row*rowStride); scanline.limit(row*rowStride+width*4);
                packed.put(scanline);
                continue;
            }
            for(int col=0;col<width;col++) {
                int offset=row*rowStride+col*pixelStride;
                for(int channel=0;channel<4;channel++) packed.put(source.get(offset+channel));
            }
        }
        packed.flip(); return packed;
    }
}
