package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalSessionRuntimeBindingsOwnershipFenceTest {
    private static final class Bridge
        implements LocalSessionRuntimeBindings.SessionBridge {
        @Override public void saveAccount(
            String tag,
            String reason
        ){}
    }

    public static void main(String[] args)
        throws Exception {

        World ownerWorld=
            World.isolatedForTest(60_000L);
        World foreignWorld=
            World.isolatedForTest(60_000L);
        World closedWorld=
            World.isolatedForTest(60_000L);

        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer closedOwner=
            new WorldPlayer();

        try{
            long ownerGeneration=
                ownerWorld.registerPlayer(
                    owner,
                    "runtime-owner"
                );

            LocalSessionRuntimeBindings foreign=
                bindings(
                    foreignWorld,
                    owner
                );

            boolean foreignRejected=false;

            try{
                foreign.register(
                    writer(1),
                    "[runtime-binding-ownership] "
                );
            }catch(IllegalStateException expected){
                foreignRejected=
                    expected.getMessage().contains(
                        "owner not registered in world"
                    );
            }

            if(!foreignRejected)
                throw new AssertionError(
                    "foreign runtime binding accepted"
                );

            if(foreign.context()!=null)
                throw new AssertionError(
                    "foreign rejection installed Player81 context"
                );

            LocalSessionRuntimeBindings owned=
                bindings(
                    ownerWorld,
                    owner
                );

            owned.register(
                writer(5),
                "[runtime-binding-ownership] "
            );

            if(owned.context()==null)
                throw new AssertionError(
                    "owned runtime binding rejected"
                );

            owned.unregister();

            if(owned.context()!=null)
                throw new AssertionError(
                    "owned runtime binding cleanup failed"
                );

            if(!ownerWorld.unregisterPlayer(
                    owner,
                    ownerGeneration
                ))
                throw new AssertionError(
                    "owner cleanup failed"
                );

            long closedGeneration=
                closedWorld.registerPlayer(
                    closedOwner,
                    "runtime-closed"
                );

            closedWorld.close();

            LocalSessionRuntimeBindings terminal=
                bindings(
                    closedWorld,
                    closedOwner
                );

            boolean closedRejected=false;

            try{
                terminal.register(
                    writer(9),
                    "[runtime-binding-ownership] "
                );
            }catch(IllegalStateException expected){
                closedRejected=
                    expected.getMessage().contains(
                        "world closed"
                    );
            }

            if(!closedRejected)
                throw new AssertionError(
                    "terminal World runtime binding accepted"
                );

            if(terminal.context()!=null)
                throw new AssertionError(
                    "terminal rejection installed Player81 context"
                );

            if(!closedWorld.unregisterPlayer(
                    closedOwner,
                    closedGeneration
                ))
                throw new AssertionError(
                    "post-close owner cleanup failed"
                );

            System.out.println(
                "LOCAL_SESSION_RUNTIME_BINDINGS_OWNERSHIP_FENCE_PASS "+
                "foreignWorldRejected=true "+
                "ownedWorldAccepted=true "+
                "terminalWorldRejected=true "+
                "postCloseCleanup=true"
            );
        }finally{
            if(owner.registered())
                ownerWorld.unregisterPlayer(
                    owner,
                    owner.generation()
                );

            if(closedOwner.registered())
                closedWorld.unregisterPlayer(
                    closedOwner,
                    closedOwner.generation()
                );

            foreignWorld.close();
            ownerWorld.close();
            closedWorld.close();
        }
    }

    private static LocalSessionRuntimeBindings bindings(
        World world,
        WorldPlayer player
    ){
        return new LocalSessionRuntimeBindings(
            world,
            player,
            new DevAuthorityWorkbench(),
            new NpcRegistry(
                new DevAuthorityWorkbench()
            ),
            player.movement(),
            player.bank(),
            new Bridge()
        );
    }

    private static ServerPacketWriter writer(
        int seed
    ){
        return new ServerPacketWriter(
            new ByteArrayOutputStream(),
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

    private LocalSessionRuntimeBindingsOwnershipFenceTest(){}
}
