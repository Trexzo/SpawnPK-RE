package spk.local;

import java.io.*;
import java.lang.reflect.*;
import java.net.Socket;

public final class RuntimeCorrectiveIntegrationTest {
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void setField(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object call(Object o,String n,Class<?>[] t,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);try{return m.invoke(o,a);}catch(InvocationTargetException e){Throwable c=e.getCause();if(c instanceof Exception)throw (Exception)c;throw e;}}

    public static void main(String[] args)throws Exception{
        LocalSession s=new LocalSession(new Socket(),true,true);
        BankState bank=(BankState)field(s,"bank");
        MovementState movement=(MovementState)field(s,"movement");
        NpcRegistry npcs=(NpcRegistry)field(s,"npcs");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(bytes,new IsaacCipher(new int[]{3,4,5,6}));
        setField(s,"scenePublisher",new SceneUpdatePublisher(w,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0)));

        // Banker Talk-to (option 1 / opcode155) is a bank service for definition 7605.
        String spawn=npcs.devSpawnNpc(7605,1,0,movement,w); if(!spawn.startsWith("DEV_NPC_SPAWN_OK"))throw new AssertionError(spawn);
        int banker=-1;for(NpcEntity n:npcs.snapshot())if(n.definitionId==7605)banker=n.sceneIndex;
        ClientPacketProbe probe=new ClientPacketProbe(new ByteArrayInputStream(new byte[0]),new IsaacCipher(new int[]{0,0,0,0}),"[v5122] ");
        Field npcPending=ClientPacketProbe.class.getDeclaredField("pendingNpcAction");npcPending.setAccessible(true);npcPending.set(probe,new NpcAction(155,banker));
        call(s,"acceptPendingNpcAction",new Class[]{ClientPacketProbe.class,ServerPacketWriter.class,String.class},probe,w,"[v5122] ");
        if(!bank.isOpen())throw new AssertionError("Banker Talk-to did not open bank");

        // Any ordinary movement closes the open bank server-side before accepting the route.
        Field movePending=ClientPacketProbe.class.getDeclaredField("pendingMovement");movePending.setAccessible(true);
        movePending.set(probe,new MovementRequest(164,false,new int[]{movement.x()+1},new int[]{movement.y()},new byte[0]));
        call(s,"acceptPendingMovement",new Class[]{ClientPacketProbe.class,ServerPacketWriter.class,String.class},probe,w,"[v5122] ");
        if(bank.isOpen())throw new AssertionError("bank remained open after movement");

        // Ground Take must not fire from an adjacent tile.
        String spawned=bank.spawnItem(995,1,w);int slot=-1;for(int i=0;i<28;i++){BankState.Stack st=bank.inventoryAt(i);if(st!=null&&st.itemId==995){slot=i;break;}}
        call(s,"dropOrdinaryGround",new Class[]{DropItemAction.class,ServerPacketWriter.class,String.class},new DropItemAction(995,BankState.NORMAL_INVENTORY_CONTAINER,slot),w,"[v5122] ");
        GroundItem old=World.shared().groundItems().find(995,movement.x(),movement.y(),0);if(old==null)throw new AssertionError("drop missing");
        // Remove the same-tile fixture and place an equivalent item one tile east.
        World.shared().groundItems().remove(old.id); try{ new SceneUpdatePublisher(w,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0)).groundRemove(old); }catch(Exception ignored){}
        GroundItem g=World.shared().groundItems().add(995,1,new Tile(movement.x()+1,movement.y(),0),"opensrc",0,false);
        Field giPending=ClientPacketProbe.class.getDeclaredField("pendingGroundItemInteraction");giPending.setAccessible(true);
        giPending.set(probe,new GroundItemInteraction(236,3,995,g.tile.x,g.tile.y));
        call(s,"acceptPendingGroundItemInteraction",new Class[]{ClientPacketProbe.class,ServerPacketWriter.class,String.class},probe,w,"[v5122] ");
        if(World.shared().groundItems().find(995,g.tile.x,g.tile.y,0)==null)throw new AssertionError("adjacent ground item was taken early");
        Object pending=field(s,"pendingGroundTake");if(pending==null)throw new AssertionError("ground take not deferred");

        System.out.println("V5122_RUNTIME_CORRECTIVE_INTEGRATION_PASS bankerTalkToBank=true bankClosesOnMovement=true groundTakeAdjacentDeferred=true");
    }
}
