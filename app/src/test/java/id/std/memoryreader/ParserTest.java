package id.std.memoryreader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
final public class ParserTest {
 @Test public void unsignedFatValues(){byte[] b={(byte)255,(byte)255,(byte)255,(byte)255};assertEquals(65535,FatVolume.u16(b,0));assertEquals(4294967295L,FatVolume.u32(b,0));}
 @Test public void exfatEntry() throws IOException {byte[] b=new byte[96];b[0]=(byte)0x85;b[1]=2;b[32]=(byte)0xc0;b[35]=1;b[64]=(byte)0xc1;b[66]=65;int sum=0;for(int i=0;i<b.length;i++)if(i!=2&&i!=3)sum=(((sum>>>1)|((sum&1)<<15))+(b[i]&255))&65535;b[2]=(byte)sum;b[3]=(byte)(sum>>8);assertEquals("A",ExFatVolume.parse(b).name);b[66]=66;try{ExFatVolume.parse(b);fail("Corrupt checksum accepted");}catch(IOException expected){}}
 @Test(expected=IOException.class) public void rejectShortEntry()throws IOException{ExFatVolume.parse(new byte[32]);}
}
