package spk.local;

import java.util.*;

/**
 * Canonical delayed player -> NPC PvM hit delivery.
 *
 * Damage calculation, attack cadence, range, presentation, kill credit and
 * rewards stay outside this service. An already-resolved damage amount is
 * delivered on the shared WorldEventQueue at an authoritative world tick.
 */
final class NpcPvmDelayedHitService {
    static final String REQUIRE_CURRENT_ATTACKER_GENERATION=
        "REQUIRE_CURRENT_ATTACKER_GENERATION";

    enum State {
        SCHEDULED,
        DELIVERED,
        CANCELLED,
        STALE_ATTACKER,
        STALE_TARGET,
        FAILED
    }

    static final class HitId {
        final long value;

        private HitId(long value){
            if(value<=0L)
                throw new IllegalArgumentException(
                    "value="+value
                );
            this.value=value;
        }

        @Override public boolean equals(Object other){
            return other instanceof HitId&&
                ((HitId)other).value==value;
        }

        @Override public int hashCode(){
            return Long.hashCode(value);
        }

        @Override public String toString(){
            return Long.toUnsignedString(value);
        }
    }

    static final class Snapshot {
        final HitId hitId;
        final State state;
        final EntityId attackerId;
        final long attackerGeneration;
        final EntityId targetId;
        final int targetDefinitionId;
        final int requestedDamage;
        final long scheduledFromTick;
        final long dueTick;
        final String damageAuthority;
        final String damageFormula;
        final String deliveryAuthority;
        final String deliveryPolicy;
        final int appliedDamage;
        final int hitpointsBefore;
        final int hitpointsAfter;
        final boolean newlyDied;
        final boolean ignoredDead;
        final String failureType;

        private Snapshot(Entry entry){
            this.hitId=entry.hitId;
            this.state=entry.state;
            this.attackerId=entry.attacker.id();
            this.attackerGeneration=
                entry.attackerGeneration;
            this.targetId=entry.target.id;
            this.targetDefinitionId=
                entry.target.definitionId;
            this.requestedDamage=
                entry.requestedDamage;
            this.scheduledFromTick=
                entry.scheduledFromTick;
            this.dueTick=entry.dueTick;
            this.damageAuthority=
                entry.damageAuthority;
            this.damageFormula=
                entry.damageFormula;
            this.deliveryAuthority=
                entry.deliveryAuthority;
            this.deliveryPolicy=
                entry.deliveryPolicy;

            NpcLifecycleService.DamageResult
                delivered=entry.delivered;

            this.appliedDamage=
                delivered==null
                    ?-1
                    :delivered.appliedDamage;
            this.hitpointsBefore=
                delivered==null
                    ?-1
                    :delivered.hitpointsBefore;
            this.hitpointsAfter=
                delivered==null
                    ?-1
                    :delivered.hitpointsAfter;
            this.newlyDied=
                delivered!=null&&
                delivered.newlyDied;
            this.ignoredDead=
                delivered!=null&&
                delivered.ignoredDead;
            this.failureType=
                entry.failureType;
        }

        boolean terminal(){
            return state!=State.SCHEDULED;
        }
    }

    private static final class Entry {
        final HitId hitId;
        final WorldPlayer attacker;
        final long attackerGeneration;
        final WorldNpc target;
        final int requestedDamage;
        final long scheduledFromTick;
        final long dueTick;
        final String damageAuthority;
        final String damageFormula;
        final String deliveryAuthority;
        final String deliveryPolicy;

        State state=State.SCHEDULED;
        boolean executing;
        WorldEventQueue.Handle handle;
        NpcLifecycleService.DamageResult delivered;
        String failureType;

