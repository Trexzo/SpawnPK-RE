package spk.local;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import spk.plugin.api.PluginTask;

/**
 * R107 OFFLINE TEST ONLY: R99 checks an event's declared owner, NOT
 * arbitrary references captured inside its Runnable. Demonstrate the
 * unguarded direct-state counterexample, then exercise the narrower
 * R107 guard on World.withOpenPlayerMutationOwnershipIfCurrent.
 */
public final class R107OfflinePluginCrossActorMutationTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R107 unexpected arguments");
        R106OfflinePluginActorReplacementTest.main(new String[0]);
        proveCheckedMutationGateAndDirectReferenceGap();
        System.out.println(
            "R107_OFFLINE_PLUGIN_CROSS_ACTOR_MUTATION_AUDIT_PASS "+
            "R103ThroughR106RegressionInherited=true "+
            "taggedOwnerContextVisibleOnlyInsidePluginCallback=true "+
            "validSameActorWorldMutationAllowed=true "+
            "crossActorWorldMutationHelperDenied=true "+
            "wrongGenerationWorldMutationHelperDenied=true "+
            "nestedOwnerChangeDenied=true "+
            "unrelatedActorCanUseOwnTaggedCallback=true "+
            "ownerContextClearedBeforeNonPlayerEvent=true "+
            "legacyWorldMutationHelperUnchangedOutsideTaggedCallback=true "+
            "DIRECT_CAPTURED_REFERENCE_BYPASS_CONFIRMED=true "+
            "NOT_A_COMPLETE_PLUGIN_SANDBOX=true "+
            "noPublicPluginAPIOrProductionSessionWiring=true");
    }

    private static void proveCheckedMutationGateAndDirectReferenceGap()
            throws Exception{
        try(World world=World.isolatedForTest(60L)){
            WorldPlayer actorA=new WorldPlayer();
            WorldPlayer actorB=new WorldPlayer();
            long genA=world.registerPlayer(actorA,"r107-a");
            long genB=world.registerPlayer(actorB,"r107-b");
            require(!actorA.id().equals(actorB.id()),
                "R107 fixture actors must have distinct IDs");

            R98OfflineFinalCaptureCommandFence ingress=
                new R98OfflineFinalCaptureCommandFence(world);
            ingress.installForTest();
            R99OfflineCrossQueueRetirementFence gate=
                new R99OfflineCrossQueueRetirementFence(world,ingress);
            gate.installForTest();
            world.events().installOfflineExplicitEventProvenanceForTest();
            PluginCallbackScope scope=new PluginCallbackScope(
                ()->world.pulse().inExecutionContext(),
                ()->!world.closed());
            scope.activate();
            PluginTaskTracker plugin=new PluginTaskTracker(
                world.clock(),world.events(),()->!world.closed(),
                null,scope,()->true,(kind,error)->{
                    throw new AssertionError(
                        "R107 unexpected plugin failure "+kind,error);
                });
            plugin.activate();
            try{
                AtomicInteger sameActorMutations=new AtomicInteger();
                AtomicInteger differentActorMutations=new AtomicInteger();
                AtomicInteger rejectedMutations=new AtomicInteger();
                AtomicInteger bypassMutations=new AtomicInteger();
                AtomicInteger globalRuns=new AtomicInteger();
                AtomicReference<Throwable> escaped=new AtomicReference<>();

                PluginTask taskA=plugin.scheduleOwnedForTest(
                    1L,actorA.id(),genA,()->{
                        try{
                            require(R107OfflinePluginOwnerCall.activeForTest()&&
                                world.pulse().inExecutionContext()&&
                                Thread.holdsLock(actorA.mutationLock())&&
                                !Thread.holdsLock(actorB.mutationLock()),
                                "R107 A callback lacks exact actor lock");
                            require(world.withOpenPlayerMutationOwnershipIfCurrent(
                                actorA,genA,()->{
                                    spawn(actorA,560);
                                    sameActorMutations.incrementAndGet();
                                }),
                                "R107 checked same-actor mutation refused");

                            expectDenied(()->world
                                .withOpenPlayerMutationOwnershipIfCurrent(
                                    actorB,genB,()->{
                                        differentActorMutations.incrementAndGet();
                                        spawn(actorB,385);
                                    }),
                                "R107_PLUGIN_CROSS_ACTOR_MUTATION_DENIED");
                            rejectedMutations.incrementAndGet();

                            expectDenied(()->world
                                .withOpenPlayerMutationOwnershipIfCurrent(
                                    actorA,genA+1L,()->{
                                        sameActorMutations.incrementAndGet();
                                    }),
                                "R107_PLUGIN_CROSS_ACTOR_MUTATION_DENIED");
                            rejectedMutations.incrementAndGet();

                            expectDenied(()->{
                                R107OfflinePluginOwnerCall.runTaggedForTest(
                                    world.events(),actorB.id(),genB,
                                    differentActorMutations::incrementAndGet);
                            },"R107_PLUGIN_OWNER_DENIED nested owner change");
                            rejectedMutations.incrementAndGet();
                            require(R107OfflinePluginOwnerCall.activeForTest(),
                                "R107 nested denial lost current owner tag");

                            /*
                             * Deliberate NEGATIVE CONTROL (not a fix):
                             * direct captured WorldPlayer.bank() does not
                             * call the guarded World helper. This still
                             * mutates B while only A's lock is held.
                             * Never describe arbitrary Runnable as safe.
                             */
                            require(!Thread.holdsLock(actorB.mutationLock()),
                                "R107 negative control accidentally has B lock");
                            spawn(actorB,385);
                            bypassMutations.incrementAndGet();
                        }catch(Throwable failure){
                            escaped.compareAndSet(null,failure);
                            throw new IllegalStateException(
                                "R107 actor A callback failed",failure);
                        }
                    });

                PluginTask taskB=plugin.scheduleOwnedForTest(
                    2L,actorB.id(),genB,()->{
                        try{
                            require(R107OfflinePluginOwnerCall.activeForTest()&&
                                world.pulse().inExecutionContext()&&
                                Thread.holdsLock(actorB.mutationLock()),
                                "R107 B callback lacks exact actor lock");
                            require(world.withOpenPlayerMutationOwnershipIfCurrent(
                                actorB,genB,()->{
                                    spawn(actorB,565);
                                    differentActorMutations.incrementAndGet();
                                }),
                                "R107 checked B actor mutation refused");
                        }catch(Throwable failure){
                            escaped.compareAndSet(null,failure);
                            throw new IllegalStateException(
                                "R107 actor B callback failed",failure);
                        }
                    });

                world.events().scheduleExplicitNonPlayerForTest(
                    3L,"R107 isolated provenance-leak diagnostic",()->{
                        if(R107OfflinePluginOwnerCall.activeForTest())
                            escaped.compareAndSet(null,new AssertionError(
                                "R107 owner context leaked to nonplayer event"));
                        globalRuns.incrementAndGet();
                    });
                world.start();
                await(()->globalRuns.get()==1&&
                    !taskA.active()&&!taskB.active()&&
                    plugin.size()==0&&world.events().size()==0,
                    "R107 callbacks or nonplayer diagnostic did not finish");
                if(escaped.get()!=null)
                    throw new AssertionError(
                        "R107 callback crashed",escaped.get());
                require(sameActorMutations.get()==1&&
                    differentActorMutations.get()==1&&
                    rejectedMutations.get()==3&&
                    bypassMutations.get()==1&&globalRuns.get()==1&&
                    gate.deniedEventsForTest()==0,
                    "R107 checked mutation guard/negative control mismatch");
                require(actorB.bank().inventoryCount(385)==1&&
                    actorB.bank().inventoryCount(565)==1&&
                    actorA.bank().inventoryCount(560)==1,
                    "R107 direct B bypass or permitted own writes missing");
                require(!R107OfflinePluginOwnerCall.activeForTest(),
                    "R107 owner context remained on test thread");
                // Outside an owner-tagged callback the older helper
                // retains its prior API and behavior.
                require(world.withOpenPlayerMutationOwnershipIfCurrent(
                    actorB,genB,()->{
                        require(Thread.holdsLock(actorB.mutationLock()),
                            "R107 ordinary World ownership lock lost");
                    }),
                    "R107 unrelated legacy World mutation path blocked");
                System.out.println(
                    "R107_CHECKED_MUTATION_GUARD_PASS "+
                    "ownActorWrites=2 "+
                    "checkedCrossActorWrites=0 "+
                    "crossActorOrGenerationDenied=3 "+
                    "directCapturedActorBWriteObserved=1 "+
                    "nonPlayerContextLeak=0 "+
                    "inheritedUntaggedWorldBehaviorPreserved=true "+
                    "arbitraryRunnableIsolationNOTProven=true");
            }finally{
                plugin.close();
                scope.close();
            }
        }
    }

    @FunctionalInterface interface Checked {
        void run()throws Exception;
    }

    private static void expectDenied(Checked action,String token)
            throws Exception{
        try{action.run();}
        catch(IllegalStateException refused){
            if(refused.getMessage()!=null&&
                refused.getMessage().contains(token))
                return;
            throw new AssertionError("R107 wrong denial",refused);
        }
        throw new AssertionError("R107 expected denial missing "+token);
    }

    private static void spawn(WorldPlayer actor,int item)
            throws IOException{
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(
                new int[]{1,2,3,4}));
        String result=actor.bank().spawnItem(item,1,writer);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R107 item mutation rejected "+result);
    }

    private static void await(BooleanSupplier condition,String message)
            throws InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
        while(!condition.getAsBoolean()){
            require(System.nanoTime()<deadline,message);
            Thread.sleep(15L);
        }
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private R107OfflinePluginCrossActorMutationTest(){}
}
