package spk.local;
public final class Player81MeasuredSyncTest{
  public static void main(String[] args)throws Exception{
    byte[] turn=Player81MeasuredSync.animationAndTurnToTile(827,3101,3504);
    if((turn[3]&255)!=10)throw new AssertionError("turn mask");
    byte[] walk=Player81MeasuredSync.walkStepAndInteraction(4,187);
    byte[] run=Player81MeasuredSync.runStepsAndInteraction(4,4,187);
    if((walk[walk.length-3]&255)!=1||(run[run.length-3]&255)!=1)throw new AssertionError("interaction mask absent");
    if((walk[walk.length-2]&255)!=(187&255)||(walk[walk.length-1]&255)!=(187>>>8))throw new AssertionError("walk target encoding");
    if((run[run.length-2]&255)!=(187&255)||(run[run.length-1]&255)!=(187>>>8))throw new AssertionError("run target encoding");
    System.out.println("V51213_PLAYER81_MEASURED_SYNC_PASS combatMovementMask=0x01 pickupMask=0x0a qLowAdd128=true rLE=true");
  }
}
