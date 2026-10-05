package spk.local;

import java.io.*;
import java.util.*;

/**
 * WORLD-R5 deterministic HOME runtime contract.
 *
 * This class deliberately does not own the generic NPC registry, pet lifecycle,
 * player movement, interaction mechanics, or pathfinder. Instead it exposes the
 * exact WORLD-owned state that MAINLINE can merge into its single packet-65 and
 * scene/collision pipelines.
 */
final class HomeWorldRuntimePlan {
    static final class NpcDelta {
        final long worldTick;
        final List<NpcEntity> added;
        final Set<Integer> removedSceneIndexes;
        final Map<Integer,Integer> walkDirectionBySceneIndex;
        final Set<Integer> desiredVisibleSceneIndexes;
        final int playerX,playerY;

        NpcDelta(long worldTick,List<NpcEntity> added,Set<Integer> removed,
                 Map<Integer,Integer> walked,Set<Integer> desired,int playerX,int playerY) {
            this.worldTick=worldTick;
            this.added=Collections.unmodifiableList(new ArrayList<>(added));
            this.removedSceneIndexes=Collections.unmodifiableSet(new LinkedHashSet<>(removed));
            this.walkDirectionBySceneIndex=Collections.unmodifiableMap(new LinkedHashMap<>(walked));
            this.desiredVisibleSceneIndexes=Collections.unmodifiableSet(new LinkedHashSet<>(desired));
            this.playerX=playerX; this.playerY=playerY;
        }
        boolean shouldRemove(int sceneIndex){ return removedSceneIndexes.contains(sceneIndex); }
        Integer walkDirection(int sceneIndex){ return walkDirectionBySceneIndex.get(sceneIndex); }
        boolean shouldRemainVisible(int sceneIndex){ return desiredVisibleSceneIndexes.contains(sceneIndex); }
        @Override public String toString(){
            return "NpcDelta{tick="+worldTick+",add="+added.size()+",remove="+removedSceneIndexes.size()+
                ",walk="+walkDirectionBySceneIndex.size()+",visible="+desiredVisibleSceneIndexes.size()+
                ",player="+playerX+","+playerY+"}";
        }
    }

    static final class ViewerPresentationSnapshot {
        final boolean bootstrapEstablished;
        final LinkedHashSet<Integer> clientVisibleWorldSceneIndexes;

        ViewerPresentationSnapshot(
            boolean bootstrapEstablished,
            Set<Integer> clientVisibleWorldSceneIndexes
        ){
            this.bootstrapEstablished=bootstrapEstablished;
            this.clientVisibleWorldSceneIndexes=
                new LinkedHashSet<>(
                    clientVisibleWorldSceneIndexes
                );
        }
    }

    static final class RemoveOnlyOpenOverride {
        final int worldX,worldY,layer,shape,rotation;
        final String provenance;
        RemoveOnlyOpenOverride(HomeCollisionOverlayRepository.Entry e){
            worldX=e.worldX; worldY=e.worldY; layer=e.layer; shape=e.shape; rotation=e.rotation; provenance=e.provenance;
        }
        @Override public String toString(){return "OpenOverride{"+worldX+","+worldY+",layer="+layer+",shape="+shape+",rot="+rotation+"}";}
    }

    private final WorldHomeNpcService homeNpcs;
    private final boolean resetOnBootstrap;
    private final NpcViewIndexMap viewIndexes=new NpcViewIndexMap();
    private final LinkedHashSet<Integer> clientVisibleWorldSceneIndexes=new LinkedHashSet<>();
    private boolean npcBootstrapEstablished;

    HomeWorldRuntimePlan(){
        this(
            new WorldHomeNpcService(
                new WorldNpcRegistry()
            ),
            true
        );
    }

    HomeWorldRuntimePlan(
        WorldHomeNpcService homeNpcs
    ){
        this(homeNpcs,false);
    }

