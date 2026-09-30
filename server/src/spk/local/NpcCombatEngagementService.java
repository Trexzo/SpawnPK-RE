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
        }

        if(engagement==null)
            return new TickResult(
                TickStatus.NONE,
                null
            );

        if(worldTick<engagement.nextAttackTick)
            return new TickResult(
                TickStatus.WAITING,
                snapshot(engagement)
            );

        WorldNpc attacker=
            world.npcs().byId(
                engagement.attackerId
            );

        if(attacker==null){
            synchronized(engagements){
                engagements.remove(
                    engagement.attackerId,
                    engagement
                );
            }
            return new TickResult(
                TickStatus.STALE_ATTACKER,
                null
            );
        }

        WorldPlayer target=
            world.players().byId(
                engagement.targetId
            );

        if(target==null||
           !world.players().owns(
                target,
                engagement.targetGeneration
            )){
            synchronized(engagements){
                engagements.remove(
                    engagement.attackerId,
                    engagement
                );
            }
            return new TickResult(
                TickStatus.STALE_TARGET,
                null
            );
        }

        Context context=
            new Context(
                attacker,
                target,
                engagement.targetGeneration,
                worldTick,
                engagement.nextAttackTick,
                engagement.revision
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

        final long nextAttackTick;

        try{
            nextAttackTick=
                Math.addExact(
                    worldTick,
                    (long)delay
                );
        }catch(ArithmeticException overflow){
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
                    engagement.revision,
                    1L
                );
        }catch(ArithmeticException overflow){
            throw new IllegalStateException(
                "NPC engagement revision overflow revision="+
                engagement.revision,
                overflow
            );
        }

        attackExecutor.execute(
            attacker,
            target,
            engagement.targetGeneration,
            worldTick
        );

        /*
         * Executor code is caller-owned and may reenter this service.
         * Never publish a schedule advance onto an engagement that it
         * removed/replaced while executing.
         */
        synchronized(engagements){
            if(engagements.get(
                    engagement.attackerId
                )!=engagement)
                throw new IllegalStateException(
                    "NPC engagement ownership changed during attack id="+
                    engagement.attackerId
                );

            engagement.nextAttackTick=
                nextAttackTick;
            engagement.revision=
                nextRevision;

            return new TickResult(
                TickStatus.ATTACKED,
                snapshot(engagement)
            );
        }
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
