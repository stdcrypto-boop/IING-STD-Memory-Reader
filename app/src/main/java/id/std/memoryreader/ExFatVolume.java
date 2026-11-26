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
 if(set.length<96||set.length%32!=0||(set[0]&255)!=0x85||(set[1]&255)!=set.length/32-1||(set[32]&255)!=0xc0)throw new IOException("Invalid exFAT entry set");
 int sum=0;for(int i=0;i<set.length;i++)if(i!=2&&i!=3)sum=(((sum>>>1)|((sum&1)<<15))+(set[i]&255))&65535;
 if(sum!=FatVolume.u16(set,2))throw new IOException("exFAT entry checksum");
 int chars=set[35]&255;if(chars==0||chars>255)throw new IOException("Invalid name");StringBuilder name=new StringBuilder();
 for(int p=64;p<set.length&&name.length()<chars;p+=32){if((set[p]&255)!=0xc1)throw new IOException("Missing name entry");for(int j=2;j<32&&name.length()<chars;j+=2)name.append((char)FatVolume.u16(set,p+j));}
 if(name.length()!=chars)throw new IOException("Truncated name");long size=u64(set,56),valid=u64(set,40),cluster=u32(set,52);if(size<0||valid<0||valid>size)throw new IOException("Invalid file length");
 return new Entry(name.toString(),cluster,size,valid,(FatVolume.u16(set,4)&16)!=0,(set[33]&2)!=0);
 }
}
