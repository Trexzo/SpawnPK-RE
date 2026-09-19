package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Client-config migrator for the LocalLab Voidglass R3 native-asset compositor trial. */
public final class VoidglassR3ConfigPatchTool {
    static final int OLD_ITEM_ID=32760, ITEM_ID=29999;
    static final int[] NPC_IDS={12000,12001,12002,12003};
    static final String ITEM_NAME="@cya@Voidglass nistirio";
    static final String HOVER="\n@cya@LocalLab custom pet compositor@whi@\nVoidglass R3: item id 29999 (inside client 0..29999 table)\nFour non-Hydra native-model composite candidates.\n@or1@Not recovered SpawnPK production content.";
    static final int[] ARCANE_SRC={910,912,1938,1814,1690,0,0};
    static final int[] ARCANE_DST={898,4,8,12,16,5206,1};

    public static void main(String[] args)throws Exception{
        if(args.length<2)throw new IllegalArgumentException("usage: VoidglassR3ConfigPatchTool preflight|patch|verify <configDir>");
        Path dir=Paths.get(args[1]).toAbsolutePath();Path i=dir.resolve("i.bin"),e=dir.resolve("e.bin"),g=dir.resolve("g.bin");
        if(!Files.isRegularFile(i)||!Files.isRegularFile(e))throw new FileNotFoundException("Expected i.bin/e.bin under "+dir);
        String mode=args[0].toLowerCase(Locale.ROOT);
        if(mode.equals("preflight")){preflight(i,e,g);return;}
        if(mode.equals("patch")){preflight(i,e,g);patch(i,e);verify(i,e);return;}
        if(mode.equals("verify")){verify(i,e);return;}throw new IllegalArgumentException("unknown mode "+args[0]);
    }

    private static void preflight(Path i,Path e,Path g)throws Exception{
        byte[] ib=Files.readAllBytes(i), eb=Files.readAllBytes(e);
        if(ITEM_ID>=30000)throw new IOException("custom item must be inside exact client 30000-entry table");
        Record petItem=record(ib,"23224");if(petItem==null)throw new IOException("Blood reaper item anchor 23224 missing");
        requireInt(petItem,"modelId",32324);requireString(petItem,"name","@gre@Blood reaper pet");
        anchorNpc(eb,4209,1662,1663,32324);anchorNpc(eb,910,66,63,17378);anchorNpc(eb,3958,8593,8592,39182);anchorNpc(eb,6335,10921,10920,44733);
        checkTarget(ib,String.valueOf(ITEM_ID),encodeItemRecord(),"item");
        Record legacy=record(ib,String.valueOf(OLD_ITEM_ID));if(legacy!=null){byte[] actual=Arrays.copyOfRange(ib,legacy.start,legacy.end);if(!Arrays.equals(actual,VoidglassR2ConfigPatchTool.encodeItemRecord()))throw new IOException("legacy 32760 collision differs from exact LocalLab R2 record; refusing removal");}
        for(int x=0;x<NPC_IDS.length;x++)checkNpcTarget(eb,x);
        if(Files.isRegularFile(g)){byte[] gb=Files.readAllBytes(g);if(record(gb,"5042")==null)throw new IOException("GFX 5042 Black hole opening missing");if(record(gb,"4076")==null)throw new IOException("GFX 4076 Blue souls missing");}
        System.out.println("VOIDGLASS_R3_CONFIG_PREFLIGHT_PASS item29999=free_or_exact legacy32760="+(legacy==null?"absent":"exact_r2_removable")+" npc12000_12003=free_or_allowed gfx5042="+(Files.isRegularFile(g)?"present":"not_checked")+" clientItemBound=30000");
    }
    private static void anchorNpc(byte[] eb,int id,int stand,int walk,int model)throws Exception{Record r=record(eb,String.valueOf(id));if(r==null)throw new IOException("NPC anchor "+id+" missing");requireInt(r,"standAnim",stand);requireInt(r,"walkAnim",walk);requireIntArrayContains(r,"models",model);}
    private static void checkNpcTarget(byte[] eb,int index)throws Exception{
        String key=String.valueOf(NPC_IDS[index]);byte[] expected=encodeNpcRecord(index);Record r=record(eb,key);if(r==null)return;byte[] actual=Arrays.copyOfRange(eb,r.start,r.end);if(Arrays.equals(actual,expected))return;
        if(index==0 && Arrays.equals(actual,VoidglassR2ConfigPatchTool.encodeNpcRecord()))return;
        throw new IOException("custom npc ID collision key="+key+" existing record differs");
    }

