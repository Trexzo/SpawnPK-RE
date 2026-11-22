package spk.local;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import spk.plugin.api.PluginTask;

/**
 * R108 OFFLINE ONLY. Cooperative bank grants can be made via a capability
 * bound to an exact World/EntityId/generation instead of exporting raw
 * WorldPlayer/BankState to callback code.
 *
 * This is not a security sandbox for arbitrary captured references:
 * the inherited R107 regression deliberately reproduces that bypass.
 */
public final class R108OfflineOwnerBankCapabilityTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R108 unexpected arguments");
        R107OfflinePluginCrossActorMutationTest.main(new String[0]);
        validBankCapabilitiesAndCrossOwnerDenial();
        staleCapabilityAfterGenerationRollover();
        System.out.println(
            "R108_OFFLINE_OWNER_BANK_CAPABILITY_PASS "+
            "R103ThroughR107RegressionsInherited=true "+
            "rawPlayerStateNeverReturnedByBankCapability=true "+
            "taggedCallbackRequired=true "+
            "currentWorldActorLockAndGenerationRequired=true "+
            "validSameActorGrantsSucceeded=true "+
            "crossActorAndCrossGenerationCapabilityDenied=true "+
            "nonPlayerCallbackCannotUsePlayerCapability=true "+
            "oldGenerationCapabilityRejectedOnRollover=true "+
            "queuedOldOwnerCallbackRetired=true "+
            "R107DirectCapturedReferenceGapRemains=true "+
            "noPublicPluginOrContentApiChange=true "+
            "noProductionWorldSessionWiring=true");
    }

    private static void validBankCapabilitiesAndCrossOwnerDenial()
            throws Exception{
        try(Fixture f=new Fixture(70L)){
            WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
            long ga=f.world.registerPlayer(a,"r108-a");
            long gb=f.world.registerPlayer(b,"r108-b");
            R108OfflineOwnerBankCapability capA=
                new R108OfflineOwnerBankCapability(f.world,a.id(),ga);
            R108OfflineOwnerBankCapability capB=
                new R108OfflineOwnerBankCapability(f.world,b.id(),gb);
            R108OfflineOwnerBankCapability wrongGeneration=
                new R108OfflineOwnerBankCapability(f.world,a.id(),ga+1L);
            AtomicInteger grantsA=new AtomicInteger();
            AtomicInteger grantsB=new AtomicInteger();
            AtomicInteger rejections=new AtomicInteger();
            AtomicInteger globals=new AtomicInteger();
            AtomicReference<Throwable> escaped=new AtomicReference<>();

            // Even a currently registered owner cannot use a capability
            // from an arbitrary caller or detached plugin event.
            expectDenied(()->capA.grantSingleItemForTest(560),
                "R108_CAPABILITY_CONTEXT_DENIED");
            expectDenied(()->capB.grantSingleItemForTest(565),
                "R108_CAPABILITY_CONTEXT_DENIED");
            require(a.bank().inventoryCount(560)==0&&
                b.bank().inventoryCount(565)==0,
                "R108 detached grant mutated inventory");

            PluginTask taskA=f.plugin.scheduleOwnedForTest(
                1L,a.id(),ga,()->{
                    try{
                        require(f.world.pulse().inExecutionContext()&&
                            Thread.holdsLock(a.mutationLock())&&
                            R107OfflinePluginOwnerCall.activeForTest(),
                            "R108 A task missing World actor context");
                        String result=capA.grantSingleItemForTest(560);
                        require(result.startsWith("ITEM_SPAWN_OK"),
                            "R108 own bank grant failed "+result);
                        grantsA.incrementAndGet();
                        expectDenied(()->capB.grantSingleItemForTest(565),
                            "R108_CAPABILITY_CONTEXT_DENIED");
                        rejections.incrementAndGet();
                        expectDenied(()->wrongGeneration.grantSingleItemForTest(560),
                            "R108_CAPABILITY_CONTEXT_DENIED");
                        rejections.incrementAndGet();
                    }catch(Throwable t){
                        escaped.compareAndSet(null,t);
                        throw new IllegalStateException(
                            "R108 tagged actor A callback failed",t);
                    }
                });

            PluginTask taskB=f.plugin.scheduleOwnedForTest(
                2L,b.id(),gb,()->{
                    try{
                        require(f.world.pulse().inExecutionContext()&&
                            Thread.holdsLock(b.mutationLock())&&
                            !Thread.holdsLock(a.mutationLock()),
                            "R108 B task missing exact player lock");
                        String result=capB.grantSingleItemForTest(565);
                        require(result.startsWith("ITEM_SPAWN_OK"),
                            "R108 B bank grant failed "+result);
                        grantsB.incrementAndGet();
                    }catch(Throwable t){
                        escaped.compareAndSet(null,t);
                        throw new IllegalStateException(
                            "R108 tagged actor B callback failed",t);
                    }
                });

            f.world.events().scheduleExplicitNonPlayerForTest(
                3L,"R108 no player provenance on nonplayer event",()->{
                    try{
                        require(!R107OfflinePluginOwnerCall.activeForTest(),
                            "R108 plugin owner leaked to nonplayer callback");
                        expectDenied(()->capA.grantSingleItemForTest(560),
                            "R108_CAPABILITY_CONTEXT_DENIED");
                        rejections.incrementAndGet();
                    }catch(Throwable t){escaped.compareAndSet(null,t);}
                    globals.incrementAndGet();
                });
            f.world.start();
            await(()->globals.get()==1&&!taskA.active()&&!taskB.active()&&
                f.plugin.size()==0&&f.world.events().size()==0,
                "R108 owner-bound callbacks not drained");
            if(escaped.get()!=null)
                throw new AssertionError(
                    "R108 bank capability callback failed",escaped.get());
            require(grantsA.get()==1&&grantsB.get()==1&&
                rejections.get()==3&&
                a.bank().inventoryCount(560)==1&&
                b.bank().inventoryCount(565)==1&&
                a.bank().inventoryCount(565)==0&&
                b.bank().inventoryCount(560)==0&&
                f.gate.deniedEventsForTest()==0,
                "R108 capability grant applied to incorrect owner");
            expectDenied(()->capA.grantSingleItemForTest(560),
                "R108_CAPABILITY_CONTEXT_DENIED");
            System.out.println(
                "R108_OWNER_CAPABILITY_TWO_ACTOR_PASS "+
                "sameOwnerGrants=2 wrongOwnerOrGenerationRefusals=3 "+
                "nonPlayerCallbackDenied=true detachedUseDenied=true "+
                "noRawWorldPlayerReturned=true");
        }
    }

    private static void staleCapabilityAfterGenerationRollover()
            throws Exception{
        try(Fixture f=new Fixture(75L)){
            WorldPlayer a=new WorldPlayer();
            long oldGen=f.world.registerPlayer(a,"r108-returning");
            R108OfflineOwnerBankCapability previous=
                new R108OfflineOwnerBankCapability(f.world,a.id(),oldGen);
            AtomicInteger staleCallbackRuns=new AtomicInteger();
            AtomicInteger currentGrants=new AtomicInteger();
            AtomicInteger deniedOldCapability=new AtomicInteger();
            AtomicReference<Throwable> escaped=new AtomicReference<>();

            PluginTask old=f.plugin.scheduleOwnedForTest(
                5L,a.id(),oldGen,staleCallbackRuns::incrementAndGet);
            require(f.world.unregisterPlayer(a,oldGen),
                "R108 could not retire previous generation");
            long gen=f.world.registerPlayer(a,"r108-returning");
            require(gen>oldGen&&f.world.players().owns(a,gen),
                "R108 generation did not advance");
            R108OfflineOwnerBankCapability current=
                new R108OfflineOwnerBankCapability(f.world,a.id(),gen);
            PluginTask newOwner=f.plugin.scheduleOwnedForTest(
                2L,a.id(),gen,()->{
                    try{
                        expectDenied(()->previous.grantSingleItemForTest(385),
                            "R108_CAPABILITY_CONTEXT_DENIED");
                        deniedOldCapability.incrementAndGet();
                        String result=current.grantSingleItemForTest(560);
                        require(result.startsWith("ITEM_SPAWN_OK"),
                            "R108 re-registration grant failed "+result);
                        currentGrants.incrementAndGet();
                    }catch(Throwable t){
                        escaped.compareAndSet(null,t);
                        throw new IllegalStateException(
                            "R108 rollover callback failed",t);
                    }
                });
            f.world.start();
            long start=f.world.clock().tick();
            await(()->f.world.clock().tick()>=start+7L&&
                !old.active()&&!newOwner.active()&&
                f.plugin.size()==0&&f.world.events().size()==0,
                "R108 stale capability or queued old event not retired");
            if(escaped.get()!=null)
                throw new AssertionError(
                    "R108 rollover callback crashed",escaped.get());
            require(staleCallbackRuns.get()==0&&
                deniedOldCapability.get()==1&&currentGrants.get()==1&&
                a.bank().inventoryCount(560)==1&&
                a.bank().inventoryCount(385)==0&&
                f.gate.deniedEventsForTest()>=1,
                "R108 old generation capability bypassed fresh owner");
            System.out.println(
                "R108_OWNER_CAPABILITY_ROLLOVER_PASS "+
                "queuedOldCallbackSuppressed=true "+
                "oldCapabilityDeniedInNewGeneration=true "+
                "currentCapabilityExecutedExactlyOnce=true "+
                "oldHandleRetired=true");
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
                        "R108 unexpected plugin callback failure "+kind,error);
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

    @FunctionalInterface interface Checked {
        void run()throws Exception;
    }

    private static void expectDenied(Checked action,String token)
            throws Exception{
        try{action.run();}
        catch(IllegalStateException rejected){
            if(rejected.getMessage()!=null&&
                rejected.getMessage().contains(token))
                return;
            throw new AssertionError("R108 wrong rejection",rejected);
        }
        throw new AssertionError("R108 expected denial "+token);
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
    private R108OfflineOwnerBankCapabilityTest(){}
}
