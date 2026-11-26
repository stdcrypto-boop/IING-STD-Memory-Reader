package id.std.memoryreader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
final public class ParserTest {
 @Test public void unsignedFatValues(){byte[] b={(byte)255,(byte)255,(byte)255,(byte)255};assertEquals(65535,FatVolume.u16(b,0));assertEquals(4294967295L,FatVolume.u32(b,0));}
 @Test public void exfatEntry() throws IOException {byte[] b=new byte[96];b[0]=(byte)0x85;b[1]=2;b[32]=(byte)0xc0;b[35]=1;b[64]=(byte)0xc1;b[66]=65;int sum=0;for(int i=0;i<b.length;i++)if(i!=2&&i!=3)sum=(((sum>>>1)|((sum&1)<<15))+(b[i]&255))&65535;b[2]=(byte)sum;b[3]=(byte)(sum>>8);assertEquals("A",ExFatVolume.parse(b).name);b[66]=66;try{ExFatVolume.parse(b);fail("Corrupt checksum accepted");}catch(IOException expected){}}
 @Test(expected=IOException.class) public void rejectShortEntry()throws IOException{ExFatVolume.parse(new byte[32]);}

 private static byte[] fixture(){byte[] b=new byte[96];b[0]=(byte)0x85;b[1]=2;b[32]=(byte)0xc0;b[35]=1;b[64]=(byte)0xc1;b[66]=65;return b;}
 private static byte[] seal(byte[] b){int sum=0;for(int i=0;i<b.length;i++)if(i!=2&&i!=3)sum=(((sum>>>1)|((sum&1)<<15))+(b[i]&255))&65535;b[2]=(byte)sum;b[3]=(byte)(sum>>8);return b;}
 @Test public void littleEndian16(){assertEquals(0x1234,FatVolume.u16(new byte[]{0x34,0x12},0));}
 @Test public void littleEndian32(){assertEquals(0x12345678L,FatVolume.u32(new byte[]{0x78,0x56,0x34,0x12},0));}
 @Test(expected=IOException.class) public void rejectPrimaryType()throws IOException{byte[] b=fixture();b[0]=0;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectSecondaryCount()throws IOException{byte[] b=fixture();b[1]=3;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectStreamType()throws IOException{byte[] b=fixture();b[32]=0;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectEmptyName()throws IOException{byte[] b=fixture();b[35]=0;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectTruncatedName()throws IOException{byte[] b=fixture();b[35]=16;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectNameType()throws IOException{byte[] b=fixture();b[64]=0;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectValidLengthOverSize()throws IOException{byte[] b=fixture();b[40]=1;ExFatVolume.parse(seal(b));}
 @Test public void directoryAndContiguousFlags()throws IOException{byte[] b=fixture();b[4]=16;b[33]=2;b[52]=2;ExFatVolume.Entry e=ExFatVolume.parse(seal(b));assertTrue(e.directory);assertTrue(e.contiguous);assertEquals(2L,e.cluster);}

 @Test(expected=IOException.class) public void rejectSlash()throws IOException{byte[] b=fixture();b[66]=47;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectBackslash()throws IOException{byte[] b=fixture();b[66]=92;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectControl()throws IOException{byte[] b=fixture();b[66]=31;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectNullName()throws IOException{byte[] b=fixture();b[66]=0;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectOversizedSet()throws IOException{byte[] b=java.util.Arrays.copyOf(fixture(),640);b[1]=19;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectUnalignedSet()throws IOException{ExFatVolume.parse(seal(java.util.Arrays.copyOf(fixture(),97)));}
 @Test(expected=IOException.class) public void rejectNegativeSize()throws IOException{byte[] b=fixture();b[63]=(byte)128;ExFatVolume.parse(seal(b));}
 @Test(expected=IOException.class) public void rejectNegativeValidSize()throws IOException{byte[] b=fixture();b[47]=(byte)128;ExFatVolume.parse(seal(b));}
 @Test public void unicodeName()throws IOException{byte[] b=fixture();b[66]=(byte)0xe9;assertEquals("é",ExFatVolume.parse(seal(b)).name);}
 @Test public void validSizeBelowLength()throws IOException{byte[] b=fixture();b[40]=3;b[56]=5;ExFatVolume.Entry e=ExFatVolume.parse(seal(b));assertEquals(3L,e.validSize);assertEquals(5L,e.size);}
 @Test public void jpegZeroWidth(){assertFalse(JpegRecovery.validDimensions(0,100));}
 @Test public void jpegZeroHeight(){assertFalse(JpegRecovery.validDimensions(100,0));}
 @Test public void jpegNegativeWidth(){assertFalse(JpegRecovery.validDimensions(-1,100));}
 @Test public void jpegNegativeHeight(){assertFalse(JpegRecovery.validDimensions(100,-1));}
 @Test public void jpegOversizedWidth(){assertFalse(JpegRecovery.validDimensions(65536,1));}
 @Test public void jpegOversizedHeight(){assertFalse(JpegRecovery.validDimensions(1,65536));}
 @Test public void jpegPixelLimit(){assertFalse(JpegRecovery.validDimensions(10001,10000));}
 @Test public void jpegPixelBoundary(){assertTrue(JpegRecovery.validDimensions(10000,10000));}
 @Test public void jpegNormalPhoto(){assertTrue(JpegRecovery.validDimensions(4000,3000));}
 @Test public void jpegSmallPhoto(){assertTrue(JpegRecovery.validDimensions(160,120));}
 @Test public void videoUnsignedZero(){assertEquals(0L,VideoRecovery.u32(new byte[4],0));}
 @Test public void videoUnsignedMax(){assertEquals(4294967295L,VideoRecovery.u32(new byte[]{-1,-1,-1,-1},0));}
 @Test public void videoBigEndian(){assertEquals(0x12345678L,VideoRecovery.u32(new byte[]{0x12,0x34,0x56,0x78},0));}
 @Test public void videoOffset(){assertEquals(16L,VideoRecovery.u32(new byte[]{99,0,0,0,16},1));}
 @Test public void videoFtyp(){assertEquals("ftyp",VideoRecovery.type(new byte[]{102,116,121,112},0));}
 @Test public void videoMoov(){assertEquals("moov",VideoRecovery.type(new byte[]{109,111,111,118},0));}
 @Test public void videoMdat(){assertEquals("mdat",VideoRecovery.type(new byte[]{109,100,97,116},0));}
 @Test public void videoBrandOffset(){assertEquals("3gp4",VideoRecovery.type(new byte[]{0,51,103,112,52},1));}
 @Test public void videoMpegPack(){assertEquals(0x1baL,VideoRecovery.u32(new byte[]{0,0,1,(byte)186},0));}
 @Test public void videoMpegEnd(){assertEquals(0x1b9L,VideoRecovery.u32(new byte[]{0,0,1,(byte)185},0));}
}
