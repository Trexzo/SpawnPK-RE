package spk.local;

import java.util.*;

/**
 * WORLD-owned production HOME population state.
 *
 * It owns only placement/presentation movement data. It does not replace
 * MAINLINE's NpcRegistry or packet-65 engine. MAINLINE may consume the stable
 * scene indexes and movement decisions from this class.
 */
final class HomeNpcWorldState {
    static final class Actor {
        final HomeNpcSpawnRepository.Spawn spawn;
        final int sceneIndex;
        int x,y;
        Actor(HomeNpcSpawnRepository.Spawn spawn){
            this.spawn=spawn; this.sceneIndex=HomeNpcRuntimePlan.sceneIndexForOrdinal(spawn.ordinal);
            this.x=spawn.anchorX; this.y=spawn.anchorY;
        }
        NpcEntity entity(){return new NpcEntity(sceneIndex,spawn.npcDefinitionId,x,y);}
    }
    static final class Move {
        final int ordinal,sceneIndex,npcDefinitionId,fromX,fromY,toX,toY,direction;
        Move(Actor a,int fromX,int fromY,int direction){
            this.ordinal=a.spawn.ordinal; this.sceneIndex=a.sceneIndex; this.npcDefinitionId=a.spawn.npcDefinitionId;
            this.fromX=fromX; this.fromY=fromY; this.toX=a.x; this.toY=a.y; this.direction=direction;
        }
        @Override public String toString(){return "Move{ord="+ordinal+",idx="+sceneIndex+",def="+npcDefinitionId+","+fromX+","+fromY+"->"+toX+","+toY+",dir="+direction+"}";}
    }

    private final LinkedHashMap<Integer,Actor> actors=new LinkedHashMap<>();

    HomeNpcWorldState(){
        for(HomeNpcSpawnRepository.Spawn s:HomeNpcSpawnRepository.defaultReplay()) actors.put(s.ordinal,new Actor(s));
    }

    int size(){return actors.size();}
    Actor actor(int ordinal){return actors.get(ordinal);}
    Collection<Actor> actors(){return Collections.unmodifiableCollection(actors.values());}

    void reset(){ for(Actor a:actors.values()){a.x=a.spawn.anchorX;a.y=a.spawn.anchorY;} }

    /** All currently representable HOME actors for packet-65 add relative to this player tile. */
    List<NpcEntity> visibleEntities(int playerX,int playerY){
        ArrayList<NpcEntity> out=new ArrayList<>();
        for(Actor a:actors.values()){
            int dx=a.x-playerX,dy=a.y-playerY;
            if(dx>=-16&&dx<=15&&dy>=-16&&dy<=15) out.add(a.entity());
        }
        out.sort(Comparator.comparingInt(n->n.sceneIndex));
        return Collections.unmodifiableList(out);
    }

    /**
     * Advance only through V9.06-observed adjacent-tile connectivity.
     * Cadence is the survey median movement interval rounded to 600 ms ticks.
     * Choice is deterministic so offline/live regressions are reproducible.
     */
    List<Move> tick(long worldTick){
        ArrayList<Move> moves=new ArrayList<>();
        for(Actor a:actors.values()){
            if(!a.spawn.localWanderObserved()) continue;
            int cadence=HomeNpcWanderRepository.cadenceTicks(a.spawn.ordinal);
            if(cadence==Integer.MAX_VALUE || worldTick<=0 || worldTick%cadence!=0) continue;
            List<HomeNpcWanderRepository.Edge> edges=HomeNpcWanderRepository.outgoing(a.spawn.ordinal,a.x,a.y);
            if(edges.isEmpty()) continue;
            HomeNpcWanderRepository.Edge chosen=weighted(edges,mix(worldTick,a.spawn.ordinal,a.x,a.y));
            int oldX=a.x,oldY=a.y;
            a.x=chosen.toX; a.y=chosen.toY;
            int dir=MovementState.direction(oldX,oldY,a.x,a.y);
            if(dir<0) throw new IllegalStateException("WORLD wander produced non-adjacent move "+a.spawn+" "+oldX+","+oldY+" -> "+a.x+","+a.y);
            moves.add(new Move(a,oldX,oldY,dir));
        }
        return Collections.unmodifiableList(moves);
    }

    private static HomeNpcWanderRepository.Edge weighted(List<HomeNpcWanderRepository.Edge> edges,long seed){
        int total=0; for(HomeNpcWanderRepository.Edge e:edges) total+=e.weight;
        int pick=(int)Math.floorMod(seed,total);
        for(HomeNpcWanderRepository.Edge e:edges){ if(pick<e.weight) return e; pick-=e.weight; }
        return edges.get(edges.size()-1);
    }

    private static long mix(long tick,int ordinal,int x,int y){
        long z=tick*0x9E3779B97F4A7C15L + ordinal*0xBF58476D1CE4E5B9L + ((long)x<<32) + (y&0xffffffffL);
        z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;
        z=(z^(z>>>27))*0x94D049BB133111EBL;
        return z^(z>>>31);
    }
}
