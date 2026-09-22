package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LOCAL_DEV transient-region exploration command coordinator.
 *
 * This remains a cache/static-collision developer projection. It does not
 * promote safe tiles or cache-backed regions into recovered production arrival
 * authority.
 */
final class LocalRegionDevCommandHandler {
    static final class Result {
        final String logText;
        final String detailText;
        final String saveReason;
        final SceneUpdatePublisher scenePublisher;

        Result(
            String logText,
            String saveReason,
            SceneUpdatePublisher scenePublisher
        ){
            this.logText=logText;
            this.detailText=detail(logText);
            this.saveReason=saveReason;
            this.scenePublisher=scenePublisher;
        }

        private static String detail(String logText){
            if(logText==null)return null;
            String load="V5160_REGION_LOAD ";
            String home="V5160_REGION_HOME ";
            if(logText.startsWith(load))return logText.substring(load.length());
            if(logText.startsWith(home))return logText.substring(home.length());
            return logText;
        }
    }

    private final World world;
    private final WorldPlayer worldPlayer;
    private final MovementState movement;
    private final LocalPlayerInteractionHandler playerInteractions;
    private final CombatEngine combat;
    private final NpcRegistry npcs;
    private final PetState petState;
    private final HomeWorldRuntimePlan homeWorld;
    private final RegionLoadLifecycle regionLoads;
    private final Runnable cancelPetFollowSchedule;

    LocalRegionDevCommandHandler(
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        LocalPlayerInteractionHandler playerInteractions,
        CombatEngine combat,
        NpcRegistry npcs,
        PetState petState,
        HomeWorldRuntimePlan homeWorld,
        Runnable cancelPetFollowSchedule
    ){
        this(
            world,
            worldPlayer,
            movement,
            playerInteractions,
            combat,
            npcs,
            petState,
            homeWorld,
            new RegionLoadLifecycle(),
            cancelPetFollowSchedule
        );
    }

    LocalRegionDevCommandHandler(
        World world,
        WorldPlayer worldPlayer,
        MovementState movement,
        LocalPlayerInteractionHandler playerInteractions,
        CombatEngine combat,
        NpcRegistry npcs,
        PetState petState,
        HomeWorldRuntimePlan homeWorld,
        RegionLoadLifecycle regionLoads,
        Runnable cancelPetFollowSchedule
    ){
        this.world=java.util.Objects.requireNonNull(world,"world");
        this.worldPlayer=java.util.Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.movement=java.util.Objects.requireNonNull(movement,"movement");
        this.playerInteractions=java.util.Objects.requireNonNull(
            playerInteractions,"playerInteractions");
        this.combat=java.util.Objects.requireNonNull(combat,"combat");
        this.npcs=java.util.Objects.requireNonNull(npcs,"npcs");
        this.petState=java.util.Objects.requireNonNull(petState,"petState");
        this.homeWorld=java.util.Objects.requireNonNull(homeWorld,"homeWorld");
        this.regionLoads=java.util.Objects.requireNonNull(
            regionLoads,
            "regionLoads"
        );
        this.cancelPetFollowSchedule=java.util.Objects.requireNonNull(
            cancelPetFollowSchedule,"cancelPetFollowSchedule");
    }

    Result handle(
        String[] p,
        String username,
        SceneUpdatePublisher currentScenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        if(p==null||p.length<1)return null;

        if(p[0].equalsIgnoreCase("regionload")||
           p[0].equalsIgnoreCase("worldload")){
            if(p.length<2){
                return new Result(
                    "V5160_REGION_LOAD result=REJECTED syntax=::regionload <regionId> [plane] | ::regionhome",
                    null,
                    currentScenePublisher
                );
            }

            int regionId=parseInt(p[1],-1);
            int plane=p.length>=3?parseInt(p[2],0):0;

            return enter(
                regionId,
                plane,
                currentScenePublisher,
                writer
            );
        }

        if(p[0].equalsIgnoreCase("regionhome")||
           p[0].equalsIgnoreCase("worldhome")){
            return returnHome(
                username,
                currentScenePublisher,
                writer
            );
        }

        return null;
    }

    Result enterForPanel(
        int regionId,
        int plane,
        SceneUpdatePublisher currentScenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        return enter(regionId,plane,currentScenePublisher,writer);
    }

    Result returnHomeForPanel(
        String username,
        SceneUpdatePublisher currentScenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        return returnHome(username,currentScenePublisher,writer);
    }

