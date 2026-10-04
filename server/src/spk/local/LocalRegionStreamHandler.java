package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Owns LocalLab's automatic packet-73 scene-window streaming.
 *
 * The client remains authoritative for terrain/object decoding from the
 * current cache. Dynamic SpawnPK overlays outside HOME remain deliberately
 * absent unless separately proven.
 */
final class LocalRegionStreamHandler {
    interface SessionBridge {
        String username();
        SceneUpdatePublisher scenePublisher();
        void replaceScenePublisher(SceneUpdatePublisher replacement);
        void resetPetFollowRuntime();
    }

    static final class AutoStreamPlan {
        enum Kind {
            WINDOW_REBASE,
            HOME_REATTACH
        }

        final Kind kind;
        final int regionId;
        final String regionName;
        final int chunkX,chunkY;
        final int baseX,baseY;
        final boolean leavingHome;
        final String reason;

        AutoStreamPlan(
            Kind kind,
            int regionId,
            String regionName,
            int chunkX,
            int chunkY,
            int baseX,
            int baseY,
            boolean leavingHome,
            String reason
        ){
            this.kind=Objects.requireNonNull(
                kind,
                "kind"
            );
            this.regionId=regionId;
            this.regionName=regionName;
            this.chunkX=chunkX;
            this.chunkY=chunkY;
            this.baseX=baseX;
            this.baseY=baseY;
            this.leavingHome=leavingHome;
            this.reason=Objects.requireNonNull(
                reason,
                "reason"
            );
        }
    }

    private final boolean movementEnabled;
    private final World world;
    private final WorldPlayer worldPlayer;
    private final MovementState movement;
    private final HomeWorldRuntimePlan homeWorld;
    private final NpcRegistry npcs;
    private final LocalPlayerInteractionHandler playerInteractions;
    private final CombatEngine combat;
    private final RegionLoadLifecycle regionLoads;
    private final SessionBridge bridge;

    LocalRegionStreamHandler(
        boolean movementEnabled,
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        HomeWorldRuntimePlan homeWorld,
        NpcRegistry npcs,
        LocalPlayerInteractionHandler playerInteractions,
        CombatEngine combat,
        SessionBridge bridge
    ){
        this(
            movementEnabled,
            world,
            worldPlayer,
            movement,
            homeWorld,
            npcs,
            playerInteractions,
            combat,
            new RegionLoadLifecycle(),
            bridge
        );
    }

