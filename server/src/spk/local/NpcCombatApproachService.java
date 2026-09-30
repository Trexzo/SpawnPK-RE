package spk.local;

import java.util.*;

/**
 * Canonical collision-safe approach primitive for an already-selected
 * NPC -> player combat pair.
 *
 * Target selection, aggression, attack cadence, damage and presentation remain
 * caller-owned. This service only owns one collision-validated NPC route step.
 */
final class NpcCombatApproachService {
    enum Status {
        IN_RANGE,
        MOVED,
        BLOCKED,
        DIFFERENT_PLANE,
        STALE_ATTACKER,
        STALE_TARGET
    }

    interface ApproachPolicy {
        int stopRange(Context context);
        RouteRequest.Policy routePolicy(Context context);
        String authority();
        String policy();
    }

    static final class Context {
        final EntityId attackerId;
        final int attackerDefinitionId;
        final Tile attackerTile;
        final EntityId targetId;
        final long targetGeneration;
        final Tile targetTile;

        private Context(
            WorldNpc attacker,
            Tile attackerTile,
            WorldPlayer target,
            long targetGeneration,
            Tile targetTile
        ){
            this.attackerId=attacker.id;
            this.attackerDefinitionId=attacker.definitionId;
            this.attackerTile=attackerTile;
            this.targetId=target.id();
            this.targetGeneration=targetGeneration;
            this.targetTile=targetTile;
        }
    }

    static final class Result {
        final Status status;
        final EntityId attackerId;
        final EntityId targetId;
        final Tile before;
        final Tile after;
        final int stopRange;
        final RouteRequest.Policy routePolicy;
        final String routeAuthority;
        final String policyAuthority;
        final String policyName;

        private Result(
            Status status,
            EntityId attackerId,
            EntityId targetId,
            Tile before,
            Tile after,
            int stopRange,
            RouteRequest.Policy routePolicy,
            String routeAuthority,
            String policyAuthority,
            String policyName
        ){
            this.status=Objects.requireNonNull(status,"status");
            this.attackerId=Objects.requireNonNull(attackerId,"attackerId");
            this.targetId=Objects.requireNonNull(targetId,"targetId");
            this.before=before;
            this.after=after;
            this.stopRange=stopRange;
            this.routePolicy=routePolicy;
            this.routeAuthority=routeAuthority;
            this.policyAuthority=Objects.requireNonNull(
                policyAuthority,
                "policyAuthority"
            );
            this.policyName=Objects.requireNonNull(
                policyName,
                "policyName"
            );
        }
    }

    private final World world;
    private final ApproachPolicy policy;
    private final String policyAuthority;
    private final String policyName;

    NpcCombatApproachService(
        World world,
        ApproachPolicy policy
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.policy=Objects.requireNonNull(policy,"policy");
        this.policyAuthority=requireGameplayAuthority(
            policy.authority()
        );
        this.policyName=requireText(
            policy.policy(),
            "policy"
        );
    }

