package id.std.memoryreader;
import java.io.*;
import java.nio.charset.StandardCharsets;
final class VideoRecovery {
 interface Sink { OutputStream open(long offset,String extension)throws IOException; void finish(boolean valid)throws IOException; void progress(long done,long total); }
 static final long LIMIT=256L*1024*1024;
 static long u32(byte[] b,int p){return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)|((long)(b[p+2]&255)<<8)|(b[p+3]&255);}
 static String type(byte[] b,int p){return new String(b,p,4,StandardCharsets.US_ASCII);}
 static void check()throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
 static byte[] at(UsbDisk d,long offset,int n)throws IOException{int bs=d.blockSize();if(offset<0||n<0||offset>d.blockCount()*bs-n)throw new EOFException();byte[] result=new byte[n];int done=0;while(done<n){check();byte[] b=d.read(offset/bs,1);int p=(int)(offset%bs),k=Math.min(n-done,bs-p);System.arraycopy(b,p,result,done,k);offset+=k;done+=k;}return result;}
 static long isoLength(UsbDisk d,long start)throws IOException{long pos=start,end=d.blockCount()*d.blockSize();boolean moov=false,mdat=false;for(int i=0;i<4096&&pos<=end-8;i++){byte[] h=at(d,pos,8);long n=u32(h,0);String t=type(h,4);if(!t.matches("ftyp|moov|mdat|free|skip|wide|uuid|pdin|meta|moof|mfra|sidx|styp"))break;if(n<8||n>end-pos||n>LIMIT-(pos-start))break;moov|=t.equals("moov");mdat|=t.equals("mdat");pos+=n;if(moov&&mdat)return pos-start;}return 0;}
 static void scan(UsbDisk d,long first,Sink sink)throws IOException{int bs=d.blockSize();long total=d.blockCount();if(first<0||first>=total)throw new IOException("Sektor awal di luar kapasitas");for(long l=first;l<total;l++){check();byte[] b=d.read(l,1);long start=l*bs,len=0;String ext=null;if(type(b,4).equals("ftyp")&&u32(b,0)>=16){String brand=type(b,8);ext=brand.startsWith("3g2")?"3g2":brand.startsWith("3g")?"3gp":"mp4";len=isoLength(d,start);}else if(u32(b,0)==0x000001baL||u32(b,0)==0x000001b3L){ext="mpeg";int state=0;outer:for(long k=l;k<total&&(k-l)*bs<LIMIT;k++){check();byte[] q=k==l?b:d.read(k,1);for(int j=0;j<q.length;j++){state=(state<<8)|(q[j]&255);if(state==0x000001b9||state==0x000001b7){len=(k-l)*bs+j+1;break outer;}}}}
 if(len>0){try(OutputStream out=sink.open(start,ext)){for(long p=0;p<len;){check();int n=(int)Math.min(16384,len-p);int blocks=(n+bs-1)/bs;byte[] chunk=d.read((start+p)/bs,blocks);out.write(chunk,0,n);p+=n;}}catch(IOException|RuntimeException e){sink.finish(false);throw e;}sink.finish(true);l+=(len-1)/bs;}sink.progress(l+1-first,total-first);}}
}
