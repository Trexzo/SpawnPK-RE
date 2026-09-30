package spk.local;

import java.util.*;

/**
 * Binds caller-defined semantic combat behavior to an already-canonical NPC.
 *
 * This class owns composition only. Target eligibility, approach range/routing,
 * cadence, damage and initial-attack timing remain supplied by the caller.
 */
final class NpcCombatRuntimeBinder {
    enum BindStatus {
        BOUND,
        UNCONFIGURED,
        STALE_ATTACKER
    }

    interface PlanResolver {
        BehaviorPlan resolve(Context context) throws Exception;
    }

    static final class Context {
        final EntityId npcId;
        final int definitionId;
        final Tile tile;

        private Context(
            WorldNpc npc,
            Tile tile
        ){
            this.npcId=npc.id;
            this.definitionId=npc.definitionId;
            this.tile=tile;
        }
    }

    static final class BehaviorPlan {
        final String planKey;
        final String sourceAuthority;
        final NpcTargetAcquisitionService.AcquisitionPolicy
            acquisitionPolicy;
        final String targetAuthority;
        final String targetPolicy;
        final NpcCombatApproachService.ApproachPolicy
            approachPolicy;
        final NpcCombatEngagementService.CadenceResolver
            cadenceResolver;
        final NpcPlayerCombatResolutionService.DamageResolver
            damageResolver;
        final NpcCombatAiService.InitialAttackTickResolver
            initialAttackTickResolver;
        final String controllerAuthority;
        final String aiAuthority;

        BehaviorPlan(
            String planKey,
            String sourceAuthority,
            NpcTargetAcquisitionService.AcquisitionPolicy
                acquisitionPolicy,
            String targetAuthority,
            String targetPolicy,
            NpcCombatApproachService.ApproachPolicy
                approachPolicy,
            NpcCombatEngagementService.CadenceResolver
                cadenceResolver,
            NpcPlayerCombatResolutionService.DamageResolver
                damageResolver,
            NpcCombatAiService.InitialAttackTickResolver
                initialAttackTickResolver,
            String controllerAuthority,
            String aiAuthority
        ){
            this.planKey=
                requireText(
                    planKey,
                    "planKey"
                );
            this.sourceAuthority=
                requireGameplayAuthority(
                    sourceAuthority,
                    "sourceAuthority"
                );
            this.acquisitionPolicy=
                Objects.requireNonNull(
                    acquisitionPolicy,
                    "acquisitionPolicy"
                );
            this.targetAuthority=
                requireGameplayAuthority(
                    targetAuthority,
                    "targetAuthority"
                );
            this.targetPolicy=
                requireText(
                    targetPolicy,
                    "targetPolicy"
                );
            this.approachPolicy=
                Objects.requireNonNull(
                    approachPolicy,
                    "approachPolicy"
                );
            this.cadenceResolver=
                Objects.requireNonNull(
                    cadenceResolver,
                    "cadenceResolver"
                );
            this.damageResolver=
                Objects.requireNonNull(
                    damageResolver,
                    "damageResolver"
                );
            this.initialAttackTickResolver=
                Objects.requireNonNull(
                    initialAttackTickResolver,
                    "initialAttackTickResolver"
                );
            this.controllerAuthority=
                requireGameplayAuthority(
                    controllerAuthority,
                    "controllerAuthority"
                );
            this.aiAuthority=
                requireGameplayAuthority(
                    aiAuthority,
                    "aiAuthority"
                );
        }
    }

    static final class BindingSnapshot {
        final EntityId npcId;
        final int definitionId;
        final String planKey;
        final String sourceAuthority;
        final boolean bound;

        private BindingSnapshot(Entry entry){
            this.npcId=entry.attacker.id;
            this.definitionId=
                entry.attacker.definitionId;
            this.planKey=
                entry.plan.planKey;
            this.sourceAuthority=
                entry.plan.sourceAuthority;
            this.bound=true;
        }
    }

    static final class BindResult {
        final BindStatus status;
        final BindingSnapshot binding;

        private BindResult(
            BindStatus status,
            BindingSnapshot binding
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.binding=binding;
        }
    }

    private static final class Entry {
        final WorldNpc attacker;
        final BehaviorPlan plan;
        final NpcCombatAiTickTarget tickTarget;

        Entry(
            WorldNpc attacker,
            BehaviorPlan plan,
            NpcCombatAiTickTarget tickTarget
        ){
            this.attacker=attacker;
            this.plan=plan;
            this.tickTarget=tickTarget;
        }