    Result step(
        WorldNpc attacker,
        WorldPlayer target,
        long targetGeneration
    )throws Exception{
        WorldNpc checkedAttacker=
            Objects.requireNonNull(attacker,"attacker");
        WorldPlayer checkedTarget=
            Objects.requireNonNull(target,"target");

        final Result[] result=
            new Result[1];

        boolean targetCurrent=
            world.withOpenPlayerMutationOwnershipIfCurrent(
                checkedTarget,
                targetGeneration,
                ()->{
                    boolean attackerCurrent=
                        world.npcs()
                            .withCurrentMutationOwnershipIfCurrent(
                                checkedAttacker,
                                ()->{
                                    Tile from=
                                        checkedAttacker.tile();
                                    Tile targetTile=
                                        new Tile(
                                            checkedTarget.movement().x(),
                                            checkedTarget.movement().y(),
                                            checkedTarget.movement().plane()
                                        );

                                    Context context=
                                        new Context(
                                            checkedAttacker,
                                            from,
                                            checkedTarget,
                                            targetGeneration,
                                            targetTile
                                        );

                                    int stopRange=
                                        policy.stopRange(
                                            context
                                        );

                                    if(stopRange<=0)
                                        throw new IllegalArgumentException(
                                            "stopRange="+
                                            stopRange
                                        );

                                    if(!world.players().owns(
                                            checkedTarget,
                                            targetGeneration
                                        )){
                                        result[0]=
                                            result(
                                                Status.STALE_TARGET,
                                                checkedAttacker,
                                                checkedTarget,
                                                null,
                                                null,
                                                -1,
                                                null,
                                                null
                                            );
                                        return;
                                    }

                                    if(world.npcs().byId(
                                            checkedAttacker.id
                                        )!=checkedAttacker){
                                        result[0]=
                                            result(
                                                Status.STALE_ATTACKER,
                                                checkedAttacker,
                                                checkedTarget,
                                                null,
                                                null,
                                                -1,
                                                null,
                                                null
                                            );
                                        return;
                                    }

                                    RouteRequest.Policy routePolicy=
                                        Objects.requireNonNull(
                                            policy.routePolicy(
                                                context
                                            ),
                                            "routePolicy"
                                        );

                                    if(!world.players().owns(
                                            checkedTarget,
                                            targetGeneration
                                        )){
                                        result[0]=
                                            result(
                                                Status.STALE_TARGET,
                                                checkedAttacker,
                                                checkedTarget,
                                                null,
                                                null,
                                                -1,
                                                null,
                                                null
                                            );
                                        return;
                                    }

                                    if(world.npcs().byId(
                                            checkedAttacker.id
                                        )!=checkedAttacker){
                                        result[0]=
                                            result(
                                                Status.STALE_ATTACKER,
                                                checkedAttacker,
                                                checkedTarget,
                                                null,
                                                null,
                                                -1,
                                                null,
                                                null
                                            );
                                        return;
                                    }

                                    if((routePolicy==
                                            RouteRequest.Policy
                                                .HOME_COMBAT_COMPATIBILITY||
                                        routePolicy==
                                            RouteRequest.Policy
                                                .HOME_RECOVERED_STATIC_AUTHORITY)&&
                                       from.plane!=0)
                                        throw new IllegalArgumentException(
                                            "HOME route policy requires plane 0"
                                        );

                                    if(from.plane!=targetTile.plane){
                                        result[0]=
                                            result(
                                                Status.DIFFERENT_PLANE,
                                                checkedAttacker,
                                                checkedTarget,
                                                from,
                                                from,
                                                stopRange,
                                                routePolicy,
                                                null
                                            );
                                        return;
                                    }

                                    if(withinStopRange(
                                            from,
                                            targetTile,
                                            stopRange,
                                            routePolicy
                                        )){
                                        result[0]=
                                            result(
                                                Status.IN_RANGE,
                                                checkedAttacker,
                                                checkedTarget,
                                                from,
                                                from,
                                                stopRange,
                                                routePolicy,
                                                null
                                            );
                                        return;
                                    }

                                    RouteFinder.Result route=
                                        RouteFinder.find(
                                            new RouteRequest(
                                                from.x,
                                                from.y,
                                                from.plane,
                                                targetTile.x,
                                                targetTile.y,
                                                stopRange,
                                                RouteRequest.Purpose.COMBAT_APPROACH,
                                                routePolicy
                                            )
                                        );

                                    if(route.path==null||
                                       route.path.isEmpty()){
                                        result[0]=
                                            result(
                                                Status.BLOCKED,
                                                checkedAttacker,
                                                checkedTarget,
                                                from,
                                                from,
                                                stopRange,
                                                routePolicy,
                                                route.authority
                                            );
                                        return;
                                    }

                                    int[] first=
                                        route.path.get(0);

                                    if(first==null||
                                       first.length<2||
                                       MovementState.direction(
                                           from.x,
                                           from.y,
                                           first[0],
                                           first[1]
                                       )<0)
                                        throw new IllegalStateException(
                                            "RouteFinder returned non-adjacent first step"
                                        );

                                    Tile after=
                                        new Tile(
                                            first[0],
                                            first[1],
                                            from.plane
                                        );

                                    checkedAttacker.moveTo(
                                        after.x,
                                        after.y,
                                        after.plane
                                    );

                                    result[0]=
                                        result(
                                            Status.MOVED,
                                            checkedAttacker,
                                            checkedTarget,
                                            from,
                                            after,
                                            stopRange,
                                            routePolicy,
                                            route.authority
                                        );
                                }
                            );

                    if(!attackerCurrent)
                        result[0]=
                            result(
                                Status.STALE_ATTACKER,
                                checkedAttacker,
                                checkedTarget,
                                null,
                                null,
                                -1,
                                null,
                                null
                            );
                }
            );

        if(!targetCurrent)
            return result(
                Status.STALE_TARGET,
                checkedAttacker,
                checkedTarget,
                null,
                null,
                -1,
                null,
                null
            );

        return Objects.requireNonNull(
            result[0],
            "approach result"
        );
    }

    String policyAuthority(){
        return policyAuthority;
    }

    String policyName(){
        return policyName;
    }

    private Result result(
        Status status,
        WorldNpc attacker,
        WorldPlayer target,
        Tile before,
        Tile after,
        int stopRange,
        RouteRequest.Policy routePolicy,
        String routeAuthority
    ){
        return new Result(
            status,
            attacker.id,
            target.id(),
            before,
            after,
            stopRange,
            routePolicy,
            routeAuthority,
            policyAuthority,
            policyName
        );
    }

    private static boolean withinStopRange(
        Tile from,
        Tile target,
        int stopRange,
        RouteRequest.Policy routePolicy
    ){
        int dx=Math.abs(
            target.x-from.x
        );
        int dy=Math.abs(
            target.y-from.y
        );

        switch(routePolicy){
            case HOME_COMBAT_COMPATIBILITY:
            case HOME_RECOVERED_STATIC_AUTHORITY:
                if(stopRange==1)
                    return dx+dy==1;
                return Math.max(dx,dy)<=stopRange&&
                    (dx!=0||dy!=0);

            case WORLD_STATIC_AUTHORITY:
                return Math.max(dx,dy)<=stopRange;

            default:
                throw new IllegalStateException(
                    "unsupported route policy "+
                    routePolicy
                );
        }
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=requireText(
            value,
            "policyAuthority"
        );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC approach actual="+
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
