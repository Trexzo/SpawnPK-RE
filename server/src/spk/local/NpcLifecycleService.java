package spk.local;

import java.util.*;

/**
 * Authoritative semantic HP/death lifecycle for canonical world-owned NPCs.
 *
 * WorldNpcRegistry remains canonical entity-existence authority. Lifecycle
 * mutation is performed only while the registry still owns the exact WorldNpc.
 * Respawn, drops, kill-credit, XP/rewards and packet-65 presentation remain
 * external.
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

        Snapshot(Entry entry){
            this.npcId=entry.npc.id;
            this.definitionId=entry.npc.definitionId;
            this.hitpoints=entry.hitpoints;
            this.maxHitpoints=entry.maxHitpoints;
            this.state=entry.state;
            this.deathTick=entry.deathTick;
            this.sourceAuthority=entry.sourceAuthority;
        }

        boolean alive(){
            return state==State.ALIVE;
        }

        boolean dead(){
            return state==State.DEAD;
        }

        boolean hasDeathTick(){
            return deathTick!=NO_DEATH_TICK;
        }
    }

    static final class LifecycleOwnershipException
        extends IllegalStateException {
        final EntityId npcId;

        LifecycleOwnershipException(
            EntityId npcId,
            String reason
        ){
            super(
                "NPC lifecycle ownership unavailable id="+
                Objects.requireNonNull(
                    npcId,
                    "npcId"
                )+
                " reason="+
                Objects.requireNonNull(
                    reason,
                    "reason"
                )
            );
            this.npcId=npcId;
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
            this.requestedDamage=requestedDamage;
            this.appliedDamage=appliedDamage;
            this.hitpointsBefore=hitpointsBefore;
            this.hitpointsAfter=hitpointsAfter;
            this.newlyDied=newlyDied;
            this.ignoredDead=ignoredDead;
            this.worldTick=worldTick;
            this.sourceAuthority=entry.sourceAuthority;
        }
    }

    private static final class Entry {
        final WorldNpc npc;
        final int maxHitpoints;
        final String sourceAuthority;

        int hitpoints;
        State state=State.ALIVE;
        long deathTick=NO_DEATH_TICK;

        Entry(
            WorldNpc npc,
            int maxHitpoints,
            String sourceAuthority
        ){
            this.npc=npc;
            this.maxHitpoints=maxHitpoints;
            this.hitpoints=maxHitpoints;
            this.sourceAuthority=sourceAuthority;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    interface DeadNpcAction {
        void run(Snapshot snapshot) throws Exception;
    }

    private final WorldNpcRegistry npcs;
    private final LinkedHashMap<EntityId,Entry> entries=
        new LinkedHashMap<>();

    NpcLifecycleService(WorldNpcRegistry npcs){
        this.npcs=Objects.requireNonNull(
            npcs,
            "npcs"
        );
    }

    Snapshot register(
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
                "maxHitpoints="+maxHitpoints
            );

        String authority=
            requireAuthority(
                sourceAuthority
            );

        Snapshot[] result=
            new Snapshot[1];

        boolean current=
            withCurrentNpc(
                checked,
                ()->{
                    synchronized(this){
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
                                authority
                            );

                        entries.put(
                            checked.id,
                            entry
                        );

                        result[0]=
                            entry.snapshot();
                    }
                }
            );

        if(!current)
            throw new IllegalArgumentException(
                "NPC is not canonical registry entity id="+
                checked.id
            );

        return result[0];
    }

    DamageResult applyDamage(
        EntityId npcId,
        int amount,
        long worldTick
    ){
        return applyDamageInternal(
            npcId,
            amount,
            worldTick,
            false
        );
    }

    DamageResult applyDamageOwned(
        EntityId npcId,
        int amount,
        long worldTick
    ){
        return applyDamageInternal(
            npcId,
            amount,
            worldTick,
            true
        );
    }

    private DamageResult applyDamageInternal(
        EntityId npcId,
        int amount,
        long worldTick,
        boolean typedOwnershipFailure
    ){
        if(amount<0)
            throw new IllegalArgumentException(
                "damage amount="+amount
            );

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        EntityId key=
            Objects.requireNonNull(
                npcId,
                "npcId"
            );

        Entry expected;

        synchronized(this){
            expected=
                entries.get(
                    key
                );

            if(expected==null){
                if(typedOwnershipFailure)
                    throw new LifecycleOwnershipException(
                        key,
                        "missing-before-damage"
                    );

                throw new IllegalArgumentException(
                    "NPC lifecycle not registered id="+
                    key
                );
            }
        }

        DamageResult[] result=
            new DamageResult[1];

        boolean current=
            withCurrentNpc(
                expected.npc,
                ()->{
                    synchronized(this){
                        Entry entry=
                            entries.get(
                                key
                            );

                        if(entry!=expected){
                            if(typedOwnershipFailure)
                                throw new LifecycleOwnershipException(
                                    key,
                                    entry==null
                                        ?"removed-during-damage"
                                        :"replaced-during-damage"
                                );

                            throw new IllegalStateException(
                                "NPC lifecycle ownership changed id="+
                                key
                            );
                        }

                        int before=
                            entry.hitpoints;

                        if(entry.state==State.DEAD){
                            result[0]=
                                new DamageResult(
                                    entry,
                                    amount,
                                    0,
                                    before,
                                    before,
                                    false,
                                    true,
                                    worldTick
                                );
                            return;
                        }

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

                        result[0]=
                            new DamageResult(
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
                }
            );

        if(!current)
            throw new IllegalStateException(
                "canonical NPC registry ownership lost id="+
                key
            );

        return result[0];
    }

    Snapshot retireDeadCanonical(
        WorldNpc npc
    ){
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );

        Snapshot[] retired=
            new Snapshot[1];

        boolean current=
            withCurrentNpc(
                checked,
                ()->{
                    synchronized(this){
                        Entry entry=
                            entries.get(
                                checked.id
                            );

                        if(entry==null)
                            throw new IllegalStateException(
                                "NPC lifecycle missing id="+
                                checked.id
                            );

                        if(entry.npc!=checked)
                            throw new IllegalStateException(
                                "NPC lifecycle exact object changed id="+
                                checked.id
                            );

                        if(entry.state!=State.DEAD||
                           entry.deathTick==
                                NO_DEATH_TICK)
                            throw new IllegalStateException(
                                "NPC is not dead id="+
                                checked.id
                            );

                        Snapshot snapshot=
                            entry.snapshot();

                        if(!npcs.remove(
                                checked.id))
                            throw new IllegalStateException(
                                "canonical NPC retirement failed id="+
                                checked.id
                            );

                        entries.remove(
                            checked.id
                        );

                        retired[0]=snapshot;
                    }
                }
            );

        if(!current)
            throw new IllegalStateException(
                "canonical NPC registry ownership lost id="+
                checked.id
            );

        return retired[0];
    }

    boolean withDeadCanonicalOwnershipIfCurrent(
        WorldNpc npc,
        DeadNpcAction action
    )throws Exception{
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );
        Objects.requireNonNull(
            action,
            "action"
        );

        return npcs
            .withCurrentMutationOwnershipIfCurrent(
                checked,
                ()->{
                    synchronized(this){
                        Entry entry=
                            entries.get(
                                checked.id
                            );

                        if(entry==null)
                            throw new IllegalStateException(
                                "NPC lifecycle missing id="+
                                checked.id
                            );

                        if(entry.npc!=checked)
                            throw new IllegalStateException(
                                "NPC lifecycle exact object changed id="+
                                checked.id
                            );

                        if(entry.state!=State.DEAD||
                           entry.deathTick==
                                NO_DEATH_TICK)
                            throw new IllegalStateException(
                                "NPC lifecycle is not dead id="+
                                checked.id
                            );

                        Snapshot before=
                            entry.snapshot();

                        action.run(
                            before
                        );

                        if(npcs.byId(
                                checked.id
                            )!=checked)
                            throw new IllegalStateException(
                                "NPC registry ownership changed during owned action id="+
                                checked.id
                            );

                        Entry after=
                            entries.get(
                                checked.id
                            );

                        if(after!=entry)
                            throw new IllegalStateException(
                                "NPC lifecycle entry changed during owned action id="+
                                checked.id
                            );

                        if(entry.state!=State.DEAD||
                           entry.deathTick!=
                                before.deathTick)
                            throw new IllegalStateException(
                                "NPC death identity changed during owned action id="+
                                checked.id
                            );
                    }
                }
            );
    }

    synchronized Snapshot get(EntityId npcId){
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

    synchronized boolean unregister(EntityId npcId){
        return entries.remove(
            Objects.requireNonNull(
                npcId,
                "npcId"
            )
        )!=null;
    }

    synchronized boolean unregisterExact(
        WorldNpc expectedNpc
    ){
        WorldNpc checked=
            Objects.requireNonNull(
                expectedNpc,
                "expectedNpc"
            );

        Entry entry=
            entries.get(
                checked.id
            );

        if(entry==null)
            return false;

        if(entry.npc!=checked)
            throw new IllegalStateException(
                "NPC lifecycle exact object changed id="+
                checked.id
            );

        entries.remove(
            checked.id
        );
        return true;
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

        for(Entry entry:ordered)
            out.add(
                entry.snapshot()
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private synchronized Entry requireEntry(
        EntityId npcId
    ){
        Entry entry=
            entries.get(
                npcId
            );

        if(entry==null)
            throw new IllegalArgumentException(
                "NPC lifecycle not registered id="+
                npcId
            );

        return entry;
    }

    private boolean withCurrentNpc(
        WorldNpc npc,
        WorldNpcRegistry.OwnedNpcAction action
    ){
        try{
            return npcs
                .withCurrentMutationOwnershipIfCurrent(
                    npc,
                    action
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected checked NPC ownership action failure",
                failure
            );
        }
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