        BindingSnapshot snapshot(){
            return new BindingSnapshot(this);
        }
    }

    private final World world;
    private final PlanResolver planResolver;
    private final LinkedHashMap<EntityId,Entry>
        bindings=
            new LinkedHashMap<>();

    NpcCombatRuntimeBinder(
        World world,
        PlanResolver planResolver
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.planResolver=
            Objects.requireNonNull(
                planResolver,
                "planResolver"
            );
    }

    BindResult bind(
        WorldNpc attacker
    )throws Exception{
        WorldNpc checked=
            Objects.requireNonNull(
                attacker,
                "attacker"
            );

        synchronized(this){
            if(bindings.containsKey(
                    checked.id))
                throw new IllegalStateException(
                    "NPC combat runtime already bound id="+
                    checked.id
                );
        }

        final Tile[] initialTile={null};

        boolean current=
            world.npcs()
                .withCurrentMutationOwnershipIfCurrent(
                    checked,
                    ()->
                        initialTile[0]=
                            checked.tile()
                );

        if(!current)
            return new BindResult(
                BindStatus.STALE_ATTACKER,
                null
            );

        BehaviorPlan plan=
            planResolver.resolve(
                new Context(
                    checked,
                    initialTile[0]
                )
            );

        if(plan==null)
            return new BindResult(
                BindStatus.UNCONFIGURED,
                null
            );

        if(world.npcs().byId(
                checked.id
            )!=checked)
            return new BindResult(
                BindStatus.STALE_ATTACKER,
                null
            );

        NpcPlayerCombatResolutionService damage=
            new NpcPlayerCombatResolutionService(
                plan.damageResolver
            );

        NpcCombatControllerService controller=
            new NpcCombatControllerService(
                world,
                plan.approachPolicy,
                plan.cadenceResolver,
                damage,
                plan.controllerAuthority,
                NpcCombatControllerService
                    .APPROACH_BEFORE_ENGAGEMENT_TICK
            );

        NpcTargetAcquisitionService acquisition=
            new NpcTargetAcquisitionService(
                world,
                plan.acquisitionPolicy,
                plan.targetAuthority,
                plan.targetPolicy
            );

        NpcCombatAiService ai=
            new NpcCombatAiService(
                world,
                acquisition,
                controller,
                plan.initialAttackTickResolver,
                plan.aiAuthority,
                NpcCombatAiService
                    .ACQUIRE_THEN_DELEGATE
            );

        NpcCombatAiTickTarget tickTarget=
            new NpcCombatAiTickTarget(
                checked,
                ai
            );

        Entry entry=
            new Entry(
                checked,
                plan,
                tickTarget
            );

        synchronized(this){
            if(bindings.containsKey(
                    checked.id))
                throw new IllegalStateException(
                    "NPC combat runtime concurrently bound id="+
                    checked.id
                );

            try{
                world.attachNpcTickTarget(
                    checked,
                    tickTarget
                );
            }catch(IllegalStateException failure){
                if(world.npcs().byId(
                        checked.id
                    )!=checked)
                    return new BindResult(
                        BindStatus.STALE_ATTACKER,
                        null
                    );

                throw failure;
            }

            bindings.put(
                checked.id,
                entry
            );

            return new BindResult(
                BindStatus.BOUND,
                entry.snapshot()
            );
        }
    }

    synchronized BindingSnapshot get(
        EntityId npcId
    ){
        Entry entry=
            bindings.get(
                Objects.requireNonNull(
                    npcId,
                    "npcId"
                )
            );

        return entry==null
            ?null
            :entry.snapshot();
    }

    synchronized boolean unbind(
        EntityId npcId
    ){
        EntityId checked=
            Objects.requireNonNull(
                npcId,
                "npcId"
            );
        Entry entry=
            bindings.get(
                checked
            );

        if(entry==null)
            return false;

        if(!world.detachNpcTickTarget(
                checked,
                entry.tickTarget))
            throw new IllegalStateException(
                "NPC combat runtime lost exact pulse target id="+
                checked
            );

        bindings.remove(
            checked,
            entry
        );

        return true;
    }

    synchronized int size(){
        return bindings.size();
    }

    private static String requireGameplayAuthority(
        String value,
        String name
    ){
        String clean=
            requireText(
                value,
                name
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC combat runtime "+
                name+
                "="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        if(value==null)
            throw new NullPointerException(name);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(name);

        return clean;
    }
}