    private static void patch(Path i,Path e)throws Exception{
        byte[] ib=Files.readAllBytes(i), eb=Files.readAllBytes(e);
        LinkedHashMap<String,byte[]> itemUp=new LinkedHashMap<>();itemUp.put(String.valueOf(ITEM_ID),encodeItemRecord());
        LinkedHashMap<String,byte[]> itemRemove=new LinkedHashMap<>();itemRemove.put(String.valueOf(OLD_ITEM_ID),VoidglassR2ConfigPatchTool.encodeItemRecord());
        byte[] nip=mutateMap(ib,itemUp,itemRemove,"item");
        LinkedHashMap<String,byte[]> npcUp=new LinkedHashMap<>();for(int x=0;x<NPC_IDS.length;x++)npcUp.put(String.valueOf(NPC_IDS[x]),encodeNpcRecord(x));
        LinkedHashMap<String,byte[]> npcAllowedReplace=new LinkedHashMap<>();npcAllowedReplace.put("12000",VoidglassR2ConfigPatchTool.encodeNpcRecord());
        byte[] nep=mutateMap(eb,npcUp,npcAllowedReplace,"npc");
        writeAtomicIfChanged(i,ib,nip);writeAtomicIfChanged(e,eb,nep);
    }
    private static void writeAtomicIfChanged(Path f,byte[] old,byte[] now)throws Exception{if(Arrays.equals(old,now)){System.out.println("VOIDGLASS_R3_CONFIG_ALREADY_PRESENT file="+f);return;}Path tmp=f.resolveSibling(f.getFileName()+".voidglass-r3.tmp");Files.write(tmp,now,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,f,StandardCopyOption.REPLACE_EXISTING);}System.out.println("VOIDGLASS_R3_CONFIG_PATCHED file="+f+" bytes="+now.length);}

    /** Upsert desired records; entries in allowedOld are removed when no desired key exists or may be replaced when desired exists. */
    static byte[] mutateMap(byte[] src,LinkedHashMap<String,byte[]> desired,LinkedHashMap<String,byte[]> allowedOld,String label)throws Exception{
        Header h=mapHeader(src,0);int p=h.end;ArrayList<byte[]> entries=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(int n=0;n<h.count;n++){
            int ks=p,ke=skip(src,ks);String k=stringValue(src,ks);int vs=ke,ve=skip(src,vs);p=ve;byte[] existing=Arrays.copyOfRange(src,vs,ve);
            if(desired.containsKey(k)){
                byte[] target=desired.get(k);if(!Arrays.equals(existing,target)){
                    byte[] allowed=allowedOld.get(k);if(allowed==null||!Arrays.equals(existing,allowed))throw new IOException("custom "+label+" ID collision key="+k+" existing differs");
                }
                entries.add(concat(encodeString(k),target));seen.add(k);continue;
            }
            if(allowedOld.containsKey(k)){
                if(!Arrays.equals(existing,allowedOld.get(k)))throw new IOException("legacy "+label+" key="+k+" differs; refusing removal");
                continue;
            }
            entries.add(Arrays.copyOfRange(src,ks,ve));
        }
        if(p!=src.length)throw new IOException("trailing bytes in top-level map");
        for(Map.Entry<String,byte[]> x:desired.entrySet())if(!seen.contains(x.getKey()))entries.add(concat(encodeString(x.getKey()),x.getValue()));
        ByteArrayOutputStream out=new ByteArrayOutputStream(src.length+4096);writeMapHeader(out,entries.size());for(byte[] b:entries)out.write(b);return out.toByteArray();
    }
    private static byte[] concat(byte[]a,byte[]b){byte[]c=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,c,a.length,b.length);return c;}

    private static void checkTarget(byte[] src,String key,byte[] expected,String label)throws Exception{Record r=record(src,key);if(r==null)return;byte[] actual=Arrays.copyOfRange(src,r.start,r.end);if(!Arrays.equals(actual,expected))throw new IOException("custom "+label+" ID collision key="+key+" existing record differs");}

    private static void verify(Path i,Path e)throws Exception{
        byte[] ib=Files.readAllBytes(i),eb=Files.readAllBytes(e);if(record(ib,String.valueOf(OLD_ITEM_ID))!=null)throw new IOException("legacy invalid item32760 still present");
        Record ir=record(ib,String.valueOf(ITEM_ID));if(ir==null)throw new IOException("custom item29999 missing");requireString(ir,"name",ITEM_NAME);requireInt(ir,"modelId",32324);requireStringArrayValue(ir,"actions",4,"Drop");
        for(int x=0;x<NPC_IDS.length;x++){Record r=record(eb,String.valueOf(NPC_IDS[x]));if(r==null)throw new IOException("candidate npc missing "+NPC_IDS[x]);requireBool(r,"pet",true);requireStringArrayValue(r,"actions",0,"Pick-up");}
        System.out.println("VOIDGLASS_R3_CONFIG_VERIFY_PASS item=29999 inventoryModel=32324 candidates=12000..12003 nonHydra=true legacy32760=absent iSha256="+sha256(i)+" eSha256="+sha256(e));
    }

    static byte[] encodeItemRecord()throws Exception{
        LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("name",ITEM_NAME);m.put("actions",new Object[]{null,null,null,null,"Drop"});m.put("modelId",32324);m.put("zoom",780);m.put("rotations",new int[]{10,2046});m.put("offsets",new int[]{-4,78});m.put("hover",HOVER);return encodeMap(m);
    }
    static byte[] encodeNpcRecord(int index)throws Exception{
        LinkedHashMap<String,Object> m=new LinkedHashMap<>();m.put("name",index==0?"Voidglass Rift Reaper":index==1?"Voidglass Arcane Singularity":index==2?"Voidglass Nightmare Shard":"Voidglass Ripper Soul");m.put("combatLevel",0);m.put("actions",new Object[]{"Pick-up",null,null,null,null});
        if(index==0){m.put("models",new int[]{32327,32325,32324,32326,32765,32530});m.put("scaleWidth",58);m.put("scaleHeight",58);m.put("standAnim",1662);m.put("walkAnim",1663);m.put("rotateAnim",1663);}
        else if(index==1){m.put("models",new int[]{17378,17394,17387,17399,17390,34252});m.put("scaleWidth",45);m.put("scaleHeight",45);m.put("srcColors",ARCANE_SRC);m.put("destColors",ARCANE_DST);m.put("standAnim",66);m.put("walkAnim",63);m.put("rotateAnim",63);}
        else if(index==2){m.put("models",new int[]{39182,32530,40177});m.put("scaleWidth",45);m.put("scaleHeight",45);m.put("standAnim",8593);m.put("walkAnim",8592);m.put("rotateAnim",8592);m.put("ambient",15);m.put("contrast",500);}
        else {m.put("models",new int[]{44733,42282,34252});m.put("scaleWidth",55);m.put("scaleHeight",55);m.put("standAnim",10921);m.put("walkAnim",10920);m.put("rotateAnim",10920);}
        m.put("size",1);m.put("minimap",false);m.put("priorityRender",false);m.put("pet",true);return encodeMap(m);
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
    private VoidglassR3ConfigPatchTool(){}
}
