package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * R8.1 local-config patcher for seven custom pet colour variants whose current
 * item records omit the hover text already established for their base family.
 * It patches only the MessagePack `hover` member in i.bin and raw-copies every
 * unrelated MessagePack value. No client.jar byte is modified.
 */
public final class ConfigHoverPatchTool {
    private static final int[] BEHEMOTHS={24016,24017,24018,24019};
    private static final int[] EVIL_WOLPERS={27340,27341,27342};
    private static final int BASE_BEHEMOTH=25425;
    private static final String EVIL_HOVER="\n- Grants +20% range and magic accuracy bonus\n- Keeps all stats boosted (with +7 magic levels) every 10 secs\n- Increases all @yel@damage and accuracy by +2.5%\n- Attacking with a switched combat style grants\nan additional @cya@+5% damage @whi@and@cya@ +10% accuracy\nfor @cya@5 seconds.@whi@ An icon displays above evil wolper\nto indicate this bonus.\n@or1@(Must switch within 6 seconds of your last attack)";
    private static final String BEHEMOTH_FALLBACK="For every 50 damage you deal, you gain @cya@Spirit charge\n- @yel@1st charge:@whi@ Increases damage by 15% & smites 1/6\n- @yel@2nd charge:@whi@ Increases defences by 25% & smites 1/5\n- @yel@3rd charge:@whi@ Increases accuracy by 25% & smites 1/4\n@or1@(Resets if you stop attacking for 20+ secs, or swap pets)";

    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("usage: ConfigHoverPatchTool patch|verify <configs.zip|i.bin>");
        Path file=Paths.get(args[1]).toAbsolutePath();
        if(!Files.isRegularFile(file))throw new FileNotFoundException(file.toString());
        boolean bare="i.bin".equalsIgnoreCase(file.getFileName().toString());
        if("patch".equalsIgnoreCase(args[0])){ if(bare)patchBin(file); else patchZip(file); }
        else if("verify".equalsIgnoreCase(args[0])){ if(bare)verifyBin(file); else verifyZip(file); }
        else throw new IllegalArgumentException("unknown mode "+args[0]);
    }

    private static void patchBin(Path bin)throws Exception{
        byte[] original=Files.readAllBytes(bin);
        String behemoth=hoverOf(original,BASE_BEHEMOTH);
        if(behemoth==null||behemoth.isEmpty())behemoth=BEHEMOTH_FALLBACK;
        LinkedHashMap<Integer,String> changes=new LinkedHashMap<>();
        for(int id:BEHEMOTHS)changes.put(id,behemoth);
        for(int id:EVIL_WOLPERS)changes.put(id,EVIL_HOVER);
        byte[] patched=patchItemMap(original,changes);
        Path tmp=bin.resolveSibling(bin.getFileName()+".r81.tmp");
        Files.write(tmp,patched);
        try{Files.move(tmp,bin,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,bin,StandardCopyOption.REPLACE_EXISTING);}
        verifyBin(bin);
        System.out.println("V5181_CONFIG_HOVER_PATCH_PASS file="+bin+" entries=7 iBinBytes="+patched.length+
            " families=SCOOBY_BEHEMOTH<-UNHOLY_BEHEMOTH_25425,RESVANO_EVIL_WOLPER<-EVIL_WOLPER_24238");
    }

    private static void verifyBin(Path bin)throws Exception{
        byte[] i=Files.readAllBytes(bin);
        verifyIbin(i,bin.toString());
    }

    private static void patchZip(Path zip)throws Exception{
        byte[] originalI=null; String iName=null;
        try(ZipFile z=new ZipFile(zip.toFile())){
            Enumeration<? extends ZipEntry> en=z.entries();
            while(en.hasMoreElements()){
                ZipEntry e=en.nextElement();
                String base=Paths.get(e.getName().replace('\\','/')).getFileName().toString();
                if("i.bin".equalsIgnoreCase(base)){
                    if(originalI!=null)throw new IOException("multiple i.bin entries");
                    originalI=readAll(z.getInputStream(e));iName=e.getName();
                }
            }
        }
        if(originalI==null)throw new IOException("i.bin not found in "+zip);
        String behemoth=hoverOf(originalI,BASE_BEHEMOTH);
        if(behemoth==null||behemoth.isEmpty())behemoth=BEHEMOTH_FALLBACK;
        LinkedHashMap<Integer,String> changes=new LinkedHashMap<>();
        for(int id:BEHEMOTHS)changes.put(id,behemoth);
        for(int id:EVIL_WOLPERS)changes.put(id,EVIL_HOVER);
        byte[] patched=patchItemMap(originalI,changes);

        Path tmp=zip.resolveSibling(zip.getFileName()+".r81.tmp");
        try(ZipFile z=new ZipFile(zip.toFile()); ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(tmp))){
            Enumeration<? extends ZipEntry> en=z.entries();
            while(en.hasMoreElements()){
                ZipEntry old=en.nextElement();
                ZipEntry ne=new ZipEntry(old.getName());
                ne.setTime(old.getTime());
                if(old.getComment()!=null)ne.setComment(old.getComment());
                if(old.getExtra()!=null)ne.setExtra(old.getExtra());
                out.putNextEntry(ne);
                if(old.getName().equals(iName))out.write(patched);
                else try(InputStream in=z.getInputStream(old)){copy(in,out);}
                out.closeEntry();
            }
        }catch(Throwable t){Files.deleteIfExists(tmp);throw t;}
        try{Files.move(tmp,zip,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,zip,StandardCopyOption.REPLACE_EXISTING);}
        verifyZip(zip);
        System.out.println("V5181_CONFIG_HOVER_PATCH_PASS file="+zip+" entries=7 iBinBytes="+patched.length+
            " families=SCOOBY_BEHEMOTH<-UNHOLY_BEHEMOTH_25425,RESVANO_EVIL_WOLPER<-EVIL_WOLPER_24238");
    }

    private static void verifyZip(Path zip)throws Exception{
        byte[] i=null;
        try(ZipFile z=new ZipFile(zip.toFile())){
            Enumeration<? extends ZipEntry> en=z.entries();
            while(en.hasMoreElements()){
                ZipEntry e=en.nextElement();
                String base=Paths.get(e.getName().replace('\\','/')).getFileName().toString();
                if("i.bin".equalsIgnoreCase(base)){i=readAll(z.getInputStream(e));break;}
            }
        }
        if(i==null)throw new IOException("i.bin not found in "+zip);
        verifyIbin(i,zip.toString());
    }

    private static void verifyIbin(byte[] i,String label)throws Exception{
        String behemoth=hoverOf(i,BASE_BEHEMOTH);if(behemoth==null||behemoth.isEmpty())behemoth=BEHEMOTH_FALLBACK;
        for(int id:BEHEMOTHS)requireEq(hoverOf(i,id),behemoth,"item "+id);
        for(int id:EVIL_WOLPERS)requireEq(hoverOf(i,id),EVIL_HOVER,"item "+id);
        System.out.println("V5181_CONFIG_HOVER_VERIFY_PASS file="+label+" items=24016,24017,24018,24019,27340,27341,27342");
    }

    private static void requireEq(String a,String b,String label){if(!Objects.equals(a,b))throw new IllegalStateException(label+" hover mismatch");}

    static byte[] patchItemMap(byte[] src,Map<Integer,String> changes)throws IOException{
        Header h=mapHeader(src,0); ByteArrayOutputStream out=new ByteArrayOutputStream(src.length+4096); writeMapHeader(out,h.count);
        int p=h.end; HashSet<Integer> found=new HashSet<>();
        for(int n=0;n<h.count;n++){
            int ks=p,ke=skip(src,ks), id=intKey(src,ks); int vs=ke,ve=skip(src,vs); p=ve;
            out.write(src,ks,ke-ks);
            String replacement=changes.get(id);
            if(replacement!=null){out.write(patchRecord(src,vs,replacement));found.add(id);} else out.write(src,vs,ve-vs);
        }
        if(p!=src.length)throw new IOException("trailing bytes after top-level i.bin map: "+(src.length-p));
        if(found.size()!=changes.size()){HashSet<Integer> miss=new HashSet<>(changes.keySet());miss.removeAll(found);throw new IOException("target item records missing: "+miss);}
        return out.toByteArray();
    }

    static String hoverOf(byte[] src,int wanted)throws IOException{
        Header h=mapHeader(src,0);int p=h.end;
        for(int n=0;n<h.count;n++){
            int ks=p,ke=skip(src,ks),id=intKey(src,ks);int vs=ke,ve=skip(src,vs);p=ve;
            if(id==wanted)return stringMember(src,vs,"hover");
        }
        return null;
    }

    private static byte[] patchRecord(byte[] src,int pos,String hover)throws IOException{
        Header h=mapHeader(src,pos);ArrayList<Slice> entries=new ArrayList<>();int p=h.end;boolean replaced=false;
        for(int i=0;i<h.count;i++){
            int ks=p,ke=skip(src,ks);String key=stringValue(src,ks);int vs=ke,ve=skip(src,vs);p=ve;
            entries.add(new Slice(ks,ke,vs,ve,"hover".equals(key)));if("hover".equals(key))replaced=true;
        }
        ByteArrayOutputStream out=new ByteArrayOutputStream(p-pos+hover.length()+16);writeMapHeader(out,h.count+(replaced?0:1));
        for(Slice e:entries){out.write(src,e.ks,e.ke-e.ks);if(e.hover)out.write(encodeString(hover));else out.write(src,e.vs,e.ve-e.vs);}
        if(!replaced){out.write(encodeString("hover"));out.write(encodeString(hover));}
        return out.toByteArray();
    }

    private static String stringMember(byte[] src,int pos,String wanted)throws IOException{
        Header h=mapHeader(src,pos);int p=h.end;
        for(int i=0;i<h.count;i++){
            int ks=p,ke=skip(src,ks);String key=stringValue(src,ks);int vs=ke,ve=skip(src,vs);p=ve;
            if(wanted.equals(key))return stringValue(src,vs);
        }
        return null;
    }

    private static Header mapHeader(byte[] a,int p)throws IOException{
        int t=u8(a,p); if((t&0xf0)==0x80)return new Header(t&15,p+1);
        if(t==0xde)return new Header(u16(a,p+1),p+3);
        if(t==0xdf){long n=u32(a,p+1);if(n>Integer.MAX_VALUE)throw new IOException("map too large");return new Header((int)n,p+5);} throw new IOException("expected map at "+p+" tag=0x"+Integer.toHexString(t));
    }
    private static void writeMapHeader(OutputStream o,int n)throws IOException{if(n<16)o.write(0x80|n);else if(n<=65535){o.write(0xde);o.write(n>>>8);o.write(n);}else{o.write(0xdf);write32(o,n);}}
    private static byte[] encodeString(String s)throws IOException{byte[] b=s.getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream o=new ByteArrayOutputStream(b.length+5);int n=b.length;if(n<32)o.write(0xa0|n);else if(n<=255){o.write(0xd9);o.write(n);}else if(n<=65535){o.write(0xda);o.write(n>>>8);o.write(n);}else{o.write(0xdb);write32(o,n);}o.write(b);return o.toByteArray();}
    private static String stringValue(byte[] a,int p)throws IOException{int t=u8(a,p),n,off;if((t&0xe0)==0xa0){n=t&31;off=p+1;}else if(t==0xd9){n=u8(a,p+1);off=p+2;}else if(t==0xda){n=u16(a,p+1);off=p+3;}else if(t==0xdb){long z=u32(a,p+1);if(z>Integer.MAX_VALUE)throw new IOException("string too large");n=(int)z;off=p+5;}else return null;bounds(a,off,n);return new String(a,off,n,StandardCharsets.UTF_8);}
    private static int intKey(byte[] a,int p)throws IOException{int t=u8(a,p);if(t<=0x7f)return t;if(t==0xcc)return u8(a,p+1);if(t==0xcd)return u16(a,p+1);if(t==0xce){long x=u32(a,p+1);return x<=Integer.MAX_VALUE?(int)x:-1;}if(t==0xd0)return (byte)u8(a,p+1);if(t==0xd1)return (short)u16(a,p+1);if(t==0xd2)return (int)u32(a,p+1);String s=stringValue(a,p);if(s!=null)try{return Integer.parseInt(s);}catch(Exception ignored){}return -1;}

    static int skip(byte[] a,int p)throws IOException{
        int t=u8(a,p); if(t<=0x7f||t>=0xe0)return p+1;
        if((t&0xe0)==0xa0)return checked(a,p+1,t&31); if((t&0xf0)==0x90){int q=p+1;for(int i=0;i<(t&15);i++)q=skip(a,q);return q;} if((t&0xf0)==0x80){int q=p+1;for(int i=0;i<(t&15)*2;i++)q=skip(a,q);return q;}
        switch(t){
            case 0xc0:case 0xc2:case 0xc3:return p+1;
            case 0xc4:return checked(a,p+2,u8(a,p+1)); case 0xc5:return checked(a,p+3,u16(a,p+1)); case 0xc6:{long n=u32(a,p+1);return checkedLong(a,p+5,n);}
            case 0xc7:return checked(a,p+3,u8(a,p+1)); case 0xc8:return checked(a,p+4,u16(a,p+1)); case 0xc9:return checkedLong(a,p+6,u32(a,p+1));
            case 0xca:return checked(a,p,5);case 0xcb:return checked(a,p,9);case 0xcc:case 0xd0:return checked(a,p,2);case 0xcd:case 0xd1:return checked(a,p,3);case 0xce:case 0xd2:return checked(a,p,5);case 0xcf:case 0xd3:return checked(a,p,9);
            case 0xd4:return checked(a,p,3);case 0xd5:return checked(a,p,4);case 0xd6:return checked(a,p,6);case 0xd7:return checked(a,p,10);case 0xd8:return checked(a,p,18);
            case 0xd9:return checked(a,p+2,u8(a,p+1));case 0xda:return checked(a,p+3,u16(a,p+1));case 0xdb:return checkedLong(a,p+5,u32(a,p+1));
            case 0xdc:{int n=u16(a,p+1),q=p+3;for(int i=0;i<n;i++)q=skip(a,q);return q;} case 0xdd:{long z=u32(a,p+1);if(z>Integer.MAX_VALUE)throw new IOException("array too large");int q=p+5;for(int i=0;i<(int)z;i++)q=skip(a,q);return q;}
            case 0xde:{int n=u16(a,p+1),q=p+3;for(int i=0;i<n*2;i++)q=skip(a,q);return q;} case 0xdf:{long z=u32(a,p+1);if(z>Integer.MAX_VALUE)throw new IOException("map too large");int q=p+5;for(int i=0;i<(int)z*2;i++)q=skip(a,q);return q;}
            default:throw new IOException("unsupported MessagePack tag 0x"+Integer.toHexString(t)+" at "+p);
        }
    }
    private static int checked(byte[] a,int start,int n)throws IOException{bounds(a,start,n);return start+n;}private static int checkedLong(byte[] a,int start,long n)throws IOException{if(n>Integer.MAX_VALUE)throw new IOException("value too large");return checked(a,start,(int)n);}private static void bounds(byte[] a,int p,int n)throws IOException{if(p<0||n<0||p+n>a.length)throw new EOFException("MessagePack truncated");}
    private static int u8(byte[] a,int p)throws IOException{bounds(a,p,1);return a[p]&255;}private static int u16(byte[] a,int p)throws IOException{return (u8(a,p)<<8)|u8(a,p+1);}private static long u32(byte[] a,int p)throws IOException{return ((long)u8(a,p)<<24)|((long)u8(a,p+1)<<16)|((long)u8(a,p+2)<<8)|u8(a,p+3);}private static void write32(OutputStream o,long v)throws IOException{o.write((int)(v>>>24));o.write((int)(v>>>16));o.write((int)(v>>>8));o.write((int)v);}
    private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();copy(in,o);return o.toByteArray();}private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];for(int n;(n=in.read(b))>=0;)if(n>0)out.write(b,0,n);}
    private static final class Header{final int count,end;Header(int c,int e){count=c;end=e;}}private static final class Slice{final int ks,ke,vs,ve;final boolean hover;Slice(int a,int b,int c,int d,boolean h){ks=a;ke=b;vs=c;ve=d;hover=h;}}
    private ConfigHoverPatchTool(){}
}
