package spk.local;

import java.util.Objects;

/**
 * Minimal autonomous-combat composition for canonical NPCs.
 *
 * Idle NPCs may acquire one caller-approved target and begin the existing
 * combat controller. Existing engagements delegate directly to that controller.
 * Aggro eligibility/priority and initial attack timing remain caller-owned.
 */
final class NpcCombatAiService {
    static final String ACQUIRE_THEN_DELEGATE=
        "ACQUIRE_THEN_DELEGATE";

    enum Status {
        NO_TARGET,
        ENGAGED,
        CONTROLLER,
        ATTACKER_DEAD,
        STALE_ATTACKER,
        STALE_TARGET
    }

    interface InitialAttackTickResolver {
        long resolve(Context context) throws Exception;
    }

    static final class Context {
        final EntityId attackerId;
        final int attackerDefinitionId;
        final Tile attackerTile;
        final EntityId targetId;
        final long targetGeneration;
        final Tile targetTile;
        final long targetPriority;
        final long worldTick;

        private Context(
            WorldNpc attacker,
            Tile attackerTile,
            NpcTargetAcquisitionService.Target target,
            long worldTick
        ){
            this.attackerId=attacker.id;
            this.attackerDefinitionId=
                attacker.definitionId;
            this.attackerTile=attackerTile;
            this.targetId=target.playerId;
            this.targetGeneration=
                target.generation;
            this.targetTile=target.tile;
            this.targetPriority=
                target.priority;
            this.worldTick=worldTick;
        }
    }

    static final class Result {
        final Status status;
        final NpcTargetAcquisitionService.Result acquisition;
        final NpcCombatEngagementService.Snapshot engagement;
        final NpcCombatControllerService.TickResult controller;
        final String authority;
        final String policy;

        private Result(
            Status status,
            NpcTargetAcquisitionService.Result acquisition,
            NpcCombatEngagementService.Snapshot engagement,
            NpcCombatControllerService.TickResult controller,
            String authority,
            String policy
        ){
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.acquisition=acquisition;
            this.engagement=engagement;
            this.controller=controller;
            this.authority=authority;
            this.policy=policy;
        }
    }

    private final World world;
    private final NpcTargetAcquisitionService acquisition;
    private final NpcCombatControllerService controller;
    private final InitialAttackTickResolver initialAttackTickResolver;
    private final String authority;
    private final String policy;

    NpcCombatAiService(
        World world,
        NpcTargetAcquisitionService acquisition,
        NpcCombatControllerService controller,
        InitialAttackTickResolver initialAttackTickResolver,
        String authority,
        String policy
    ){
        this.world=
            Objects.requireNonNull(
                world,
                "world"
            );
        this.acquisition=
            Objects.requireNonNull(
                acquisition,
                "acquisition"
            );
        this.controller=
            Objects.requireNonNull(
                controller,
                "controller"
            );
        this.initialAttackTickResolver=
            Objects.requireNonNull(
                initialAttackTickResolver,
                "initialAttackTickResolver"
            );
        this.authority=
            requireGameplayAuthority(
                authority
            );
        this.policy=
            requireText(
                policy,
                "policy"
            );

        if(!ACQUIRE_THEN_DELEGATE.equals(
                this.policy))
            throw new IllegalArgumentException(
                "unsupported NPC combat AI policy "+
                this.policy
            );
    }

