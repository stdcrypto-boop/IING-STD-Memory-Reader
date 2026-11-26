package id.std.memoryreader;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
final class FatFiles {
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
