package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalMovementRequestHandlerTest {
    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        @Override public String username(){return "opensrc";}
        @Override public boolean persistentAccount(){return true;}
        @Override public long sessionWorldTick(){return 1L;}
        @Override public SceneUpdatePublisher scenePublisher(){return null;}
        @Override public void saveAccount(String tag,String reason){}
        @Override public int syncScopesightPassive(
            ServerPacketWriter serverPackets
        ){return 0;}
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class MovementBridge
        implements LocalMovementRequestHandler.SessionBridge
    {
        boolean keysCleared;
        boolean overlayCleared;

        @Override public void clearDialogNumberKeys(){
            keysCleared=true;
        }

        @Override public void clearOpponentOverlay(
            ServerPacketWriter serverPackets,
            String tag,
            String reason
        ){
            overlayCleared=true;
        }
    }

    private static LocalMovementRequestHandler create(
        boolean enabled,
        World world,
        WorldPlayer player,
        ServerPacketWriter w
    ){
        MovementState movement=player.movement();
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);

        LocalPetInventoryDialogHandler petDialogs=
            new LocalPetInventoryDialogHandler(
                bank,
                player.miniPets(),
                player.petState(),
                npcs,
                movement,
                new PetAccessoryState()
            );

        LocalPetDropPickupHandler petDropPickup=
            new LocalPetDropPickupHandler(
                world,
                bank,
                movement,
                player.petState(),
                player.petEffects(),
                player.miniPets(),
                npcs,
                new VoidglassPetState(),
                new PetAccessoryState(),
                dev,
                new PetBridge()
            );

        return new LocalMovementRequestHandler(
            enabled,
            bank,
            petDialogs,
            new DevControlCenter(),
            movement,
            new CombatEngine(dev),
            equipment,
            new LocalPlayerInteractionHandler(
                world,
                player,
                movement,
                equipment
            ),
            petDropPickup,
            npcs,
            new MovementBridge()
        );
    }

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter w=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(new int[]{1,2,3,4})
            );

        World observeWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            LocalMovementRequestHandler h=
                create(false,observeWorld,player,w);

            MovementRequest req=
                new MovementRequest(
                    164,
                    false,
                    new int[]{
                        MovementState.INITIAL_X+1
                    },
                    new int[]{
                        MovementState.INITIAL_Y
                    },
                    new byte[0]
                );

            h.handle(req,w,"[movement-test] ");

            if(player.movement().queued()!=0)
                throw new AssertionError(
                    "observe-only movement mutated queue"
                );
        }finally{
            observeWorld.close();
        }

        World enabledWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            LocalMovementRequestHandler h=
                create(true,enabledWorld,player,w);

            MovementRequest req=
                new MovementRequest(
                    164,
                    false,
                    new int[]{
                        MovementState.INITIAL_X+1
                    },
                    new int[]{
                        MovementState.INITIAL_Y
                    },
                    new byte[0]
                );

            h.handle(req,w,"[movement-test] ");

            if(player.movement().queued()<=0)
                throw new AssertionError(
                    "enabled movement did not enter authoritative queue"
                );
        }finally{
            enabledWorld.close();
        }

        if(LocalMovementRequestHandler.chebyshev(
            10,10,12,11
        )!=2){
            throw new AssertionError(
                "movement Chebyshev metric changed"
            );
        }

        if(LocalSession.chebyshev(
            10,10,12,11
        )!=2){
            throw new AssertionError(
                "LocalSession compatibility seam changed"
            );
        }

        System.out.println(
            "LOCAL_MOVEMENT_REQUEST_HANDLER_PASS "+
            "observeOnly=true authoritativeQueue=true "+
            "chebyshevCompatibility=true"
        );
    }
}
