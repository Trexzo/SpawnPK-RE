package spk.local;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R101 OFFLINE ONLY: strict event-provenance admission unit regression,
 * followed by a real isolated World R72/R98/R99/R96 final-save fixture.
 *
 * No actor identity can be inferred from an untagged Runnable closure.
 * The strict hook refuses those unclassified schedules; reviewers must
 * explicitly choose owner/generation or non-player provenance in tests.
 */
public final class R101OfflineExplicitEventProvenanceTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R101 unexpected arguments");
        legacyUntaggedGapIsObservable();
        strictProvenanceRetainsQueueOrdering();
        R99OfflineCrossQueueRetirementFenceTest.main(
            new String[]{"r101-strict"});
        System.out.println(
            "R101_OFFLINE_EXPLICIT_EVENT_PROVENANCE_PASS "+
            "legacyUntaggedGapDemonstrated=true "+
            "strictUntaggedSchedulingRejected=true "+
            "ownerTaggedEventsRetainGenerationAndDispatchCheck=true "+
            "explicitNonPlayerEventsRemainAvailable=true "+
            "missingPurposeRejected=true "+
            "noExtraQueueItemsOnDeniedAdmission=true "+
            "realWorldFinalCaptureAndRetirementStillPass=true "+
            "noLiveWorldOrLocalSessionWiring=true");
    }

    private static void legacyUntaggedGapIsObservable(){
        WorldEventQueue queue=new WorldEventQueue();
        AtomicInteger untaggedExecuted=new AtomicInteger();
        AtomicInteger taggedExecuted=new AtomicInteger();
        EntityId owner=new EntityId(7197L);
        queue.installOfflineOwnedEventAdmissionForTest(
            new WorldEventQueue.OfflineOwnedEventAdmission(){
                @Override public boolean permits(
                    EntityId id,long generation){return true;}
                @Override public void dispatch(
                    EntityId id,long generation,Runnable task){
                    // A hostile/stale owner would be refused here.
                    taggedExecuted.incrementAndGet();
                }
            });
        queue.schedule(1L,untaggedExecuted::incrementAndGet);
        queue.scheduleOwnedForTest(
            1L,owner,1L,()->taggedExecuted.incrementAndGet());
        require(queue.runDue(1L)==2 &&
                untaggedExecuted.get()==1 &&
                taggedExecuted.get()==1,
            "R101 untagged legacy scheduler gap not demonstrated");
        queue.close();
    }

    private static void strictProvenanceRetainsQueueOrdering(){
        WorldEventQueue queue=new WorldEventQueue();
        AtomicBoolean admitted=new AtomicBoolean(true);
        AtomicInteger taggedExecuted=new AtomicInteger();
        AtomicInteger explicitGlobalExecuted=new AtomicInteger();
        EntityId owner=new EntityId(7198L);
        expectDenied(
            queue::installOfflineExplicitEventProvenanceForTest,
            "R101_EXPLICIT_EVENT_GATE_DENIED");
        queue.installOfflineOwnedEventAdmissionForTest(
            new WorldEventQueue.OfflineOwnedEventAdmission(){
                @Override public boolean permits(
                    EntityId id,long generation){
                    return admitted.get() && owner.equals(id) &&
                        generation==7L;
                }
                @Override public void dispatch(
                    EntityId id,long generation,Runnable action){
                    if(permits(id,generation))action.run();
                }
            });
        queue.installOfflineExplicitEventProvenanceForTest();
        expectDenied(
            ()->queue.installOfflineExplicitEventProvenanceForTest(),
            "R101_EXPLICIT_EVENT_GATE_DENIED");
        expectDenied(
            ()->queue.schedule(1L,
                ()->taggedExecuted.addAndGet(100)),
            "R101_UNTAGGED_EVENT_DENIED");
        expectDenied(
            ()->queue.scheduleExplicitNonPlayerForTest(
                1L," ",explicitGlobalExecuted::incrementAndGet),
            "R101_EXPLICIT_NONPLAYER_DENIED");
        require(queue.size()==0,
            "R101 denied untagged tasks were still enqueued");

        queue.scheduleOwnedForTest(
            1L,owner,7L,taggedExecuted::incrementAndGet);
        queue.scheduleOwnedForTest(
            3L,owner,7L,()->taggedExecuted.addAndGet(100));
        queue.scheduleExplicitNonPlayerForTest(
            2L,"Independent fixture global clock",
            explicitGlobalExecuted::incrementAndGet);
        require(queue.runDue(1L)==1 &&
                taggedExecuted.get()==1,
            "R101 valid owner-tagged event was not executed");
        // An owner changes/quiesces before the already-tagged event's
        // due time. The existing R76 dispatch policy must recheck.
        admitted.set(false);
        require(queue.runDue(3L)==2 &&
                taggedExecuted.get()==1 &&
                explicitGlobalExecuted.get()==1,
            "R101 stale tagged event executed or global event blocked");
        expectDenied(
            ()->queue.scheduleOwnedForTest(
                4L,owner,7L,taggedExecuted::incrementAndGet),
            "R76_EVENT_QUIESCED before schedule");
        require(queue.size()==0,
            "R101 disallowed scheduled event was enqueued");
        queue.close();
    }

    private static void expectDenied(
        Runnable action,String reason){
        try{
            action.run();
            throw new AssertionError("R101 missing expected refusal "+reason);
        }catch(IllegalStateException denied){
            require(denied.getMessage()!=null &&
                    denied.getMessage().contains(reason),
                "R101 incorrect refusal "+denied);
        }
    }

    private static void require(boolean success,String message){
        if(!success)throw new AssertionError(message);
    }

    private R101OfflineExplicitEventProvenanceTest(){}
}
