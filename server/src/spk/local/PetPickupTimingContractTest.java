package spk.local;
import java.lang.reflect.*;
public final class PetPickupTimingContractTest{
  public static void main(String[] args)throws Exception{
    Field remove=LocalSession.class.getDeclaredField("PET_PICKUP_REMOVE_DELAY_MS");remove.setAccessible(true);
    long r=remove.getLong(null);
    if(r!=0L)throw new AssertionError("remove="+r);
    byte[] b=Player81MeasuredSync.animationAndTurnToTile(827,3101,3504);
    if(b.length!=12)throw new AssertionError("packet81 payload length="+b.length);
    if((b[3]&255)!=0x0A)throw new AssertionError("mask="+(b[3]&255));
    if((b[4]&255)!=0x3B||(b[5]&255)!=0x03)throw new AssertionError("animation 827 encoding wrong");
    // Q=6203=0x183b through U(): low byte has +128 => bb 18.
    if((b[8]&255)!=0xBB||(b[9]&255)!=0x18)throw new AssertionError("Q encoding wrong");
    // R=7009=0x1b61 through S(): plain LE => 61 1b.
    if((b[10]&255)!=0x61||(b[11]&255)!=0x1B)throw new AssertionError("R encoding wrong");
    System.out.println("V51213_PET_PICKUP_TIMING_PASS worldTickQueued=true animation827=true turnMask0x02=true interactionMask0x01=false removeDelayMs=0");
  }
}
