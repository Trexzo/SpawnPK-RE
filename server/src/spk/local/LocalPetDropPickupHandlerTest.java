package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPetDropPickupHandlerTest {
    private static final class Bridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher scenePublisher;
        String saveReason;
        long followResetCount;
        long followEnsureCount;

        @Override public String username(){
            return "opensrc";
        }

        @Override public boolean persistentAccount(){
            return true;
        }

        @Override public long sessionWorldTick(){
            return 123L;
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return scenePublisher;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){
            saveReason=reason;
        }

        @Override public int syncScopesightPassive(
            ServerPacketWriter serverPackets
        ){
            return 0;
        }

        @Override public void resetPetFollowDeadline(){
            followResetCount++;
        }

        @Override public void ensurePetFollowScheduled(long now){
            followEnsureCount++;
        }
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            MovementState movement=player.movement();
            PetState petState=player.petState();
            DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter w=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            Bridge bridge=new Bridge();
            bridge.scenePublisher=
                new SceneUpdatePublisher(
                    w,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalPetDropPickupHandler h=
                new LocalPetDropPickupHandler(
                    world,
                    player.bank(),
                    movement,
                    petState,
                    player.petEffects(),
                    player.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    bridge
                );

            if(h.pickupPending())
                throw new AssertionError(
                    "new coordinator must start without pickup"
                );

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(24019);
            if(def==null)
                throw new AssertionError(
                    "expected certified pet definition 24019"
                );

            String spawn=npcs.spawnPet(def,movement,w);
            if(!spawn.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(
                    "pet spawn failed: "+spawn
                );
            petState.activate(def);

            NpcEntity pet=npcs.pet();
            if(pet==null)
                throw new AssertionError("spawned pet missing");

            NpcAction pickup=
                new NpcAction(155,pet.sceneIndex);

            if(!LocalPetDropPickupHandler.isPetPickupAction(
                pickup,
                pet,
                petState
            )){
                throw new AssertionError(
                    "opcode155 active-pet pickup classification changed"
                );
            }

            if(!h.handlePickupNpcAction(
                pickup,
                w,
                "[pet-pickup-test] "
            )){
                throw new AssertionError(
                    "pickup action not handled"
                );
            }

            if(!h.pickupPending())
                throw new AssertionError(
                    "pickup state was not owned by coordinator"
                );

            h.cancelDeferredForNewNpcAction(
                new NpcAction(17,pet.sceneIndex),
                "[pet-pickup-test] "
            );

            if(h.pickupPending())
                throw new AssertionError(
                    "new NPC action did not cancel deferred pickup"
                );

            if(LocalPetDropPickupHandler.isPetPickupAction(
                new NpcAction(72,pet.sceneIndex),
                pet,
                petState
            )){
                throw new AssertionError(
                    "attack opcode must not classify as pickup"
                );
            }

            if(LocalPetDropPickupHandler.chebyshev(
                10,10,12,11
            )!=2){
                throw new AssertionError(
                    "pickup distance metric changed"
                );
            }

            System.out.println(
                "LOCAL_PET_DROP_PICKUP_HANDLER_PASS "+
                "pickupOwned=true npcCancel=true opcodeBoundary=true"
            );
        }finally{
            world.close();
        }
    }
}