    private Result enter(
        int regionId,
        int plane,
        SceneUpdatePublisher currentScenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        if(world.players().size()!=1){
            return new Result(
                "V5160_REGION_LOAD REJECTED_MULTIPLAYER members="+
                world.players().size()+
                " reason=PER_VIEW_REGION_MEMBERSHIP_NOT_YET_PROMOTED",
                null,
                currentScenePublisher
            );
        }

        WorldRegionAuthorityRepository.Region region=
            WorldRegionAuthorityRepository.get(regionId);

        if(region==null){
            return new Result(
                "V5160_REGION_LOAD REJECTED_UNKNOWN_REGION id="+
                regionId,
                null,
                currentScenePublisher
            );
        }

        if(!region.mapPresent||!region.terrainParseOk){
            return new Result(
                "V5160_REGION_LOAD REJECTED_TERRAIN_NOT_DECODED id="+
                regionId+
                " mapPresent="+region.mapPresent+
                " terrainParseOk="+region.terrainParseOk,
                null,
                currentScenePublisher
            );
        }

        if(!WorldCollisionAuthority.hasRegion(regionId)){
            return new Result(
                "V5160_REGION_LOAD REJECTED_COLLISION_AUTHORITY_MISSING id="+
                regionId,
                null,
                currentScenePublisher
            );
        }

        if(plane<0||plane>3){
            return new Result(
                "V5160_REGION_LOAD REJECTED_PLANE expected=0..3",
                null,
                currentScenePublisher
            );
        }

        Tile tile=WorldCollisionAuthority.safeTile(
            regionId,plane);

        if(tile==null){
            return new Result(
                "V5160_REGION_LOAD REJECTED_NO_SAFE_STATIC_TILE id="+
                regionId+
                " plane="+plane,
                null,
                currentScenePublisher
            );
        }

        TradeService.cancelIfActive(
            worldPlayer,"REGION_DEV_LOAD");
        playerInteractions.clearTargets();
        combat.cancelForManualMovement();
        cancelPetFollowSchedule.run();

        int chunkX=tile.x>>3;
        int chunkY=tile.y>>3;
        int baseX=(chunkX-6)<<3;
        int baseY=(chunkY-6)<<3;

        // Explicitly remove the HOME NPC view before changing region. The
        // registry itself stays intact so regionhome republishes those exact
        // semantic actors instead of reconstructing or duplicating them.
        List<NpcEntity> oldNpcs=npcs.snapshot();
        if(!oldNpcs.isEmpty()){
            ArrayList<NpcSyncEncoder.Update> removals=
                new ArrayList<>();

            for(NpcEntity npc:oldNpcs){
                removals.add(
                    NpcSyncEncoder.Update.remove(npc));
            }

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

        movement.enterTransientRegion(
            tile.x,
            tile.y,
            plane,
            baseX,
            baseY);

        writer.fixed(219,new byte[0]);
        writer.fixed(
            73,
            BootstrapPackets.region73(chunkX,chunkY));
        RegionLoadLifecycle.Begin regionLoad=
            regionLoads.begin(
                chunkX,
                chunkY,
                baseX,
                baseY,
                "DEV_REGION_RELOCATION"
            );
        writer.varShort(
            81,
            BootstrapPackets.player81TeleportNoAppearance(
                plane,
                tile.y-baseY,
                tile.x-baseX));

        SceneUpdatePublisher nextPublisher=
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    baseX,baseY,plane));

        return new Result(
            "V5160_REGION_LOAD OK region="+regionId+
            " name=["+region.name+"]"+
            " group=["+region.group+"]"+
            " landing="+tile.x+","+tile.y+","+plane+
            " base="+baseX+","+baseY+
            " packet73="+chunkX+","+chunkY+
            " regionLoadSeq="+regionLoad.sequence+
            " placement=SERVER_PLAYER81_RELOCATION"+
            " collision=EXACT_CURRENT_STATIC"+
            " removedHomeNpcView="+oldNpcs.size()+
            " arrivalAuthority=LOCAL_DEV_SAFE_TILE_NOT_PRODUCTION"+
            " persistence=HOME_FALLBACK",
            "REGION_DEV_LOAD_NONPERSISTENT",
            nextPublisher
        );
    }

    private Result returnHome(
        String username,
        SceneUpdatePublisher currentScenePublisher,
        ServerPacketWriter writer
    )throws IOException{
        if(!movement.transientRegion()){
            return new Result(
                "V5160_REGION_HOME ALREADY_HOME world="+
                movement.x()+","+movement.y(),
                null,
                currentScenePublisher
            );
        }

        if(world.players().size()!=1){
            return new Result(
                "V5160_REGION_HOME REJECTED_MULTIPLAYER members="+
                world.players().size(),
                null,
                currentScenePublisher
            );
        }

        movement.returnHome();

        writer.fixed(219,new byte[0]);
        writer.fixed(
            73,
            BootstrapPackets.region73(385,436));
        RegionLoadLifecycle.Begin regionLoad=
            regionLoads.begin(
                385,
                436,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y,
                "DEV_RETURN_HOME_RELOCATION"
            );
        writer.varShort(
            81,
            BootstrapPackets.player81TeleportNoAppearance(
                0,55,55));

        SceneUpdatePublisher nextPublisher=
            new SceneUpdatePublisher(
                writer,
                new SceneCoordinateContext(
                    MovementState.REGION_BASE_X,
                    MovementState.REGION_BASE_Y,
                    0));

        HomeObjectOverlayReplayer.Stats scene=
            homeWorld.replayScene(
                writer,
                MovementState.REGION_BASE_X,
                MovementState.REGION_BASE_Y);

        nextPublisher.context().invalidate();

        List<NpcEntity> homeNpcs=npcs.snapshot();
        if(!homeNpcs.isEmpty()){
            writer.varShort(
                65,
                NpcSyncEncoder.initial(
                    homeNpcs,
                    movement.x(),
                    movement.y()));
        }

        int replay=0;
        for(GroundItem item:world.groundItems().snapshot()){
            if(item.owner==null||
               item.owner.equalsIgnoreCase(username)){
                nextPublisher.groundSpawn(item);
                replay++;
            }
        }

        return new Result(
            "V5160_REGION_HOME OK world="+
            movement.x()+","+movement.y()+
            " packet73=385,436"+
            " regionLoadSeq="+regionLoad.sequence+
            " placement=SERVER_PLAYER81_RELOCATION"+
            " scene={"+scene+"}"+
            " groundReplay="+replay+
            " npcRepublish="+homeNpcs.size()+
            " pet="+petState.active(),
            "REGION_DEV_RETURN_HOME",
            nextPublisher
        );
    }

    private static int parseInt(String value,int fallback){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }
}
