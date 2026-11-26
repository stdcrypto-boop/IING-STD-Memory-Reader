package id.std.memoryreader;
import java.io.*;
final class FatVolume {
 final UsbDisk disk; final long start,total,fat,data,clusters,rootSector,rootCluster; final int spc,bps,rootEntries; final boolean fat32,fat12; private long cachedFat=-1; private byte[] fatCache;
 static int u16(byte[] b,int p){return (b[p]&255)|((b[p+1]&255)<<8);}
 static long u32(byte[] b,int p){return (long)u16(b,p)|((long)u16(b,p+2)<<16);}
 static FatVolume discover(UsbDisk d)throws IOException{
 byte[] b=d.read(0,1);try{return new FatVolume(d,0,d.blockCount(),b);}catch(InvalidVolume e){}
 boolean mbr=u16(b,510)==0xaa55;
 if(mbr)for(int i=0;i<4;i++){int p=446+i*16;int type=b[p+4]&255;if(type==0||type==0xee)continue;
 long start=u32(b,p+8),len=u32(b,p+12);if(start==0||len==0||start>d.blockCount()-len)continue;
 try{return new FatVolume(d,start,len,d.read(start,1));}catch(InvalidVolume e){} if(len>6){try{FatVolume v=new FatVolume(d,start,len,d.read(start+6,1));if(v.fat32)return v;}catch(InvalidVolume e){}}
 } for(long start:new long[]{0,63,128,2048,4096,8192}){long len=d.blockCount()-start;if(len<=6)continue;try{return new FatVolume(d,start,len,d.read(start,1));}catch(InvalidVolume e){}try{FatVolume v=new FatVolume(d,start,len,d.read(start+6,1));if(v.fat32)return v;}catch(InvalidVolume e){}}throw new InvalidVolume("FAT12/16/32 tidak ditemukan pada lokasi utama atau cadangan; exFAT belum didukung");
 }
 static final class InvalidVolume extends IOException{InvalidVolume(String s){super(s);}}
 FatVolume(UsbDisk d,long start,long limit,byte[] b)throws IOException{
 disk=d;this.start=start;bps=u16(b,11);spc=b[13]&255;int reserved=u16(b,14),copies=b[16]&255;rootEntries=u16(b,17);
 long t=u16(b,19);total=t!=0?t:u32(b,32);long f=u16(b,22);long fs=f!=0?f:u32(b,36);
 if(u16(b,510)!=0xaa55||bps!=d.blockSize()||spc==0||spc>128||(spc&(spc-1))!=0||reserved==0||copies<1||copies>2||fs==0||total==0||total>limit)throw new InvalidVolume("Invalid FAT geometry");
 long rs=((long)rootEntries*32+bps-1)/bps;long overhead=reserved+copies*fs+rs;
 if(overhead>=total)throw new InvalidVolume("Invalid data offset");
 clusters=(total-overhead)/spc;fat32=clusters>=65525;fat12=clusters<4085;
 if(clusters<1||clusters>=0x0ffffff5L||fat32!=(f==0)|| (fat32?rootEntries!=0:rootEntries==0))throw new InvalidVolume("Unsupported FAT type");
 if((fat12?fs*bps*2/3:fs*bps/(fat32?4:2))<clusters+2)throw new InvalidVolume("FAT too small");
 int flags=fat32?u16(b,40):0;int active=(flags&128)!=0?flags&15:0;if(active>=copies)throw new InvalidVolume("Invalid active FAT");
 fat=start+reserved+active*fs;rootSector=start+reserved+copies*fs;data=start+overhead;rootCluster=fat32?u32(b,44):0;
 if(fat32&&(rootCluster<2||rootCluster>=clusters+2))throw new InvalidVolume("Invalid root cluster");
 }
 long sectorOf(long c)throws IOException{if(c<2||c>=clusters+2)throw new InvalidVolume("Cluster outside volume");return data+(c-2)*spc;}
 int fatByte(long off)throws IOException{long sector=fat+off/bps;if(cachedFat!=sector){fatCache=disk.read(sector,1);cachedFat=sector;}return fatCache[(int)(off%bps)]&255;}
 long next(long c)throws IOException{sectorOf(c);long off=fat12?c+c/2:c*(fat32?4:2);long n=fatByte(off)|(fatByte(off+1)<<8);if(fat12)n=(c%2==0?n:n>>4)&4095;else if(fat32)n=(n|((long)fatByte(off+2)<<16)|((long)fatByte(off+3)<<24))&0x0fffffffL;if(n>=(fat12?0xff8:fat32?0x0ffffff8L:0xfff8L))return -1;if(n>=(fat12?0xff0:fat32?0x0ffffff0L:0xfff0L))throw new InvalidVolume("Reserved or bad cluster");sectorOf(n);return n;}
}
