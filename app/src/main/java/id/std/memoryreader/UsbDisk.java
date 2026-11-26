package id.std.memoryreader;
import android.hardware.usb.*;
import java.io.*;
import java.nio.*;
final class UsbDisk implements Closeable {
 private final UsbDeviceConnection c; private final UsbInterface iface; private final UsbEndpoint in,out; private int tag=1; private long blocks; private int size; private boolean failed, closed;
 UsbDisk(android.content.Context context,UsbManager m,UsbDevice d,UsbInterface f)throws IOException {
 if(f.getInterfaceClass()!=8||f.getInterfaceSubclass()!=6||f.getInterfaceProtocol()!=80)throw new IOException("Unsupported protocol");
 UsbEndpoint a=null,b=null;for(int i=0;i<f.getEndpointCount();i++){UsbEndpoint e=f.getEndpoint(i);if(e.getType()==2){if(e.getDirection()==128)a=e;else b=e;}}
 if(a==null||b==null)throw new IOException("Missing bulk endpoints");in=a;out=b;iface=f;c=m.openDevice(d);if(c==null)throw new IOException("USB permission unavailable");
 if(!c.claimInterface(f,false)){
 if(android.os.Build.VERSION.SDK_INT<24){c.close();throw new IOException("USB busy");} boolean mounted=false;
 android.os.storage.StorageManager sm=(android.os.storage.StorageManager)context.getSystemService(android.content.Context.STORAGE_SERVICE);
 if(sm==null){c.close();throw new IOException("Storage state unavailable");}
 for(android.os.storage.StorageVolume v:sm.getStorageVolumes()){if(v.isRemovable()&&("mounted".equals(v.getState())||"mounted_ro".equals(v.getState())||"checking".equals(v.getState())))mounted=true;}
 if(mounted||!c.claimInterface(f,true)){c.close();throw new IOException("USB masih dikuasai sistem; lepaskan penyimpanan terlebih dahulu");}
 }
 try{byte[] cmd=new byte[10];cmd[0]=0x25;ByteBuffer cap=ByteBuffer.wrap(command(cmd,8));blocks=Integer.toUnsignedLong(cap.getInt())+1;size=cap.getInt();if(size<512||size>4096||(size&(size-1))!=0||blocks==4294967296L)throw new IOException("Unsupported capacity");}catch(IOException|RuntimeException e){close();throw e;}
 }
 long blockCount(){return blocks;} int blockSize(){return size;}
 synchronized byte[] read(long lba,int count)throws IOException{
 if(closed||failed)throw new IOException("Sesi USB berhenti. Hubungkan kembali card reader.");
 if(size<512||lba<0||count<1||count>Math.min(32,16384/size)||lba>blocks-count)throw new IOException("Read outside media");
 byte[] cmd=new byte[10];ByteBuffer b=ByteBuffer.wrap(cmd);b.put(0,(byte)0x28);b.putInt(2,(int)lba);b.putShort(7,(short)count);try{return command(cmd,count*size);}catch(InterruptedIOException e){throw e;}catch(IOException e){throw new IOException("Sektor "+lba+" ("+count+" blok): "+e.getMessage(),e); }
 }
 private byte[] command(byte[] cmd,int len)throws IOException{
 if(closed||failed)throw new IOException("Sesi USB berhenti. Hubungkan kembali card reader.");if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Dibatalkan");try{
 if(cmd[0]!=0x25&&cmd[0]!=0x28)throw new IOException("Read-only command policy");
 int t=tag++;ByteBuffer cbw=ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN);cbw.putInt(0x43425355).putInt(t).putInt(len).put((byte)0x80).put((byte)0).put((byte)cmd.length).put(cmd);
 if(c.bulkTransfer(out,cbw.array(),31,3000)!=31)throw new IOException("Gagal mengirim perintah USB; periksa konektor OTG");
 byte[] data=new byte[len];int got=c.bulkTransfer(in,data,len,10000);if(got<0)throw new IOException("Transfer USB gagal atau timeout (10 detik); bukan bukti sektor rusak. Sesi dihentikan");if(got!=len)throw new IOException("Data USB tidak lengkap: "+got+"/"+len+" byte; sesi dihentikan");
 byte[] csw=new byte[13];if(c.bulkTransfer(in,csw,13,3000)!=13)throw new IOException("Respons akhir USB tidak lengkap; sesi dihentikan");
 ByteBuffer s=ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN);int signature=s.getInt(),replyTag=s.getInt();long residue=Integer.toUnsignedLong(s.getInt());int result=s.get()&255;if(signature!=0x53425355)throw new IOException("Respons USB tidak valid; sesi dihentikan");if(replyTag!=t)throw new IOException("Respons USB tidak cocok dengan perintah; sesi dihentikan");if(residue!=0)throw new IOException("SCSI residue: "+residue+" byte");if(result!=0)throw new IOException("SCSI status: "+result);if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Dibatalkan");return data;
 }catch(IOException|RuntimeException e){failed=true;throw e;}
 }
 public synchronized void close(){if(closed)return;closed=true;failed=true;try{c.releaseInterface(iface);}finally{c.close();}}
}
