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
}
