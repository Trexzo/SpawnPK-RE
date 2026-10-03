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
        final LocalSession.WorldTickGate gate=
            new LocalSession.WorldTickGate();
        ServerPacketWriter writer;
        int rejectedCallbacks;
        int acceptedCallbacks;

        @Override public ServerPacketWriter sessionPackets(){
            return writer;
        }

        @Override public String sessionTag(){
            return "[pet-realtime-test] ";
        }

        @Override public boolean
            runIfSessionWorldCallbackActive(
                Runnable action
            )
        {
            boolean accepted=
                gate.runRunnableIfActiveAndWriterLive(
                    writer,
                    action
                );

            if(accepted)
                acceptedCallbacks++;
            else
                rejectedCallbacks++;

            return accepted;
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
            schedulerBridge.gate.activate();

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
                        true,
                        new int[]{
                            MovementState.INITIAL_X+2
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

            if(!ownerTick.running||ownerTick.tiles!=2)
                throw new AssertionError(
                    "expected two-tile RUN follow setup"
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

            movement.rebaseLoadedWindow(
                3040,
                3480,
                true
            );

            scheduler.ensureFollowScheduled(1_500L);

            if(!scheduler.followScheduled())
                throw new AssertionError(
                    "transient-region follow was not armed"
                );

            if(world.realtime().size()!=2)
                throw new AssertionError(
                    "expected retained HOME follow task + transient follow task, got "+
                    world.realtime().size()
                );

            scheduler.resetFollowRuntime();

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

            if(world.realtime().size()!=3)
                throw new AssertionError(
                    "expected retained HOME follow + transient follow + test task, got "+
                    world.realtime().size()
                );

            int wireBeforeTeardown=
                wire.size();
            NpcEntity petBeforeTeardown=
                npcs.pet();
            int petXBeforeTeardown=
                petBeforeTeardown==null
                    ?Integer.MIN_VALUE
                    :petBeforeTeardown.x;
            int petYBeforeTeardown=
                petBeforeTeardown==null
                    ?Integer.MIN_VALUE
                    :petBeforeTeardown.y;
            int petNativeStateBeforeTeardown=
                npcs.petNativeState();

            schedulerBridge.gate.disableAndAwait();

            int drainedAfterDisable=
                world.realtime().runDue(
                    Long.MAX_VALUE
                );

            if(drainedAfterDisable!=3)
                throw new AssertionError(
                    "expected three due realtime wrappers after teardown disable, got "+
                    drainedAfterDisable
                );

            if(schedulerBridge.rejectedCallbacks!=3)
                throw new AssertionError(
                    "teardown gate did not reject every realtime callback rejected="+
                    schedulerBridge.rejectedCallbacks
                );

            if(wire.size()!=wireBeforeTeardown)
                throw new AssertionError(
                    "teardown-rejected realtime callback emitted wire bytes before="+
                    wireBeforeTeardown+
                    " after="+
                    wire.size()
                );

            NpcEntity petAfterTeardown=
                npcs.pet();

            if(petAfterTeardown!=petBeforeTeardown||
               (petAfterTeardown!=null&&
                (petAfterTeardown.x!=
                    petXBeforeTeardown||
                 petAfterTeardown.y!=
                    petYBeforeTeardown))||
               npcs.petNativeState()!=
                    petNativeStateBeforeTeardown)
                throw new AssertionError(
                    "teardown-rejected realtime callback mutated pet presentation state"
                );

            if(scheduler.followScheduled())
                throw new AssertionError(
                    "gate-rejected follow callback left scheduled latch armed"
                );

            if(scheduler.testSequenceScheduled())
                throw new AssertionError(
                    "gate-rejected test callback left scheduled latch armed"
                );

            schedulerBridge.gate.activate();

            scheduler.ensureFollowScheduled(
                2_000L
            );
            scheduler.ensureTestSequenceScheduled(
                System.currentTimeMillis()
            );

            if(!scheduler.followScheduled()||
               !scheduler.testSequenceScheduled())
                throw new AssertionError(
                    "gate-rejected pet realtime work did not re-arm after activation"
                );

            if(world.realtime().size()!=2)
                throw new AssertionError(
                    "expected follow + test recovery wrappers after activation size="+
                    world.realtime().size()
                );

            /*
             * Let the recovered wrappers enter the now-active gate without
             * allowing either fixture to recursively schedule more work.
             */
            npcs.devFollowFreeze(true);
            runtime.failSequence();

            int recoveredDue=
                world.realtime().runDue(
                    Long.MAX_VALUE
                );

            if(recoveredDue!=2)
                throw new AssertionError(
                    "recovered pet realtime wrappers did not drain count="+
                    recoveredDue
                );

            if(schedulerBridge.acceptedCallbacks!=2)
                throw new AssertionError(
                    "recovered pet realtime wrappers did not enter active gate accepted="+
                    schedulerBridge.acceptedCallbacks
                );

            if(scheduler.followScheduled()||
               scheduler.testSequenceScheduled())
                throw new AssertionError(
                    "recovered pet realtime latches did not settle after accepted callbacks"
                );

            System.out.println(
                "LOCAL_PET_REALTIME_GATE_REJECTION_RECOVERY_PASS "+
                "followLatchRecovered=true "+
                "testLatchRecovered=true "+
                "rescheduledAfterActivation=true"
            );

            npcs.devFollowFreeze(false);

            /*
             * Separate terminal-entry case: arm a fresh realtime follow while
             * the writer is still live, then latch that exact writer terminal
             * before the due wrapper reaches the session callback gate.
             */
            scheduler.resetFollowRuntime();
            schedulerBridge.gate.activate();

            scheduler.ensureFollowScheduled(
                2_000L
            );

            if(world.realtime().size()!=1)
                throw new AssertionError(
                    "terminal realtime entry fixture did not arm one follow task size="+
                    world.realtime().size()
                );

            int terminalWireBefore=
                wire.size();
            NpcEntity terminalPetBefore=
                npcs.pet();
            int terminalPetXBefore=
                terminalPetBefore==null
                    ?Integer.MIN_VALUE
                    :terminalPetBefore.x;
            int terminalPetYBefore=
                terminalPetBefore==null
                    ?Integer.MIN_VALUE
                    :terminalPetBefore.y;
            int terminalNativeStateBefore=
                npcs.petNativeState();

            writer.markTerminal();

            int terminalDue=
                world.realtime().runDue(
                    Long.MAX_VALUE
                );

            if(terminalDue!=1)
                throw new AssertionError(
                    "terminal realtime entry fixture did not drain one wrapper count="+
                    terminalDue
                );

            if(schedulerBridge.rejectedCallbacks!=4)
                throw new AssertionError(
                    "terminal writer did not reject realtime callback rejected="+
                    schedulerBridge.rejectedCallbacks
                );

            if(scheduler.followScheduled())
                throw new AssertionError(
                    "terminal-writer rejection left follow scheduled latch armed"
                );

            if(wire.size()!=terminalWireBefore)
                throw new AssertionError(
                    "terminal-writer realtime callback emitted wire bytes"
                );

            NpcEntity terminalPetAfter=
                npcs.pet();

            if(terminalPetAfter!=terminalPetBefore||
               (terminalPetAfter!=null&&
                (terminalPetAfter.x!=
                    terminalPetXBefore||
                 terminalPetAfter.y!=
                    terminalPetYBefore))||
               npcs.petNativeState()!=
                    terminalNativeStateBefore)
                throw new AssertionError(
                    "terminal-writer realtime callback mutated pet presentation state"
                );

            System.out.println(
                "LOCAL_PET_REALTIME_SCHEDULER_PASS "+
                "emptyFollowFailClosed=true followArm=true "+
                "transientFollowArm=true "+
                "resetState=true testSequenceArm=true "+
                "teardownGateRejectsRealtimeCallbacks=true "+
                "gateRejectionLatchRecovery=true "+
                "terminalWriterRejectsRealtimeCallback=true"
            );
        }finally{
            world.close();
        }
    }
}
