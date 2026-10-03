package spk.local;

import java.util.*;

/**
 * Explicit canonical NPC combat engagement/cadence state.
 *
 * This service does not choose targets, path/chase, enforce attack range,
 * calculate damage, publish presentation, or decide combat outcomes.
 */
final class NpcCombatEngagementService {
    enum TickStatus {
        NONE,
        WAITING,
        ATTACKED,
        STALE_ATTACKER,
        STALE_TARGET
    }

    enum ValidationStatus {
        CURRENT,
        GONE,
        STALE_ATTACKER,
        STALE_TARGET
    }

    interface CadenceResolver {
        int nextDelayTicks(Context context);
        String authority();
        String policy();
    }

    interface AttackExecutor {
        void execute(
            WorldNpc attacker,
            WorldPlayer target,
            long targetGeneration,
            long worldTick
        ) throws Exception;
    }

    static final class Context {
        final EntityId attackerId;
        final int attackerDefinitionId;
        final Tile attackerTile;
        final EntityId targetId;
        final long targetGeneration;
        final long worldTick;
        final long scheduledTick;
        final long revision;

        private Context(
            WorldNpc attacker,
            WorldPlayer target,
            long targetGeneration,
            long worldTick,
            long scheduledTick,
            long revision
        ){
            this.attackerId=attacker.id;
            this.attackerDefinitionId=attacker.definitionId;
            this.attackerTile=attacker.tile();
            this.targetId=target.id();
            this.targetGeneration=targetGeneration;
            this.worldTick=worldTick;
            this.scheduledTick=scheduledTick;
            this.revision=revision;
        }
    }

    static final class Snapshot {
        final EntityId attackerId;
        final EntityId targetId;
        final long targetGeneration;
        final long nextAttackTick;
        final long revision;
        final String cadenceAuthority;
        final String cadencePolicy;
        private final Engagement identity;

        private Snapshot(
            Engagement engagement,
            String cadenceAuthority,
            String cadencePolicy
        ){
            this.attackerId=engagement.attackerId;
            this.targetId=engagement.targetId;
            this.targetGeneration=engagement.targetGeneration;
            this.nextAttackTick=engagement.nextAttackTick;
            this.revision=engagement.revision;
            this.cadenceAuthority=cadenceAuthority;
            this.cadencePolicy=cadencePolicy;
            this.identity=engagement;
        }
    }

    static final class TickResult {
        final TickStatus status;
        final Snapshot snapshot;

        private TickResult(
            TickStatus status,
            Snapshot snapshot
        ){
            this.status=Objects.requireNonNull(status,"status");
            this.snapshot=snapshot;
        }
    }

    private static final class Engagement {
        final EntityId attackerId;
        final EntityId targetId;
        final long targetGeneration;
        long nextAttackTick;
        long revision;
        boolean executing;

        Engagement(
            EntityId attackerId,
            EntityId targetId,
            long targetGeneration,
            long nextAttackTick
        ){
            this.attackerId=attackerId;
            this.targetId=targetId;
            this.targetGeneration=targetGeneration;
            this.nextAttackTick=nextAttackTick;
            this.revision=0L;
        }
    }

    private final World world;
    private final CadenceResolver cadenceResolver;
    private final AttackExecutor attackExecutor;
    private final String cadenceAuthority;
    private final String cadencePolicy;
    private final LinkedHashMap<EntityId,Engagement> engagements=
        new LinkedHashMap<>();

    NpcCombatEngagementService(
        World world,
        CadenceResolver cadenceResolver,
        AttackExecutor attackExecutor
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.cadenceResolver=Objects.requireNonNull(
            cadenceResolver,
            "cadenceResolver"
        );
        this.attackExecutor=Objects.requireNonNull(
            attackExecutor,
            "attackExecutor"
        );
        this.cadenceAuthority=requireGameplayAuthority(
            cadenceResolver.authority()
        );
        this.cadencePolicy=requireText(
            cadenceResolver.policy(),
            "cadencePolicy"
        );
    }

