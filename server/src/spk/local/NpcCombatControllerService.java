package spk.local;

import java.util.Objects;

/**
 * Protocol-independent controller for an explicitly-created NPC -> player
 * engagement. Target selection and aggro stay outside this service.
 *
 * One controller tick approaches the already-selected target by at most one
 * collision-safe step. Only an in-range engagement is allowed to enter the
 * existing cadence service, whose attack executor applies canonical NPC ->
 * player damage.
 */
final class NpcCombatControllerService {
    static final String APPROACH_BEFORE_ENGAGEMENT_TICK=
        "APPROACH_BEFORE_ENGAGEMENT_TICK";

    enum Status {
        NONE,
        APPROACHED,
        BLOCKED,
        DIFFERENT_PLANE,
        WAITING,
        ATTACKED,
        STALE_ATTACKER,
        STALE_TARGET,
        TARGET_DEAD
    }

    static final class TickResult {
        final Status status;
        final NpcCombatEngagementService.Snapshot engagement;
        final NpcCombatApproachService.Result approach;
        final NpcCombatEngagementService.TickResult cadence;

        private TickResult(
            Status status,
            NpcCombatEngagementService.Snapshot engagement,
            NpcCombatApproachService.Result approach,
            NpcCombatEngagementService.TickResult cadence
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.engagement=engagement;
            this.approach=approach;
            this.cadence=cadence;
        }
    }

    private static final class EngagementChangedException
        extends RuntimeException {
        private static final long serialVersionUID=1L;
    }

    private static final class TargetAlreadyDeadException
        extends RuntimeException {
        private static final long serialVersionUID=1L;
    }

    private final World world;
    private final NpcCombatEngagementService engagements;
    private final NpcCombatApproachService approach;
    private final NpcPlayerCombatResolutionService damage;
    private final String orchestrationAuthority;
    private final String orchestrationPolicy;

    private NpcCombatEngagementService.Snapshot activeApproachEngagement;
    private NpcCombatEngagementService.Snapshot activeAttackEngagement;
    private boolean ticking;

    NpcCombatControllerService(
        World world,
        NpcCombatApproachService.ApproachPolicy approachPolicy,
        NpcCombatEngagementService.CadenceResolver cadenceResolver,
        NpcPlayerCombatResolutionService damage,
        String orchestrationAuthority,
        String orchestrationPolicy
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        NpcCombatApproachService.ApproachPolicy delegateApproach=
            Objects.requireNonNull(
                approachPolicy,
                "approachPolicy"
            );
        NpcCombatEngagementService.CadenceResolver checkedCadence=
            Objects.requireNonNull(
                cadenceResolver,
                "cadenceResolver"
            );
        this.damage=
            Objects.requireNonNull(
                damage,
                "damage"
            );
        this.orchestrationAuthority=
            requireGameplayAuthority(
                orchestrationAuthority
            );
        this.orchestrationPolicy=
            requireText(
                orchestrationPolicy,
                "orchestrationPolicy"
            );

        if(!APPROACH_BEFORE_ENGAGEMENT_TICK.equals(
                this.orchestrationPolicy))
            throw new IllegalArgumentException(
                "unsupported NPC combat controller policy "+
                this.orchestrationPolicy
            );

        this.engagements=
            new NpcCombatEngagementService(
                this.world,
                checkedCadence,
                this::executeCanonicalAttack
            );

        this.approach=
            new NpcCombatApproachService(
                this.world,
                new NpcCombatApproachService.ApproachPolicy(){
                    @Override public int stopRange(
                        NpcCombatApproachService.Context context
                    ){
                        int range=
                            delegateApproach.stopRange(
                                context
                            );
                        requireApproachEngagementCurrent();
                        return range;
                    }

                    @Override public RouteRequest.Policy routePolicy(
                        NpcCombatApproachService.Context context
                    ){
                        RouteRequest.Policy policy=
                            delegateApproach.routePolicy(
                                context
                            );
                        requireApproachEngagementCurrent();
                        return policy;
                    }

                    @Override public String authority(){
                        return delegateApproach.authority();
                    }

                    @Override public String policy(){
                        return delegateApproach.policy();
                    }
                }
            );
    }