    synchronized Result tick(
        WorldNpc attacker,
        long worldTick
    )throws Exception{
        WorldNpc checked=
            Objects.requireNonNull(
                attacker,
                "attacker"
            );

        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        requireCurrentWorldTick(
            worldTick
        );

        final NpcLifecycleService.Snapshot[] lifecycle={null};

        boolean attackerCurrent=
            world.npcs()
                .withCurrentMutationOwnershipIfCurrent(
                    checked,
                    ()->
                        lifecycle[0]=
                            world.npcLifecycle()
                                .get(
                                    checked.id
                                )
                );

        if(!attackerCurrent)
            return result(
                Status.STALE_ATTACKER,
                null,
                null,
                null
            );

        if(lifecycle[0]!=null&&
           lifecycle[0].dead()){
            try{
                controller.cancel(
                    checked
                );
            }catch(IllegalStateException failure){
                if(world.npcs().byId(
                        checked.id
                    )!=checked)
                    return result(
                        Status.STALE_ATTACKER,
                        null,
                        null,
                        null
                    );

                throw failure;
            }

            return result(
                Status.ATTACKER_DEAD,
                null,
                null,
                null
            );
        }

        NpcCombatEngagementService.Snapshot active=
            controller.get(
                checked.id
            );

        if(active!=null){
            NpcCombatControllerService.TickResult
                delegated=
                    controller.tick(
                        checked.id,
                        worldTick
                    );

            return result(
                Status.CONTROLLER,
                null,
                controller.get(
                    checked.id
                ),
                delegated
            );
        }

        NpcTargetAcquisitionService.Result acquired=
            acquisition.acquire(
                checked
            );

        switch(acquired.status){
            case STALE_ATTACKER:
                return result(
                    Status.STALE_ATTACKER,
                    acquired,
                    null,
                    null
                );

            case NONE:
                return result(
                    Status.NO_TARGET,
                    acquired,
                    null,
                    null
                );

            case ACQUIRED:
                break;

            default:
                throw new IllegalStateException(
                    "unsupported acquisition status "+
                    acquired.status
                );
        }

        final Tile[] attackerTile={null};

        boolean attackerCurrent=
            world.npcs()
                .withCurrentMutationOwnershipIfCurrent(
                    checked,
                    ()->
                        attackerTile[0]=
                            checked.tile()
                );

        if(!attackerCurrent)
            return result(
                Status.STALE_ATTACKER,
                acquired,
                null,
                null
            );

        NpcTargetAcquisitionService.Target target=
            Objects.requireNonNull(
                acquired.target,
                "acquired target"
            );

        long firstAttackTick=
            initialAttackTickResolver.resolve(
                new Context(
                    checked,
                    attackerTile[0],
                    target,
                    worldTick
                )
            );

        if(firstAttackTick<worldTick)
            throw new IllegalStateException(
                "initial attack tick precedes current world tick current="+
                worldTick+
                " first="+
                firstAttackTick
            );

        requireCurrentWorldTick(
            worldTick
        );

        final NpcCombatEngagementService.Snapshot
            begun;

        try{
            begun=
                controller.begin(
                    checked,
                    target.player,
                    target.generation,
                    firstAttackTick
                );
        }catch(IllegalStateException failure){
            if(world.npcs().byId(
                    checked.id
                )!=checked)
                return result(
                    Status.STALE_ATTACKER,
                    acquired,
                    null,
                    null
                );

            if(!world.players().owns(
                    target.player,
                    target.generation
                ))
                return result(
                    Status.STALE_TARGET,
                    acquired,
                    null,
                    null
                );

            throw failure;
        }

        return result(
            Status.ENGAGED,
            acquired,
            begun,
            null
        );
    }

    String authority(){
        return authority;
    }

    String policy(){
        return policy;
    }

    private Result result(
        Status status,
        NpcTargetAcquisitionService.Result acquisitionResult,
        NpcCombatEngagementService.Snapshot engagement,
        NpcCombatControllerService.TickResult controllerResult
    ){
        return new Result(
            status,
            acquisitionResult,
            engagement,
            controllerResult,
            authority,
            policy
        );
    }

    private void requireCurrentWorldTick(
        long expected
    ){
        long current=
            world.clock().tick();

        if(current!=expected)
            throw new IllegalStateException(
                "NPC combat AI requires shared world tick expected="+
                current+
                " actual="+
                expected
            );
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=
            requireText(
                value,
                "authority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC combat AI actual="+
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
