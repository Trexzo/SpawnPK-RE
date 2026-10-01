package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class LocalMonsterSpawnerCallbackLinearizationTest {
    private static final String OWNER="callback-linearization-owner";
    private static final String POLICY=
        "CUSTOM_LOCALLAB_CALLBACK_LINEARIZATION_POLICY";
    private static final String CATALOG=
        "CUSTOM_LOCALLAB_CALLBACK_LINEARIZATION_CATALOG";

    public static void main(String[] args)throws Exception{
        unregisterWaitsForAdmittedCallback();
        closeWaitsForAdmittedCallback();
        queuedCallbackRejectedAfterClosePublication();
        sessionCloseUnregisterWaits();
        sessionCloseWorldCloseWaits();
        queuedSessionCloseRejectedAfterClosePublication();
        factoryCreateUnregisterWaits();
        factoryCreateWorldCloseWaits();
        queuedFactoryCreateRejectedAfterClosePublication();
        factoryCreateRejectsStaleGeneration();
        widgetAndOpenRejectStaleGeneration();
        widgetAndOpenRejectClosedWorld();
        unregisterWaitsForAdmittedWidgetTransaction();
        queuedWidgetRejectedAfterClosePublication();
        uiCloseWaitsForAdmittedWidgetTransaction();
        uiCloseWinningFirstRejectsWidget();

        System.out.println(
            "MONSTER_SPAWNER_CALLBACK_LINEARIZATION_PASS "+
            "unregisterWaits=true "+
            "closeWaits=true "+
            "queuedAfterCloseRejected=true "+
            "exactGeneration=true "+
            "callbackOnce=true "+
            "sessionCloseUnregisterWaits=true "+
            "sessionCloseCloseWaits=true "+
            "sessionCloseQueuedAfterCloseRejected=true "+
            "factoryCreateUnregisterWaits=true "+
            "factoryCreateCloseWaits=true "+
            "factoryCreateQueuedAfterCloseRejected=true "+
            "factoryCreateExactGeneration=true "+
            "widgetTransactionExactGeneration=true "+
            "widgetTransactionClosedWorld=true "+
            "commandOpenExactGeneration=true "+
            "commandOpenClosedWorld=true "+
            "widgetUnregisterWaits=true "+
            "widgetQueuedAfterCloseRejected=true "+
            "uiCloseWaitsForAdmittedWidget=true "+
            "postUiCloseWidgetNoop=true "+
            "uiCloseWinningFirst=true"
        );
    }

    private static void unregisterWaitsForAdmittedCallback()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);
        Fixture fixture=new Fixture(world);

        CountDownLatch callbackEntered=new CountDownLatch(1);
        CountDownLatch releaseCallback=new CountDownLatch(1);
        CountDownLatch unregisterDone=new CountDownLatch(1);

        Throwable[] callbackFailure={null};
        Throwable[] unregisterFailure={null};
        boolean[] unregisterResult={false};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingFactory(
                callbackEntered,
                releaseCallback,
                callbackCalls
            );

        Thread callback=
            new Thread(
                ()->{
                    try{
                        LocalSession.forwardMonsterSpawnerUiResult(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER,
                            fixture.result,
                            fixture.writer,
                            "[callback-linearization-unregister] "
                        );
                    }catch(Throwable failure){
                        callbackFailure[0]=failure;
                    }
                },
                "monster-callback-unregister"
            );

        Thread unregister=
            new Thread(
                ()->{
                    try{
                        unregisterResult[0]=
                            world.unregisterPlayer(
                                player,
                                generation
                            );
                    }catch(Throwable failure){
                        unregisterFailure[0]=failure;
                    }finally{
                        unregisterDone.countDown();
                    }
                },
                "monster-callback-unregister-owner"
            );

        try{
            callback.start();

            await(
                callbackEntered,
                "callback did not enter before unregister"
            );

            unregister.start();

            awaitBlocked(
                unregister,
                "unregister did not block behind admitted callback"
            );

            if(unregisterDone.getCount()==0L)
                throw new AssertionError(
                    "unregister completed while callback still owned mutation boundary"
                );

            releaseCallback.countDown();

            join(
                callback,
                "callback did not complete after release"
            );
            join(
                unregister,
                "unregister did not complete after callback release"
            );

            require(
                callbackFailure[0]==null&&
                unregisterFailure[0]==null&&
                unregisterResult[0]&&
                callbackCalls[0]==1&&
                !world.players().owns(
                    player,
                    generation
                ),
                "unregister linearization result"
            );
        }finally{
            releaseCallback.countDown();

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void closeWaitsForAdmittedCallback()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);
        Fixture fixture=new Fixture(world);

        CountDownLatch callbackEntered=new CountDownLatch(1);
        CountDownLatch releaseCallback=new CountDownLatch(1);
        CountDownLatch closeDone=new CountDownLatch(1);

        Throwable[] callbackFailure={null};
        Throwable[] closeFailure={null};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingFactory(
                callbackEntered,
                releaseCallback,
                callbackCalls
            );

        Thread callback=
            new Thread(
                ()->{
                    try{
                        LocalSession.forwardMonsterSpawnerUiResult(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER,
                            fixture.result,
                            fixture.writer,
                            "[callback-linearization-close] "
                        );
                    }catch(Throwable failure){
                        callbackFailure[0]=failure;
                    }
                },
                "monster-callback-close"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }finally{
                        closeDone.countDown();
                    }
                },
                "monster-callback-world-close"
            );

        try{
            callback.start();

            await(
                callbackEntered,
                "callback did not enter before World close"
            );

            closer.start();

            awaitClosed(
                world,
                "World close flag was not published"
            );

            awaitBlocked(
                closer,
                "World close did not wait behind admitted callback"
            );

            if(closeDone.getCount()==0L)
                throw new AssertionError(
                    "World close completed while admitted callback was still blocked"
                );

            releaseCallback.countDown();

            join(
                callback,
                "callback did not complete after close release"
            );
            join(
                closer,
                "World close did not complete after callback release"
            );

            require(
                callbackFailure[0]==null&&
                closeFailure[0]==null&&
                callbackCalls[0]==1&&
                world.closed()&&
                closeDone.getCount()==0L,
                "World close linearization result"
            );
        }finally{
            releaseCallback.countDown();
            world.close();
        }
    }

    private static void queuedCallbackRejectedAfterClosePublication()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);
        Fixture fixture=new Fixture(world);

        CountDownLatch lifecycleHeld=new CountDownLatch(1);
        CountDownLatch releaseLifecycle=new CountDownLatch(1);

        Throwable[] blockerFailure={null};
        Throwable[] callbackFailure={null};
        Throwable[] closeFailure={null};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    return fixture.ui;
                }

                @Override public void onCommittedResult(
                    World callbackWorld,
                    WorldPlayer callbackPlayer,
                    String canonicalUsername,
                    LocalMonsterSpawnerUiHandler.Result result,
                    ServerPacketWriter writer,
                    String tag
                ){
                    callbackCalls[0]++;
                }
            };

        Thread blocker=
            new Thread(
                ()->{
                    try{
                        world.withOpenLifecycleOwnership(
                            ()->{
                                lifecycleHeld.countDown();

                                if(!releaseLifecycle.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "lifecycle release timeout"
                                    );
                            }
                        );
                    }catch(Throwable failure){
                        blockerFailure[0]=failure;
                    }
                },
                "monster-callback-lifecycle-blocker"
            );

        Thread callback=
            new Thread(
                ()->{
                    try{
                        LocalSession.forwardMonsterSpawnerUiResult(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER,
                            fixture.result,
                            fixture.writer,
                            "[callback-linearization-queued] "
                        );
                    }catch(Throwable failure){
                        callbackFailure[0]=failure;
                    }
                },
                "monster-callback-queued"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }
                },
                "monster-callback-queued-close"
            );

        try{
            blocker.start();

            await(
                lifecycleHeld,
                "lifecycle blocker did not enter"
            );

            callback.start();

            awaitBlocked(
                callback,
                "queued callback did not wait for lifecycle ownership"
            );

            closer.start();

            awaitClosed(
                world,
                "queued-callback World close flag was not published"
            );

            releaseLifecycle.countDown();

            join(
                blocker,
                "lifecycle blocker did not exit"
            );
            join(
                callback,
                "queued callback did not terminate"
            );
            join(
                closer,
                "queued-callback World close did not terminate"
            );

            require(
                blockerFailure[0]==null&&
                closeFailure[0]==null&&
                callbackFailure[0] instanceof IllegalStateException&&
                callbackCalls[0]==0&&
                world.closed(),
                "queued callback crossed World terminal publication"
            );
        }finally{
            releaseLifecycle.countDown();
            world.close();
        }
    }

    private static void sessionCloseUnregisterWaits()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);

        CountDownLatch callbackEntered=new CountDownLatch(1);
        CountDownLatch releaseCallback=new CountDownLatch(1);
        CountDownLatch unregisterDone=new CountDownLatch(1);

        Throwable[] callbackFailure={null};
        Throwable[] unregisterFailure={null};
        boolean[] unregisterResult={false};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingSessionCloseFactory(
                callbackEntered,
                releaseCallback,
                callbackCalls
            );

        Thread callback=
            new Thread(
                ()->{
                    try{
                        LocalSession.notifyMonsterSpawnerSessionClosed(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER
                        );
                    }catch(Throwable failure){
                        callbackFailure[0]=failure;
                    }
                },
                "monster-session-close-unregister"
            );

        Thread unregister=
            new Thread(
                ()->{
                    try{
                        unregisterResult[0]=
                            world.unregisterPlayer(
                                player,
                                generation
                            );
                    }catch(Throwable failure){
                        unregisterFailure[0]=failure;
                    }finally{
                        unregisterDone.countDown();
                    }
                },
                "monster-session-close-unregister-owner"
            );

        try{
            callback.start();

            await(
                callbackEntered,
                "session-close callback did not enter before unregister"
            );

            unregister.start();

            awaitBlocked(
                unregister,
                "unregister did not block behind admitted session-close callback"
            );

            if(unregisterDone.getCount()==0L)
                throw new AssertionError(
                    "unregister completed while session-close callback owned mutation boundary"
                );

            releaseCallback.countDown();

            join(
                callback,
                "session-close callback did not complete after release"
            );
            join(
                unregister,
                "unregister did not complete after session-close callback release"
            );

            require(
                callbackFailure[0]==null&&
                unregisterFailure[0]==null&&
                unregisterResult[0]&&
                callbackCalls[0]==1&&
                !world.players().owns(
                    player,
                    generation
                ),
                "session-close unregister linearization result"
            );
        }finally{
            releaseCallback.countDown();

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void sessionCloseWorldCloseWaits()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);

        CountDownLatch callbackEntered=new CountDownLatch(1);
        CountDownLatch releaseCallback=new CountDownLatch(1);
        CountDownLatch closeDone=new CountDownLatch(1);

        Throwable[] callbackFailure={null};
        Throwable[] closeFailure={null};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingSessionCloseFactory(
                callbackEntered,
                releaseCallback,
                callbackCalls
            );

        Thread callback=
            new Thread(
                ()->{
                    try{
                        LocalSession.notifyMonsterSpawnerSessionClosed(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER
                        );
                    }catch(Throwable failure){
                        callbackFailure[0]=failure;
                    }
                },
                "monster-session-close-world-close"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }finally{
                        closeDone.countDown();
                    }
                },
                "monster-session-close-world-closer"
            );

        try{
            callback.start();

            await(
                callbackEntered,
                "session-close callback did not enter before World close"
            );

            closer.start();

            awaitClosed(
                world,
                "session-close World terminal flag was not published"
            );

            awaitBlocked(
                closer,
                "World close did not wait behind admitted session-close callback"
            );

            if(closeDone.getCount()==0L)
                throw new AssertionError(
                    "World close completed while session-close callback was still admitted"
                );

            releaseCallback.countDown();

            join(
                callback,
                "session-close callback did not finish after release"
            );
            join(
                closer,
                "World close did not finish after session-close callback release"
            );

            require(
                callbackFailure[0]==null&&
                closeFailure[0]==null&&
                callbackCalls[0]==1&&
                world.closed()&&
                closeDone.getCount()==0L,
                "session-close World-close linearization result"
            );
        }finally{
            releaseCallback.countDown();
            world.close();
        }
    }

    private static void queuedSessionCloseRejectedAfterClosePublication()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);

        CountDownLatch lifecycleHeld=new CountDownLatch(1);
        CountDownLatch releaseLifecycle=new CountDownLatch(1);

        Throwable[] blockerFailure={null};
        Throwable[] callbackFailure={null};
        Throwable[] closeFailure={null};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    return null;
                }

                @Override public void onSessionClosed(
                    World callbackWorld,
                    WorldPlayer callbackPlayer,
                    long expectedGeneration,
                    String canonicalUsername
                ){
                    callbackCalls[0]++;
                }
            };

        Thread blocker=
            new Thread(
                ()->{
                    try{
                        world.withOpenLifecycleOwnership(
                            ()->{
                                lifecycleHeld.countDown();

                                if(!releaseLifecycle.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "session-close lifecycle release timeout"
                                    );
                            }
                        );
                    }catch(Throwable failure){
                        blockerFailure[0]=failure;
                    }
                },
                "monster-session-close-lifecycle-blocker"
            );

        Thread callback=
            new Thread(
                ()->{
                    try{
                        LocalSession.notifyMonsterSpawnerSessionClosed(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER
                        );
                    }catch(Throwable failure){
                        callbackFailure[0]=failure;
                    }
                },
                "monster-session-close-queued"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }
                },
                "monster-session-close-queued-world-close"
            );

        try{
            blocker.start();

            await(
                lifecycleHeld,
                "session-close lifecycle blocker did not enter"
            );

            callback.start();

            awaitBlocked(
                callback,
                "queued session-close callback did not wait for lifecycle ownership"
            );

            closer.start();

            awaitClosed(
                world,
                "queued session-close World terminal flag was not published"
            );

            releaseLifecycle.countDown();

            join(
                blocker,
                "session-close lifecycle blocker did not exit"
            );
            join(
                callback,
                "queued session-close callback did not terminate"
            );
            join(
                closer,
                "queued session-close World close did not terminate"
            );

            require(
                blockerFailure[0]==null&&
                closeFailure[0]==null&&
                callbackFailure[0] instanceof IllegalStateException&&
                callbackCalls[0]==0&&
                world.closed(),
                "queued session-close callback crossed World terminal publication"
            );
        }finally{
            releaseLifecycle.countDown();
            world.close();
        }
    }

    private static void factoryCreateUnregisterWaits()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);
        Fixture fixture=new Fixture(world);

        CountDownLatch factoryEntered=new CountDownLatch(1);
        CountDownLatch releaseFactory=new CountDownLatch(1);
        CountDownLatch unregisterDone=new CountDownLatch(1);

        Throwable[] factoryFailure={null};
        Throwable[] unregisterFailure={null};
        boolean[] unregisterResult={false};
        LocalMonsterSpawnerUiHandler[] resolved={null};
        int[] createCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingCreateFactory(
                fixture.ui,
                factoryEntered,
                releaseFactory,
                createCalls
            );

        Thread resolver=
            new Thread(
                ()->{
                    try{
                        resolved[0]=
                            LocalSession.resolveMonsterSpawnerUiAfterLogin(
                                factory,
                                world,
                                player,
                                generation,
                                OWNER
                            );
                    }catch(Throwable failure){
                        factoryFailure[0]=failure;
                    }
                },
                "monster-factory-unregister"
            );

        Thread unregister=
            new Thread(
                ()->{
                    try{
                        unregisterResult[0]=
                            world.unregisterPlayer(
                                player,
                                generation
                            );
                    }catch(Throwable failure){
                        unregisterFailure[0]=failure;
                    }finally{
                        unregisterDone.countDown();
                    }
                },
                "monster-factory-unregister-owner"
            );

        try{
            resolver.start();

            await(
                factoryEntered,
                "factory create did not enter before unregister"
            );

            unregister.start();

            awaitBlocked(
                unregister,
                "unregister did not block behind admitted factory create"
            );

            if(unregisterDone.getCount()==0L)
                throw new AssertionError(
                    "unregister completed while factory create owned mutation boundary"
                );

            releaseFactory.countDown();

            join(
                resolver,
                "factory create did not complete after release"
            );
            join(
                unregister,
                "unregister did not complete after factory release"
            );

            require(
                factoryFailure[0]==null&&
                unregisterFailure[0]==null&&
                unregisterResult[0]&&
                resolved[0]==fixture.ui&&
                createCalls[0]==1&&
                !world.players().owns(
                    player,
                    generation
                ),
                "factory/unregister linearization result"
            );
        }finally{
            releaseFactory.countDown();

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void factoryCreateWorldCloseWaits()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);
        Fixture fixture=new Fixture(world);

        CountDownLatch factoryEntered=new CountDownLatch(1);
        CountDownLatch releaseFactory=new CountDownLatch(1);
        CountDownLatch closeDone=new CountDownLatch(1);

        Throwable[] factoryFailure={null};
        Throwable[] closeFailure={null};
        LocalMonsterSpawnerUiHandler[] resolved={null};
        int[] createCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingCreateFactory(
                fixture.ui,
                factoryEntered,
                releaseFactory,
                createCalls
            );

        Thread resolver=
            new Thread(
                ()->{
                    try{
                        resolved[0]=
                            LocalSession.resolveMonsterSpawnerUiAfterLogin(
                                factory,
                                world,
                                player,
                                generation,
                                OWNER
                            );
                    }catch(Throwable failure){
                        factoryFailure[0]=failure;
                    }
                },
                "monster-factory-world-close"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }finally{
                        closeDone.countDown();
                    }
                },
                "monster-factory-world-closer"
            );

        try{
            resolver.start();

            await(
                factoryEntered,
                "factory create did not enter before World close"
            );

            closer.start();

            awaitClosed(
                world,
                "factory-create World terminal flag was not published"
            );

            awaitBlocked(
                closer,
                "World close did not wait behind admitted factory create"
            );

            if(closeDone.getCount()==0L)
                throw new AssertionError(
                    "World close completed while factory create was admitted"
                );

            releaseFactory.countDown();

            join(
                resolver,
                "factory create did not finish after release"
            );
            join(
                closer,
                "World close did not finish after factory release"
            );

            require(
                factoryFailure[0]==null&&
                closeFailure[0]==null&&
                resolved[0]==fixture.ui&&
                createCalls[0]==1&&
                world.closed()&&
                closeDone.getCount()==0L,
                "factory/World-close linearization result"
            );
        }finally{
            releaseFactory.countDown();
            world.close();
        }
    }

    private static void queuedFactoryCreateRejectedAfterClosePublication()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,OWNER);
        Fixture fixture=new Fixture(world);

        CountDownLatch lifecycleHeld=new CountDownLatch(1);
        CountDownLatch releaseLifecycle=new CountDownLatch(1);

        Throwable[] blockerFailure={null};
        Throwable[] factoryFailure={null};
        Throwable[] closeFailure={null};
        int[] createCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    createCalls[0]++;
                    return fixture.ui;
                }
            };

        Thread blocker=
            new Thread(
                ()->{
                    try{
                        world.withOpenLifecycleOwnership(
                            ()->{
                                lifecycleHeld.countDown();

                                if(!releaseLifecycle.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "factory lifecycle release timeout"
                                    );
                            }
                        );
                    }catch(Throwable failure){
                        blockerFailure[0]=failure;
                    }
                },
                "monster-factory-lifecycle-blocker"
            );

        Thread resolver=
            new Thread(
                ()->{
                    try{
                        LocalSession.resolveMonsterSpawnerUiAfterLogin(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER
                        );
                    }catch(Throwable failure){
                        factoryFailure[0]=failure;
                    }
                },
                "monster-factory-queued"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }
                },
                "monster-factory-queued-world-close"
            );

        try{
            blocker.start();

            await(
                lifecycleHeld,
                "factory lifecycle blocker did not enter"
            );

            resolver.start();

            awaitBlocked(
                resolver,
                "queued factory create did not wait for lifecycle ownership"
            );

            closer.start();

            awaitClosed(
                world,
                "queued factory World terminal flag was not published"
            );

            releaseLifecycle.countDown();

            join(
                blocker,
                "factory lifecycle blocker did not exit"
            );
            join(
                resolver,
                "queued factory create did not terminate"
            );
            join(
                closer,
                "queued factory World close did not terminate"
            );

            require(
                blockerFailure[0]==null&&
                closeFailure[0]==null&&
                factoryFailure[0] instanceof IllegalStateException&&
                createCalls[0]==0&&
                world.closed(),
                "queued factory create crossed World terminal publication"
            );
        }finally{
            releaseLifecycle.countDown();
            world.close();
        }
    }

    private static void factoryCreateRejectsStaleGeneration()
        throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generationA=world.registerPlayer(player,OWNER);
        Fixture fixture=new Fixture(world);
        int[] createCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    createCalls[0]++;
                    return fixture.ui;
                }
            };

        try{
            require(
                world.unregisterPlayer(
                    player,
                    generationA
                ),
                "factory stale generation unregister"
            );

            long generationB=
                world.registerPlayer(
                    player,
                    OWNER
                );

            boolean staleRejected=false;
            try{
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    factory,
                    world,
                    player,
                    generationA,
                    OWNER
                );
            }catch(IllegalStateException expected){
                staleRejected=true;
            }

            require(
                staleRejected&&
                createCalls[0]==0,
                "stale generation invoked Monster Spawner factory"
            );

            LocalMonsterSpawnerUiHandler current=
                LocalSession.resolveMonsterSpawnerUiAfterLogin(
                    factory,
                    world,
                    player,
                    generationB,
                    OWNER
                );

            require(
                current==fixture.ui&&
                createCalls[0]==1,
                "current generation did not invoke Monster Spawner factory"
            );

            require(
                world.unregisterPlayer(
                    player,
                    generationB
                ),
                "factory current generation unregister"
            );
        }finally{
            world.close();
        }
    }

    private static void unregisterWaitsForAdmittedWidgetTransaction()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                OWNER
            );
        Fixture fixture=
            new Fixture(
                world
            );
        CountDownLatch callbackEntered=
            new CountDownLatch(
                1
            );
        CountDownLatch releaseCallback=
            new CountDownLatch(
                1
            );
        CountDownLatch unregisterDone=
            new CountDownLatch(
                1
            );
        Throwable[] widgetFailure={null};
        Throwable[] unregisterFailure={null};
        boolean[] unregisterResult={false};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingFactory(
                callbackEntered,
                releaseCallback,
                callbackCalls
            );

        Thread widget=
            new Thread(
                ()->{
                    try{
                        LocalSessionUiActionHandler.MonsterSpawnerDispatch
                            dispatch=
                                LocalSession
                                    .dispatchMonsterSpawnerWidgetForCurrentSession(
                                        factory,
                                        world,
                                        player,
                                        generation,
                                        OWNER,
                                        fixture.ui,
                                        MonsterSpawnerPresentation
                                            .TOGGLE_WIDGET,
                                        fixture.writer,
                                        "[widget-unregister] "
                                    );

                        require(
                            dispatch.admitted&&
                            dispatch.result!=null&&
                            dispatch.result.status==
                                LocalMonsterSpawnerUiHandler
                                    .Status.ACTIVATED,
                            "admitted widget transaction result"
                        );
                    }catch(Throwable failure){
                        widgetFailure[0]=failure;
                    }
                },
                "monster-widget-unregister"
            );

        Thread unregister=
            new Thread(
                ()->{
                    try{
                        unregisterResult[0]=
                            world.unregisterPlayer(
                                player,
                                generation
                            );
                    }catch(Throwable failure){
                        unregisterFailure[0]=failure;
                    }finally{
                        unregisterDone.countDown();
                    }
                },
                "monster-widget-unregister-owner"
            );

        try{
            widget.start();

            await(
                callbackEntered,
                "widget callback did not enter before unregister"
            );

            unregister.start();

            awaitBlocked(
                unregister,
                "unregister did not wait behind admitted widget transaction"
            );

            if(unregisterDone.getCount()==0L)
                throw new AssertionError(
                    "unregister completed while widget transaction was admitted"
                );

            releaseCallback.countDown();

            join(
                widget,
                "widget transaction did not finish after release"
            );
            join(
                unregister,
                "unregister did not finish after widget release"
            );

            MonsterSpawnerService.SessionSnapshot after=
                fixture.spawner.getSession(
                    OWNER
                );

            require(
                widgetFailure[0]==null&&
                unregisterFailure[0]==null&&
                unregisterResult[0]&&
                callbackCalls[0]==1&&
                after.active&&
                after.remainingSpawnBudget==1&&
                !world.players().owns(
                    player,
                    generation
                ),
                "widget/unregister transaction linearization"
            );
        }finally{
            releaseCallback.countDown();

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void queuedWidgetRejectedAfterClosePublication()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                OWNER
            );
        Fixture fixture=
            new Fixture(
                world
            );
        CountDownLatch lifecycleHeld=
            new CountDownLatch(
                1
            );
        CountDownLatch releaseLifecycle=
            new CountDownLatch(
                1
            );
        Throwable[] blockerFailure={null};
        Throwable[] widgetFailure={null};
        Throwable[] closeFailure={null};
        LocalSessionUiActionHandler.MonsterSpawnerDispatch[]
            dispatch={null};
        int[] callbackCalls={0};
        int wireBefore=
            fixture.wire.size();
        MonsterSpawnerService.SessionSnapshot before=
            fixture.spawner.getSession(
                OWNER
            );

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    return fixture.ui;
                }

                @Override public void onCommittedResult(
                    World callbackWorld,
                    WorldPlayer callbackPlayer,
                    String canonicalUsername,
                    LocalMonsterSpawnerUiHandler.Result result,
                    ServerPacketWriter writer,
                    String tag
                ){
                    callbackCalls[0]++;
                }
            };

        Thread blocker=
            new Thread(
                ()->{
                    try{
                        world.withOpenLifecycleOwnership(
                            ()->{
                                lifecycleHeld.countDown();

                                if(!releaseLifecycle.await(
                                        5L,
                                        TimeUnit.SECONDS))
                                    throw new AssertionError(
                                        "widget lifecycle release timeout"
                                    );
                            }
                        );
                    }catch(Throwable failure){
                        blockerFailure[0]=failure;
                    }
                },
                "monster-widget-lifecycle-blocker"
            );

        Thread widget=
            new Thread(
                ()->{
                    try{
                        dispatch[0]=
                            LocalSession
                                .dispatchMonsterSpawnerWidgetForCurrentSession(
                                    factory,
                                    world,
                                    player,
                                    generation,
                                    OWNER,
                                    fixture.ui,
                                    MonsterSpawnerPresentation
                                        .TOGGLE_WIDGET,
                                    fixture.writer,
                                    "[widget-queued-close] "
                                );
                    }catch(Throwable failure){
                        widgetFailure[0]=failure;
                    }
                },
                "monster-widget-queued-close"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        world.close();
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }
                },
                "monster-widget-queued-closer"
            );

        try{
            blocker.start();

            await(
                lifecycleHeld,
                "widget lifecycle blocker did not enter"
            );

            widget.start();

            awaitBlocked(
                widget,
                "queued widget did not wait for lifecycle ownership"
            );

            closer.start();

            awaitClosed(
                world,
                "queued-widget World terminal flag was not published"
            );

            releaseLifecycle.countDown();

            join(
                blocker,
                "widget lifecycle blocker did not exit"
            );
            join(
                widget,
                "queued widget did not terminate"
            );
            join(
                closer,
                "queued-widget World close did not terminate"
            );

            MonsterSpawnerService.SessionSnapshot after=
                fixture.spawner.getSession(
                    OWNER
                );

            require(
                blockerFailure[0]==null&&
                widgetFailure[0]==null&&
                closeFailure[0]==null&&
                dispatch[0]!=null&&
                !dispatch[0].admitted&&
                dispatch[0].result==null&&
                callbackCalls[0]==0&&
                !before.active&&
                !after.active&&
                before.remainingSpawnBudget==
                    after.remainingSpawnBudget&&
                before.selectedRowIndex.equals(
                    after.selectedRowIndex
                )&&
                fixture.wire.size()==wireBefore&&
                world.closed(),
                "queued widget crossed World terminal publication"
            );
        }finally{
            releaseLifecycle.countDown();
            world.close();
        }
    }

    private static void uiCloseWaitsForAdmittedWidgetTransaction()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                OWNER
            );
        Fixture fixture=
            new Fixture(
                world
            );
        boolean[] uiOpen={true};
        CountDownLatch callbackEntered=
            new CountDownLatch(
                1
            );
        CountDownLatch releaseCallback=
            new CountDownLatch(
                1
            );
        Throwable[] widgetFailure={null};
        Throwable[] closeFailure={null};
        boolean[] closeWasOpen={false};
        int[] callbackCalls={0};
        LocalSessionUiActionHandler.MonsterSpawnerDispatch[]
            firstDispatch={null};

        LocalSession.MonsterSpawnerUiFactory factory=
            blockingFactory(
                callbackEntered,
                releaseCallback,
                callbackCalls
            );

        LocalSessionUiActionHandler.MonsterSpawnerWidgetAction
            widgetAction=
                ()->{
                    if(!uiOpen[0])
                        return LocalSessionUiActionHandler
                            .MonsterSpawnerDispatch
                            .closedUiNoop();

                    return LocalSessionUiActionHandler
                        .MonsterSpawnerDispatch
                        .admitted(
                            fixture.ui.handle(
                                MonsterSpawnerPresentation
                                    .TOGGLE_WIDGET,
                                fixture.writer
                            )
                        );
                };

        Thread widget=
            new Thread(
                ()->{
                    try{
                        firstDispatch[0]=
                            LocalSession
                                .dispatchMonsterSpawnerWidgetForCurrentSession(
                                    factory,
                                    world,
                                    player,
                                    generation,
                                    OWNER,
                                    fixture.ui,
                                    MonsterSpawnerPresentation
                                        .TOGGLE_WIDGET,
                                    fixture.writer,
                                    "[widget-ui-close] ",
                                    widgetAction
                                );
                    }catch(Throwable failure){
                        widgetFailure[0]=failure;
                    }
                },
                "monster-widget-ui-close"
            );

        Thread closer=
            new Thread(
                ()->{
                    try{
                        closeWasOpen[0]=
                            LocalSession
                                .revokeMonsterSpawnerForCurrentSession(
                                    world,
                                    player,
                                    generation,
                                    ()->{
                                        boolean wasOpen=
                                            uiOpen[0];
                                        uiOpen[0]=false;
                                        return wasOpen;
                                    }
                                );
                    }catch(Throwable failure){
                        closeFailure[0]=failure;
                    }
                },
                "monster-ui-close-owner"
            );

        try{
            widget.start();

            await(
                callbackEntered,
                "widget callback did not enter before UI close"
            );

            closer.start();

            awaitBlocked(
                closer,
                "UI close did not wait behind admitted widget transaction"
            );

            require(
                uiOpen[0],
                "UI close crossed admitted widget transaction"
            );

            releaseCallback.countDown();

            join(
                widget,
                "widget did not finish before UI close"
            );
            join(
                closer,
                "UI close did not finish after admitted widget"
            );

            MonsterSpawnerService.SessionSnapshot afterFirst=
                fixture.spawner.getSession(
                    OWNER
                );

            require(
                widgetFailure[0]==null&&
                closeFailure[0]==null&&
                firstDispatch[0]!=null&&
                firstDispatch[0].admitted&&
                !firstDispatch[0].closedUiNoop&&
                firstDispatch[0].result!=null&&
                firstDispatch[0].result.status==
                    LocalMonsterSpawnerUiHandler
                        .Status.ACTIVATED&&
                callbackCalls[0]==1&&
                closeWasOpen[0]&&
                !uiOpen[0]&&
                afterFirst.active,
                "admitted widget/UI-close linearization"
            );

            int wireBeforeClosed=
                fixture.wire.size();
            MonsterSpawnerService.SessionSnapshot beforeClosed=
                fixture.spawner.getSession(
                    OWNER
                );

            LocalSessionUiActionHandler.MonsterSpawnerDispatch
                closedDispatch=
                    LocalSession
                        .dispatchMonsterSpawnerWidgetForCurrentSession(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER,
                            fixture.ui,
                            MonsterSpawnerPresentation
                                .TOGGLE_WIDGET,
                            fixture.writer,
                            "[widget-after-ui-close] ",
                            widgetAction
                        );

            MonsterSpawnerService.SessionSnapshot afterClosed=
                fixture.spawner.getSession(
                    OWNER
                );

            require(
                closedDispatch.admitted&&
                closedDispatch.closedUiNoop&&
                closedDispatch.result==null&&
                callbackCalls[0]==1&&
                fixture.wire.size()==wireBeforeClosed&&
                beforeClosed.active==
                    afterClosed.active&&
                beforeClosed.remainingSpawnBudget==
                    afterClosed.remainingSpawnBudget&&
                beforeClosed.selectedRowIndex.equals(
                    afterClosed.selectedRowIndex
                ),
                "post-close widget mutated Monster Spawner state"
            );
        }finally{
            releaseCallback.countDown();

            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void uiCloseWinningFirstRejectsWidget()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                OWNER
            );
        Fixture fixture=
            new Fixture(
                world
            );
        boolean[] uiOpen={true};
        int[] callbackCalls={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    return fixture.ui;
                }

                @Override public void onCommittedResult(
                    World callbackWorld,
                    WorldPlayer callbackPlayer,
                    String canonicalUsername,
                    LocalMonsterSpawnerUiHandler.Result result,
                    ServerPacketWriter writer,
                    String tag
                ){
                    callbackCalls[0]++;
                }
            };

        try{
            boolean closeWasOpen=
                LocalSession
                    .revokeMonsterSpawnerForCurrentSession(
                        world,
                        player,
                        generation,
                        ()->{
                            boolean wasOpen=
                                uiOpen[0];
                            uiOpen[0]=false;
                            return wasOpen;
                        }
                    );

            int wireBefore=
                fixture.wire.size();
            MonsterSpawnerService.SessionSnapshot before=
                fixture.spawner.getSession(
                    OWNER
                );

            LocalSessionUiActionHandler.MonsterSpawnerDispatch
                dispatch=
                    LocalSession
                        .dispatchMonsterSpawnerWidgetForCurrentSession(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER,
                            fixture.ui,
                            MonsterSpawnerPresentation
                                .TOGGLE_WIDGET,
                            fixture.writer,
                            "[ui-close-before-widget] ",
                            ()->{
                                if(!uiOpen[0])
                                    return LocalSessionUiActionHandler
                                        .MonsterSpawnerDispatch
                                        .closedUiNoop();

                                return LocalSessionUiActionHandler
                                    .MonsterSpawnerDispatch
                                    .admitted(
                                        fixture.ui.handle(
                                            MonsterSpawnerPresentation
                                                .TOGGLE_WIDGET,
                                            fixture.writer
                                        )
                                    );
                            }
                        );

            MonsterSpawnerService.SessionSnapshot after=
                fixture.spawner.getSession(
                    OWNER
                );

            require(
                closeWasOpen&&
                !uiOpen[0]&&
                dispatch.admitted&&
                dispatch.closedUiNoop&&
                dispatch.result==null&&
                callbackCalls[0]==0&&
                fixture.wire.size()==wireBefore&&
                before.active==
                    after.active&&
                before.remainingSpawnBudget==
                    after.remainingSpawnBudget&&
                before.selectedRowIndex.equals(
                    after.selectedRowIndex
                ),
                "UI-close-first widget admission"
            );
        }finally{
            if(world.players().owns(
                    player,
                    generation
                ))
                world.unregisterPlayer(
                    player,
                    generation
                );

            world.close();
        }
    }

    private static void widgetAndOpenRejectStaleGeneration()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                OWNER
            );
        Fixture fixture=
            new Fixture(
                world
            );
        int[] callbacks={0};
        int[] opens={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    return fixture.ui;
                }

                @Override public void onCommittedResult(
                    World callbackWorld,
                    WorldPlayer callbackPlayer,
                    String canonicalUsername,
                    LocalMonsterSpawnerUiHandler.Result result,
                    ServerPacketWriter writer,
                    String tag
                ){
                    callbacks[0]++;
                }
            };

        try{
            require(
                world.unregisterPlayer(
                    player,
                    generation
                ),
                "widget stale generation unregister"
            );

            MonsterSpawnerService.SessionSnapshot before=
                fixture.spawner.getSession(
                    OWNER
                );
            int wireBefore=
                fixture.wire.size();

            LocalSessionUiActionHandler.MonsterSpawnerDispatch
                dispatch=
                    LocalSession
                        .dispatchMonsterSpawnerWidgetForCurrentSession(
                            factory,
                            world,
                            player,
                            generation,
                            OWNER,
                            fixture.ui,
                            MonsterSpawnerPresentation
                                .TOGGLE_WIDGET,
                            fixture.writer,
                            "[widget-stale] "
                        );

            boolean opened=
                LocalSession
                    .openMonsterSpawnerForCurrentSession(
                        world,
                        player,
                        generation,
                        ()->{
                            opens[0]++;
                            fixture.ui.open(
                                fixture.writer
                            );
                            return true;
                        }
                    );

            MonsterSpawnerService.SessionSnapshot after=
                fixture.spawner.getSession(
                    OWNER
                );

            require(
                !dispatch.admitted&&
                dispatch.result==null&&
                !opened&&
                callbacks[0]==0&&
                opens[0]==0&&
                !before.active&&
                !after.active&&
                before.remainingSpawnBudget==
                    after.remainingSpawnBudget&&
                before.selectedRowIndex.equals(
                    after.selectedRowIndex
                )&&
                fixture.wire.size()==wireBefore,
                "stale generation crossed Monster Spawner widget/open admission"
            );
        }finally{
            world.close();
        }
    }

    private static void widgetAndOpenRejectClosedWorld()
        throws Exception{
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer player=
            new WorldPlayer();
        long generation=
            world.registerPlayer(
                player,
                OWNER
            );
        Fixture fixture=
            new Fixture(
                world
            );
        int[] callbacks={0};
        int[] opens={0};

        LocalSession.MonsterSpawnerUiFactory factory=
            new LocalSession.MonsterSpawnerUiFactory(){
                @Override public LocalMonsterSpawnerUiHandler create(
                    World factoryWorld,
                    WorldPlayer factoryPlayer,
                    String canonicalUsername
                ){
                    return fixture.ui;
                }

                @Override public void onCommittedResult(
                    World callbackWorld,
                    WorldPlayer callbackPlayer,
                    String canonicalUsername,
                    LocalMonsterSpawnerUiHandler.Result result,
                    ServerPacketWriter writer,
                    String tag
                ){
                    callbacks[0]++;
                }
            };

        MonsterSpawnerService.SessionSnapshot before=
            fixture.spawner.getSession(
                OWNER
            );
        int wireBefore=
            fixture.wire.size();

        world.close();

        LocalSessionUiActionHandler.MonsterSpawnerDispatch
            dispatch=
                LocalSession
                    .dispatchMonsterSpawnerWidgetForCurrentSession(
                        factory,
                        world,
                        player,
                        generation,
                        OWNER,
                        fixture.ui,
                        MonsterSpawnerPresentation
                            .TOGGLE_WIDGET,
                        fixture.writer,
                        "[widget-closed] "
                    );

        boolean opened=
            LocalSession
                .openMonsterSpawnerForCurrentSession(
                    world,
                    player,
                    generation,
                    ()->{
                        opens[0]++;
                        fixture.ui.open(
                            fixture.writer
                        );
                        return true;
                    }
                );

        MonsterSpawnerService.SessionSnapshot after=
            fixture.spawner.getSession(
                OWNER
            );

        require(
            !dispatch.admitted&&
            dispatch.result==null&&
            !opened&&
            callbacks[0]==0&&
            opens[0]==0&&
            !before.active&&
            !after.active&&
            before.remainingSpawnBudget==
                after.remainingSpawnBudget&&
            before.selectedRowIndex.equals(
                after.selectedRowIndex
            )&&
            fixture.wire.size()==wireBefore,
            "closed World crossed Monster Spawner widget/open admission"
        );
    }

    private static LocalSession.MonsterSpawnerUiFactory
        blockingCreateFactory(
            LocalMonsterSpawnerUiHandler adapter,
            CountDownLatch entered,
            CountDownLatch release,
            int[] calls
        ){
        return new LocalSession.MonsterSpawnerUiFactory(){
            @Override public LocalMonsterSpawnerUiHandler create(
                World factoryWorld,
                WorldPlayer factoryPlayer,
                String canonicalUsername
            )throws Exception{
                calls[0]++;
                entered.countDown();

                if(!release.await(
                        5L,
                        TimeUnit.SECONDS))
                    throw new AssertionError(
                        "factory create release timeout"
                    );

                return adapter;
            }
        };
    }

    private static LocalSession.MonsterSpawnerUiFactory
        blockingSessionCloseFactory(
            CountDownLatch entered,
            CountDownLatch release,
            int[] calls
        ){
        return new LocalSession.MonsterSpawnerUiFactory(){
            @Override public LocalMonsterSpawnerUiHandler create(
                World factoryWorld,
                WorldPlayer factoryPlayer,
                String canonicalUsername
            ){
                return null;
            }

            @Override public void onSessionClosed(
                World callbackWorld,
                WorldPlayer callbackPlayer,
                long expectedGeneration,
                String canonicalUsername
            )throws Exception{
                calls[0]++;
                entered.countDown();

                if(!release.await(
                        5L,
                        TimeUnit.SECONDS))
                    throw new AssertionError(
                        "session-close callback release timeout"
                    );
            }
        };
    }

    private static LocalSession.MonsterSpawnerUiFactory
        blockingFactory(
            CountDownLatch entered,
            CountDownLatch release,
            int[] calls
        ){
        return new LocalSession.MonsterSpawnerUiFactory(){
            @Override public LocalMonsterSpawnerUiHandler create(
                World factoryWorld,
                WorldPlayer factoryPlayer,
                String canonicalUsername
            ){
                return null;
            }

            @Override public void onCommittedResult(
                World callbackWorld,
                WorldPlayer callbackPlayer,
                String canonicalUsername,
                LocalMonsterSpawnerUiHandler.Result result,
                ServerPacketWriter writer,
                String tag
            )throws Exception{
                calls[0]++;
                entered.countDown();

                if(!release.await(
                        5L,
                        TimeUnit.SECONDS))
                    throw new AssertionError(
                        "callback release timeout"
                    );
            }
        };
    }

    private static final class Fixture {
        final MonsterSpawnerService spawner;
        final LocalMonsterSpawnerUiHandler ui;
        final ByteArrayOutputStream wire;
        final ServerPacketWriter writer;
        final LocalMonsterSpawnerUiHandler.Result result;

        Fixture(
            World world
        )throws Exception{
            spawner=
                new MonsterSpawnerService(
                    world.npcs()
                );

            spawner.replaceCatalog(
                Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "callback-linearization",
                        1530
                    )
                ),
                CATALOG
            );

            spawner.openSession(
                OWNER,
                POLICY
            );

            ui=
                new LocalMonsterSpawnerUiHandler(
                    spawner,
                    OWNER,
                    new LocalMonsterSpawnerUiHandler
                        .ActivationBudgetResolver(){
                        @Override public int spawnBudget(
                            LocalMonsterSpawnerUiHandler.Context context
                        ){
                            return 1;
                        }

                        @Override public String authority(){
                            return POLICY;
                        }
                    },
                    new LocalMonsterSpawnerUiHandler
                        .SelectedNpcLabelResolver(){
                        @Override public String label(
                            MonsterSpawnerService.CatalogEntry entry
                        ){
                            return "NPC-"+entry.definitionId;
                        }

                        @Override public String authority(){
                            return CATALOG;
                        }
                    }
                );

            wire=
                new ByteArrayOutputStream();

            writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            result=
                ui.handle(
                    MonsterSpawnerPresentation.rowWidget(0),
                    writer
                );

            require(
                result!=null&&
                result.status==
                    LocalMonsterSpawnerUiHandler.Status.ROW_SELECTED&&
                OWNER.equals(
                    result.session.ownerRef
                ),
                "callback fixture result"
            );
        }
    }

    private static void await(
        CountDownLatch latch,
        String label
    )throws Exception{
        if(!latch.await(
                5L,
                TimeUnit.SECONDS))
            throw new AssertionError(
                label
            );
    }

    private static void awaitBlocked(
        Thread thread,
        String label
    )throws Exception{
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(
                5L
            );

        while(System.nanoTime()<deadline){
            if(thread.getState()==
                    Thread.State.BLOCKED)
                return;

            if(!thread.isAlive())
                break;

            Thread.yield();
        }

        throw new AssertionError(
            label+
            " state="+
            thread.getState()
        );
    }

    private static void awaitClosed(
        World world,
        String label
    ){
        long deadline=
            System.nanoTime()+
            TimeUnit.SECONDS.toNanos(
                5L
            );

        while(!world.closed()&&
              System.nanoTime()<deadline)
            Thread.yield();

        if(!world.closed())
            throw new AssertionError(
                label
            );
    }

    private static void join(
        Thread thread,
        String label
    )throws Exception{
        thread.join(
            5_000L
        );

        if(thread.isAlive())
            throw new AssertionError(
                label+
                " state="+
                thread.getState()
            );
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

    private LocalMonsterSpawnerCallbackLinearizationTest(){}
}
