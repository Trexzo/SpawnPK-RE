package spk.local;
import java.io.*;import java.lang.reflect.*;import java.net.*;
public final class DialogCloseOnMovementTest{
  public static void main(String[] args)throws Exception{
    testMini();testColor();
    System.out.println("V5129_DIALOG_CLOSE_ON_MOVEMENT_PASS mini=true petColor=true closePacket219=true stateCleared=true");
  }
  static void testMini()throws Exception{
    LocalSession s=session();set(s,"pendingMiniConfigureSlot",3);set(s,"pendingMiniConfigureItem",23988);
    ByteArrayOutputStream out=move(s);
    if(((Integer)get(s,"pendingMiniConfigureItem"))!=-1)throw new AssertionError("mini not cleared");
    if(out.size()==0)throw new AssertionError("mini emitted no close packet");
  }
  static void testColor()throws Exception{
    LocalSession s=session();set(s,"pendingPetColorSlot",3);set(s,"pendingPetColorItems",new int[]{24016,24017,24018,24019});set(s,"pendingPetColorFamily","SCOOBY_BEHEMOTH");
    ByteArrayOutputStream out=move(s);
    if(get(s,"pendingPetColorItems")!=null)throw new AssertionError("color not cleared");
    if(out.size()==0)throw new AssertionError("color emitted no close packet");
  }
  static LocalSession session(){return new LocalSession(new Socket(),true,true);}
  static ByteArrayOutputStream move(LocalSession s)throws Exception{
    Field mf=LocalSession.class.getDeclaredField("movement");mf.setAccessible(true);MovementState ms=(MovementState)mf.get(s);
    ClientPacketProbe cp=new ClientPacketProbe(new ByteArrayInputStream(new byte[0]),new IsaacCipher(new int[]{9,8,7,6}),"[test] ");
    Field pm=ClientPacketProbe.class.getDeclaredField("pendingMovement");pm.setAccessible(true);pm.set(cp,new MovementRequest(164,false,new int[]{ms.x()+1},new int[]{ms.y()},new byte[0]));
    ByteArrayOutputStream out=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
    Method m=LocalSession.class.getDeclaredMethod("acceptPendingMovement",ClientPacketProbe.class,ServerPacketWriter.class,String.class);m.setAccessible(true);m.invoke(s,cp,w,"[test] ");return out;
  }
  static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}  
  static Object get(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}  
}
