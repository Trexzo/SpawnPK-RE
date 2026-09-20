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
        this.movementEnabled=movementEnabled;
        this.world=Objects.requireNonNull(world,"world");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.homeWorld=Objects.requireNonNull(homeWorld,"homeWorld");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.playerInteractions=Objects.requireNonNull(
            playerInteractions,"playerInteractions");
        this.combat=Objects.requireNonNull(combat,"combat");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    boolean maybeStream(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        if(!movementEnabled||world.players().size()!=1)
            return false;

        if(movement.transientRegion()&&
           movement.insideHomeInnerCore(24)){
            reattachHome(writer,tag);
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

        boolean leavingHome=!movement.transientRegion();
        int removed=0;

        if(leavingHome){
            List<NpcEntity> old=npcs.snapshot();
            removed=old.size();

            if(!old.isEmpty()){
                ArrayList<NpcSyncEncoder.Update> removals=
                    new ArrayList<>();
                for(NpcEntity npc:old)
                    removals.add(
                        NpcSyncEncoder.Update.remove(npc)
                    );

                writer.varShort(
                    65,
                    NpcSyncEncoder.encode(
                        removals,
                        Collections.emptyList(),
                        movement.x(),
                        movement.y()
                    )
                );
            }

            bridge.resetPetFollowRuntime();
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
        writer.varShort(
            81,
            BootstrapPackets.player81TeleportNoAppearance(
                movement.plane(),
                movement.y()-baseY,
                movement.x()-baseX
            )
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
            " removedHomeNpcView="+removed+
            " terrain=CLIENT_CACHE collision=EXACT_CURRENT_STATIC dynamicOverlays=UNRESOLVED_SERVER_AUTHORITY"
        );

        return true;
    }

    private void reattachHome(
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        movement.restoreHomeWindowAtCurrentPosition();

        writer.fixed(219,new byte[0]);
        writer.fixed(
            73,
            BootstrapPackets.region73(385,436)
        );
        writer.varShort(
            81,
            BootstrapPackets.player81TeleportNoAppearance(
                0,
                movement.y()-MovementState.REGION_BASE_Y,
                movement.x()-MovementState.REGION_BASE_X
            )
        );

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

        HomeObjectOverlayReplayer.Stats scene=
            homeWorld.replayScene(
                writer,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y
            );

        replacement.context().invalidate();

        List<NpcEntity> homeNpcs=npcs.snapshot();
        if(!homeNpcs.isEmpty()){
            writer.varShort(
                65,
                NpcSyncEncoder.initial(
                    homeNpcs,
                    movement.x(),
                    movement.y()
                )
            );
        }

        int replay=0;
        for(GroundItem item:
            world.groundItems().snapshot()){
            if(item.owner==null||
               item.owner.equalsIgnoreCase(
                   bridge.username()
               )){
                replacement.groundSpawn(item);
                replay++;
            }
        }

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
            " scene={"+scene+"}"+
            " npcRepublish="+homeNpcs.size()+
            " groundReplay="+replay+
            " dynamicOutsideHome=false"
        );
    }
}
