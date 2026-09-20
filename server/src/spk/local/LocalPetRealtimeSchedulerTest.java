package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalPetRealtimeSchedulerTest {
    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;

        @Override public String username(){return "opensrc";}
        @Override public boolean persistentAccount(){return true;}
        @Override public long sessionWorldTick(){return 1L;}
        @Override public SceneUpdatePublisher scenePublisher(){return publisher;}
        @Override public void saveAccount(String tag,String reason){}
        @Override public int syncScopesightPassive(
            ServerPacketWriter serverPackets
        ){return 0;}
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class SchedulerBridge
        implements LocalPetRealtimeScheduler.SessionBridge
    {
        ServerPacketWriter writer;

        @Override public ServerPacketWriter sessionPackets(){
            return writer;
        }

        @Override public String sessionTag(){
            return "[pet-realtime-test] ";
        }
    }

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(50L);

        try{
            WorldPlayer player=new WorldPlayer();
            world.registerPlayer(player,"opensrc");

            MovementState movement=player.movement();
            PetState petState=player.petState();
            PetEffectState petEffects=player.petEffects();

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=new NpcRegistry(dev);

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(new int[]{1,2,3,4})
                );

            SceneUpdatePublisher publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            LocalPetRuntimeCommandHandler runtime=
                new LocalPetRuntimeCommandHandler(
                    petState,
                    petEffects,
                    npcs,
                    movement
                );

            PetBridge petBridge=new PetBridge();
            petBridge.publisher=publisher;

            LocalPetDropPickupHandler petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    player.bank(),
                    movement,
                    petState,
                    petEffects,
                    player.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    petBridge
                );

            SchedulerBridge schedulerBridge=
                new SchedulerBridge();
            schedulerBridge.writer=writer;

            LocalPetRealtimeScheduler scheduler=
                new LocalPetRealtimeScheduler(
                    true,
                    world,
                    player,
                    movement,
                    npcs,
                    petDropPickup,
                    runtime,
                    schedulerBridge
                );

            scheduler.ensureFollowScheduled(1_000L);

            if(world.realtime().size()!=0)
                throw new AssertionError(
                    "follow scheduled without an active pet"
                );

            PetDefinitionRepository.Def def=
                PetDefinitionRepository.get(24019);

            if(def==null)
                throw new AssertionError(
                    "expected certified pet definition 24019"
                );

            String spawned=
                npcs.spawnPet(
                    def,
                    movement,
                    writer
                );

            if(!spawned.startsWith("PET_SPAWN_OK"))
                throw new AssertionError(
                    "pet spawn failed: "+spawned
                );

            petState.activate(def);

            String accepted=
                movement.accept(
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
                    )
                );

            if(!accepted.startsWith("ACCEPTED"))
                throw new AssertionError(
                    "movement setup rejected: "+accepted
                );

            MovementState.Tick ownerTick=
                movement.advance();

            if(ownerTick==null)
                throw new AssertionError(
                    "movement setup produced no tick"
                );

            npcs.queueOwnerMovement(ownerTick);

            scheduler.ensureFollowScheduled(1_000L);

            if(!scheduler.followScheduled())
                throw new AssertionError(
                    "follow scheduler flag not armed"
                );

            if(scheduler.followDeadline()!=1_200L)
                throw new AssertionError(
                    "follow deadline changed: "+
                    scheduler.followDeadline()
                );

            if(world.realtime().size()!=1)
                throw new AssertionError(
                    "expected exactly one follow realtime task, got "+
                    world.realtime().size()
                );

            scheduler.resetFollowRuntime();

            if(scheduler.followScheduled())
                throw new AssertionError(
                    "follow scheduler flag survived reset"
                );

            if(scheduler.followDeadline()!=Long.MAX_VALUE)
                throw new AssertionError(
                    "follow deadline survived reset"
                );

            runtime.handle(
                new String[]{"pettestall"},
                writer
            );

            scheduler.ensureTestSequenceScheduled(
                System.currentTimeMillis()
            );

            if(!scheduler.testSequenceScheduled())
                throw new AssertionError(
                    "pet test sequence was not armed"
                );

            if(world.realtime().size()!=2)
                throw new AssertionError(
                    "expected retained follow task + test task, got "+
                    world.realtime().size()
                );

            System.out.println(
                "LOCAL_PET_REALTIME_SCHEDULER_PASS "+
                "emptyFollowFailClosed=true followArm=true "+
                "resetState=true testSequenceArm=true"
            );
        }finally{
            world.close();
        }
    }
}
