package spk.local;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import spk.plugin.api.PluginTask;

/**
 * R109 OFFLINE ONLY: explicit, trusted-fixture item-on-player permit is
 * distinct from arbitrary plugin code touching captured target state.
 *
 * A-tagged callback may mutate B only via a specific issued, one-use
 * permit with both actor IDs/generations checked under both actor locks.
 * Tests deliberately inherit R107's direct raw-reference bypass.
 */
public final class R109OfflineItemOnPlayerPermitTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R109 unexpected arguments");
        R108OfflineOwnerBankCapabilityTest.main(new String[0]);
        twoCurrentActorsAndExplicitNonplayerControl();
        targetGenerationAndObjectReplacement();
        System.out.println(
            "R109_OFFLINE_ITEM_ON_PLAYER_PAIR_PERMIT_PASS "+
            "R103ThroughR108RegressionInherited=true "+
            "declaredSourceAndTargetIdAndGenerationRequired=true "+
            "sourceTaggedPluginContextRequired=true "+
            "bothWorldPlayerMutationLocksHeld=true "+
            "differentTargetAndWrongItemDenied=true "+
            "validPairOnlyOneGrantNoReplay=true "+
            "reverseDirectionRequiresDifferentPermit=true "+
            "nonplayerEventCannotUseTargetPermit=true "+
            "staleTargetGenerationDenied=true "+
            "replacementTargetObjectDeniedForOldPermit=true "+
            "newPairPermitSucceedsAfterReplacement=true "+
            "R107UnmediatedCapturedReferenceBypassStillPresent=true "+
            "trustedSyntheticAuthorizationNotRealClientIntent=true "+
            "noPublicPluginOrContentApiOrProductionWorldChange=true");
    }

    private static void twoCurrentActorsAndExplicitNonplayerControl()
            throws Exception{
        try(Fixture f=new Fixture(70L)){
            WorldPlayer source=new WorldPlayer();
            WorldPlayer target=new WorldPlayer();
            WorldPlayer unrelated=new WorldPlayer();
            long sourceGen=f.world.registerPlayer(source,"r109-source");
            long targetGen=f.world.registerPlayer(target,"r109-target");
            long unrelatedGen=f.world.registerPlayer(
                unrelated,"r109-unrelated");
            R109OfflineItemOnPlayerPermit sourceToTarget=
                R109OfflineItemOnPlayerPermit.issueForTest(
                    f.world,source,sourceGen,target,targetGen,560);
            R109OfflineItemOnPlayerPermit targetToSource=
                R109OfflineItemOnPlayerPermit.issueForTest(
                    f.world,target,targetGen,source,sourceGen,565);
            R109OfflineItemOnPlayerPermit unusedForNonplayer=
                R109OfflineItemOnPlayerPermit.issueForTest(
                    f.world,source,sourceGen,unrelated,unrelatedGen,385);
            expectIllegal(()->R109OfflineItemOnPlayerPermit.issueForTest(
                f.world,source,sourceGen,source,sourceGen,560),
                "R109_PAIR_PERMIT_DENIED invalid source/target/item");
            expectDenied(()->R109OfflineItemOnPlayerPermit.issueForTest(
                f.world,source,sourceGen,target,targetGen+1L,560),
                "R109_PAIR_PERMIT_DENIED not currently owned at issue");

            expectDenied(()->sourceToTarget.grantToTargetForTest(
                target.id(),560),"R108_CAPABILITY_CONTEXT_DENIED");
            AtomicInteger granted=new AtomicInteger();
            AtomicInteger rejected=new AtomicInteger();
            AtomicInteger global=new AtomicInteger();
            AtomicReference<Throwable> callbackFailure=new AtomicReference<>();

            PluginTask a=f.plugin.scheduleOwnedForTest(
                1L,source.id(),sourceGen,()->{
                    try{
                        require(f.world.pulse().inExecutionContext()&&
                            Thread.holdsLock(source.mutationLock())&&
                            !Thread.holdsLock(target.mutationLock()),
                            "R109 source callback did not start with A only");
                        expectDenied(()->sourceToTarget.grantToTargetForTest(
                            unrelated.id(),560),
                            "R109_PAIR_PERMIT_DENIED target/item mismatch");
                        rejected.incrementAndGet();
                        expectDenied(()->sourceToTarget.grantToTargetForTest(
                            target.id(),565),
                            "R109_PAIR_PERMIT_DENIED target/item mismatch");
                        rejected.incrementAndGet();
                        expectDenied(()->targetToSource.grantToTargetForTest(
                            source.id(),565),
                            "R108_CAPABILITY_CONTEXT_DENIED");
                        rejected.incrementAndGet();

                        String result=sourceToTarget.grantToTargetForTest(
                            target.id(),560);
                        require(result.startsWith("ITEM_SPAWN_OK"),
                            "R109 A to B authorized grant failed "+result);
                        granted.incrementAndGet();
                        expectDenied(()->sourceToTarget.grantToTargetForTest(
                            target.id(),560),
                            "R109_PAIR_PERMIT_DENIED already spent");
                        rejected.incrementAndGet();
                    }catch(Throwable error){
                        callbackFailure.compareAndSet(null,error);
                        throw new IllegalStateException(
                            "R109 source callback failed",error);
                    }
                });

            PluginTask b=f.plugin.scheduleOwnedForTest(
                2L,target.id(),targetGen,()->{
                    try{
                        require(f.world.pulse().inExecutionContext()&&
                            Thread.holdsLock(target.mutationLock()),
                            "R109 target callback missing B World ownership");
                        String result=targetToSource.grantToTargetForTest(
                            source.id(),565);
                        require(result.startsWith("ITEM_SPAWN_OK"),
                            "R109 B to A independent permit failed "+result);
                        granted.incrementAndGet();
                    }catch(Throwable error){
                        callbackFailure.compareAndSet(null,error);
                        throw new IllegalStateException(
                            "R109 reverse callback failed",error);
                    }
                });
            f.world.events().scheduleExplicitNonPlayerForTest(
                3L,"R109 global event must not inherit a player permit",()->{
                    try{
                        require(!R107OfflinePluginOwnerCall.activeForTest(),
                            "R109 owner marker leaked to global event");
                        expectDenied(
                            ()->unusedForNonplayer.grantToTargetForTest(
                                unrelated.id(),385),
                            "R108_CAPABILITY_CONTEXT_DENIED");
                        rejected.incrementAndGet();
                    }catch(Throwable error){
                        callbackFailure.compareAndSet(null,error);
                    }
                    global.incrementAndGet();
                });
            f.world.start();
            await(()->global.get()==1&&!a.active()&&!b.active()&&
                f.plugin.size()==0&&f.world.events().size()==0,
                "R109 current actor tasks not drained");
            if(callbackFailure.get()!=null)
                throw new AssertionError("R109 task failed",
                    callbackFailure.get());

            require(granted.get()==2&&rejected.get()==5&&
                sourceToTarget.spentForTest()&&
                targetToSource.spentForTest()&&
                !unusedForNonplayer.spentForTest()&&
                target.bank().inventoryCount(560)==1&&
                source.bank().inventoryCount(565)==1&&
                unrelated.bank().inventoryCount(385)==0&&
                source.bank().inventoryCount(560)==0&&
                target.bank().inventoryCount(565)==0&&
                f.gate.deniedEventsForTest()==0,
                "R109 wrong actor/item/replay or global grant reached bank");
            System.out.println(
                "R109_EXPLICIT_PAIR_BOTH_DIRECTIONS_PASS "+
                "authorizedGrants=2 wrongActorOrItemOrReplayDenied=5 "+
                "unrelatedTargetUnmodified=true "+
                "nonplayerPermitDenied=true oneShotPairPermitsConsumed=true");
        }
    }

    private static void targetGenerationAndObjectReplacement()
            throws Exception{
        try(Fixture f=new Fixture(85L)){
            WorldPlayer source=new WorldPlayer();
            WorldPlayer target=new WorldPlayer();
            long sourceGen=f.world.registerPlayer(source,"r109-source2");
            long firstTargetGen=f.world.registerPlayer(
                target,"r109-target2");
            R109OfflineItemOnPlayerPermit oldGenerationPermit=
                R109OfflineItemOnPlayerPermit.issueForTest(
                    f.world,source,sourceGen,
                    target,firstTargetGen,385);
            require(f.world.unregisterPlayer(target,firstTargetGen),
                "R109 could not unregister first target epoch");
            long currentTargetGen=f.world.registerPlayer(
                target,"r109-target2");
            require(currentTargetGen>firstTargetGen,
                "R109 same-object target generation did not advance");
            R109OfflineItemOnPlayerPermit currentGenerationPermit=
                R109OfflineItemOnPlayerPermit.issueForTest(
                    f.world,source,sourceGen,
                    target,currentTargetGen,560);
            AtomicInteger firstCallback=new AtomicInteger();
            AtomicInteger secondCallback=new AtomicInteger();
            AtomicReference<Throwable> failure=new AtomicReference<>();
            PluginTask first=f.plugin.scheduleOwnedForTest(
                1L,source.id(),sourceGen,()->{
                    try{
                        expectDenied(()->oldGenerationPermit
                            .grantToTargetForTest(target.id(),385),
                            "R109_PAIR_PERMIT_DENIED stale owner generation");
                        require(currentGenerationPermit
                            .grantToTargetForTest(target.id(),560)
                            .startsWith("ITEM_SPAWN_OK"),
                            "R109 current generation target grant failed");
                        firstCallback.incrementAndGet();
                    }catch(Throwable t){
                        failure.compareAndSet(null,t);
                        throw new IllegalStateException(
                            "R109 generation callback failed",t);
                    }
                });
            f.world.start();
            await(()->firstCallback.get()==1&&!first.active(),
                "R109 target generation callback did not finish");
            if(failure.get()!=null)
                throw new AssertionError("R109 generation callback failed",
                    failure.get());
            require(!oldGenerationPermit.spentForTest()&&
                currentGenerationPermit.spentForTest()&&
                target.bank().inventoryCount(385)==0&&
                target.bank().inventoryCount(560)==1,
                "R109 stale generation permit was applied");

            R109OfflineItemOnPlayerPermit oldObjectPermit=
                R109OfflineItemOnPlayerPermit.issueForTest(
                    f.world,source,sourceGen,
                    target,currentTargetGen,385);
            require(f.world.unregisterPlayer(target,currentTargetGen),
                "R109 could not unregister former target object");
            WorldPlayer replacement=new WorldPlayer();
            long newTargetGen=f.world.registerPlayer(
                replacement,"r109-target2");
            require(target!=replacement&&
                !target.id().equals(replacement.id())&&
                newTargetGen==firstTargetGen&&
                f.world.players().byName("r109-target2")==replacement,
                "R109 replacement target ID/generation fixture invalid");
            R109OfflineItemOnPlayerPermit replacementPermit=
                R109OfflineItemOnPlayerPermit.issueForTest(
                    f.world,source,sourceGen,
                    replacement,newTargetGen,565);
            PluginTask second=f.plugin.scheduleOwnedForTest(
                1L,source.id(),sourceGen,()->{
                    try{
                        expectDenied(()->oldObjectPermit
                            .grantToTargetForTest(target.id(),385),
                            "R109_PAIR_PERMIT_DENIED stale actor reference");
                        require(replacementPermit
                            .grantToTargetForTest(replacement.id(),565)
                            .startsWith("ITEM_SPAWN_OK"),
                            "R109 replacement target grant failed");
                        secondCallback.incrementAndGet();
                    }catch(Throwable t){
                        failure.compareAndSet(null,t);
                        throw new IllegalStateException(
                            "R109 replacement callback failed",t);
                    }
                });
            await(()->secondCallback.get()==1&&!second.active()&&
                f.plugin.size()==0&&f.world.events().size()==0,
                "R109 replacement target callback did not finish");
            if(failure.get()!=null)
                throw new AssertionError("R109 replacement callback failed",
                    failure.get());
            require(!oldObjectPermit.spentForTest()&&
                replacementPermit.spentForTest()&&
                target.bank().inventoryCount(385)==0&&
                replacement.bank().inventoryCount(565)==1&&
                replacement.bank().inventoryCount(385)==0&&
                source.bank().inventoryCount(565)==0,
                "R109 replaced actor inherited a stale pair permit");
            System.out.println(
                "R109_TARGET_REPLACEMENT_FENCE_PASS "+
                "oldTargetGenerationDenied=true "+
                "oldTargetEntityIdDenied=true "+
                "replacementTargetAuthorizedExplicitly=true "+
                "sameUsernameNewActorNumericGenerationReused=true "+
                "noStaleTargetMutation=true");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world;
        final R99OfflineCrossQueueRetirementFence gate;
        final PluginCallbackScope scope;
        final PluginTaskTracker plugin;

        Fixture(long tickMillis){
            world=World.isolatedForTest(tickMillis);
            R98OfflineFinalCaptureCommandFence finalCapture=
                new R98OfflineFinalCaptureCommandFence(world);
            finalCapture.installForTest();
            gate=new R99OfflineCrossQueueRetirementFence(
                world,finalCapture);
            gate.installForTest();
            world.events().installOfflineExplicitEventProvenanceForTest();
            scope=new PluginCallbackScope(
                ()->world.pulse().inExecutionContext(),
                ()->!world.closed());
            scope.activate();
            plugin=new PluginTaskTracker(
                world.clock(),world.events(),()->!world.closed(),
                null,scope,()->true,(kind,error)->{
                    throw new AssertionError(
                        "R109 unexpected plugin callback failure "+
                        kind,error);
                });
            plugin.activate();
        }

        @Override public void close(){
            try{plugin.close();}
            finally{
                try{scope.close();}
                finally{world.close();}
            }
        }
    }

    @FunctionalInterface interface Checked{
        void run()throws Exception;
    }

    private static void expectDenied(Checked task,String reason)
            throws Exception{
        try{task.run();}
        catch(IllegalStateException rejected){
            if(rejected.getMessage()!=null&&
                rejected.getMessage().contains(reason))
                return;
            throw new AssertionError("R109 wrong denial",rejected);
        }
        throw new AssertionError("R109 expected rejection "+reason);
    }

    private static void expectIllegal(Checked task,String reason)
            throws Exception{
        try{task.run();}
        catch(IllegalArgumentException rejected){
            if(rejected.getMessage()!=null&&
                rejected.getMessage().contains(reason))
                return;
            throw new AssertionError("R109 wrong validation failure",
                rejected);
        }
        throw new AssertionError("R109 missing illegal pair validation");
    }

    private static void await(BooleanSupplier condition,String message)
            throws InterruptedException{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15L);
        while(!condition.getAsBoolean()){
            require(System.nanoTime()<deadline,message);
            Thread.sleep(15L);
        }
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private R109OfflineItemOnPlayerPermitTest(){}
}