    private HomeWorldRuntimePlan(
        WorldHomeNpcService homeNpcs,
        boolean resetOnBootstrap
    ){
        this.homeNpcs=Objects.requireNonNull(
            homeNpcs,
            "homeNpcs"
        );
        this.resetOnBootstrap=resetOnBootstrap;
    }

    /** Replay exact production HOME object mutations using packets 85/101/151. */
    HomeObjectOverlayReplayer.Stats replayScene(ServerPacketWriter writer) throws IOException {
        return replayScene(writer, MovementState.REGION_BASE_X, MovementState.REGION_BASE_Y);
    }

    /** Replay using the caller's actual loaded-region base, not V9.06's capture base. */
    HomeObjectOverlayReplayer.Stats replayScene(ServerPacketWriter writer,int runtimeBaseX,int runtimeBaseY) throws IOException {
        HomeWorldManifest.requireSceneSerializerCertification();
        return HomeObjectOverlayReplayer.replayHome(writer,runtimeBaseX,runtimeBaseY);
    }

    /**
     * Establish WORLD's initial NPC subset for MAINLINE's single initial packet-65.
     * MAINLINE may append its pet/dynamic actors before encoding that one packet.
     */
    List<NpcEntity> bootstrapNpcs(int playerX,int playerY) {
        if(resetOnBootstrap)homeNpcs.resetToAnchors();
        else homeNpcs.ensureInitialized();

        ArrayList<NpcEntity> initial=new ArrayList<>();
        for(WorldHomeNpcService.VisibleNpc visible:
            homeNpcs.visibleCanonical(playerX,playerY))
            initial.add(project(visible));

        clientVisibleWorldSceneIndexes.clear();
        for(NpcEntity n:initial)
            clientVisibleWorldSceneIndexes.add(n.sceneIndex);
        npcBootstrapEstablished=true;
        return Collections.unmodifiableList(initial);
    }

    ViewerPresentationSnapshot snapshotViewerPresentation(){
        return new ViewerPresentationSnapshot(
            npcBootstrapEstablished,
            clientVisibleWorldSceneIndexes
        );
    }

    void restoreViewerPresentation(
        ViewerPresentationSnapshot snapshot
    ){
        if(snapshot==null)
            throw new NullPointerException(
                "snapshot"
            );

        npcBootstrapEstablished=
            snapshot.bootstrapEstablished;
        clientVisibleWorldSceneIndexes.clear();
        clientVisibleWorldSceneIndexes.addAll(
            snapshot.clientVisibleWorldSceneIndexes
        );
    }

    List<NpcEntity> currentProjection(
        int playerX,
        int playerY
    ){
        if(!npcBootstrapEstablished)
            throw new IllegalStateException(
                "bootstrapNpcs must be called before currentProjection"
            );

        ArrayList<NpcEntity> current=
            new ArrayList<>();

        for(WorldHomeNpcService.VisibleNpc visible:
                homeNpcs.visibleCanonical(
                    playerX,
                    playerY
                ))
            current.add(
                project(
                    visible
                )
            );

        return Collections.unmodifiableList(
            current
        );
    }

