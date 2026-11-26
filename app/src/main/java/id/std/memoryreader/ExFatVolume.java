package id.std.memoryreader;
import java.io.*;
import java.util.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
final class ExFatVolume {
 final UsbDisk disk; final long start,total,fat,heap,clusters,root; final int bps,spc;
 static long u32(byte[] b,int p){return FatVolume.u32(b,p);}
 static long u64(byte[] b,int p){return ByteBuffer.wrap(b,p,8).order(ByteOrder.LITTLE_ENDIAN).getLong();}
 ExFatVolume(UsbDisk d,long start,byte[] b)throws IOException{
 disk=d;this.start=start;
 if(!new String(b,3,8,StandardCharsets.US_ASCII).equals("EXFAT   ")||FatVolume.u16(b,510)!=0xaa55)throw new IOException("Not exFAT");
 int shift=b[108]&255,cs=b[109]&255;if(shift<9||shift>12||cs>25-shift)throw new IOException("Invalid exFAT shifts");
 bps=1<<shift;spc=1<<cs;total=u64(b,72);long fo=u32(b,80),fl=u32(b,84),ho=u32(b,88);clusters=u32(b,92);root=u32(b,96);
 int copies=b[110]&255,active=FatVolume.u16(b,106)&1;
 if(bps!=d.blockSize()||total<=0||start<0||start>d.blockCount()-total||copies<1||copies>2||active>=copies||fo<24||fl==0||ho<fo+fl*copies||ho>=total||clusters<1||clusters>(total-ho)/spc||fl*bps/4<clusters+2||root<2||root>=clusters+2)throw new IOException("Invalid exFAT geometry");
 fat=start+fo+active*fl;heap=start+ho;
 }
 long sector(long c)throws IOException{if(c<2||c>=clusters+2)throw new IOException("Invalid exFAT cluster");return heap+(c-2)*spc;}
 long next(long c)throws IOException{sector(c);long off=c*4;long n=u32(disk.read(fat+off/bps,1),(int)(off%bps));if(n>=0xfffffff8L)return -1;sector(n);return n;}
 static final class Entry {final String name;final long cluster,size,validSize;final boolean directory,contiguous;Entry(String n,long c,long s,long v,boolean d,boolean t){name=n;cluster=c;size=s;validSize=v;directory=d;contiguous=t;}}
 static Entry parse(byte[] set)throws IOException{
 if(set.length<96||set.length>608||set.length%32!=0||(set[0]&255)!=0x85||(set[1]&255)!=set.length/32-1||(set[32]&255)!=0xc0)throw new IOException("Invalid exFAT entry set");
 int sum=0;for(int i=0;i<set.length;i++)if(i!=2&&i!=3)sum=(((sum>>>1)|((sum&1)<<15))+(set[i]&255))&65535;
 if(sum!=FatVolume.u16(set,2))throw new IOException("exFAT entry checksum");
 int chars=set[35]&255;if(chars==0||chars>255)throw new IOException("Invalid name");StringBuilder name=new StringBuilder();
 for(int p=64;p<set.length&&name.length()<chars;p+=32){if((set[p]&255)!=0xc1)throw new IOException("Missing name entry");for(int j=2;j<32&&name.length()<chars;j+=2)name.append((char)FatVolume.u16(set,p+j));}
 if(name.length()!=chars)throw new IOException("Truncated name");for(int i=0;i<name.length();i++){char ch=name.charAt(i);if(ch<32||ch==47||ch==92)throw new IOException("Invalid filename character");}long size=u64(set,56),valid=u64(set,40),cluster=u32(set,52);if(size<0||valid<0||valid>size)throw new IOException("Invalid file length");
 return new Entry(name.toString(),cluster,size,valid,(FatVolume.u16(set,4)&16)!=0,(set[33]&2)!=0);
 }

 static ExFatVolume discover(UsbDisk d)throws IOException{
 byte[] m=d.read(0,1);ArrayList<Long> starts=new ArrayList<>();starts.add(0L);
 if(FatVolume.u16(m,510)==0xaa55)for(int i=0;i<4;i++){int p=446+i*16;long n=u32(m,p+8),len=u32(m,p+12);if((m[p+4]&255)!=0&&n>0&&len>0&&n<=d.blockCount()-len&&!starts.contains(n))starts.add(n);}
 for(long n:starts){byte[] boot=n==0?m:d.read(n,1);if(new String(boot,3,8,StandardCharsets.US_ASCII).equals("EXFAT   "))return new ExFatVolume(d,n,boot);}
 throw new FatVolume.InvalidVolume("exFAT tidak ditemukan");
 }
 interface Visitor{void accept(Entry e)throws IOException;}
 static void check()throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
 void list(Entry directory,Visitor visitor)throws IOException{
 long c=directory==null?root:directory.cluster, remaining=directory==null?Long.MAX_VALUE:directory.validSize;
 boolean contiguous=directory!=null&&directory.contiguous;Set<Long> seen=new HashSet<>();ByteArrayOutputStream set=null;int pending=0,entries=0;
 while(c!=-1&&remaining>=32){check();if(seen.size()>=16384||!seen.add(c))throw new IOException("Directory chain limit");long base=sector(c);
 for(int i=0;i<spc&&remaining>=32;i++){check();byte[] data=disk.read(base+i,1);int limit=(int)Math.min(remaining,bps);
 for(int p=0;p+32<=limit;p+=32){int type=data[p]&255;if(type==0){if(pending!=0)throw new EOFException("Truncated directory entry");return;}
 if(pending>0){set.write(data,p,32);if(--pending==0){Entry e=parse(set.toByteArray());validateEntry(e);if(++entries>100000)throw new IOException("Directory entry limit");visitor.accept(e);set=null;}continue;}
 if(type==0x85){pending=data[p+1]&255;if(pending<2||pending>18)throw new IOException("Invalid secondary count");set=new ByteArrayOutputStream();set.write(data,p,32);}
 }remaining-=limit;}
 if(remaining>=32)c=contiguous?c+1:next(c);
 }if(pending!=0)throw new EOFException("Truncated directory entry");
 }
 void validateEntry(Entry e)throws IOException{
 long clusterBytes=(long)bps*spc;
 if(e.size>clusters*clusterBytes)throw new IOException("File length exceeds volume");
 if(e.directory&&(e.size==0||e.validSize!=e.size))throw new IOException("Invalid directory length");
 if(e.size>0){sector(e.cluster);if(e.contiguous&&((e.size-1)/clusterBytes)>=clusters+2-e.cluster)throw new IOException("Contiguous file exceeds volume");}
 }
 void copy(Entry e,OutputStream out)throws IOException{
 validateEntry(e);if(e.directory)throw new IOException("Not a file");long remaining=e.validSize,c=e.cluster;Set<Long> seen=new HashSet<>();
 while(remaining>0){check();if(seen.size()>=1048576||!seen.add(c))throw new IOException("File chain limit");long base=sector(c);
 for(int i=0;i<spc&&remaining>0;){check();int blocks=(int)Math.min(Math.min(spc-i,16384/bps),1+(remaining-1)/bps);byte[] data=disk.read(base+i,blocks);int n=(int)Math.min(remaining,data.length);out.write(data,0,n);remaining-=n;i+=blocks;}
 if(remaining>0){c=e.contiguous?c+1:next(c);if(c==-1)throw new EOFException("Truncated file");}}
 byte[] zeros=new byte[4096];for(long n=e.size-e.validSize;n>0;){check();int k=(int)Math.min(n,zeros.length);out.write(zeros,0,k);n-=k;}
 }
}
