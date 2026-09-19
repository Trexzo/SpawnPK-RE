package spk.local;
import java.util.*;
public final class BottomSidebarProductionParityTest {
 public static void main(String[] args){
  int[][] rows={{18128,7},{5065,8},{5715,9},{2449,10},{904,11},{147,12},{0,13}};
  String[] hex={"46 D0 87","13 C9 88","16 53 89","09 91 8A","03 88 8B","00 93 8C","00 00 8D"};
  for(int i=0;i<rows.length;i++){String got=ClientPacketProbe.hex(BootstrapPackets.sidebar71(rows[i][0],rows[i][1]),16);if(!got.equals(hex[i]))throw new AssertionError(Arrays.toString(rows[i])+" got="+got+" want="+hex[i]);}
  System.out.println("V57_BOTTOM_SIDEBAR_PRODUCTION_PARITY_PASS clan18128 friends5065 ignore5715 logout2449 options904 emotes147 spawn0to67027 tabs7to13=true");
 }
}