    /**
     * Advance evidence-backed HOME wandering and produce only WORLD's delta.
     * The caller merges this with pets/other dynamic actors in a single NpcSyncEncoder pulse.
     */
    NpcDelta tick(long worldTick,int playerX,int playerY) {
        if(!npcBootstrapEstablished) throw new IllegalStateException("bootstrapNpcs must be called before tick");
        if(worldTick<=0) throw new IllegalArgumentException("worldTick");

        List<HomeNpcWorldState.Move> moves=homeNpcs.tick(worldTick);
        LinkedHashMap<Integer,Integer> moved=new LinkedHashMap<>();
        for(HomeNpcWorldState.Move m:moves){
            WorldNpc canonical=
                homeNpcs.canonicalForOrdinal(m.ordinal);
            if(canonical==null)
                throw new IllegalStateException(
                    "missing canonical HOME NPC ordinal "+
                    m.ordinal
                );
            int scene=viewIndexes.bind(
                canonical.id,
                HomeNpcRuntimePlan.sceneIndexForOrdinal(
                    m.ordinal
                )
            );
            moved.put(scene,m.direction);
        }

        LinkedHashMap<Integer,NpcEntity> desiredByScene=new LinkedHashMap<>();
        for(WorldHomeNpcService.VisibleNpc visible:
            homeNpcs.visibleCanonical(playerX,playerY)){
            NpcEntity n=project(visible);
            if(desiredByScene.put(n.sceneIndex,n)!=null)
                throw new IllegalStateException(
                    "duplicate HOME scene index "+
                    n.sceneIndex
                );
        }
        LinkedHashSet<Integer> desired=new LinkedHashSet<>(desiredByScene.keySet());

        LinkedHashSet<Integer> removed=new LinkedHashSet<>();
        for(Integer scene:clientVisibleWorldSceneIndexes) if(!desired.contains(scene)) removed.add(scene);

        ArrayList<NpcEntity> added=new ArrayList<>();
        for(Map.Entry<Integer,NpcEntity> e:desiredByScene.entrySet()) if(!clientVisibleWorldSceneIndexes.contains(e.getKey())) added.add(e.getValue());

        LinkedHashMap<Integer,Integer> walked=new LinkedHashMap<>();
        for(Map.Entry<Integer,Integer> e:moved.entrySet()) {
            int scene=e.getKey();
            if(clientVisibleWorldSceneIndexes.contains(scene) && desired.contains(scene)) walked.put(scene,e.getValue());
        }

        clientVisibleWorldSceneIndexes.clear();
        clientVisibleWorldSceneIndexes.addAll(desired);
        return new NpcDelta(worldTick,added,removed,walked,desired,playerX,playerY);
    }

    private NpcEntity project(
        WorldHomeNpcService.VisibleNpc visible
    ){
        int sceneIndex=viewIndexes.bind(
            visible.canonicalId,
            HomeNpcRuntimePlan.sceneIndexForOrdinal(
                visible.ordinal
            )
        );

        return visible.project(
            sceneIndex
        );
    }

    Integer sceneIndexForCanonical(
        EntityId entityId
    ){
        return viewIndexes.sceneIndex(entityId);
    }

    /** True for the stable scene-index block reserved by WORLD-R3. */
    static boolean isHomeWorldSceneIndex(int sceneIndex){
        return sceneIndex>=HomeNpcRuntimePlan.sceneIndexForOrdinal(1) && sceneIndex<=HomeNpcRuntimePlan.sceneIndexForOrdinal(HomeNpcSpawnRepository.count());
    }

    /** Exact collision deltas for the 26 production HOME additions. */
    static List<ClientCollisionDeltaCodec.Delta> collisionAddDeltas(){
        HomeWorldManifest.requireCollisionPolicyCertification();
        ArrayList<ClientCollisionDeltaCodec.Delta> out=new ArrayList<>();
        for(HomeCollisionOverlayRepository.Entry e:HomeCollisionOverlayRepository.all()) if(e.isAdd()) out.addAll(HomeCollisionOverlayRepository.addDeltas(e));
        return Collections.unmodifiableList(out);
    }

    /** The one V9.06 remove-only tile whose old cached object identity was not persisted. */
    static List<RemoveOnlyOpenOverride> removeOnlyOpenOverrides(){
        ArrayList<RemoveOnlyOpenOverride> out=new ArrayList<>();
        for(HomeCollisionOverlayRepository.Entry e:HomeCollisionOverlayRepository.all()) if(e.removeOnly()) out.add(new RemoveOnlyOpenOverride(e));
        return Collections.unmodifiableList(out);
    }

    static List<HomeLandmarkRepository.Landmark> landmarks(){ return HomeWorldManifest.landmarks(); }
    int trackedWorldNpcCount(){ return homeNpcs.size(); }
    int clientVisibleWorldNpcCount(){ return clientVisibleWorldSceneIndexes.size(); }
}
