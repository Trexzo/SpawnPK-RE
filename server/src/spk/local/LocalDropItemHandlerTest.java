package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalDropItemHandlerTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(600L);
        try{
            WorldPlayer player=new WorldPlayer();
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);
            VoidglassPetState voidglass=
                new VoidglassPetState();
            PetAccessoryState accessory=
                new PetAccessoryState();

            final String[] saveReason={null};
            final int[] facingClearCount={0};
            final int[] scopesightSyncCount={0};

            LocalDropItemHandler handler=
                new LocalDropItemHandler(
                    world,
                    player.bank(),
                    player.movement(),
                    player.petState(),
                    player.petEffects(),
                    player.miniPets(),
                    npcs,
                    dev,
                    voidglass,
                    accessory,
                    (tag,reason)->saveReason[0]=reason,
                    (writer,tag,reason)->facingClearCount[0]++,
                    writer->{
                        scopesightSyncCount[0]++;
                        return 0;
                    }
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();

            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}));

            SceneUpdatePublisher scene=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0));

            String spawned=
                player.bank().spawnItem(
                    4151,1,writer);

            int slot=findSlot(player.bank(),4151);
            if(slot<0){
                throw new AssertionError(
                    "ordinary item spawn failed: "+spawned);
            }

            handler.handle(
                new DropItemAction(
                    4151,
                    BankState.NORMAL_INVENTORY_CONTAINER,
                    slot),
                "droptest",
                7L,
                false,
                scene,
                writer,
                "[test] "
            );

            if(player.bank().inventoryCount(4151)!=0){
                throw new AssertionError(
                    "ordinary item remained in inventory");
            }

            GroundItem ground=
                world.groundItems().findOwned(
                    4151,
                    player.movement().x(),
                    player.movement().y(),
                    0,
                    "droptest");

            if(ground==null||
               ground.amount!=1||
               !"GROUND_DROP".equals(saveReason[0])){
                throw new AssertionError(
                    "ground="+ground+
                    " save="+saveReason[0]);
            }

            if(facingClearCount[0]!=0||
               scopesightSyncCount[0]!=0){
                throw new AssertionError(
                    "pet-only callbacks fired for ordinary drop");
            }

            player.bank().spawnItem(
                995,1,writer);
            int coins=findSlot(player.bank(),995);
            int before=player.bank().inventoryCount(995);

            handler.handle(
                new DropItemAction(
                    995,
                    9999,
                    coins),
                "droptest",
                8L,
                false,
                scene,
                writer,
                "[test] "
            );

            if(player.bank().inventoryCount(995)!=before){
                throw new AssertionError(
                    "unsupported widget mutated inventory");
            }

            System.out.println(
                "LOCAL_DROP_ITEM_HANDLER_PASS ordinaryGround=true saveSignal=true petCallbacksScoped=true unsupportedWidgetFailClosed=true");
        }finally{
            world.close();
        }
    }

    private static int findSlot(
        BankState bank,
        int itemId
    ){
        for(int i=0;i<bank.inventoryCapacity();i++){
            BankState.Stack stack=bank.inventoryAt(i);
            if(stack!=null&&
               stack.itemId==itemId&&
               stack.qty>0){
                return i;
            }
        }
        return -1;
    }
}
