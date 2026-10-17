package spk.local;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class WorldNpcMutationOwnershipTest {
    public static void main(String[] args)throws Exception{
        WorldNpcRegistry registry=
            new WorldNpcRegistry();

        WorldNpc npc=
            registry.spawn(
                1488,
                3200,
                3200,
                0
            );

        AtomicInteger currentCalls=
            new AtomicInteger();

        boolean currentAccepted=
            registry
                .withCurrentMutationOwnershipIfCurrent(
                    npc,
                    ()->{
                        require(
                            registry.byId(
                                npc.id
                            )==npc,
                            "reentrant current lookup"
                        );
                        currentCalls.incrementAndGet();
                    }
                );

        require(
            currentAccepted&&
            currentCalls.get()==1,
            "current exact NPC ownership"
        );

        WorldNpc staleSameId=
            new WorldNpc(
                npc.id,
                npc.definitionId,
                npc.x(),
                npc.y(),
                npc.plane(),
                npc.ownerId,
                npc.sourceItemId
            );

        AtomicBoolean staleActionRan=
            new AtomicBoolean();

        boolean staleAccepted=
            registry
                .withCurrentMutationOwnershipIfCurrent(
                    staleSameId,
                    ()->
                        staleActionRan.set(true)
                );

        require(
            !staleAccepted&&
            !staleActionRan.get(),
            "stale exact-object ownership accepted"
        );

        ExecutorService workers=
            Executors.newFixedThreadPool(2);

        try{
            assertRemoveLinearized(
                registry,
                npc,
                workers
            );

            assertReplaceLinearized(
                registry,
                workers
            );

            assertActionFailureSafe(
                registry
            );
        }finally{
            workers.shutdownNow();

            require(
                workers.awaitTermination(
                    5L,
                    TimeUnit.SECONDS
                ),
                "worker shutdown"
            );
        }

        System.out.println(
            "WORLD_NPC_MUTATION_OWNERSHIP_PASS "+
            "exactObject=true "+
            "removeLinearized=true "+
            "replaceLinearized=true "+
            "actionFailureSafe=true "+
            "reentrantRead=true "+
            "protocolIndependent=true"
        );
    }

    private static void assertRemoveLinearized(
        WorldNpcRegistry registry,
        WorldNpc npc,
        ExecutorService workers
    )throws Exception{
        CountDownLatch actionEntered=
            new CountDownLatch(1);
        CountDownLatch releaseAction=
            new CountDownLatch(1);
        CountDownLatch removeAttempted=
            new CountDownLatch(1);

        Future<Boolean> owner=
            workers.submit(
                ()->
                    registry
                        .withCurrentMutationOwnershipIfCurrent(
                            npc,
                            ()->{
                                actionEntered.countDown();

                                require(
                                    releaseAction.await(
                                        5L,
                                        TimeUnit.SECONDS
                                    ),
                                    "remove fixture release"
                                );

                                require(
                                    registry.byId(
                                        npc.id
                                    )==npc,
                                    "remove interleaved before owned action completed"
                                );
                            }
                        )
            );

        require(
            actionEntered.await(
                5L,
                TimeUnit.SECONDS
            ),
            "remove fixture action entry"
        );

        Future<Boolean> remove=
            workers.submit(
                ()->{
                    removeAttempted.countDown();
                    return registry.remove(
                        npc.id
                    );
                }
            );

        require(
            removeAttempted.await(
                5L,
                TimeUnit.SECONDS
            ),
            "remove fixture attempt"
        );

        requireBlocked(
            remove,
            "remove completed while ownership action held registry"
        );

        releaseAction.countDown();

        require(
            owner.get(
                5L,
                TimeUnit.SECONDS
            ),
            "owned remove action rejected"
        );

        require(
            remove.get(
                5L,
                TimeUnit.SECONDS
            )&&
            registry.byId(
                npc.id
            )==null,
            "remove did not proceed after owned action"
        );
    }

    private static void assertReplaceLinearized(
        WorldNpcRegistry registry,
        ExecutorService workers
    )throws Exception{
        EntityId ownerId=
            EntityId.next();

        WorldNpc npc=
            registry.spawnOwned(
                1490,
                3201,
                3200,
                0,
                ownerId,
                1000
            );

        CountDownLatch actionEntered=
            new CountDownLatch(1);
        CountDownLatch releaseAction=
            new CountDownLatch(1);
        CountDownLatch replaceAttempted=
            new CountDownLatch(1);

        Future<Boolean> owner=
            workers.submit(
                ()->
                    registry
                        .withCurrentMutationOwnershipIfCurrent(
                            npc,
                            ()->{
                                actionEntered.countDown();

                                require(
                                    releaseAction.await(
                                        5L,
                                        TimeUnit.SECONDS
                                    ),
                                    "replace fixture release"
                                );

                                require(
                                    registry.byId(
                                        npc.id
                                    )==npc,
                                    "replacement interleaved before owned action completed"
                                );
                            }
                        )
            );

        require(
            actionEntered.await(
                5L,
                TimeUnit.SECONDS
            ),
            "replace fixture action entry"
        );

        Future<WorldNpc> replace=
            workers.submit(
                ()->{
                    replaceAttempted.countDown();

                    return registry.replaceOwned(
                        npc,
                        1491,
                        3202,
                        3200,
                        0,
                        ownerId,
                        1001
                    );
                }
            );

        require(
            replaceAttempted.await(
                5L,
                TimeUnit.SECONDS
            ),
            "replace fixture attempt"
        );

        requireBlocked(
            replace,
            "replace completed while ownership action held registry"
        );

        releaseAction.countDown();

        require(
            owner.get(
                5L,
                TimeUnit.SECONDS
            ),
            "owned replace action rejected"
        );

        WorldNpc replacement=
            replace.get(
                5L,
                TimeUnit.SECONDS
            );

        require(
            replacement!=null&&
            registry.byId(
                npc.id
            )==null&&
            registry.byId(
                replacement.id
            )==replacement,
            "replace did not proceed after owned action"
        );
    }

    private static void assertActionFailureSafe(
        WorldNpcRegistry registry
    )throws Exception{
        WorldNpc npc=
            registry.spawn(
                1492,
                3203,
                3200,
                0
            );

        boolean failed=false;

        try{
            registry
                .withCurrentMutationOwnershipIfCurrent(
                    npc,
                    ()->{
                        throw new IllegalStateException(
                            "fixture action failure"
                        );
                    }
                );
        }catch(
            IllegalStateException expected
        ){
            failed=true;
        }

        require(
            failed&&
            registry.byId(
                npc.id
            )==npc,
            "action failure changed canonical ownership"
        );

        require(
            registry.remove(
                npc.id
            ),
            "registry monitor not released after action failure"
        );
    }

    private static void requireBlocked(
        Future<?> future,
        String label
    )throws Exception{
        try{
            future.get(
                150L,
                TimeUnit.MILLISECONDS
            );

            throw new AssertionError(
                label
            );
        }catch(
            TimeoutException expected
        ){
            // Expected: registry ownership remains held
            // through the caller action.
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private WorldNpcMutationOwnershipTest(){}
}
