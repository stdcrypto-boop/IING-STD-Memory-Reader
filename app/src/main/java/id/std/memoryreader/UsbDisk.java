package id.std.memoryreader;
import android.hardware.usb.*;
import java.io.*;
import java.nio.*;
final class UsbDisk implements Closeable {
 private final UsbDeviceConnection c; private final UsbInterface iface; private final UsbEndpoint in,out; private int tag=1; private long blocks; private int size; private boolean failed;
 UsbDisk(UsbManager m,UsbDevice d,UsbInterface f)throws IOException {
 if(f.getInterfaceClass()!=8||f.getInterfaceSubclass()!=6||f.getInterfaceProtocol()!=80)throw new IOException("Unsupported protocol");
 UsbEndpoint a=null,b=null;for(int i=0;i<f.getEndpointCount();i++){UsbEndpoint e=f.getEndpoint(i);if(e.getType()==2){if(e.getDirection()==128)a=e;else b=e;}}
 if(a==null||b==null)throw new IOException("Missing bulk endpoints");in=a;out=b;iface=f;c=m.openDevice(d);if(c==null)throw new IOException("USB permission unavailable");
 if(!c.claimInterface(f,false)){c.close();throw new IOException("USB interface busy");}
 try{byte[] cmd=new byte[10];cmd[0]=0x25;ByteBuffer cap=ByteBuffer.wrap(command(cmd,8));blocks=Integer.toUnsignedLong(cap.getInt())+1;size=cap.getInt();if(size<512||size>4096||(size&(size-1))!=0||blocks==4294967296L)throw new IOException("Unsupported capacity");}catch(IOException|RuntimeException e){close();throw e;}
 }
 long blockCount(){return blocks;} int blockSize(){return size;}
 synchronized byte[] read(long lba,int count)throws IOException{
 if(lba<0||count<1||count>Math.min(32,16384/size)||lba>blocks-count)throw new IOException("Read outside media");
 byte[] cmd=new byte[10];ByteBuffer b=ByteBuffer.wrap(cmd);b.put(0,(byte)0x28);b.putInt(2,(int)lba);b.putShort(7,(short)count);return command(cmd,count*size);
 }
 private byte[] command(byte[] cmd,int len)throws IOException{
 if(failed)throw new IOException("Transport stopped");try{
 if(cmd[0]!=0x25&&cmd[0]!=0x28)throw new IOException("Read-only command policy");
 int t=tag++;ByteBuffer cbw=ByteBuffer.allocate(31).order(ByteOrder.LITTLE_ENDIAN);cbw.putInt(0x43425355).putInt(t).putInt(len).put((byte)0x80).put((byte)0).put((byte)cmd.length).put(cmd);
 if(c.bulkTransfer(out,cbw.array(),31,3000)!=31)throw new IOException("CBW failed");
 byte[] data=new byte[len];if(c.bulkTransfer(in,data,len,5000)!=len)throw new IOException("Short data read");
 byte[] csw=new byte[13];if(c.bulkTransfer(in,csw,13,3000)!=13)throw new IOException("Missing CSW");
 ByteBuffer s=ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN);if(s.getInt()!=0x53425355||s.getInt()!=t||s.getInt()!=0||s.get()!=0)throw new IOException("SCSI read failed");return data;
 }catch(IOException|RuntimeException e){failed=true;throw e;}
 }
 public synchronized void close(){failed=true;c.releaseInterface(iface);c.close();}
}
