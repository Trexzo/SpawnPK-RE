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

    private static final class RegionBatchSnapshot {
        final MovementState.LoadedWindowSnapshot movementWindow;
        final RegionLoadLifecycle.Snapshot regionLoad;
        final NpcRegistry.RegionViewSnapshot npcView;
        final SceneUpdatePublisher scenePublisher;
        boolean petFollowResetPending;

        RegionBatchSnapshot(
            MovementState.LoadedWindowSnapshot movementWindow,
            RegionLoadLifecycle.Snapshot regionLoad,
            NpcRegistry.RegionViewSnapshot npcView,
            SceneUpdatePublisher scenePublisher
        ){
            this.movementWindow=movementWindow;
            this.regionLoad=regionLoad;
            this.npcView=npcView;
            this.scenePublisher=scenePublisher;
        }
    }

    private RegionBatchSnapshot stagedRegionBatch;

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

    boolean maybeStream(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(!movementEnabled||world.players().size()!=1)
            return false;

        if(regionLoads.pending())
            return false;

        if(movement.transientRegion()&&
           movement.insideHomeInnerCore(16)){
            reattachHome(
                writer,
                tag,
                false,
                "AUTO_HOME_REATTACH"
            );
            return true;
        }

        if(!movement.nearLoadedEdge(16))
            return false;

        int regionId=
            ((movement.x()>>6)<<8)|
            (movement.y()>>6);

        WorldRegionAuthorityRepository.Region region=
            WorldRegionAuthorityRepository.get(regionId);

        if(region==null||
           !region.mapPresent||
           !region.terrainParseOk||
           !WorldCollisionAuthority.hasRegion(regionId)){
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
                        WorldCollisionAuthority.hasRegion(regionId))
            );
            return false;
        }

        int chunkX=movement.x()>>3;
        int chunkY=movement.y()>>3;
        int baseX=(chunkX-6)<<3;
        int baseY=(chunkY-6)<<3;

        if(baseX==movement.loadedBaseX()&&
           baseY==movement.loadedBaseY()){
            return false;
        }

        beginRegionBatchIfNeeded(
            writer
        );

        boolean leavingHome=!movement.transientRegion();
        int removed=0;

        if(leavingHome){
            removed=
                npcs.detachRegionViewPreservingFollowers(
                    writer
                );

            resetPetFollowRuntimeWithBatchFence();
            TradeService.cancelIfActive(
                worldPlayer,
                "AUTO_REGION_REBASE"
            );
            playerInteractions.clearTargets();
            combat.cancelForManualMovement();
        }

        movement.rebaseLoadedWindow(
            baseX,
            baseY,
            true
        );

        writer.fixed(219,new byte[0]);
        writer.fixed(
            73,
            BootstrapPackets.region73(chunkX,chunkY)
        );
        RegionLoadLifecycle.Begin regionLoad=
            regionLoads.begin(
                chunkX,
                chunkY,
                baseX,
                baseY,
                "AUTO_WINDOW_REBASE"
            );

        bridge.replaceScenePublisher(
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    baseX,
                    baseY,
                    movement.plane()
                )
            )
        );

        System.out.println(
            tag+
            "V5181_WORLD_AUTO_REBASE result=OK region="+
            regionId+
            " name=["+region.name+"]"+
            " world="+
            movement.x()+","+
            movement.y()+","+
            movement.plane()+
            " base="+baseX+","+baseY+
            " packet73="+chunkX+","+chunkY+
            " regionLoadSeq="+regionLoad.sequence+
            " placement=CLIENT_PACKET73_REBASE_PRESERVES_WORLD"+
            " removedHomeNpcView="+removed+
            " terrain=CLIENT_CACHE collision=EXACT_CURRENT_STATIC dynamicOverlays=UNRESOLVED_SERVER_AUTHORITY"
        );

        return true;
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

    private void beginRegionBatchIfNeeded(
        ServerPacketWriter writer
    ){
        if(writer==null||
           !writer.batchActive())
            return;

        if(stagedRegionBatch!=null)
            throw new IllegalStateException(
                "region stream batch already staged"
            );

        stagedRegionBatch=
            new RegionBatchSnapshot(
                movement.snapshotLoadedWindow(),
                regionLoads.snapshot(),
                npcs.snapshotRegionView(),
                bridge.scenePublisher()
            );
    }

    boolean commitRegionStreamBatch(){
        RegionBatchSnapshot snapshot=
            stagedRegionBatch;

        if(snapshot==null)
            return false;

        stagedRegionBatch=null;

        if(snapshot.petFollowResetPending)
            resetPetFollowRuntimeWithBatchFence();

        return true;
    }

    boolean abortRegionStreamBatch(){
        RegionBatchSnapshot snapshot=
            stagedRegionBatch;

        if(snapshot==null)
            return false;

        stagedRegionBatch=null;

        movement.restoreLoadedWindow(
            snapshot.movementWindow
        );
        regionLoads.restore(
            snapshot.regionLoad
        );
        npcs.restoreRegionView(
            snapshot.npcView
        );

        if(bridge.scenePublisher()!=
                snapshot.scenePublisher)
            bridge.replaceScenePublisher(
                snapshot.scenePublisher
            );

        return true;
    }

    private void resetPetFollowRuntimeWithBatchFence(){
        RegionBatchSnapshot snapshot=
            stagedRegionBatch;

        if(snapshot==null){
            bridge.resetPetFollowRuntime();
            return;
        }

        snapshot.petFollowResetPending=true;
    }

    boolean regionStreamBatchStaged(){
        return stagedRegionBatch!=null;
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
        beginRegionBatchIfNeeded(
            writer
        );

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

        resetPetFollowRuntimeWithBatchFence();

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
