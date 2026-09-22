package spk.local;

import java.io.ByteArrayOutputStream;

public final class PlayerInteractionTargetGenerationFenceTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer target=
            new WorldPlayer();

        long ownerGeneration=
            world.registerPlayer(
                owner,
                "interaction-owner"
            );
        long targetGenerationA=
            world.registerPlayer(
                target,
                "interaction-target"
            );

        ByteArrayOutputStream ownerOut=
            new ByteArrayOutputStream();
        ByteArrayOutputStream targetOut=
            new ByteArrayOutputStream();

        ServerPacketWriter ownerWriter=
            writer(ownerOut,1);
        ServerPacketWriter targetWriter=
            writer(targetOut,5);

        Player81WorldSync.Context ownerSync=
            Player81WorldSync.register(
                ownerWriter,
                world,
                owner,
                new DevAuthorityWorkbench()
            );
        Player81WorldSync.register(
            targetWriter,
            world,
            target,
            new DevAuthorityWorkbench()
        );

        try{
            moveTarget(
                target,
                MovementState.INITIAL_X+1,
                MovementState.INITIAL_Y
            );

            Player81WorldSync.transformForTest(
                ownerSync,
                BootstrapPackets.player81Idle()
            );

            int targetIndex=
                ownerSync.clientIndexFor(target);

            if(targetIndex<0)
                throw new AssertionError(
                    "target not visible"
                );

            LocalPlayerInteractionHandler handler=
                new LocalPlayerInteractionHandler(
                    world,
                    owner,
                    owner.movement(),
                    owner.equipment(),
                    ()->ownerGeneration
                );

            PlayerAction attack=
                new PlayerAction(
                    128,
                    1,
                    targetIndex,
                    "Attack"
                );

            String attackAccepted=
                handler.handleResolved(
                    attack,
                    target,
                    ownerSync
                );

            requireContains(
                attackAccepted,
                "V5131_PLAYER_ATTACK_REQUEST",
                "attack acceptance"
            );

            if(handler.activeAttackGeneration()!=
                    targetGenerationA)
                throw new AssertionError(
                    "attack did not capture target generation A"
                );

            int hpBefore=
                target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    );

            long targetGenerationB=
                replaceGeneration(
                    world,
                    target,
                    targetGenerationA
                );

            String staleAttack=
                handler.tickAttack(
                    1L,
                    ownerWriter,
                    ownerSync
                );

            requireContains(
                staleAttack,
                "TARGET_OWNERSHIP_CHANGED",
                "stale attack cancellation"
            );

            if(handler.activeAttack()!=null||
               handler.activeAttackGeneration()!=0L)
                throw new AssertionError(
                    "stale attack state survived"
                );

            if(target.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    )!=hpBefore)
                throw new AssertionError(
                    "stale attack damaged replacement generation"
                );

            PlayerAction follow=
                new PlayerAction(
                    153,
                    2,
                    targetIndex,
                    "Follow"
                );

            String followAccepted=
                handler.handleResolved(
                    follow,
                    target,
                    ownerSync
                );

            requireContains(
                followAccepted,
                "V5131_PLAYER_FOLLOW_REQUEST",
                "follow acceptance"
            );

            if(handler.activeFollowGeneration()!=
                    targetGenerationB)
                throw new AssertionError(
                    "follow did not capture target generation B"
                );

            long targetGenerationC=
                replaceGeneration(
                    world,
                    target,
                    targetGenerationB
                );

            Integer staleFacing=
                handler.movementInteractionTarget(
                    ownerSync
                );

            if(staleFacing!=null)
                throw new AssertionError(
                    "stale follow still supplied facing target "+
                    staleFacing
                );

            if(handler.activeFollow()!=null||
               handler.activeFollowGeneration()!=0L)
                throw new AssertionError(
                    "stale follow state survived"
                );

            moveTarget(
                target,
                MovementState.INITIAL_X+2,
                MovementState.INITIAL_Y
            );

            PlayerAction trade=
                new PlayerAction(
                    73,
                    3,
                    targetIndex,
                    "Trade with"
                );

            String tradeAccepted=
                handler.handleResolved(
                    trade,
                    target,
                    ownerSync
                );

            requireContains(
                tradeAccepted,
                "V5141_PLAYER_TRADE_APPROACH",
                "trade acceptance"
            );

            if(handler.activeTradeGeneration()!=
                    targetGenerationC)
                throw new AssertionError(
                    "trade did not capture target generation C"
                );

            long targetGenerationD=
                replaceGeneration(
                    world,
                    target,
                    targetGenerationC
                );

            String staleTrade=
                handler.prepareTick(
                    2L,
                    ownerSync
                );

            requireContains(
                staleTrade,
                "TARGET_OWNERSHIP_CHANGED",
                "stale trade cancellation"
            );

            if(handler.activeTrade()!=null||
               handler.activeTradeGeneration()!=0L)
                throw new AssertionError(
                    "stale trade state survived"
                );

            if(TradeService.active(owner)||
               TradeService.active(target))
                throw new AssertionError(
                    "stale trade dispatched into TradeService"
                );

            String freshFollow=
                handler.handleResolved(
                    follow,
                    target,
                    ownerSync
                );

            requireContains(
                freshFollow,
                "V5131_PLAYER_FOLLOW_REQUEST",
                "replacement follow acceptance"
            );

            if(handler.activeFollow()==null||
               handler.activeFollowGeneration()!=
                    targetGenerationD)
                throw new AssertionError(
                    "replacement target generation was not accepted freshly"
                );

            System.out.println(
                "PLAYER_INTERACTION_TARGET_GENERATION_FENCE_PASS "+
                "attackCapturedA=true "+
                "staleAttackCancelled=true "+
                "staleAttackNoDamage=true "+
                "staleFollowFacingRejected=true "+
                "staleTradeDispatchRejected=true "+
                "allOldStateCleared=true "+
                "replacementGenerationAccepted=true"
            );
        }finally{
            TradeService.unregister(owner);
            TradeService.unregister(target);
            Player81WorldSync.unregister(ownerWriter);
            Player81WorldSync.unregister(targetWriter);

            if(owner.registered())
                world.unregisterPlayer(
                    owner,
                    owner.generation()
                );

            if(target.registered())
                world.unregisterPlayer(
                    target,
                    target.generation()
                );

            world.close();
        }
    }

    private static long replaceGeneration(
        World world,
        WorldPlayer target,
        long expectedGeneration
    ){
        if(!world.unregisterPlayer(
                target,
                expectedGeneration
            ))
            throw new AssertionError(
                "target generation unregister failed "+
                expectedGeneration
            );

        long replacement=
            world.registerPlayer(
                target,
                "interaction-target"
            );

        if(replacement<=expectedGeneration)
            throw new AssertionError(
                "target generation did not advance "+
                expectedGeneration+
                " -> "+
                replacement
            );

        return replacement;
    }

    private static void moveTarget(
        WorldPlayer target,
        int x,
        int y
    ){
        String accepted=
            target.movement().accept(
                new MovementRequest(
                    164,
                    false,
                    new int[]{x},
                    new int[]{y},
                    new byte[0]
                )
            );

        if(!accepted.startsWith("ACCEPTED"))
            throw new AssertionError(
                "target movement setup failed "+
                accepted
            );

        target.movement().advance();
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out,
        int seed
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static void requireContains(
        String actual,
        String expected,
        String phase
    ){
        if(actual==null||
           !actual.contains(expected))
            throw new AssertionError(
                phase+
                " expected="+expected+
                " actual="+actual
            );
    }

    private PlayerInteractionTargetGenerationFenceTest(){}
}
