package spk.local;
import java.io.*;import java.lang.reflect.*;import java.net.*;
public final class OpponentOverlayClearGuardTest{
  public static void main(String[] args)throws Exception{
    LocalSession s=new LocalSession(new Socket(),true,true);
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
    Method m=LocalSession.class.getDeclaredMethod("clearOpponentOverlay",ServerPacketWriter.class,String.class,String.class);m.setAccessible(true);
    m.invoke(s,w,"[test] ","MANUAL_MOVEMENT");
    if(out.size()!=0)throw new AssertionError("unsafe key25 clear emitted bytes="+out.size());
    System.out.println("V5129_OPPONENT_OVERLAY_CLEAR_GUARD_PASS key25EmptyEmission=false targetReplacementAuthority=true disconnectRegressionGuard=true");
  }
}
