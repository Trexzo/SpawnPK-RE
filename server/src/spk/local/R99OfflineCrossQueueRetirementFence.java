package spk.local;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R99 OFFLINE TEST ONLY. Reuses the existing R75/R76 opt-in admission
 * seams to extend an R98 WorldCommandInbox final-capture freeze across:
 * owner-tagged realtime callbacks, owner-tagged World events, and player
 * periodic WorldPulse callbacks.
 *
 * No production World.shared, LocalSession, sockets, or untagged legacy
 * events install this policy. This deliberately does not claim global
 * gameplay shutdown or safe independent player-login authority.
 */
final class R99OfflineCrossQueueRetirementFence
        implements WorldRealtimeQueue.OfflineRealtimeAdmission,
                   WorldEventQueue.OfflineOwnedEventAdmission,
                   WorldPulse.OfflineTickAdmission {
    private final World world;
    private final R98OfflineFinalCaptureCommandFence finalCapture;
    private final AtomicInteger deniedRealtime=new AtomicInteger();
    private final AtomicInteger deniedEvents=new AtomicInteger();
    private final AtomicInteger deniedTicks=new AtomicInteger();

    R99OfflineCrossQueueRetirementFence(
        World world,R98OfflineFinalCaptureCommandFence finalCapture){
        this.world=Objects.requireNonNull(world,"world");
        this.finalCapture=Objects.requireNonNull(
            finalCapture,"finalCapture");
    }

    void installForTest(){
        if(world.pulse().running()||world.events().size()!=0||
                world.realtime().size()!=0)
            throw new IllegalStateException(
                "R99_QUEUE_GATE_DENIED World running or queues not empty");
        // Existing queue implementations refuse replacing an installed
        // policy. The caller must install R98 command policy separately.
        world.realtime().installOfflineRealtimeAdmissionForTest(this);
        world.events().installOfflineOwnedEventAdmissionForTest(this);
        world.pulse().installOfflineTickAdmissionForTest(this);
    }

    private boolean currentAndNotQuiesced(
        WorldPlayer actor,long generation){
        return actor!=null &&
            world.players().owns(actor,generation) &&
            !finalCapture.quiescedForTest(actor,generation);
    }

    @Override public boolean permits(
        WorldPlayer actor,long generation,Runnable task){
        boolean allow=currentAndNotQuiesced(actor,generation);
        if(!allow)deniedRealtime.incrementAndGet();
        return allow;
    }

    @Override public boolean permits(
        EntityId ownerId,long generation){
        WorldPlayer actor=ownerId==null?null:
            world.players().byId(ownerId);
        boolean allow=currentAndNotQuiesced(actor,generation);
        if(!allow)deniedEvents.incrementAndGet();
        return allow;
    }

    @Override public void dispatch(
        EntityId ownerId,long generation,Runnable task){
        Objects.requireNonNull(task,"task");
        WorldPlayer actor=ownerId==null?null:
            world.players().byId(ownerId);
        if(actor==null){
            deniedEvents.incrementAndGet();
            return;
        }
        try{
            // WorldEventQueue releases its queue monitor before dispatch.
            // World then owns the same mutation lock used by R98's
            // final-capture handler. A pre-capture scheduled action is
            // rechecked here, not grandfathered into post-capture play.
            boolean current=world.withOpenPlayerMutationOwnershipIfCurrent(
                actor,generation,()->{
                    if(currentAndNotQuiesced(actor,generation))
                        task.run();
                    else
                        deniedEvents.incrementAndGet();
                });
            if(!current)
                deniedEvents.incrementAndGet();
        }catch(Exception e){
            throw new IllegalStateException(
                "R99_EVENT_DISPATCH_DENIED offline callback failed",e);
        }
    }

    @Override public boolean permits(
        WorldPlayer actor,long generation){
        boolean allow=currentAndNotQuiesced(actor,generation);
        if(!allow)deniedTicks.incrementAndGet();
        return allow;
    }

    int deniedRealtimeForTest(){return deniedRealtime.get();}
    int deniedEventsForTest(){return deniedEvents.get();}
    int deniedTicksForTest(){return deniedTicks.get();}
}
