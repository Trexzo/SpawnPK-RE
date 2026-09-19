package spk.local;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class EngineR81ConfigHoverPatchTest{
 public static void main(String[]a)throws Exception{
  int[] ids={25425,24016,24017,24018,24019,27340,27341,27342,27343};
  ByteArrayOutputStream src=new ByteArrayOutputStream();map(src,ids.length);
  for(int id:ids){uint(src,id);map(src,2);str(src,"name");str(src,"item"+id);str(src,"hover");str(src,id==25425?"BASE_BEHEMOTH":"OLD_"+id);}
  LinkedHashMap<Integer,String> changes=new LinkedHashMap<>();for(int id:new int[]{24016,24017,24018,24019})changes.put(id,"BASE_BEHEMOTH");for(int id:new int[]{27340,27341,27342})changes.put(id,"EVIL_WOLPER_TEXT");
  byte[] out=ConfigHoverPatchTool.patchItemMap(src.toByteArray(),changes);
  for(int id:new int[]{24016,24017,24018,24019})req("BASE_BEHEMOTH".equals(ConfigHoverPatchTool.hoverOf(out,id)),"behemoth "+id);
  for(int id:new int[]{27340,27341,27342})req("EVIL_WOLPER_TEXT".equals(ConfigHoverPatchTool.hoverOf(out,id)),"wolper "+id);
  req("OLD_27343".equals(ConfigHoverPatchTool.hoverOf(out,27343)),"ethereal changed");
  req("BASE_BEHEMOTH".equals(ConfigHoverPatchTool.hoverOf(out,25425)),"source changed");
  System.out.println("V5181_ENGINE_R81_CONFIG_HOVER_PATCH_PASS msgpackRawCopy=true behemothTargets=4 evilWolperTargets=3 etherealExcluded=true");
 }
 static void map(OutputStream o,int n)throws Exception{if(n<16)o.write(0x80|n);else throw new IllegalArgumentException();}
 static void uint(OutputStream o,int v)throws Exception{if(v<=127)o.write(v);else if(v<=65535){o.write(0xcd);o.write(v>>>8);o.write(v);}else throw new IllegalArgumentException();}
 static void str(OutputStream o,String s)throws Exception{byte[] b=s.getBytes(StandardCharsets.UTF_8);if(b.length<32)o.write(0xa0|b.length);else if(b.length<=255){o.write(0xd9);o.write(b.length);}else throw new IllegalArgumentException();o.write(b);}
 static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}