    synchronized NpcCombatEngagementService.Snapshot begin(
        WorldNpc attacker,
        WorldPlayer target,
        long targetGeneration,
        long firstAttackTick
    ){
        return engagements.begin(
            attacker,
            target,
            targetGeneration,
            firstAttackTick
        );
    }

    synchronized boolean cancel(
        WorldNpc attacker
    ){
        return engagements.cancel(
            attacker
        );
    }

    synchronized NpcCombatEngagementService.Snapshot get(
        EntityId attackerId
    ){
        return engagements.get(
            attackerId
        );
    }

    synchronized int size(){
        return engagements.size();
    }

    synchronized TickResult tick(
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
                "worldTick="+
                worldTick
            );

        requireCurrentWorldTick(
            worldTick
        );

        if(ticking)
            throw new IllegalStateException(
                "reentrant NPC combat controller tick"
            );

        ticking=true;

        try{
            NpcCombatEngagementService.Snapshot snapshot=
                engagements.get(
                    checkedId
                );

            if(snapshot==null)
                return result(
                    Status.NONE,
                    null,
                    null,
                    null
                );

            TickResult stale=
                validateStale(
                    snapshot
                );

            if(stale!=null)
                return stale;

            WorldNpc attacker=
                world.npcs().byId(
                    snapshot.attackerId
                );
            WorldPlayer target=
                world.players().byId(
                    snapshot.targetId
                );

            if(attacker==null||
               target==null)
                return validateStaleOrChanged(
                    snapshot
                );

            if(target.lifecycle().dead()){
                if(engagements.isCurrent(
                        snapshot))
                    engagements.cancel(
                        attacker
                    );

                return result(
                    Status.TARGET_DEAD,
                    engagements.get(
                        checkedId
                    ),
                    null,
                    null
                );
            }

            NpcCombatApproachService.Result approachResult;

            activeApproachEngagement=
                snapshot;

            try{
                approachResult=
                    approach.step(
                        attacker,
                        target,
                        snapshot.targetGeneration
                    );
            }catch(EngagementChangedException changed){
                return changedDuringApproach(
                    checkedId
                );
            }finally{
                activeApproachEngagement=null;
            }

            switch(approachResult.status){
                case MOVED:
                    return result(
                        Status.APPROACHED,
                        engagements.get(
                            checkedId
                        ),
                        approachResult,
                        null
                    );

                case BLOCKED:
                    return result(
                        Status.BLOCKED,
                        engagements.get(
                            checkedId
                        ),
                        approachResult,
                        null
                    );

                case DIFFERENT_PLANE:
                    return result(
                        Status.DIFFERENT_PLANE,
                        engagements.get(
                            checkedId
                        ),
                        approachResult,
                        null
                    );

                case STALE_ATTACKER:
                case STALE_TARGET:
                    return validateStaleOrChanged(
                        snapshot
                    );

                case IN_RANGE:
                    requireCurrentWorldTick(
                        worldTick
                    );
                    break;

                default:
                    throw new IllegalStateException(
                        "unsupported approach status "+
                        approachResult.status
                    );
            }

            NpcCombatEngagementService.TickResult cadence;

            activeAttackEngagement=
                snapshot;

            try{
                cadence=
                    engagements.tick(
                        checkedId,
                        worldTick
                    );
            }catch(TargetAlreadyDeadException dead){
                return result(
                    Status.TARGET_DEAD,
                    engagements.get(
                        checkedId
                    ),
                    approachResult,
                    null
                );
            }finally{
                activeAttackEngagement=null;
            }

            switch(cadence.status){
                case NONE:
                    return result(
                        Status.NONE,
                        engagements.get(
                            checkedId
                        ),
                        approachResult,
                        cadence
                    );

                case WAITING:
                    return result(
                        Status.WAITING,
                        cadence.snapshot,
                        approachResult,
                        cadence
                    );

                case ATTACKED:
                    return result(
                        Status.ATTACKED,
                        cadence.snapshot,
                        approachResult,
                        cadence
                    );

                case STALE_ATTACKER:
                    return result(
                        Status.STALE_ATTACKER,
                        null,
                        approachResult,
                        cadence
                    );

                case STALE_TARGET:
                    return result(
                        Status.STALE_TARGET,
                        null,
                        approachResult,
                        cadence
                    );

                default:
                    throw new IllegalStateException(
                        "unsupported engagement status "+
                        cadence.status
                    );
            }
        }finally{
            activeApproachEngagement=null;
            activeAttackEngagement=null;
            ticking=false;
        }
    }

    private void executeCanonicalAttack(
        WorldNpc attacker,
        WorldPlayer target,
        long targetGeneration,
        long worldTick
    )throws Exception{
        requireCurrentWorldTick(
            worldTick
        );

        NpcPlayerCombatResolutionService.Result resolved=
            damage.resolveImmediateOwnedAtExpectedTick(
                world,
                attacker,
                target,
                targetGeneration,
                worldTick
            );

        if(resolved.lifecycle.died){
            cancelActiveAttackEngagement(
                attacker
            );
            return;
        }

        if(resolved.lifecycle.ignoredDead){
            cancelActiveAttackEngagement(
                attacker
            );
            throw new TargetAlreadyDeadException();
        }
    }

    private void cancelActiveAttackEngagement(
        WorldNpc attacker
    ){
        NpcCombatEngagementService.Snapshot expected=
            activeAttackEngagement;

        if(expected!=null&&
           engagements.isCurrent(
                expected
            ))
            engagements.cancel(
                attacker
            );
    }

    private void requireCurrentWorldTick(
        long expectedTick
    ){
        long authoritativeTick=
            world.clock().tick();

        if(expectedTick!=authoritativeTick)
            throw new IllegalStateException(
                "NPC combat controller requires shared world tick expected="+
                authoritativeTick+
                " actual="+
                expectedTick
            );
    }

    String orchestrationAuthority(){
        return orchestrationAuthority;
    }

    String orchestrationPolicy(){
        return orchestrationPolicy;
    }

    private TickResult validateStale(
        NpcCombatEngagementService.Snapshot snapshot
    ){
        NpcCombatEngagementService.ValidationStatus validation=
            engagements.validateAndRetireStale(
                snapshot
            );

        switch(validation){
            case CURRENT:
                return null;

            case GONE:
                return result(
                    engagements.get(
                        snapshot.attackerId
                    )==null
                        ?Status.NONE
                        :Status.WAITING,
                    engagements.get(
                        snapshot.attackerId
                    ),
                    null,
                    null
                );

            case STALE_ATTACKER:
                return result(
                    Status.STALE_ATTACKER,
                    null,
                    null,
                    null
                );

            case STALE_TARGET:
                return result(
                    Status.STALE_TARGET,
                    null,
                    null,
                    null
                );

            default:
                throw new IllegalStateException(
                    "unsupported validation status "+
                    validation
                );
        }
    }

    private TickResult validateStaleOrChanged(
        NpcCombatEngagementService.Snapshot snapshot
    ){
        TickResult stale=
            validateStale(
                snapshot
            );

        if(stale!=null)
            return stale;

        return result(
            Status.WAITING,
            engagements.get(
                snapshot.attackerId
            ),
            null,
            null
        );
    }

    private TickResult changedDuringApproach(
        EntityId attackerId
    ){
        NpcCombatEngagementService.Snapshot current=
            engagements.get(
                attackerId
            );

        return result(
            current==null
                ?Status.NONE
                :Status.WAITING,
            current,
            null,
            null
        );
    }

    private void requireApproachEngagementCurrent(){
        NpcCombatEngagementService.Snapshot expected=
            activeApproachEngagement;

        if(expected==null||
           !engagements.isCurrent(
                expected
            ))
            throw new EngagementChangedException();
    }

    private static TickResult result(
        Status status,
        NpcCombatEngagementService.Snapshot engagement,
        NpcCombatApproachService.Result approach,
        NpcCombatEngagementService.TickResult cadence
    ){
        return new TickResult(
            status,
            engagement,
            approach,
            cadence
        );
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=
            requireText(
                value,
                "orchestrationAuthority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC combat orchestration actual="+
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
}
