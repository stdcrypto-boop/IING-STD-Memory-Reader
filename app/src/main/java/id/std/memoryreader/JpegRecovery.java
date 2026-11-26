package id.std.memoryreader;
import java.io.*;
final class JpegRecovery {
 interface Sink { void found(long offset,byte[] jpeg)throws IOException; void progress(long done,long total); }
 static void scan(UsbDisk disk,long first,long count,Sink sink)throws IOException{
 if(first<0||count<1||first>disk.blockCount()-count)throw new IOException("Invalid scan range");
 int bs=disk.blockSize(),batch=1,prev=-1,prev2=-1; long start=0; ByteArrayOutputStream candidate=null;
 for(long i=0;i<count;){
 if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");
 int n=(int)Math.min(batch,count-i);byte[] data=disk.read(first+i,n);
 for(int j=0;j<data.length;j++){
 int b=data[j]&255;long pos=(first+i)*bs+j;
 if(prev2==255&&prev==216&&b==255){candidate=new ByteArrayOutputStream();candidate.write(255);candidate.write(216);candidate.write(255);start=pos-2;}
 else if(candidate!=null){candidate.write(b);if(candidate.size()>16*1024*1024){candidate=null;}else if(prev==255&&b==217){byte[] image=candidate.toByteArray();candidate=null;if(image.length>=128){android.graphics.BitmapFactory.Options o=new android.graphics.BitmapFactory.Options();o.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(image,0,image.length,o);if(o.outWidth>0&&o.outHeight>0)sink.found(start,image);}}}
 prev2=prev;prev=b;
 }
 i+=n;sink.progress(i,count);
 }
 }
}
