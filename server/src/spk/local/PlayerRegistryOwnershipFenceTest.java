package spk.local;

import java.util.concurrent.CompletableFuture;

public final class PlayerRegistryOwnershipFenceTest {
    public static void main(String[] args){
        registryFence();
        worldFence();
        crossWorldFence();

        System.out.println(
            "PLAYER_REGISTRY_OWNERSHIP_FENCE_PASS "+
            "staleRegistryCleanupRejected=true "+
            "staleWorldCleanupRejected=true "+
            "newTickTargetPreserved=true "+
            "newCommandPreserved=true "+
            "crossWorldDuplicateOwnerRejected=true"
        );
    }

    private static void registryFence(){
        PlayerRegistry registry=
            new PlayerRegistry();
        WorldPlayer player=
            new WorldPlayer();

        long first=
            registry.register(
                player,
                "registry-fence"
            );

        if(!registry.unregister(
                player,
                first
            ))
            throw new AssertionError(
                "initial registry unregister failed"
            );

        long replacement=
            registry.register(
                player,
                "registry-fence"
            );

        if(replacement==first)
            throw new AssertionError(
                "replacement generation did not advance"
            );

        if(registry.unregister(
                player,
                first
            ))
            throw new AssertionError(
                "stale registry generation removed replacement"
            );

        if(!player.accepts(replacement) ||
           registry.byId(player.id())!=player ||
           registry.byName("REGISTRY-FENCE")!=player)
            throw new AssertionError(
                "stale registry cleanup damaged replacement"
            );

        if(!registry.unregister(
                player,
                replacement
            ))
            throw new AssertionError(
                "replacement registry cleanup failed"
            );
    }

    private static void worldFence(){
        World world=
            World.isolatedForTest(60_000L);

        try{
            WorldPlayer player=
                new WorldPlayer();

            long first=
                world.registerPlayer(
                    player,
                    "world-fence"
                );

            if(!world.unregisterPlayer(
                    player,
                    first
                ))
                throw new AssertionError(
                    "initial world unregister failed"
                );

            long replacement=
                world.registerPlayer(
                    player,
                    "world-fence"
                );

            final boolean[] ticked=
                new boolean[1];

            WorldTickTarget target=
                new WorldTickTarget(){
                    @Override public EntityId ownerId(){
                        return player.id();
                    }

                    @Override public long ownerGeneration(){
                        return replacement;
                    }

                    @Override public void onWorldTick(
                        long tick,
                        long nowMillis
                    ){
                        ticked[0]=true;
                    }
                };

            world.attachTickTarget(target);

            final int[] commands=
                new int[1];

            CompletableFuture<Void> queued=
                world.submit(
                    player,
                    ()->commands[0]++
                );

            if(world.detachTickTarget(
                    player.id(),
                    first
                ))
                throw new AssertionError(
                    "stale generation detached replacement tick target"
                );

            if(world.unregisterPlayer(
                    player,
                    first
                ))
                throw new AssertionError(
                    "stale generation unregistered replacement player"
                );

            if(!player.accepts(replacement) ||
               world.players().byId(player.id())!=player ||
               world.players().size()!=1)
                throw new AssertionError(
                    "stale world cleanup damaged replacement membership"
                );

            if(world.tickTargetsSnapshot().size()!=1 ||
               world.tickTargetsSnapshot().get(0)!=target)
                throw new AssertionError(
                    "replacement tick target was not preserved"
                );

            if(world.commands().size()!=1)
                throw new AssertionError(
                    "replacement command was cancelled by stale cleanup"
                );

            if(world.commands().drain(8,8)!=1)
                throw new AssertionError(
                    "replacement command did not drain"
                );

            queued.join();

            if(commands[0]!=1)
                throw new AssertionError(
                    "replacement command did not execute"
                );

            if(!world.unregisterPlayer(
                    player,
                    replacement
                ))
                throw new AssertionError(
                    "current owner unregister failed"
                );

            if(player.registered() ||
               world.players().size()!=0 ||
               !world.tickTargetsSnapshot().isEmpty())
                throw new AssertionError(
                    "current owner cleanup incomplete"
                );

            if(ticked[0])
                throw new AssertionError(
                    "test unexpectedly ran WorldPulse"
                );
        }finally{
            world.close();
        }
    }

    private static void crossWorldFence(){
        World firstWorld=
            World.isolatedForTest(60_000L);
        World secondWorld=
            World.isolatedForTest(60_000L);

        try{
            WorldPlayer player=
                new WorldPlayer();

            long generation=
                firstWorld.registerPlayer(
                    player,
                    "cross-world-fence"
                );

            boolean rejected=false;

            try{
                secondWorld.registerPlayer(
                    player,
                    "cross-world-fence"
                );
            }catch(IllegalStateException expected){
                rejected=
                    "already registered".equals(
                        expected.getMessage()
                    );
            }

            if(!rejected)
                throw new AssertionError(
                    "cross-world duplicate ownership accepted"
                );

            if(firstWorld.players().byId(
                    player.id()
                )!=player ||
               firstWorld.players().size()!=1 ||
               secondWorld.players().size()!=0 ||
               !player.accepts(generation))
                throw new AssertionError(
                    "cross-world rejection was not failure atomic"
                );

            if(!firstWorld.unregisterPlayer(
                    player,
                    generation
                ))
                throw new AssertionError(
                    "cross-world owner cleanup failed"
                );
        }finally{
            secondWorld.close();
            firstWorld.close();
        }
    }

    private PlayerRegistryOwnershipFenceTest(){}
}
