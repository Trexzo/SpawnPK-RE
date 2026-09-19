package spk.local;

import java.io.*;import java.lang.reflect.*;import java.net.Socket;

public final class EngineR1RuntimeIntegrationTest {
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static void set(Object o,String n,Object v)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object call(Object o,String n,Class<?>[] t,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,t);m.setAccessible(true);try{return m.invoke(o,a);}catch(InvocationTargetException e){throw (e.getCause() instanceof Exception)?(Exception)e.getCause():e;}}
    public static void main(String[] args)throws Exception{
        LocalSession s=new LocalSession(new Socket(),true,true);
        BankState bank=(BankState)field(s,"bank");MovementState movement=(MovementState)field(s,"movement");NpcRegistry npcs=(NpcRegistry)field(s,"npcs");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ServerPacketWriter w=new ServerPacketWriter(bytes,new IsaacCipher(new int[]{6,7,8,9}));
        SceneUpdatePublisher pub=new SceneUpdatePublisher(w,new SceneCoordinateContext(MovementState.REGION_BASE_X,MovementState.REGION_BASE_Y,0));set(s,"scenePublisher",pub);
        String spawned=bank.spawnItem(995,123,w);if(!spawned.startsWith("ITEM_SPAWN_OK"))throw new AssertionError(spawned);int slot=-1;for(int z=0;z<28;z++){BankState.Stack st=bank.inventoryAt(z);if(st!=null&&st.itemId==995){slot=z;break;}}
        call(s,"dropOrdinaryGround",new Class[]{DropItemAction.class,ServerPacketWriter.class,String.class},new DropItemAction(995,BankState.NORMAL_INVENTORY_CONTAINER,slot),w,"[runtime] ");
        GroundItem ground=World.shared().groundItems().find(995,movement.x(),movement.y(),0);if(ground==null||ground.amount!=123||bank.inventoryCount(995)!=0)throw new AssertionError("drop path");
        call(s,"takeGroundNow",new Class[]{GroundItem.class,ServerPacketWriter.class,String.class,String.class},ground,w,"[runtime] ","TEST");
        if(World.shared().groundItems().find(995,movement.x(),movement.y(),0)!=null||bank.inventoryCount(995)!=123)throw new AssertionError("take path");

        // Exercise the actual LocalSession NPC-action handler through ClientPacketProbe pending state.
        String spawnBanker=npcs.devSpawnNpc(7605,1,0,movement,w);if(!spawnBanker.startsWith("DEV_NPC_SPAWN_OK"))throw new AssertionError(spawnBanker);
        int bankerScene=-1;for(NpcEntity n:npcs.snapshot())if(n.definitionId==7605)bankerScene=n.sceneIndex;if(bankerScene<0)throw new AssertionError("banker scene");
        ClientPacketProbe probe=new ClientPacketProbe(new ByteArrayInputStream(new byte[0]),new IsaacCipher(new int[]{0,0,0,0}),"[runtime] ");Field pf=ClientPacketProbe.class.getDeclaredField("pendingNpcAction");pf.setAccessible(true);pf.set(probe,new NpcAction(17,bankerScene));
        call(s,"acceptPendingNpcAction",new Class[]{ClientPacketProbe.class,ServerPacketWriter.class,String.class},probe,w,"[runtime] ");
        if(!bank.isOpen())throw new AssertionError("banker did not open bank");
        System.out.println("V511_ENGINE_RUNTIME_INTEGRATION_PASS ordinaryDropTake=true scenePublisher=true banker7605Option3OpensBank=true actualLocalSessionHandlers=true");
    }
}