    Snapshot begin(
        WorldNpc attacker,
        WorldPlayer target,
        long targetGeneration,
        long firstAttackTick
    ){
        WorldNpc checkedAttacker=
            Objects.requireNonNull(attacker,"attacker");
        WorldPlayer checkedTarget=
            Objects.requireNonNull(target,"target");

        if(firstAttackTick<0L)
            throw new IllegalArgumentException(
                "firstAttackTick="+firstAttackTick
            );

        final Snapshot[] result=
            new Snapshot[1];

        try{
            boolean playerOwned=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    checkedTarget,
                    targetGeneration,
                    ()->{
                        boolean npcOwned=
                            world.npcs()
                                .withCurrentMutationOwnershipIfCurrent(
                                    checkedAttacker,
                                    ()->{
                                        synchronized(engagements){
                                            if(engagements.containsKey(
                                                    checkedAttacker.id))
                                                throw new IllegalStateException(
                                                    "NPC already engaged id="+
                                                    checkedAttacker.id
                                                );

                                            Engagement engagement=
                                                new Engagement(
                                                    checkedAttacker.id,
                                                    checkedTarget.id(),
                                                    targetGeneration,
                                                    firstAttackTick
                                                );

                                            engagements.put(
                                                checkedAttacker.id,
                                                engagement
                                            );

                                            result[0]=
                                                snapshot(engagement);
                                        }
                                    }
                                );

                        if(!npcOwned)
                            throw new IllegalStateException(
                                "NPC attacker is not exact canonical registry owner id="+
                                checkedAttacker.id
                            );
                    }
                );

            if(!playerOwned)
                throw new IllegalStateException(
                    "player target is not exact current world generation id="+
                    checkedTarget.id()+
                    " expectedGeneration="+
                    targetGeneration
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected engagement ownership failure",
                failure
            );
        }

