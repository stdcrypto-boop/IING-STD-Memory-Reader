package id.std.memoryreader;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
final class FatFiles {
 static FatFiles discover(UsbDisk d)throws IOException{byte[] m=d.read(0,1);java.util.LinkedHashMap<Long,Long> candidates=new java.util.LinkedHashMap<>();candidates.put(0L,d.blockCount());if(FatVolume.u16(m,510)==0xaa55)for(int i=0;i<4;i++){int p=446+i*16;long start=FatVolume.u32(m,p+8),len=FatVolume.u32(m,p+12);int type=m[p+4]&255;if(type!=0&&type!=0xee&&start>0&&len>0&&start<=d.blockCount()-len)candidates.put(start,len);}for(java.util.Map.Entry<Long,Long> e:candidates.entrySet()){check();long start=e.getKey(),len=e.getValue();byte[] boot=start==0?m:d.read(start,1);FatFiles found=probe(d,start,len,boot);if(found!=null)return found;}java.util.Set<Long> alreadyProbed=new java.util.HashSet<>(candidates.keySet());for(long offset:new long[]{63,128,2048,4096,8192})if(offset<d.blockCount()&&!candidates.containsKey(offset))candidates.put(offset,d.blockCount()-offset);for(java.util.Map.Entry<Long,Long> e:candidates.entrySet()){check();long start=e.getKey(),len=e.getValue();if(!alreadyProbed.contains(start)){FatFiles found=probe(d,start,len,d.read(start,1));if(found!=null)return found;}if(len>6){try{FatVolume v=new FatVolume(d,start,len,d.read(start+6,1));if(v.fat32)return new FatFiles(v);}catch(FatVolume.InvalidVolume ignored){}}if(len>12){byte[] boot=d.read(start+12,1);if(isEx(boot)){try{ExFatVolume v=new ExFatVolume(d,start,boot);if(v.total<=len)return new FatFiles(v);}catch(IOException ignored){}}}}// Bounded search for displaced boot records; stop on any transport failure.
 for(long offset=1;offset<Math.min(4096L,d.blockCount());offset+=32){
 check();int count=(int)Math.min(Math.min(32,16384/d.blockSize()),Math.min(4096L,d.blockCount())-offset);
 if(count<=0)break;
 // Advance by actual transfer size for devices with larger logical sectors.
 byte[] chunk=d.read(offset,count);
 for(int j=0;j<count;j++){int pos=j*d.blockSize();if((chunk[pos+510]&255)!=85||(chunk[pos+511]&255)!=170)continue;
 long start=offset+j;if(candidates.containsKey(start))continue;
 byte[] boot=Arrays.copyOfRange(chunk,pos,pos+d.blockSize());
 // Require the recorded partition offset to agree with the candidate.
 long recorded=isEx(boot)?ExFatVolume.u64(boot,64):FatVolume.u32(boot,28);
 if(recorded!=start)continue;
 FatFiles found=probe(d,start,d.blockCount()-start,boot);if(found!=null)return found;
 }
 offset+=count-32;
 }
 throw new FatVolume.InvalidVolume("USB terhubung, tetapi struktur FAT/exFAT tidak dikenali. Jangan format.");}
 static FatFiles discoverAt(UsbDisk d,long start,boolean backup)throws IOException{
 if(start<0||start>=d.blockCount())throw new IOException("Sektor partisi di luar kapasitas");
 long len=d.blockCount()-start;
 if(!backup){FatFiles f=probe(d,start,len,d.read(start,1));if(f!=null)return f;}
 else {
 if(len>6){try{FatVolume v=new FatVolume(d,start,len,d.read(start+6,1));if(v.fat32)return new FatFiles(v);}catch(FatVolume.InvalidVolume ignored){}}
 if(len>12){byte[] boot=d.read(start+12,1);if(isEx(boot)){ExFatVolume v=new ExFatVolume(d,start,boot);if(v.total<=len)return new FatFiles(v);}}
 }
 throw new FatVolume.InvalidVolume("Struktur folder tidak dikenali pada posisi pilihan");
 }
 static boolean isEx(byte[] b){return new String(b,3,8,StandardCharsets.US_ASCII).equals("EXFAT   ");}
 static FatFiles probe(UsbDisk d,long start,long len,byte[] boot)throws IOException{if(isEx(boot)){try{ExFatVolume v=new ExFatVolume(d,start,boot);if(v.total<=len)return new FatFiles(v);}catch(IOException ignored){}return null;}try{return new FatFiles(new FatVolume(d,start,len,boot));}catch(FatVolume.InvalidVolume ignored){return null;}}
 final FatVolume v; final ExFatVolume ex; final Map<Long,ExFatVolume.Entry> dirs=new HashMap<>(); FatFiles(FatVolume v){this.v=v;ex=null;} FatFiles(ExFatVolume ex){this.ex=ex;v=null;} String type(){return ex!=null?"exFAT":v.fat32?"FAT32":v.fat12?"FAT12":"FAT16";}
 static final class Entry {
 final String name; final long cluster,size; final boolean directory; ExFatVolume.Entry exEntry;
 Entry(String n,long c,long s,boolean d){name=n;cluster=c;size=s;directory=d;}
 }
 interface Visitor {void accept(Entry e)throws IOException;}
 private static void check()throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled");}
 void list(long cluster,Visitor visitor)throws IOException{
 if(ex!=null){ExFatVolume.Entry dir=dirs.get(cluster);if(cluster!=0&&dir==null)throw new IOException("Folder unavailable");ex.list(dir,e->{Entry item=new Entry(e.name,e.cluster,e.size,e.directory);item.exEntry=e;if(e.directory){if(dirs.size()>=100000)throw new IOException("Directory cache limit");dirs.put(e.cluster,e);}visitor.accept(item);});return;} Parser p=new Parser(visitor);
 if(!v.fat32&&cluster==0){long sectors=((long)v.rootEntries*32+v.bps-1)/v.bps;for(long i=0;i<sectors;i++){check();if(p.parse(v.disk.read(v.rootSector+i,1)))return;}return;}
 long c=cluster==0?v.rootCluster:cluster;Set<Long> seen=new HashSet<>();
 while(c!=-1){check();if(seen.size()>=16384||!seen.add(c))throw new IOException("Directory chain limit or cycle");long base=v.sectorOf(c);for(int i=0;i<v.spc;i++){check();if(p.parse(v.disk.read(base+i,1)))return;}c=v.next(c);}
 }
 final class Parser{
 final Visitor visitor; String[] parts; int expected,checksum; int entries;
 Parser(Visitor visitor){this.visitor=visitor;}
 boolean parse(byte[] b)throws IOException{
 for(int i=0;i+32<=b.length;i+=32){
 int first=b[i]&255,attr=b[i+11]&255;if(first==0)return true;if(first==229){parts=null;continue;}
 if(attr==15){int seq=first&31;if((first&64)!=0){parts=seq>=1&&seq<=20?new String[seq]:null;expected=seq;checksum=b[i+13]&255;}
 if(parts==null||seq!=expected||seq<1||seq>parts.length||checksum!=(b[i+13]&255)||b[i+12]!=0||FatVolume.u16(b,i+26)!=0){parts=null;continue;}
 StringBuilder s=new StringBuilder();for(int off:new int[]{1,3,5,7,9,14,16,18,20,22,24,28,30}){int ch=FatVolume.u16(b,i+off);if(ch==0||ch==65535)break;s.append((char)ch);}parts[seq-1]=s.toString();expected--;continue;}
 int sum=0;for(int j=0;j<11;j++)sum=(((sum&1)<<7)+(sum>>1)+(b[i+j]&255))&255;
 String name=new String(b,i,8,StandardCharsets.ISO_8859_1).trim();String ext=new String(b,i+8,3,StandardCharsets.ISO_8859_1).trim();if(!ext.isEmpty())name+="."+ext;
 if(parts!=null&&expected==0&&sum==checksum){StringBuilder s=new StringBuilder();for(String x:parts)s.append(x);if(s.length()>0)name=s.toString();}parts=null;
 if((attr&8)!=0||name.equals(".")||name.equals("..")||name.isEmpty())continue;
 if(++entries>100000)throw new IOException("Directory entry limit");
 long c=FatVolume.u16(b,i+26);if(v.fat32)c|=(long)(FatVolume.u16(b,i+20)&4095)<<16;
 visitor.accept(new Entry(name,c,FatVolume.u32(b,i+28),(attr&16)!=0));
 }return false;
 }
 }
 void copy(Entry e,OutputStream out)throws IOException{
 if(ex!=null){if(e.exEntry==null)throw new IOException("Missing exFAT entry");ex.copy(e.exEntry,out);return;} if(e.directory)throw new IOException("Not a file");long remaining=e.size,c=e.cluster;Set<Long> seen=new HashSet<>();
 while(remaining>0){check();if(seen.size()>=1048576||!seen.add(c))throw new IOException("File chain limit or cycle");long base=v.sectorOf(c);
 for(int i=0;i<v.spc&&remaining>0;i++){check();byte[] b=v.disk.read(base+i,1);int n=(int)Math.min(remaining,b.length);out.write(b,0,n);remaining-=n;}
 if(remaining>0){c=v.next(c);if(c==-1)throw new EOFException("Truncated file chain");}
 }
 }
}
