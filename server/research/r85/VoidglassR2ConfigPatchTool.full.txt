package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Definition-only client-config patcher for the LocalLab Voidglass R2 trial. */
public final class VoidglassR2ConfigPatchTool {
    static final int ITEM_ID=32760, NPC_ID=12000;
    static final int[] SRC={16796,17686,15774,13212,13208,13217,16904,13248,21656,21652,21656,18,22,21658,26,16796,17686,15774,13212,13208,13217,16904,13248,13204,32920,15320,10448,10462,16656,14259};
    // Deliberately custom LocalLab palette composed only from values already present
    // in exact-current Hydra-family definitions. It is NOT claimed as recovered R2 production data.
    static final int[] DST={5,129770,5,130770,923,129770,942,130770,1,129770,920,5,3,923,942,5,923,5,1,130770,5,5,920,5,5,5,923,942,130770,5};
    static final String ITEM_NAME="@cya@Voidglass nistirio";
    static final String NPC_NAME="Voidglass Nistirio";
    static final String HOVER="\n@cya@LocalLab custom content trial@whi@\nVoidglass R2: Hydra-family model 36185\nIdle/walk 8233/8232 | proc 8236 + GFX 4098\n@or1@Not recovered SpawnPK production content.";

    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("usage: VoidglassR2ConfigPatchTool preflight|patch|verify <configDir>");
        Path dir=Paths.get(args[1]).toAbsolutePath();
        Path i=dir.resolve("i.bin"), e=dir.resolve("e.bin"), g=dir.resolve("g.bin");
        if(!Files.isRegularFile(i)||!Files.isRegularFile(e))throw new FileNotFoundException("Expected i.bin/e.bin under "+dir);
        String mode=args[0].toLowerCase(Locale.ROOT);
        if(mode.equals("preflight")){preflight(i,e,g);return;}
        if(mode.equals("patch")){preflight(i,e,g);patchFile(i,true);patchFile(e,false);verify(i,e);return;}
        if(mode.equals("verify")){verify(i,e);return;}
        throw new IllegalArgumentException("unknown mode "+args[0]);
    }

    private static void preflight(Path i,Path e,Path g)throws Exception{
        byte[] ib=Files.readAllBytes(i), eb=Files.readAllBytes(e);
        Record ir=record(ib,"22842"); if(ir==null)throw new IOException("Hydra item anchor 22842 missing");
        requireInt(ir,"modelId",36185); requireString(ir,"name","@gre@Blood hydra pet");
        Record er=record(eb,"4723"); if(er==null)throw new IOException("Hydra NPC anchor 4723 missing");
        requireInt(er,"standAnim",8233);requireInt(er,"walkAnim",8232);requireInt(er,"rotateAnim",8232);requireIntArrayContains(er,"models",36185);requireBool(er,"pet",true);
        checkTarget(ib,String.valueOf(ITEM_ID),encodeItemRecord(),"item");
        checkTarget(eb,String.valueOf(NPC_ID),encodeNpcRecord(),"npc");
        if(Files.isRegularFile(g)){byte[] gb=Files.readAllBytes(g);if(record(gb,"4098")==null)throw new IOException("GFX config anchor 4098 missing from g.bin");}
        System.out.println("VOIDGLASS_R2_CONFIG_PREFLIGHT_PASS iSha256="+sha256(i)+" eSha256="+sha256(e)+" gfx4098="+(Files.isRegularFile(g)?"present":"not_checked")+" item32760=free_or_exact npc12000=free_or_exact anchors=HYDRA_CURRENT_EXACT");
    }

    private static void verify(Path i,Path e)throws Exception{
        byte[] ib=Files.readAllBytes(i),eb=Files.readAllBytes(e);
        Record ir=record(ib,String.valueOf(ITEM_ID)); if(ir==null)throw new IOException("custom item missing");
        requireString(ir,"name",ITEM_NAME); requireInt(ir,"modelId",36185); requireStringArrayValue(ir,"actions",4,"Drop"); requireIntArrayExact(ir,"srcColors",SRC);requireIntArrayExact(ir,"destColors",DST);
        Record er=record(eb,String.valueOf(NPC_ID));if(er==null)throw new IOException("custom NPC missing");
        requireString(er,"name",NPC_NAME);requireIntArrayContains(er,"models",36185);requireInt(er,"scaleWidth",27);requireInt(er,"scaleHeight",27);requireInt(er,"standAnim",8233);requireInt(er,"walkAnim",8232);requireInt(er,"rotateAnim",8232);requireBool(er,"pet",true);requireStringArrayValue(er,"actions",0,"Pick-up");requireIntArrayExact(er,"srcColors",SRC);requireIntArrayExact(er,"destColors",DST);
        System.out.println("VOIDGLASS_R2_CONFIG_VERIFY_PASS item="+ITEM_ID+" npc="+NPC_ID+" model=36185 anims=8233/8232 palette=CUSTOM_V1 iSha256="+sha256(i)+" eSha256="+sha256(e));
    }

    private static void patchFile(Path f,boolean item)throws Exception{
        byte[] src=Files.readAllBytes(f), rec=item?encodeItemRecord():encodeNpcRecord();String key=String.valueOf(item?ITEM_ID:NPC_ID);
        byte[] patched=appendOrExact(src,key,rec,item?"item":"npc");
        if(Arrays.equals(src,patched)){System.out.println("VOIDGLASS_R2_CONFIG_ALREADY_PRESENT file="+f);return;}
        Path tmp=f.resolveSibling(f.getFileName()+".voidglass-r2.tmp");Files.write(tmp,patched,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        try{Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING);}
        System.out.println("VOIDGLASS_R2_CONFIG_PATCHED file="+f+" bytes="+patched.length);
    }

    static byte[] appendOrExact(byte[] src,String key,byte[] rec,String label)throws Exception{
        Header h=mapHeader(src,0);int p=h.end;ByteArrayOutputStream out=new ByteArrayOutputStream(src.length+rec.length+64);boolean found=false;
        writeMapHeader(out,h.count+1);
        for(int n=0;n<h.count;n++){
            int ks=p,ke=skip(src,ks);String k=stringValue(src,ks);int vs=ke,ve=skip(src,vs);p=ve;
            if(key.equals(k)){
                found=true;byte[] existing=Arrays.copyOfRange(src,vs,ve);if(!Arrays.equals(existing,rec))throw new IOException("custom "+label+" ID collision key="+key+" existing record differs; refusing mutation");
            }
            out.write(src,ks,ve-ks);
        }
        if(p!=src.length)throw new IOException("trailing bytes in top-level map");
        if(found)return src;
        out.write(encodeString(key));out.write(rec);return out.toByteArray();
    }
    private static void checkTarget(byte[] src,String key,byte[] expected,String label)throws Exception{Record r=record(src,key);if(r==null)return;byte[] actual=Arrays.copyOfRange(src,r.start,r.end);if(!Arrays.equals(actual,expected))throw new IOException("custom "+label+" ID collision key="+key+" existing record differs");}

    static byte[] encodeItemRecord()throws Exception{
        LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("name",ITEM_NAME);m.put("actions",new Object[]{null,null,null,null,"Drop"});m.put("srcColors",SRC);m.put("destColors",DST);m.put("modelId",36185);m.put("zoom",9970);m.put("rotations",new int[]{130,1946});m.put("offsets",new int[]{-38,-138});m.put("hover",HOVER);return encodeMap(m);
    }
    static byte[] encodeNpcRecord()throws Exception{
        LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("name",NPC_NAME);m.put("combatLevel",0);m.put("actions",new Object[]{"Pick-up",null,null,null,null});m.put("models",new int[]{36185});m.put("scaleWidth",27);m.put("scaleHeight",27);m.put("srcColors",SRC);m.put("destColors",DST);m.put("size",1);m.put("standAnim",8233);m.put("walkAnim",8232);m.put("rotateAnim",8232);m.put("minimap",false);m.put("priorityRender",false);m.put("ambient",30);m.put("contrast",220);m.put("pet",true);return encodeMap(m);
    }
    private static byte[] encodeMap(LinkedHashMap<String,Object> m)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();writeMapHeader(o,m.size());for(Map.Entry<String,Object>x:m.entrySet()){o.write(encodeString(x.getKey()));writeValue(o,x.getValue());}return o.toByteArray();}
    private static void writeValue(OutputStream o,Object v)throws Exception{
        if(v==null){o.write(0xc0);return;}if(v instanceof Boolean){o.write((Boolean)v?0xc3:0xc2);return;}if(v instanceof Integer){writeInt(o,(Integer)v);return;}if(v instanceof String){o.write(encodeString((String)v));return;}
        if(v instanceof int[]){int[]a=(int[])v;writeArrayHeader(o,a.length);for(int x:a)writeInt(o,x);return;}if(v instanceof Object[]){Object[]a=(Object[])v;writeArrayHeader(o,a.length);for(Object x:a)writeValue(o,x);return;}throw new IOException("unsupported encode type "+v.getClass());
    }
    private static void writeInt(OutputStream o,int v)throws IOException{if(v>=0){if(v<=127)o.write(v);else if(v<=255){o.write(0xcc);o.write(v);}else if(v<=65535){o.write(0xcd);o.write(v>>>8);o.write(v);}else{o.write(0xce);write32(o,v&0xffffffffL);}}else if(v>=-32)o.write(256+v);else if(v>=-128){o.write(0xd0);o.write(v);}else if(v>=-32768){o.write(0xd1);o.write(v>>>8);o.write(v);}else{o.write(0xd2);write32(o,v&0xffffffffL);}}
    private static void writeArrayHeader(OutputStream o,int n)throws IOException{if(n<16)o.write(0x90|n);else if(n<=65535){o.write(0xdc);o.write(n>>>8);o.write(n);}else{o.write(0xdd);write32(o,n);}}

    private static Record record(byte[] a,String wanted)throws Exception{Header h=mapHeader(a,0);int p=h.end;for(int i=0;i<h.count;i++){int ks=p,ke=skip(a,ks);String k=stringValue(a,ks);int vs=ke,ve=skip(a,vs);p=ve;if(wanted.equals(k))return new Record(a,vs,ve);}return null;}
    private static byte[] member(Record r,String wanted)throws Exception{Header h=mapHeader(r.a,r.start);int p=h.end;for(int i=0;i<h.count;i++){int ks=p,ke=skip(r.a,ks);String k=stringValue(r.a,ks);int vs=ke,ve=skip(r.a,vs);p=ve;if(wanted.equals(k))return Arrays.copyOfRange(r.a,vs,ve);}return null;}
    private static void requireInt(Record r,String k,int v)throws Exception{byte[]b=member(r,k);if(b==null||intValue(b,0)!=v)throw new IOException("field "+k+" expected="+v);}
    private static void requireString(Record r,String k,String v)throws Exception{byte[]b=member(r,k);String s=b==null?null:stringValue(b,0);if(!Objects.equals(s,v))throw new IOException("field "+k+" expected="+v+" actual="+s);}
    private static void requireBool(Record r,String k,boolean v)throws Exception{byte[]b=member(r,k);if(b==null||((b[0]&255)==0xc3)!=v)throw new IOException("field "+k+" boolean mismatch");}
    private static void requireIntArrayContains(Record r,String k,int v)throws Exception{int[]a=intArray(member(r,k));for(int x:a)if(x==v)return;throw new IOException("field "+k+" lacks "+v);}
    private static void requireIntArrayExact(Record r,String k,int[]v)throws Exception{int[]a=intArray(member(r,k));if(!Arrays.equals(a,v))throw new IOException("field "+k+" array mismatch");}
    private static void requireStringArrayValue(Record r,String k,int idx,String v)throws Exception{byte[]b=member(r,k);Header h=arrayHeader(b,0);if(idx>=h.count)throw new IOException("field "+k+" too short");int p=h.end;for(int i=0;i<h.count;i++){int e=skip(b,p);if(i==idx){String s=stringValue(b,p);if(!Objects.equals(s,v))throw new IOException("field "+k+"["+idx+"] mismatch actual="+s);return;}p=e;}throw new IOException("field "+k+" index missing");}
    private static int[] intArray(byte[]b)throws Exception{if(b==null)throw new IOException("missing array");Header h=arrayHeader(b,0);int[]a=new int[h.count];int p=h.end;for(int i=0;i<a.length;i++){a[i]=intValue(b,p);p=skip(b,p);}return a;}

    private static Header mapHeader(byte[]a,int p)throws Exception{int t=u8(a,p);if((t&0xf0)==0x80)return new Header(t&15,p+1);if(t==0xde)return new Header(u16(a,p+1),p+3);if(t==0xdf)return new Header((int)u32(a,p+1),p+5);throw new IOException("expected map tag at "+p);}
    private static Header arrayHeader(byte[]a,int p)throws Exception{int t=u8(a,p);if((t&0xf0)==0x90)return new Header(t&15,p+1);if(t==0xdc)return new Header(u16(a,p+1),p+3);if(t==0xdd)return new Header((int)u32(a,p+1),p+5);throw new IOException("expected array tag at "+p);}
    private static void writeMapHeader(OutputStream o,int n)throws IOException{if(n<16)o.write(0x80|n);else if(n<=65535){o.write(0xde);o.write(n>>>8);o.write(n);}else{o.write(0xdf);write32(o,n);}}
    private static byte[] encodeString(String s)throws IOException{byte[]b=s.getBytes(StandardCharsets.UTF_8);ByteArrayOutputStream o=new ByteArrayOutputStream();int n=b.length;if(n<32)o.write(0xa0|n);else if(n<=255){o.write(0xd9);o.write(n);}else if(n<=65535){o.write(0xda);o.write(n>>>8);o.write(n);}else{o.write(0xdb);write32(o,n);}o.write(b);return o.toByteArray();}
    private static String stringValue(byte[]a,int p)throws Exception{int t=u8(a,p),n,off;if(t==0xc0)return null;if((t&0xe0)==0xa0){n=t&31;off=p+1;}else if(t==0xd9){n=u8(a,p+1);off=p+2;}else if(t==0xda){n=u16(a,p+1);off=p+3;}else if(t==0xdb){n=(int)u32(a,p+1);off=p+5;}else return null;bounds(a,off,n);return new String(a,off,n,StandardCharsets.UTF_8);}
    private static int intValue(byte[]a,int p)throws Exception{int t=u8(a,p);if(t<=0x7f)return t;if(t>=0xe0)return (byte)t;if(t==0xcc)return u8(a,p+1);if(t==0xcd)return u16(a,p+1);if(t==0xce)return (int)u32(a,p+1);if(t==0xd0)return (byte)u8(a,p+1);if(t==0xd1)return (short)u16(a,p+1);if(t==0xd2)return (int)u32(a,p+1);throw new IOException("not integer tag=0x"+Integer.toHexString(t));}
    static int skip(byte[]a,int p)throws Exception{int t=u8(a,p);if(t<=0x7f||t>=0xe0)return p+1;if((t&0xe0)==0xa0)return checked(a,p+1,t&31);if((t&0xf0)==0x90){int q=p+1;for(int i=0;i<(t&15);i++)q=skip(a,q);return q;}if((t&0xf0)==0x80){int q=p+1;for(int i=0;i<(t&15)*2;i++)q=skip(a,q);return q;}switch(t){case 0xc0:case 0xc2:case 0xc3:return p+1;case 0xc4:return checked(a,p+2,u8(a,p+1));case 0xc5:return checked(a,p+3,u16(a,p+1));case 0xc6:return checked(a,p+5,(int)u32(a,p+1));case 0xca:return checked(a,p,5);case 0xcb:return checked(a,p,9);case 0xcc:case 0xd0:return checked(a,p,2);case 0xcd:case 0xd1:return checked(a,p,3);case 0xce:case 0xd2:return checked(a,p,5);case 0xcf:case 0xd3:return checked(a,p,9);case 0xd9:return checked(a,p+2,u8(a,p+1));case 0xda:return checked(a,p+3,u16(a,p+1));case 0xdb:return checked(a,p+5,(int)u32(a,p+1));case 0xdc:{int n=u16(a,p+1),q=p+3;for(int i=0;i<n;i++)q=skip(a,q);return q;}case 0xdd:{int n=(int)u32(a,p+1),q=p+5;for(int i=0;i<n;i++)q=skip(a,q);return q;}case 0xde:{int n=u16(a,p+1),q=p+3;for(int i=0;i<n*2;i++)q=skip(a,q);return q;}case 0xdf:{int n=(int)u32(a,p+1),q=p+5;for(int i=0;i<n*2;i++)q=skip(a,q);return q;}case 0xd4:return checked(a,p,3);case 0xd5:return checked(a,p,4);case 0xd6:return checked(a,p,6);case 0xd7:return checked(a,p,10);case 0xd8:return checked(a,p,18);case 0xc7:return checked(a,p+3,u8(a,p+1));case 0xc8:return checked(a,p+4,u16(a,p+1));case 0xc9:return checked(a,p+6,(int)u32(a,p+1));default:throw new IOException("unsupported MessagePack tag 0x"+Integer.toHexString(t)+" at "+p);}}
    private static int checked(byte[]a,int p,int n)throws Exception{bounds(a,p,n);return p+n;}private static void bounds(byte[]a,int p,int n)throws Exception{if(p<0||n<0||p+n>a.length)throw new EOFException("MessagePack truncated");}private static int u8(byte[]a,int p)throws Exception{bounds(a,p,1);return a[p]&255;}private static int u16(byte[]a,int p)throws Exception{return(u8(a,p)<<8)|u8(a,p+1);}private static long u32(byte[]a,int p)throws Exception{return((long)u8(a,p)<<24)|((long)u8(a,p+1)<<16)|((long)u8(a,p+2)<<8)|u8(a,p+3);}private static void write32(OutputStream o,long v)throws IOException{o.write((int)(v>>>24));o.write((int)(v>>>16));o.write((int)(v>>>8));o.write((int)v);}
    private static String sha256(Path p)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[]b=new byte[65536];for(int n;(n=in.read(b))>0;)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte b:md.digest())s.append(String.format("%02x",b&255));return s.toString();}
    private static final class Header{final int count,end;Header(int c,int e){count=c;end=e;}}private static final class Record{final byte[]a;final int start,end;Record(byte[]a,int s,int e){this.a=a;start=s;end=e;}}
    private VoidglassR2ConfigPatchTool(){}
}