    LocalRegionStreamHandler(
        boolean movementEnabled,
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        HomeWorldRuntimePlan homeWorld,
        NpcRegistry npcs,
        LocalPlayerInteractionHandler playerInteractions,
        CombatEngine combat,
        RegionLoadLifecycle regionLoads,
        SessionBridge bridge
    ){
        this.movementEnabled=movementEnabled;
        this.world=Objects.requireNonNull(world,"world");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.homeWorld=Objects.requireNonNull(homeWorld,"homeWorld");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.playerInteractions=Objects.requireNonNull(
            playerInteractions,"playerInteractions");
        this.combat=Objects.requireNonNull(combat,"combat");
        this.regionLoads=Objects.requireNonNull(
            regionLoads,
            "regionLoads"
        );
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    AutoStreamPlan prepareAutoStream(
        String tag
    ){
        if(!movementEnabled||
           world.players().size()!=1)
            return null;

        if(regionLoads.pending())
            return null;

        if(movement.transientRegion()&&
           movement.insideHomeInnerCore(16))
            return new AutoStreamPlan(
                AutoStreamPlan.Kind.HOME_REATTACH,
                -1,
                "HOME",
                385,
                436,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                false,
                "AUTO_HOME_REATTACH"
            );

        if(!movement.nearLoadedEdge(16))
            return null;

        int regionId=
            ((movement.x()>>6)<<8)|
            (movement.y()>>6);

        WorldRegionAuthorityRepository.Region region=
            WorldRegionAuthorityRepository.get(
                regionId
            );

        if(region==null||
           !region.mapPresent||
           !region.terrainParseOk||
           !WorldCollisionAuthority.hasRegion(
                regionId
           )){
            System.out.println(
                tag+
                "V5181_WORLD_AUTO_REBASE result=FAIL_CLOSED region="+
                regionId+
                " world="+
                movement.x()+","+
                movement.y()+","+
                movement.plane()+
                " authority="+
                (region==null
                    ?"UNKNOWN"
                    :"map="+region.mapPresent+
                        " terrain="+region.terrainParseOk+
                        " collision="+
                        WorldCollisionAuthority.hasRegion(
                            regionId
                        ))
            );
            return null;
        }

        int chunkX=movement.x()>>3;
        int chunkY=movement.y()>>3;
        int baseX=(chunkX-6)<<3;
        int baseY=(chunkY-6)<<3;

        if(baseX==movement.loadedBaseX()&&
           baseY==movement.loadedBaseY())
            return null;

        return new AutoStreamPlan(
            AutoStreamPlan.Kind.WINDOW_REBASE,
            regionId,
            region.name,
            chunkX,
            chunkY,
            baseX,
            baseY,
            !movement.transientRegion(),
            "AUTO_WINDOW_REBASE"
        );
    }

    boolean settlePreparedAutoStream(
        AutoStreamPlan plan,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(plan==null)
            return false;

        if(regionLoads.pending())
            throw new IllegalStateException(
                "region stream became pending before prepared settlement"
            );

        if(plan.kind==
                AutoStreamPlan.Kind.HOME_REATTACH){
            if(!movement.transientRegion()||
               !movement.insideHomeInnerCore(16))
                throw new IllegalStateException(
                    "prepared HOME reattach preimage changed"
                );
        }else{
            int chunkX=movement.x()>>3;
            int chunkY=movement.y()>>3;

            if(chunkX!=plan.chunkX||
               chunkY!=plan.chunkY||
               movement.loadedBaseX()==plan.baseX&&
               movement.loadedBaseY()==plan.baseY)
                throw new IllegalStateException(
                    "prepared region rebase preimage changed"
                );
        }

        NpcRegistry.PreparedRegionViewDetach detach=
            (plan.kind==
                AutoStreamPlan.Kind.HOME_REATTACH||
             plan.leavingHome)
                ?npcs.prepareRegionViewDetachPreservingFollowers()
                :null;

        writer.beginBatch();
        boolean ended=false;

        try{
            if(detach!=null)
                npcs.publishPreparedRegionViewDetach(
                    detach,
                    writer
                );

            writer.fixed(
                219,
                new byte[0]
            );
            writer.fixed(
                73,
                BootstrapPackets.region73(
                    plan.chunkX,
                    plan.chunkY
                )
            );

            writer.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                abortFailedPacketBatch(
                    writer,
                    failure
                );
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                abortFailedPacketBatch(
                    writer,
                    failure
                );
            throw failure;
        }catch(Error failure){
            if(!ended)
                abortFailedPacketBatch(
                    writer,
                    failure
                );
            throw failure;
        }

        int removed=
            detach==null
                ?0
                :npcs.commitPreparedRegionViewDetach(
                    detach
                );

        if(plan.kind==
                AutoStreamPlan.Kind.HOME_REATTACH){
            movement.restoreHomeWindowAtCurrentPosition();
        }else{
            if(plan.leavingHome){
                bridge.resetPetFollowRuntime();
                TradeService.cancelIfActive(
                    worldPlayer,
                    "AUTO_REGION_REBASE"
                );
                playerInteractions.clearTargets();
                combat.cancelForManualMovement();
            }

            movement.rebaseLoadedWindow(
                plan.baseX,
                plan.baseY,
                true
            );
        }

        RegionLoadLifecycle.Begin regionLoad=
            regionLoads.begin(
                plan.chunkX,
                plan.chunkY,
                plan.baseX,
                plan.baseY,
                plan.reason
            );

        bridge.replaceScenePublisher(
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    plan.baseX,
                    plan.baseY,
                    movement.plane()
                )
            )
        );

        if(plan.kind==
                AutoStreamPlan.Kind.HOME_REATTACH){
            bridge.resetPetFollowRuntime();

            System.out.println(
                tag+
                "V5181_WORLD_AUTO_HOME_REATTACH world="+
                movement.x()+","+
                movement.y()+",0"+
                " base="+
                MovementState.REGION_BASE_X+","+
                MovementState.REGION_BASE_Y+
                " packet73=385,436"+
                " regionLoadSeq="+
                regionLoad.sequence+
                " placement=CLIENT_PACKET73_REBASE_PRESERVES_WORLD"+
                " transientNpcPruned="+removed+
                " homeSceneReplay=DEFERRED_UNTIL_OPCODE121"+
                " dynamicOutsideHome=false"
            );

            return true;
        }

        System.out.println(
            tag+
            "V5181_WORLD_AUTO_REBASE result=OK region="+
            plan.regionId+
            " name=["+plan.regionName+"]"+
            " world="+
            movement.x()+","+
            movement.y()+","+
            movement.plane()+
            " base="+plan.baseX+","+plan.baseY+
            " packet73="+plan.chunkX+","+plan.chunkY+
            " regionLoadSeq="+regionLoad.sequence+
            " placement=CLIENT_PACKET73_REBASE_PRESERVES_WORLD"+
            " removedHomeNpcView="+removed+
            " terrain=CLIENT_CACHE collision=EXACT_CURRENT_STATIC dynamicOverlays=UNRESOLVED_SERVER_AUTHORITY"
        );

