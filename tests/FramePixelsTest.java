package com.example.quickshot;
import java.nio.ByteBuffer;
import java.util.Arrays;
public class FramePixelsTest {
 public static void main(String[] args) {
  byte[] expected={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
  byte[] padded={1,2,3,4,5,6,7,8,99,99,99,99,9,10,11,12,13,14,15,16};
  byte[] output=new byte[16]; FramePixels.packRgba(ByteBuffer.wrap(padded),2,2,12,4).get(output);
  if(!Arrays.equals(expected,output)) throw new AssertionError("Row padding entered screenshot");
  FramePixels.packRgba(ByteBuffer.wrap(expected),2,2,8,4).get(output);
  if(!Arrays.equals(expected,output)) throw new AssertionError("Unpadded frame changed");
  byte[] interleaved={1,2,3,4,99,5,6,7,8,99};
  byte[] row=new byte[8]; FramePixels.packRgba(ByteBuffer.wrap(interleaved),2,1,10,5).get(row);
  if(!Arrays.equals(Arrays.copyOf(expected,8),row)) throw new AssertionError("Pixel padding entered screenshot");
  try { FramePixels.packRgba(ByteBuffer.wrap(new byte[15]),2,2,8,4); throw new AssertionError("Incomplete frame accepted"); } catch(IllegalArgumentException correct){}
  System.out.println("PASS: padded rows, unpadded frames, pixel padding, incomplete frames");
 }
}
