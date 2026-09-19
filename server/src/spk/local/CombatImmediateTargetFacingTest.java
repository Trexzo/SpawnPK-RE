package spk.local;
import java.io.*;
public final class CombatImmediateTargetFacingTest {
  public static void main(String[] args)throws Exception{
    CombatEngine c=new CombatEngine(); MovementState m=new MovementState();
    NpcEntity npc=new NpcEntity(131,1488,m.x()+4,m.y());
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
    String r=c.request(npc,m,21566,System.currentTimeMillis(),null,w);
    if(!r.contains("TARGET_DEFERRED_RANGE")||!r.contains("clickFacing=false")||!r.contains("FIRST_AUTHORITATIVE_MOVEMENT"))throw new AssertionError(r);
    if(out.size()!=0)throw new AssertionError("click-time request emitted player81 bytes="+out.size());
    Integer first=c.consumeApproachFacingTargetForMovement();
    if(first==null||first.intValue()!=npc.sceneIndex)throw new AssertionError("first movement target="+first);
    if(c.consumeApproachFacingTargetForMovement()!=null)throw new AssertionError("approach target published more than once");
    if(c.consumeLastDamage()!=0)throw new AssertionError("request emitted damage before attack tick");
    System.out.println("V51213_COMBAT_MEASURED_FACING_PASS clickPublishesInteraction81=false firstAuthoritativeMovementTarget="+first+" once=true swingDeferred=true");
  }
}
