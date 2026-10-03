package spk.local;

import java.util.*;

/**
 * World-owned deterministic HOME NPC state.
 *
 * Canonical NPC identity/position lives in WorldNpcRegistry. Viewer code receives
 * packet-65 projections with the existing stable HOME scene indexes.
 */
final class WorldHomeNpcService {
    static final class VisibleNpc {
        final int ordinal;
        final EntityId canonicalId;
        final int definitionId;
        final int x;
        final int y;
        final int plane;

        VisibleNpc(
            int ordinal,
            EntityId canonicalId,
            int definitionId,
            int x,
            int y,
            int plane
        ){
            this.ordinal=ordinal;
            this.canonicalId=
                Objects.requireNonNull(
                    canonicalId,
                    "canonicalId"
                );
            this.definitionId=definitionId;
            this.x=x;
            this.y=y;
            this.plane=plane;
        }

        NpcEntity project(int sceneIndex){
            NpcEntity projection=
                new NpcEntity(
                    sceneIndex,
                    definitionId,
                    x,
                    y
                );

            projection.bindCanonicalId(
                canonicalId
            );

            return projection;
        }
    }

    private final WorldNpcRegistry registry;
    private final HomeNpcWorldState state=
        HomeNpcRuntimePlan.newWorldState();
    private final LinkedHashMap<Integer,EntityId> byOrdinal=
        new LinkedHashMap<>();

    private boolean initialized;
    private long lastAdvancedTick=Long.MIN_VALUE;
    private List<HomeNpcWorldState.Move> lastMoves=
        Collections.emptyList();

    WorldHomeNpcService(WorldNpcRegistry registry){
        this.registry=Objects.requireNonNull(
            registry,
            "registry"
        );
    }

    synchronized void ensureInitialized(){
        if(initialized)return;

        for(HomeNpcWorldState.Actor actor:state.actors()){
            WorldNpc npc=
                registry.spawn(
                    actor.spawn.npcDefinitionId,
                    actor.x,
                    actor.y,
                    0
                );

            if(byOrdinal.put(
                actor.spawn.ordinal,
                npc.id
            )!=null)
                throw new IllegalStateException(
                    "duplicate HOME NPC ordinal "+
                    actor.spawn.ordinal
                );
        }

        initialized=true;
    }

    /**
     * Legacy isolated-plan compatibility only. Shared production World plans do
     * not call this when another viewer attaches.
     */
    synchronized void resetToAnchors(){
        ensureInitialized();
        state.reset();

        for(HomeNpcWorldState.Actor actor:state.actors()){
            WorldNpc npc=canonicalForOrdinal(
                actor.spawn.ordinal
            );
            if(npc==null)
                throw new IllegalStateException(
                    "missing canonical HOME NPC ordinal "+
                    actor.spawn.ordinal
                );
            registry.move(
                npc.id,
                actor.x,
                actor.y,
                0
            );
        }

        lastAdvancedTick=Long.MIN_VALUE;
        lastMoves=Collections.emptyList();
    }

    synchronized List<VisibleNpc> visibleCanonical(
        int playerX,
        int playerY
    ){
        ensureInitialized();

        ArrayList<VisibleNpc> out=new ArrayList<>();

        for(HomeNpcWorldState.Actor actor:state.actors()){
            WorldNpc npc=canonicalForOrdinal(
                actor.spawn.ordinal
            );

            if(npc==null)
                throw new IllegalStateException(
                    "missing canonical HOME NPC ordinal "+
                    actor.spawn.ordinal
                );

            Tile tile=npc.tile();
            int dx=tile.x-playerX;
            int dy=tile.y-playerY;

            if(dx>=-16&&dx<=15&&dy>=-16&&dy<=15)
                out.add(
                    new VisibleNpc(
                        actor.spawn.ordinal,
                        npc.id,
                        npc.definitionId,
                        tile.x,
                        tile.y,
                        tile.plane
                    )
                );
        }

        out.sort(
            Comparator.comparingInt(
                visible->visible.ordinal
            )
        );

        return Collections.unmodifiableList(out);
    }

    /**
     * Compatibility projection for older tests/tools. Production viewer code
     * owns the canonical-id -> scene-index map in HomeWorldRuntimePlan.
     */
    synchronized List<NpcEntity> visibleEntities(
        int playerX,
        int playerY
    ){
        ArrayList<NpcEntity> out=new ArrayList<>();
        for(VisibleNpc visible:
            visibleCanonical(playerX,playerY)){
            int sceneIndex=
                HomeNpcRuntimePlan.sceneIndexForOrdinal(
                    visible.ordinal
                );
            out.add(
                visible.project(
                    sceneIndex
                )
            );
        }
        return Collections.unmodifiableList(out);
    }

    synchronized List<HomeNpcWorldState.Move> tick(
        long worldTick
    ){
        if(worldTick<=0)
            throw new IllegalArgumentException(
                "worldTick"
            );

        ensureInitialized();

        if(worldTick==lastAdvancedTick)
            return lastMoves;

        if(lastAdvancedTick!=Long.MIN_VALUE&&
           worldTick<lastAdvancedTick)
            throw new IllegalArgumentException(
                "worldTick regressed "+
                lastAdvancedTick+"->"+worldTick
            );

        List<HomeNpcWorldState.Move> moves=
            state.tick(worldTick);

        for(HomeNpcWorldState.Move move:moves){
            WorldNpc npc=canonicalForOrdinal(
                move.ordinal
            );

            if(npc==null)
                throw new IllegalStateException(
                    "missing canonical HOME NPC ordinal "+
                    move.ordinal
                );

            registry.move(
                npc.id,
                move.toX,
                move.toY,
                0
            );
        }

        lastAdvancedTick=worldTick;
        lastMoves=moves;
        return lastMoves;
    }

    synchronized WorldNpc canonicalForOrdinal(
        int ordinal
    ){
        ensureInitialized();
        EntityId id=byOrdinal.get(ordinal);
        return id==null?null:registry.byId(id);
    }

    synchronized boolean ownsCanonical(
        EntityId id
    ){
        return id!=null&&
            byOrdinal.containsValue(id);
    }

    synchronized int size(){
        ensureInitialized();
        return byOrdinal.size();
    }
}