        return result[0];
    }

    TickResult tick(
        EntityId attackerId,
        long worldTick
    )throws Exception{
        EntityId checkedId=
            Objects.requireNonNull(
                attackerId,
                "attackerId"
            );

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        final Engagement engagement;

        synchronized(engagements){
            engagement=
                engagements.get(
                    checkedId
                );

            if(engagement==null)
                return new TickResult(
                    TickStatus.NONE,
                    null
                );

            if(worldTick<engagement.nextAttackTick||
               engagement.executing)
                return new TickResult(
                    TickStatus.WAITING,
                    snapshot(engagement)
                );

            /*
             * Reserve this exact due revision before releasing service state.
             * Concurrent/reentrant ticks must not execute the same revision.
             */
            engagement.executing=true;
        }

        try{
            WorldNpc attacker=
                world.npcs().byId(
                    engagement.attackerId
                );

            if(attacker==null)
                return staleResult(
                    engagement,
                    TickStatus.STALE_ATTACKER
                );

            WorldPlayer target=
                world.players().byId(
                    engagement.targetId
                );

            if(target==null)
                return staleResult(
                    engagement,
                    TickStatus.STALE_TARGET
                );

            final TickResult[] result=
                new TickResult[1];
            final boolean[] npcLost={false};
            final boolean[] engagementGone={false};

            boolean playerOwned=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    target,
                    engagement.targetGeneration,
                    ()->{
                        boolean npcOwned=
                            world.npcs()
                                .withCurrentMutationOwnershipIfCurrent(
                                    attacker,
                                    ()->{
                                        final long scheduledTick;
                                        final long revision;

                                        synchronized(engagements){
                                            if(engagements.get(
                                                    engagement.attackerId
                                                )!=engagement||
                                               !engagement.executing){
                                                engagementGone[0]=true;
                                                return;
                                            }

                                            if(worldTick<
                                               engagement.nextAttackTick){
                                                engagement.executing=false;
                                                result[0]=
                                                    new TickResult(
                                                        TickStatus.WAITING,
                                                        snapshot(engagement)
                                                    );
                                                return;
                                            }

                                            scheduledTick=
                                                engagement.nextAttackTick;
                                            revision=
                                                engagement.revision;
                                        }

                                        Context context=
                                            new Context(
                                                attacker,
                                                target,
                                                engagement.targetGeneration,
                                                worldTick,
                                                scheduledTick,
                                                revision
                                            );

                                        int delay=
                                            cadenceResolver.nextDelayTicks(
                                                context
                                            );

                                        if(delay<=0)
                                            throw new IllegalStateException(
                                                "NPC cadence resolver returned non-positive delay="+
                                                delay+
                                                " authority="+
                                                cadenceAuthority
                                            );

                                        /*
                                         * Cadence is caller-owned and may
                                         * reenter/cancel this engagement.
                                         * Revalidate the exact reservation
                                         * again before any attack side effect.
                                         */
                                        synchronized(engagements){
                                            if(engagements.get(
                                                    engagement.attackerId
                                                )!=engagement||
                                               !engagement.executing){
                                                engagementGone[0]=true;
                                                return;
                                            }
                                        }

                                        final long nextAttackTick;

                                        try{
                                            nextAttackTick=
                                                Math.addExact(
                                                    worldTick,
                                                    (long)delay
                                                );
                                        }catch(
                                            ArithmeticException overflow
                                        ){
                                            throw new IllegalStateException(
                                                "NPC next attack tick overflow worldTick="+
                                                worldTick+
                                                " delay="+
                                                delay,
                                                overflow
                                            );
                                        }

                                        final long nextRevision;

                                        try{
                                            nextRevision=
                                                Math.addExact(
                                                    revision,
                                                    1L
                                                );
                                        }catch(
                                            ArithmeticException overflow
                                        ){
                                            throw new IllegalStateException(
                                                "NPC engagement revision overflow revision="+
                                                revision,
                                                overflow
                                            );
                                        }

                                        /*
                                         * Exact target generation and exact
                                         * canonical NPC ownership remain held
                                         * across caller side effects.
                                         */
                                        attackExecutor.execute(
                                            attacker,
                                            target,
                                            engagement.targetGeneration,
                                            worldTick
                                        );

                                        /*
                                         * Executor code may reenter this
                                         * service. Publish only if this exact
                                         * reserved engagement still owns the
                                         * map entry.
                                         */
                                        synchronized(engagements){
                                            if(engagements.get(
                                                    engagement.attackerId
                                                )!=engagement||
                                               !engagement.executing){
                                                /*
                                                 * The executor returned
                                                 * successfully, so its attack
                                                 * side effect already happened.
                                                 * Reentrant caller code may
                                                 * deliberately cancel or replace
                                                 * the engagement while the exact
                                                 * player/NPC ownership fences are
                                                 * still held. Do not publish the
                                                 * old schedule onto a replacement,
                                                 * and do not report a false
                                                 * failure for the completed
                                                 * attack.
                                                 */
                                                result[0]=
                                                    new TickResult(
                                                        TickStatus.ATTACKED,
                                                        null
                                                    );
                                                return;
                                            }

                                            engagement.nextAttackTick=
                                                nextAttackTick;
                                            engagement.revision=
                                                nextRevision;
                                            engagement.executing=false;

                                            result[0]=
                                                new TickResult(
                                                    TickStatus.ATTACKED,
                                                    snapshot(engagement)
                                                );
                                        }
                                    }
                                );

                        if(!npcOwned)
                            npcLost[0]=true;
                    }
                );

            if(!playerOwned)
                return staleResult(
                    engagement,
                    TickStatus.STALE_TARGET
                );

            if(npcLost[0])
                return staleResult(
                    engagement,
                    TickStatus.STALE_ATTACKER
                );

            if(engagementGone[0])
                return new TickResult(
                    TickStatus.NONE,
                    null
                );

            if(result[0]==null)
                throw new IllegalStateException(
                    "NPC engagement tick completed without a result id="+
                    engagement.attackerId
                );

            return result[0];
        }finally{
            /*
             * Cadence/executor/ownership failure must release the reservation
             * without advancing schedule or revision. A removed/replaced
             * engagement is deliberately left untouched.
             */
            synchronized(engagements){
                if(engagements.get(
                        engagement.attackerId
                    )==engagement&&
                   engagement.executing)
                    engagement.executing=false;
            }
        }
    }

    private TickResult staleResult(
        Engagement engagement,
        TickStatus status
    ){
        synchronized(engagements){
            engagements.remove(
                engagement.attackerId,
                engagement
            );
        }

        return new TickResult(
            status,
            null
        );
    }

    boolean cancel(
        WorldNpc attacker
    ){
        WorldNpc checked=
            Objects.requireNonNull(
                attacker,
                "attacker"
            );

        synchronized(engagements){
            if(!engagements.containsKey(
                    checked.id))
                return false;
        }

        final boolean[] removed={false};

        try{
            boolean npcOwned=
                world.npcs()
                    .withCurrentMutationOwnershipIfCurrent(
                        checked,
                        ()->{
                            synchronized(engagements){
                                removed[0]=
                                    engagements.remove(
                                        checked.id
                                    )!=null;
                            }
                        }
                    );

            if(!npcOwned)
                throw new IllegalStateException(
                    "NPC attacker is not exact canonical registry owner id="+
                    checked.id
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected engagement cancellation ownership failure",
                failure
            );
        }

        return removed[0];
    }

    Snapshot get(
        EntityId attackerId
    ){
        synchronized(engagements){
            Engagement engagement=
                engagements.get(
                    Objects.requireNonNull(
                        attackerId,
                        "attackerId"
                    )
                );

            return engagement==null
                ?null
                :snapshot(engagement);
        }
    }

    boolean isCurrent(
        Snapshot snapshot
    ){
        Snapshot checked=
            Objects.requireNonNull(
                snapshot,
                "snapshot"
            );

        synchronized(engagements){
            return engagements.get(
                checked.attackerId
            )==checked.identity;
        }
    }

    ValidationStatus validateAndRetireStale(
        Snapshot snapshot
    ){
        Snapshot checked=
            Objects.requireNonNull(
                snapshot,
                "snapshot"
            );
        Engagement expected=
            checked.identity;

        synchronized(engagements){
            if(engagements.get(
                    checked.attackerId
                )!=expected)
                return ValidationStatus.GONE;
        }

        WorldNpc attacker=
            world.npcs().byId(
                checked.attackerId
            );

        if(attacker==null)
            return retireValidatedStale(
                checked,
                expected,
                ValidationStatus.STALE_ATTACKER
            );

        WorldPlayer target=
            world.players().byId(
                checked.targetId
            );

        if(target==null||
           !world.players().owns(
                target,
                checked.targetGeneration
            ))
            return retireValidatedStale(
                checked,
                expected,
                ValidationStatus.STALE_TARGET
            );

        synchronized(engagements){
            return engagements.get(
                checked.attackerId
            )==expected
                ?ValidationStatus.CURRENT
                :ValidationStatus.GONE;
        }
    }

    private ValidationStatus retireValidatedStale(
        Snapshot snapshot,
        Engagement expected,
        ValidationStatus stale
    ){
        synchronized(engagements){
            if(engagements.get(
                    snapshot.attackerId
                )!=expected)
                return ValidationStatus.GONE;

            engagements.remove(
                snapshot.attackerId,
                expected
            );

            return stale;
        }
    }

    int size(){
        synchronized(engagements){
            return engagements.size();
        }
    }

    String cadenceAuthority(){
        return cadenceAuthority;
    }

    String cadencePolicy(){
        return cadencePolicy;
    }

    private Snapshot snapshot(
        Engagement engagement
    ){
        return new Snapshot(
            engagement,
            cadenceAuthority,
            cadencePolicy
        );
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=requireText(
            value,
            "cadenceAuthority"
        );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC cadence actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}