        return true;
    }

    boolean maybeStream(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        AutoStreamPlan plan=
            prepareAutoStream(
                tag
            );

        return settlePreparedAutoStream(
            plan,
            writer,
            tag
        );
    }

    private static void abortFailedPacketBatch(
        ServerPacketWriter writer,
        Throwable primary
    ){
        try{
            writer.abortBatch();
        }catch(Throwable abortFailure){
            primary.addSuppressed(
                abortFailure
            );
        }
    }

    boolean completeRegionLoad(
        RegionLoadLifecycle.Completion completion,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(completion==null||!completion.matched)
            return false;

        String reason=completion.reason;

        if(!("AUTO_HOME_REATTACH".equals(reason)||
             "RESPAWN_REATTACH".equals(reason)||
             "DEV_RETURN_HOME_RELOCATION".equals(reason)||
             "MAGIC_HOME_TELEPORT".equals(reason)))
            return false;

        if(movement.transientRegion()||
           movement.loadedBaseX()!=MovementState.REGION_BASE_X||
           movement.loadedBaseY()!=MovementState.REGION_BASE_Y){
            System.out.println(
                tag+
                "V5182_HOME_SCENE_POST_ACK_SKIPPED seq="+
                completion.sequence+
                " reason="+reason+
                " world="+movement.x()+","+movement.y()+","+movement.plane()+
                " base="+movement.loadedBaseX()+","+movement.loadedBaseY()+
                " transient="+movement.transientRegion()
            );
            return false;
        }

        SceneUpdatePublisher publisher=
            bridge.scenePublisher();

        if(publisher==null)
            throw new IllegalStateException(
                "HOME post-ACK replay has no scene publisher"
            );

        HomeObjectOverlayReplayer.Stats scene=
            homeWorld.replayScene(
                writer,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

        publisher.context().invalidate();

        int homeNpcAdded=
            npcs.reattachHomeView(
                writer,
                movement,
                homeWorld
            );
        int npcView=npcs.visibleCount();

        int groundReplay=0;
        for(GroundItem item:
            world.groundItems().snapshot()){
            if(item.owner==null||
               item.owner.equalsIgnoreCase(
                   bridge.username()
               )){
                publisher.groundSpawn(item);
                groundReplay++;
            }
        }

        System.out.println(
            tag+
            "V5182_HOME_SCENE_POST_ACK seq="+
            completion.sequence+
            " reason="+reason+
            " scene={"+scene+"}"+
            " homeNpcAdded="+homeNpcAdded+
            " npcView="+npcView+
            " groundReplay="+groundReplay+
            " overlayTiming=AFTER_OPCODE121"
        );

        return true;
    }

    boolean regionLoadPending(){
        return regionLoads.pending();
    }

    void reattachHomeForRespawn(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        reattachHome(
            writer,
            tag,
            true,
            "RESPAWN_REATTACH"
        );
    }

    private void reattachHome(
        ServerPacketWriter writer,
        String tag,
        boolean emitPlacement,
        String reason
    )throws IOException{
        int prunedTransientNpcView=
            npcs.detachRegionViewPreservingFollowers(
                writer
            );

        movement.restoreHomeWindowAtCurrentPosition();

        writer.fixed(219,new byte[0]);
        writer.fixed(
            73,
            BootstrapPackets.region73(385,436)
        );
        RegionLoadLifecycle.Begin regionLoad=
            regionLoads.begin(
                385,
                436,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                reason
            );

        if(emitPlacement){
            writer.varShort(
                81,
                BootstrapPackets.player81TeleportNoAppearance(
                    0,
                    movement.y()-MovementState.REGION_BASE_Y,
                    movement.x()-MovementState.REGION_BASE_X
                )
            );
        }

        SceneUpdatePublisher replacement=
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0
                )
            );
        bridge.replaceScenePublisher(replacement);

        bridge.resetPetFollowRuntime();

        System.out.println(
            tag+
            "V5181_WORLD_AUTO_HOME_REATTACH world="+
            movement.x()+","+
            movement.y()+",0"+
            " base="+
            MovementState.REGION_BASE_X+","+
            MovementState.REGION_BASE_Y+
            " packet73=385,436"+
            " regionLoadSeq="+regionLoad.sequence+
            " placement="+
                (emitPlacement
                    ?"SERVER_PLAYER81_RELOCATION"
                    :"CLIENT_PACKET73_REBASE_PRESERVES_WORLD")+
            " transientNpcPruned="+prunedTransientNpcView+
            " homeSceneReplay=DEFERRED_UNTIL_OPCODE121"+
            " dynamicOutsideHome=false"
        );
    }
}