        Entry(
            HitId hitId,
            WorldPlayer attacker,
            long attackerGeneration,
            WorldNpc target,
            int requestedDamage,
            long scheduledFromTick,
            long dueTick,
            String damageAuthority,
            String damageFormula,
            String deliveryAuthority,
            String deliveryPolicy
        ){
            this.hitId=hitId;
            this.attacker=attacker;
            this.attackerGeneration=
                attackerGeneration;
            this.target=target;
            this.requestedDamage=
                requestedDamage;
            this.scheduledFromTick=
                scheduledFromTick;
            this.dueTick=dueTick;
            this.damageAuthority=
                damageAuthority;
            this.damageFormula=
                damageFormula;
            this.deliveryAuthority=
                deliveryAuthority;
            this.deliveryPolicy=
                deliveryPolicy;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final World world;
    private final NpcLifecycleService lifecycle;
    private final String deliveryAuthority;
    private final String deliveryPolicy;
    private final LinkedHashMap<HitId,Entry> entries=
        new LinkedHashMap<>();
    private long nextHitId;

    NpcPvmDelayedHitService(
        World world,
        NpcLifecycleService lifecycle,
        String deliveryAuthority,
        String deliveryPolicy
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.lifecycle=
            Objects.requireNonNull(
                lifecycle,
                "lifecycle"
            );
        this.deliveryAuthority=
            requireGameplayAuthority(
                deliveryAuthority
            );
        this.deliveryPolicy=
            requireText(
                deliveryPolicy,
                "deliveryPolicy"
            );

        if(!REQUIRE_CURRENT_ATTACKER_GENERATION
                .equals(
                    this.deliveryPolicy
                ))
            throw new IllegalArgumentException(
                "unsupported delayed-hit delivery policy "+
                this.deliveryPolicy
            );
    }

    Snapshot schedule(
        WorldPlayer attacker,
        long attackerGeneration,
        WorldNpc target,
        int resolvedDamage,
        int delayTicks,
        String damageAuthority,
        String damageFormula
    )throws Exception{
        return scheduleInternal(
            attacker,
            attackerGeneration,
            target,
            resolvedDamage,
            delayTicks,
            damageAuthority,
            damageFormula,
            null
        );
    }

    Snapshot scheduleAtExpectedTick(
        WorldPlayer attacker,
        long attackerGeneration,
        WorldNpc target,
        int resolvedDamage,
        int delayTicks,
        String damageAuthority,
        String damageFormula,
        long expectedScheduledFromTick
    )throws Exception{
        if(expectedScheduledFromTick<0L)
            throw new IllegalArgumentException(
                "expectedScheduledFromTick="+
                expectedScheduledFromTick
            );

        return scheduleInternal(
            attacker,
            attackerGeneration,
            target,
            resolvedDamage,
            delayTicks,
            damageAuthority,
            damageFormula,
            Long.valueOf(
                expectedScheduledFromTick
            )
        );
    }

    private Snapshot scheduleInternal(
        WorldPlayer attacker,
        long attackerGeneration,
        WorldNpc target,
        int resolvedDamage,
        int delayTicks,
        String damageAuthority,
        String damageFormula,
        Long expectedScheduledFromTick
    )throws Exception{
        WorldPlayer checkedAttacker=
            Objects.requireNonNull(
                attacker,
                "attacker"
            );
        WorldNpc checkedTarget=
            Objects.requireNonNull(
                target,
                "target"
            );

        if(resolvedDamage<0)
            throw new IllegalArgumentException(
                "resolvedDamage="+
                resolvedDamage
            );
        if(delayTicks<=0)
            throw new IllegalArgumentException(
                "delayTicks="+
                delayTicks
            );

        String checkedDamageAuthority=
            requireText(
                damageAuthority,
                "damageAuthority"
            );
        String checkedDamageFormula=
            requireText(
                damageFormula,
                "damageFormula"
            );

        final Snapshot[] result=
            new Snapshot[1];

        boolean attackerCurrent=
            world.withOpenPlayerMutationOwnershipIfCurrent(
                checkedAttacker,
                attackerGeneration,
                ()->{
                    boolean targetCurrent=
                        world.npcs()
                            .withCurrentMutationOwnershipIfCurrent(
                                checkedTarget,
                                ()->{
                                    NpcLifecycleService.Snapshot
                                        lifecycleSnapshot=
                                            lifecycle.get(
                                                checkedTarget.id
                                            );

                                    if(lifecycleSnapshot==null)
                                        throw new IllegalStateException(
                                            "NPC lifecycle missing id="+
                                            checkedTarget.id
                                        );

                                    world.withClockEventPublicationOwnership(
                                        scheduledFromTick->{
                                            if(expectedScheduledFromTick!=null&&
                                               scheduledFromTick!=
                                                    expectedScheduledFromTick
                                                        .longValue())
                                                throw new IllegalStateException(
                                                    "delayed PvM schedule tick drift expected="+
                                                    expectedScheduledFromTick+
                                                    " actual="+
                                                    scheduledFromTick
                                                );

                                            final long dueTick;

                                            try{
                                                dueTick=
                                                    Math.addExact(
                                                        scheduledFromTick,
                                                        (long)delayTicks
                                                    );
                                            }catch(
                                                ArithmeticException overflow
                                            ){
                                                throw new IllegalStateException(
                                                    "delayed PvM hit tick overflow tick="+
                                                    scheduledFromTick+
                                                    " delay="+
                                                    delayTicks,
                                                    overflow
                                                );
                                            }

                                            Entry entry;

                                            synchronized(this){
                                                HitId id=
                                                    new HitId(
                                                        nextId()
                                                    );

                                                entry=
                                                    new Entry(
                                                        id,
                                                        checkedAttacker,
                                                        attackerGeneration,
                                                        checkedTarget,
                                                        resolvedDamage,
                                                        scheduledFromTick,
                                                        dueTick,
                                                        checkedDamageAuthority,
                                                        checkedDamageFormula,
                                                        deliveryAuthority,
                                                        deliveryPolicy
                                                    );

                                                entries.put(
                                                    id,
                                                    entry
                                                );
                                            }

                                            WorldEventQueue.Handle handle;

                                            try{
                                                handle=
                                                    world.events()
                                                        .schedule(
                                                            dueTick,
                                                            ()->
                                                                deliverFromWorldEvent(
                                                                    entry.hitId
                                                                )
                                                        );
                                            }catch(Throwable failure){
                                                synchronized(this){
                                                    entries.remove(
                                                        entry.hitId,
                                                        entry
                                                    );
                                                }
                                                rethrowUnchecked(
                                                    failure
                                                );
                                                return;
                                            }

                                            boolean cancelHandle=false;

                                            synchronized(this){
                                                Entry current=
                                                    entries.get(
                                                        entry.hitId
                                                    );

                                                if(current==entry&&
                                                   entry.state==
                                                        State.SCHEDULED)
                                                    entry.handle=handle;
                                                else
                                                    cancelHandle=true;

                                                result[0]=
                                                    entry.snapshot();
                                            }

                                            if(cancelHandle)
                                                handle.cancel();
                                        }
                                    );
                                }
                            );

                    if(!targetCurrent)
                        throw new IllegalStateException(
                            "NPC target is not exact canonical registry owner id="+
                            checkedTarget.id
                        );
                }
            );

        if(!attackerCurrent)
            throw new IllegalStateException(
                "player attacker is not exact current world generation id="+
                checkedAttacker.id()+
                " expectedGeneration="+
                attackerGeneration
            );

        return Objects.requireNonNull(
            result[0],
            "scheduled hit"
        );
    }

    synchronized Snapshot get(HitId hitId){
        Entry entry=
            entries.get(
                Objects.requireNonNull(
                    hitId,
                    "hitId"
                )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    boolean cancel(HitId hitId){
        Entry entry;
        WorldEventQueue.Handle handle;

        synchronized(this){
            entry=
                entries.get(
                    Objects.requireNonNull(
                        hitId,
                        "hitId"
                    )
                );

            if(entry==null||
               entry.state!=State.SCHEDULED)
                return false;

            entry.state=State.CANCELLED;
            entry.executing=false;
            handle=entry.handle;
        }

        if(handle!=null)
            handle.cancel();

        return true;
    }

    synchronized boolean retireTerminal(
        HitId hitId
    ){
        HitId checked=
            Objects.requireNonNull(
                hitId,
                "hitId"
            );

        Entry entry=
            entries.get(
                checked
            );

        if(entry==null||
           entry.state==State.SCHEDULED)
            return false;

        return entries.remove(
            checked,
            entry
        );
    }

    synchronized int size(){
        return entries.size();
    }

    boolean isBoundTo(
        World expectedWorld,
        NpcLifecycleService expectedLifecycle
    ){
        return world==expectedWorld&&
            lifecycle==expectedLifecycle;
    }

    String deliveryAuthority(){
        return deliveryAuthority;
    }

    String deliveryPolicy(){
        return deliveryPolicy;
    }

    private void deliverFromWorldEvent(
        HitId hitId
    ){
        final Entry entry;

        synchronized(this){
            entry=
                entries.get(
                    hitId
                );

            if(entry==null||
               entry.state!=State.SCHEDULED||
               entry.executing)
                return;

            entry.executing=true;
        }

        try{
            final boolean[] targetLost={false};
            final boolean[] cancelled={false};

            boolean attackerCurrent=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    entry.attacker,
                    entry.attackerGeneration,
                    ()->{
                        boolean targetCurrent=
                            world.npcs()
                                .withCurrentMutationOwnershipIfCurrent(
                                    entry.target,
                                    ()->{
                                        synchronized(this){
                                            if(entries.get(
                                                    entry.hitId
                                                )!=entry||
                                               entry.state!=
                                                    State.SCHEDULED){
                                                cancelled[0]=true;
                                                return;
                                            }

                                            NpcLifecycleService.DamageResult
                                                delivered=
                                                    lifecycle.applyDamage(
                                                        entry.target.id,
                                                        entry.requestedDamage,
                                                        entry.dueTick
                                                    );

                                            entry.delivered=
                                                delivered;
                                            entry.state=
                                                State.DELIVERED;
                                            entry.executing=
                                                false;
                                        }
                                    }
                                );

                        if(!targetCurrent)
                            targetLost[0]=true;
                    }
                );

            if(cancelled[0])
                return;

            if(!attackerCurrent){
                terminalWithoutDamage(
                    entry,
                    State.STALE_ATTACKER
                );
                return;
            }

            if(targetLost[0]){
                terminalWithoutDamage(
                    entry,
                    State.STALE_TARGET
                );
                return;
            }

            synchronized(this){
                if(entries.get(
                        entry.hitId
                    )==entry&&
                   entry.state==State.SCHEDULED&&
                   entry.executing){
                    entry.state=State.FAILED;
                    entry.failureType=
                        IllegalStateException.class
                            .getName();
                    entry.executing=false;
                }
            }
        }catch(Throwable failure){
            synchronized(this){
                if(entries.get(
                        entry.hitId
                    )==entry&&
                   entry.state==State.SCHEDULED){
                    entry.state=State.FAILED;
                    entry.failureType=
                        failure.getClass()
                            .getName();
                }

                entry.executing=false;
            }

            rethrowUnchecked(
                failure
            );
        }
    }

    private void terminalWithoutDamage(
        Entry entry,
        State state
    ){
        synchronized(this){
            if(entries.get(
                    entry.hitId
                )!=entry||
               entry.state!=State.SCHEDULED)
                return;

            entry.state=state;
            entry.executing=false;
        }
    }

    private synchronized long nextId(){
        try{
            nextHitId=
                Math.addExact(
                    nextHitId,
                    1L
                );
        }catch(ArithmeticException overflow){
            throw new IllegalStateException(
                "delayed PvM hit id overflow",
                overflow
            );
        }

        if(nextHitId<=0L)
            throw new IllegalStateException(
                "delayed PvM hit id invalid "+
                nextHitId
            );

        return nextHitId;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=
            requireText(
                value,
                "deliveryAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define delayed-hit delivery actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        if(value==null)
            throw new NullPointerException(
                name
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                name
            );

        return clean;
    }

    private static void rethrowUnchecked(
        Throwable failure
    ){
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;

        throw new IllegalStateException(
            failure
        );
    }
}
