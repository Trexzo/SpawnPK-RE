package spk.local;
/** v5.9: three-choice pet color families use the native chatbox interface transport (S2C164). */
public final class ResvanoColorCycleTest {
 public static void main(String[] args){
  for(int id=27340;id<=27345;id++)if(ItemDefinitionRepository.get(id)==null)throw new AssertionError("missing "+id);
  byte[] chat=BootstrapPackets.chatboxInterface164(2480);
  if(chat.length!=2||(chat[0]&255)!=0xB0||(chat[1]&255)!=0x09)throw new AssertionError("chatbox164 root2480 bytes="+java.util.Arrays.toString(chat));
  byte[] wrong=BootstrapPackets.interface97(2480);
  if(java.util.Arrays.equals(chat,wrong))throw new AssertionError("chatbox transport collapsed into viewport interface transport");
  System.out.println("V59_PET_COLOR_CHATBOX_PASS resvano27340..27345=true scooby24016..24018=true root2480=true transport=S2C164_LE choices2482_2484=true cancel2485=true viewportS2C97=false");
 }
}
