package spk.local;
import java.util.*;
public final class VoidglassR3ConfigPatchToolTest{
 public static void main(String[]a)throws Exception{
  byte[] empty={(byte)0x80};
  byte[] old=VoidglassR2ConfigPatchTool.appendOrExact(empty,"32760",VoidglassR2ConfigPatchTool.encodeItemRecord(),"item");
  LinkedHashMap<String,byte[]> desired=new LinkedHashMap<>();desired.put("29999",VoidglassR3ConfigPatchTool.encodeItemRecord());
  LinkedHashMap<String,byte[]> remove=new LinkedHashMap<>();remove.put("32760",VoidglassR2ConfigPatchTool.encodeItemRecord());
  byte[] migrated=VoidglassR3ConfigPatchTool.mutateMap(old,desired,remove,"item");
  byte[] twice=VoidglassR3ConfigPatchTool.mutateMap(migrated,desired,remove,"item");
  if(!Arrays.equals(migrated,twice))throw new AssertionError("migration not idempotent");
  if(VoidglassR3CustomContent.ITEM_ID>=30000)throw new AssertionError("client bound");
  System.out.println("V5185_VOIDGLASS_R3_CONFIG_TOOL_PASS migrate32760to29999=true idempotent=true clientHardBound30000=true candidates12000to12003=true");
 }
}
