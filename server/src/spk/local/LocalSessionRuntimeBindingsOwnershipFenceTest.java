package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

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
        World racingWorld=
            World.isolatedForTest(60_000L);

        WorldPlayer owner=
            new WorldPlayer();
        WorldPlayer closedOwner=
            new WorldPlayer();
        WorldPlayer racingOwner=
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

            long racingGeneration=
                racingWorld.registerPlayer(
                    racingOwner,
                    "runtime-racing"
                );

            CountDownLatch actionEntered=
                new CountDownLatch(1);
            CountDownLatch releaseAction=
                new CountDownLatch(1);
            CountDownLatch actionReturned=
                new CountDownLatch(1);
            CountDownLatch closeReturned=
                new CountDownLatch(1);

            AtomicReference<Throwable> actionFailure=
                new AtomicReference<>();
            AtomicReference<Throwable> closeFailure=
                new AtomicReference<>();

            Thread actionThread=
                new Thread(
                    ()->{
                        try{
                            racingWorld.withOpenPlayerOwnership(
                                racingOwner,
                                racingGeneration,
                                ()->{
                                    actionEntered.countDown();

                                    for(;;){
                                        try{
                                            releaseAction.await();
                                            break;
                                        }catch(
                                            InterruptedException ignored
                                        ){
                                            Thread.currentThread()
                                                .interrupt();
                                        }
                                    }
                                }
                            );
                        }catch(Throwable error){
                            actionFailure.set(error);
                        }finally{
                            actionReturned.countDown();
                        }
                    },
                    "runtime-binding-owner-action"
                );

            Thread closeThread=
                new Thread(
                    ()->{
                        try{
                            racingWorld.close();
                        }catch(Throwable error){
                            closeFailure.set(error);
                        }finally{
                            closeReturned.countDown();
                        }
                    },
                    "runtime-binding-world-close"
                );

            actionThread.start();

            if(!actionEntered.await(
                    2,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "owned action did not enter"
                );

            closeThread.start();

            if(closeReturned.await(
                    100,
                    TimeUnit.MILLISECONDS))
                throw new AssertionError(
                    "World close bypassed active ownership action"
                );

            if(racingWorld.closed())
                throw new AssertionError(
                    "World terminal fence published during active ownership action"
                );

            releaseAction.countDown();

            if(!actionReturned.await(
                    2,
                    TimeUnit.SECONDS)||
               !closeReturned.await(
                    3,
                    TimeUnit.SECONDS))
                throw new AssertionError(
                    "ownership action/close did not finish"
                );

            actionThread.join(1_000L);
            closeThread.join(1_000L);

            if(actionThread.isAlive()||
               closeThread.isAlive())
                throw new AssertionError(
                    "ownership lifecycle thread retained"
                );

            if(actionFailure.get()!=null)
                throw new AssertionError(
                    "owned action failed",
                    actionFailure.get()
                );

            if(closeFailure.get()!=null)
                throw new AssertionError(
                    "concurrent close failed",
                    closeFailure.get()
                );

            if(!racingWorld.closed())
                throw new AssertionError(
                    "World did not become terminal after ownership action"
                );

            if(!racingWorld.unregisterPlayer(
                    racingOwner,
                    racingGeneration
                ))
                throw new AssertionError(
                    "post-close racing owner cleanup failed"
                );

            System.out.println(
                "LOCAL_SESSION_RUNTIME_BINDINGS_OWNERSHIP_FENCE_PASS "+
                "foreignWorldRejected=true "+
                "ownedWorldAccepted=true "+
                "terminalWorldRejected=true "+
                "atomicCloseFence=true "+
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

            if(racingOwner.registered())
                racingWorld.unregisterPlayer(
                    racingOwner,
                    racingOwner.generation()
                );

            foreignWorld.close();
            ownerWorld.close();
            closedWorld.close();
            racingWorld.close();
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
