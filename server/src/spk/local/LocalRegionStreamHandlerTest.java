package spk.local;

import java.io.*;

public final class LocalRegionStreamHandlerTest {
    private static final class Bridge
        implements LocalRegionStreamHandler.SessionBridge
    {
        SceneUpdatePublisher publisher;
        int replacements;
        int petFollowResets;

        @Override public String username(){
            return "opensrc";
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
            replacements++;
        }

        @Override public void resetPetFollowRuntime(){
            petFollowResets++;
        }
    }

    private static LocalRegionStreamHandler create(
        boolean enabled,
        World world,
        WorldPlayer player,
        NpcRegistry npcs,
        HomeWorldRuntimePlan homeWorld,
        CombatEngine combat,
        Bridge bridge
    ){
        MovementState movement=player.movement();

        return new LocalRegionStreamHandler(
            enabled,
            world,
            player,
            movement,
            homeWorld,
            npcs,
            new LocalPlayerInteractionHandler(
                world,
                player,
                movement,
                player.equipment()
            ),
            combat,
            bridge
        );
    }

    public static void main(String[] args)throws Exception{
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(new int[]{1,2,3,4})
            );

        World disabledWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            disabledWorld.registerPlayer(player,"opensrc");

            MovementState movement=player.movement();
            movement.enterTransientRegion(
                MovementState.INITIAL_X,
                MovementState.INITIAL_Y,
                0,
                3040,
                3456
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            Bridge bridge=new Bridge();
            bridge.publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        3040,
                        3456,
                        0
                    )
                );

            LocalRegionStreamHandler h=
                create(
                    false,
                    disabledWorld,
                    player,
                    new NpcRegistry(dev),
                    new HomeWorldRuntimePlan(),
                    new CombatEngine(dev),
                    bridge
                );

            if(h.maybeStream(
                writer,
                "[region-stream-test] "
            )){
                throw new AssertionError(
                    "disabled region streaming must fail closed"
                );
            }

            if(!movement.transientRegion())
                throw new AssertionError(
                    "disabled streaming mutated transient state"
                );
        }finally{
            disabledWorld.close();
        }

        World enabledWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            enabledWorld.registerPlayer(player,"opensrc");

            MovementState movement=player.movement();
            int beforeX=MovementState.INITIAL_X;
            int beforeY=3527; // inside HOME margin 16, outside old margin 24.

            movement.enterTransientRegion(
                beforeX,
                beforeY,
                0,
                3040,
                3480
            );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            Bridge bridge=new Bridge();
            bridge.publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        3040,
                        3456,
                        0
                    )
                );

            RegionLoadLifecycle homeLifecycle=
                new RegionLoadLifecycle();
            NpcRegistry homeNpcs=
                new NpcRegistry(dev);
            HomeWorldRuntimePlan homePlan=
                new HomeWorldRuntimePlan();

            LocalRegionStreamHandler h=
                new LocalRegionStreamHandler(
                    true,
                    enabledWorld,
                    player,
                    movement,
                    homePlan,
                    homeNpcs,
                    new LocalPlayerInteractionHandler(
                        enabledWorld,
                        player,
                        movement,
                        player.equipment()
                    ),
                    new CombatEngine(dev),
                    homeLifecycle,
                    bridge
                );

            int wireBefore=wire.size();

            if(!h.maybeStream(
                writer,
                "[region-stream-test] "
            )){
                throw new AssertionError(
                    "HOME reattach path was not handled"
                );
            }

            if(!movement.inHomeWindow())
                throw new AssertionError(
                    "HOME window was not restored"
                );

            if(movement.x()!=beforeX||
               movement.y()!=beforeY){
                throw new AssertionError(
                    "HOME reattach changed authoritative world position"
                );
            }

            if(bridge.replacements!=1)
                throw new AssertionError(
                    "scene publisher replacement count changed: "+
                    bridge.replacements
                );

            if(bridge.petFollowResets!=1)
                throw new AssertionError(
                    "pet-follow runtime reset count changed: "+
                    bridge.petFollowResets
                );

            if(wire.size()<=wireBefore)
                throw new AssertionError(
                    "HOME reattach emitted no packets"
                );

            if(!homeLifecycle.pending())
                throw new AssertionError(
                    "HOME reattach did not await opcode121"
                );

            int wireBeforeAck=wire.size();
            RegionLoadLifecycle.Completion completion=
                homeLifecycle.complete();

            boolean homeGroundSnapshot=
                h.completeRegionLoad(
                    completion,
                    writer,
                    "[region-stream-test] "
                );

            if(!homeGroundSnapshot)
                throw new AssertionError(
                    "HOME completion did not report ground snapshot"
                );

            if(wire.size()<=wireBeforeAck)
                throw new AssertionError(
                    "HOME overlays were not replayed after opcode121"
                );

            if(homeNpcs.visibleCount()<=0)
                throw new AssertionError(
                    "HOME NPC view was not republished after opcode121"
                );

            if(bridge.petFollowResets!=1)
                throw new AssertionError(
                    "post-ACK replay duplicated pet reset"
                );

            System.out.println(
                "LOCAL_REGION_STREAM_HANDLER_PASS "+
                "disabledFailClosed=true homeReattach=true "+
                "homeMargin16=true positionPreserved=true "+
                "sceneReplaced=true overlayAfter121=true "+
                "groundSnapshotCompletionFact=true"
            );
        }finally{
            enabledWorld.close();
        }

        World rebaseWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            rebaseWorld.registerPlayer(player,"opensrc");

            MovementState movement=player.movement();

            // Region 16193 is existing exact-current static-collision authority.
            // Start at the south-west edge of an intentionally stale window so
            // maybeStream must replace the scene frame while preserving world XY.
            movement.enterTransientRegion(
                4064,
                4192,
                0,
                4064,
                4192
            );

            int[] seed={31,32,33,34};
            ByteArrayOutputStream rebaseWire=
                new ByteArrayOutputStream();
            ServerPacketWriter rebaseWriter=
                new ServerPacketWriter(
                    rebaseWire,
                    new IsaacCipher(seed.clone())
                );

            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            Bridge bridge=new Bridge();
            bridge.publisher=
                new SceneUpdatePublisher(
                    rebaseWriter,
                    new SceneCoordinateContext(
                        4064,
                        4192,
                        0
                    )
                );

            RegionLoadLifecycle lifecycle=
                new RegionLoadLifecycle();

            LocalRegionStreamHandler h=
                new LocalRegionStreamHandler(
                    true,
                    rebaseWorld,
                    player,
                    movement,
                    new HomeWorldRuntimePlan(),
                    new NpcRegistry(dev),
                    new LocalPlayerInteractionHandler(
                        rebaseWorld,
                        player,
                        movement,
                        player.equipment()
                    ),
                    new CombatEngine(dev),
                    lifecycle,
                    bridge
                );

            if(!h.maybeStream(
                    rebaseWriter,
                    "[region-rebase-probe] "
                ))
                throw new AssertionError(
                    "automatic external rebase was not handled"
                );

            if(movement.x()!=4064||
               movement.y()!=4192)
                throw new AssertionError(
                    "packet73 rebase changed world position "+
                    movement.x()+","+
                    movement.y()
                );

            if(movement.loadedBaseX()!=4016||
               movement.loadedBaseY()!=4144)
                throw new AssertionError(
                    "center/base formula drift base="+
                    movement.loadedBaseX()+","+
                    movement.loadedBaseY()
                );

            if(!lifecycle.pending())
                throw new AssertionError(
                    "packet73 did not open pending lifecycle"
                );

            ByteArrayInputStream in=
                new ByteArrayInputStream(
                    rebaseWire.toByteArray()
                );
            IsaacCipher decoder=
                new IsaacCipher(seed.clone());

            int first=
                ((in.read()&255)-
                 decoder.nextInt())&255;

            if(first!=219)
                throw new AssertionError(
                    "expected pre-region packet 219 got="+
                    first
                );

            int second=
                ((in.read()&255)-
                 decoder.nextInt())&255;

            if(second!=73)
                throw new AssertionError(
                    "expected region packet 73 got="+
                    second
                );

            byte[] payload=Binary.readExactly(in,4);
            byte[] expected=
                BootstrapPackets.region73(508,524);

            if(!java.util.Arrays.equals(
                    payload,
                    expected))
                throw new AssertionError(
                    "packet73 payload drift"
                );

            if(in.read()!=-1)
                throw new AssertionError(
                    "ordinary window rebase emitted an extra packet; "+
                    "packet81 relocation must remain separate"
                );

            boolean autoGroundSnapshot=
                h.completeRegionLoad(
                    lifecycle.complete(),
                    rebaseWriter,
                    "[region-rebase-probe] "
                );

            if(autoGroundSnapshot)
                throw new AssertionError(
                    "AUTO_WINDOW_REBASE falsely reported ground snapshot"
                );

            System.out.println(
                "LOCAL_REGION_STREAM_RUNTIME_PROBE_ALIGNMENT_PASS "+
                "worldPreserved=true center=508,524 "+
                "base=4016,4144 packet73=true packet81=false "+
                "ack121Pending=true"
            );
        }finally{
            rebaseWorld.close();
        }

        World atomicWorld=World.isolatedForTest(50L);
        try{
            WorldPlayer player=new WorldPlayer();
            atomicWorld.registerPlayer(
                player,
                "opensrc"
            );

            MovementState movement=
                player.movement();
            movement.enterTransientRegion(
                4064,
                4192,
                0,
                4064,
                4192
            );

            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    1024
                );
            ServerPacketWriter atomicWriter=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{41,42,43,44}
                    )
                );
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            HomeWorldRuntimePlan home=
                new HomeWorldRuntimePlan();
            Bridge bridge=
                new Bridge();
            SceneUpdatePublisher beforePublisher=
                new SceneUpdatePublisher(
                    atomicWriter,
                    new SceneCoordinateContext(
                        4064,
                        4192,
                        0
                    )
                );
            bridge.publisher=beforePublisher;
            RegionLoadLifecycle lifecycle=
                new RegionLoadLifecycle();

            LocalRegionStreamHandler h=
                new LocalRegionStreamHandler(
                    true,
                    atomicWorld,
                    player,
                    movement,
                    home,
                    npcs,
                    new LocalPlayerInteractionHandler(
                        atomicWorld,
                        player,
                        movement,
                        player.equipment()
                    ),
                    new CombatEngine(dev),
                    lifecycle,
                    bridge
                );

            LocalRegionStreamHandler.AutoStreamPlan
                plan=
                    h.prepareAutoStream(
                        "[region-batch-test] "
                    );

            if(plan==null||
               movement.loadedBaseX()!=4064||
               movement.loadedBaseY()!=4192||
               !movement.transientRegion()||
               lifecycle.pending()||
               bridge.publisher!=beforePublisher)
                throw new AssertionError(
                    "region rebase prepare mutated semantic preimage"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    1024
                );
            boolean failed=false;

            try{
                h.settlePreparedAutoStream(
                    plan,
                    atomicWriter,
                    "[region-batch-test] "
                );
            }catch(IOException expected){
                failed=true;
            }finally{
                pressure.release();
            }

            if(!failed||
               queue.queuedBytes()!=0||
               movement.loadedBaseX()!=4064||
               movement.loadedBaseY()!=4192||
               !movement.transientRegion()||
               lifecycle.pending()||
               bridge.publisher!=beforePublisher)
                throw new AssertionError(
                    "failed region rebase settlement changed semantic preimage"
                );

            if(!h.settlePreparedAutoStream(
                    plan,
                    atomicWriter,
                    "[region-batch-test] "
                ))
                throw new AssertionError(
                    "region rebase retry was not handled"
                );

            if(queue.queuedBytes()<=0||
               movement.loadedBaseX()!=4016||
               movement.loadedBaseY()!=4144||
               !movement.transientRegion()||
               !lifecycle.pending()||
               bridge.publisher==beforePublisher)
                throw new AssertionError(
                    "region rebase retry did not commit exact postimage"
                );

            drain(
                queue
            );

            System.out.println(
                "REGION_REBASE_BATCH_COMMIT_FENCE_PASS "+
                "abortRestoresLoadedWindow=true "+
                "abortClearsStalePending=true "+
                "retryReemitsRegion73=true "+
                "commitAdvancesRegionState=true"
            );
        }finally{
            atomicWorld.close();
        }

        World homeAtomicWorld=
            World.isolatedForTest(
                50L
            );
        try{
            WorldPlayer player=
                new WorldPlayer();
            homeAtomicWorld.registerPlayer(
                player,
                "opensrc"
            );

            MovementState movement=
                player.movement();
            movement.enterTransientRegion(
                MovementState.INITIAL_X,
                3527,
                0,
                3040,
                3480
            );

            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    1024
                );
            ServerPacketWriter homeWriter=
                new ServerPacketWriter(
                    queue,
                    new IsaacCipher(
                        new int[]{51,52,53,54}
                    )
                );
            DevAuthorityWorkbench dev=
                new DevAuthorityWorkbench();
            NpcRegistry npcs=
                new NpcRegistry(dev);
            HomeWorldRuntimePlan home=
                new HomeWorldRuntimePlan();

            npcs.bootstrapHome(
                homeWriter,
                movement,
                new PetState(),
                home
            );
            drain(
                queue
            );

            int visibleBefore=
                npcs.visibleCount();

            Bridge bridge=
                new Bridge();
            SceneUpdatePublisher beforePublisher=
                new SceneUpdatePublisher(
                    homeWriter,
                    new SceneCoordinateContext(
                        3040,
                        3480,
                        0
                    )
                );
            bridge.publisher=beforePublisher;
            RegionLoadLifecycle lifecycle=
                new RegionLoadLifecycle();

            LocalRegionStreamHandler h=
                new LocalRegionStreamHandler(
                    true,
                    homeAtomicWorld,
                    player,
                    movement,
                    home,
                    npcs,
                    new LocalPlayerInteractionHandler(
                        homeAtomicWorld,
                        player,
                        movement,
                        player.equipment()
                    ),
                    new CombatEngine(dev),
                    lifecycle,
                    bridge
                );

            LocalRegionStreamHandler.AutoStreamPlan
                plan=
                    h.prepareAutoStream(
                        "[region-home-batch-test] "
                    );

            if(plan==null||
               plan.kind!=
                    LocalRegionStreamHandler
                        .AutoStreamPlan.Kind.HOME_REATTACH)
                throw new AssertionError(
                    "HOME reattach plan missing"
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    1024
                );
            boolean failed=false;

            try{
                h.settlePreparedAutoStream(
                    plan,
                    homeWriter,
                    "[region-home-batch-test] "
                );
            }catch(IOException expected){
                failed=true;
            }finally{
                pressure.release();
            }

            if(!failed||
               queue.queuedBytes()!=0||
               !movement.transientRegion()||
               movement.loadedBaseX()!=3040||
               movement.loadedBaseY()!=3480||
               lifecycle.pending()||
               bridge.publisher!=beforePublisher||
               bridge.petFollowResets!=0||
               npcs.visibleCount()!=visibleBefore)
                throw new AssertionError(
                    "failed HOME reattach changed semantic preimage"
                );

            if(!h.settlePreparedAutoStream(
                    plan,
                    homeWriter,
                    "[region-home-batch-test] "
                ))
                throw new AssertionError(
                    "HOME reattach retry was not handled"
                );

            if(queue.queuedBytes()<=0||
               !movement.inHomeWindow()||
               !lifecycle.pending()||
               bridge.publisher==beforePublisher||
               bridge.petFollowResets!=1||
               npcs.visibleCount()>=visibleBefore)
                throw new AssertionError(
                    "HOME reattach retry did not commit exact postimage"
                );

            System.out.println(
                "REGION_HOME_REATTACH_BATCH_COMMIT_FENCE_PASS "+
                "abortPreservesTransientWindow=true "+
                "abortPreservesNpcProjection=true "+
                "abortPreservesPetRuntime=true "+
                "retryCommitsHomeWindow=true"
            );
        }finally{
            homeAtomicWorld.close();
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            1<<20
        );
    }
}
