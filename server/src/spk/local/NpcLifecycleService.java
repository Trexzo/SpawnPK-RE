package spk.local;

import java.util.*;

/**
 * Authoritative semantic HP/death lifecycle for canonical world-owned NPCs.
 *
 * WorldNpcRegistry remains canonical entity-existence authority. This service
 * owns only hitpoints and the ALIVE -> DEAD transition. Respawn, drops,
 * kill-credit, XP/rewards and packet-65 presentation remain external.
 */
final class NpcLifecycleService {
    static final long NO_DEATH_TICK=-1L;

    enum State {
        ALIVE,
        DEAD
    }

    static final class Snapshot {
        final EntityId npcId;
        final int definitionId;
        final int hitpoints;
        final int maxHitpoints;
        final State state;
        final long deathTick;
        final String sourceAuthority;

        Snapshot(
            Entry entry
        ){
            this.npcId=entry.npc.id;
            this.definitionId=
                entry.npc.definitionId;
            this.hitpoints=
                entry.hitpoints;
            this.maxHitpoints=
                entry.maxHitpoints;
            this.state=entry.state;
            this.deathTick=
                entry.deathTick;
            this.sourceAuthority=
                entry.sourceAuthority;
        }

        boolean alive(){
            return state==State.ALIVE;
        }

        boolean dead(){
            return state==State.DEAD;
        }

        boolean hasDeathTick(){
            return deathTick!=
                NO_DEATH_TICK;
        }
    }

    static final class DamageResult {
        final EntityId npcId;
        final int requestedDamage;
        final int appliedDamage;
        final int hitpointsBefore;
        final int hitpointsAfter;
        final boolean newlyDied;
        final boolean ignoredDead;
        final long worldTick;
        final String sourceAuthority;

        DamageResult(
            Entry entry,
            int requestedDamage,
            int appliedDamage,
            int hitpointsBefore,
            int hitpointsAfter,
            boolean newlyDied,
            boolean ignoredDead,
            long worldTick
        ){
            this.npcId=entry.npc.id;
            this.requestedDamage=
                requestedDamage;
            this.appliedDamage=
                appliedDamage;
            this.hitpointsBefore=
                hitpointsBefore;
            this.hitpointsAfter=
                hitpointsAfter;
            this.newlyDied=newlyDied;
            this.ignoredDead=
                ignoredDead;
            this.worldTick=worldTick;
            this.sourceAuthority=
                entry.sourceAuthority;
        }
    }

    private static final class Entry {
        final WorldNpc npc;
        final int maxHitpoints;
        final String sourceAuthority;

        int hitpoints;
        State state=State.ALIVE;
        long deathTick=
            NO_DEATH_TICK;

        Entry(
            WorldNpc npc,
            int maxHitpoints,
            String sourceAuthority
        ){
            this.npc=npc;
            this.maxHitpoints=
                maxHitpoints;
            this.hitpoints=
                maxHitpoints;
            this.sourceAuthority=
                sourceAuthority;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final WorldNpcRegistry npcs;

    private final LinkedHashMap<EntityId,Entry>
        entries=
            new LinkedHashMap<>();

    NpcLifecycleService(
        WorldNpcRegistry npcs
    ){
        this.npcs=
            Objects.requireNonNull(
                npcs,
                "npcs"
            );
    }

    synchronized Snapshot register(
        WorldNpc npc,
        int maxHitpoints,
        String sourceAuthority
    ){
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        if(maxHitpoints<=0)
            throw new IllegalArgumentException(
                "maxHitpoints="+
                maxHitpoints
            );

        WorldNpc canonical=
            npcs.byId(
                checked.id
            );

        if(canonical!=checked)
            throw new IllegalArgumentException(
                "NPC is not canonical registry entity id="+
                checked.id
            );

        if(entries.containsKey(
                checked.id))
            throw new IllegalStateException(
                "NPC lifecycle already registered id="+
                checked.id
            );

        Entry entry=
            new Entry(
                checked,
                maxHitpoints,
                requireAuthority(
                    sourceAuthority
                )
            );

        entries.put(
            checked.id,
            entry
        );

        return entry.snapshot();
    }

    synchronized DamageResult applyDamage(
        EntityId npcId,
        int amount,
        long worldTick
    ){
        if(amount<0)
            throw new IllegalArgumentException(
                "damage amount="+amount
            );

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        Entry entry=
            requireEntry(
                npcId
            );

        requireCanonical(entry);

        int before=
            entry.hitpoints;

        if(entry.state==State.DEAD)
            return new DamageResult(
                entry,
                amount,
                0,
                before,
                before,
                false,
                true,
                worldTick
            );

        int applied=
            Math.min(
                amount,
                before
            );

        int after=
            before-applied;

        entry.hitpoints=after;

        boolean newlyDied=
            after==0;

        if(newlyDied){
            entry.state=State.DEAD;
            entry.deathTick=
                worldTick;
        }

        return new DamageResult(
            entry,
            amount,
            applied,
            before,
            after,
            newlyDied,
            false,
            worldTick
        );
    }

    synchronized Snapshot get(
        EntityId npcId
    ){
        Entry entry=
            entries.get(
                Objects.requireNonNull(
                    npcId,
                    "npcId"
                )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized boolean unregister(
        EntityId npcId
    ){
        return entries.remove(
            Objects.requireNonNull(
                npcId,
                "npcId"
            )
        )!=null;
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Entry> ordered=
            new ArrayList<>(
                entries.values()
            );

        ordered.sort(
            Comparator.comparingLong(
                entry->
                    entry.npc.id.value
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Entry entry:
                ordered)
            out.add(
                entry.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private Entry requireEntry(
        EntityId npcId
    ){
        EntityId key=
            Objects.requireNonNull(
                npcId,
                "npcId"
            );

        Entry entry=
            entries.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "NPC lifecycle not registered id="+
                key
            );

        return entry;
    }

    private void requireCanonical(
        Entry entry
    ){
        WorldNpc canonical=
            npcs.byId(
                entry.npc.id
            );

        if(canonical!=entry.npc)
            throw new IllegalStateException(
                "canonical NPC registry ownership lost id="+
                entry.npc.id
            );
    }

    private static String requireAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "sourceAuthority"
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "sourceAuthority blank"
            );

        return normalized;
    }
}
