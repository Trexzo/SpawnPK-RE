package spk.local;

import java.io.IOException;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * R93: COOPERATIVE, OFFLINE-ONLY World save/retirement handshake.
 *
 * Unlike R92 this never holds the World lifecycle lock across disk IO.
 * A short ownership check registers an in-flight lease; an optional World
 * unregister hook immediately refuses the transition while a lease exists.
 * The caller MUST explicitly retry unregister after the lease drains.
 *
 * No production World/LocalSession installs this gate. Direct non-cooperating
 * filesystem writes and a hostile JVM are outside this model.
 */
final class R93OfflineNonblockingWorldTurnoverGate
        implements WorldPlayerPersistence.OfflineStampedSaveAdmission,
                   World.OfflineUnregisterAdmission {
    @FunctionalInterface interface BeforePublishForTest {
        void run() throws IOException;
    }

    private static final class Lease {
        int active;
        boolean retiring;
        /** R94: a failed leased writer must not authorize retirement. */
        boolean uncertain;
        boolean durableWitnessRequired;
        boolean durableUnregisterPermitted;
    }

    private final World world;
    private final R89OfflineDurableCaptureAdmission admitted;
    private final IdentityHashMap<WorldPlayer,Map<Long,Lease>> owners=
        new IdentityHashMap<>();
    private volatile BeforePublishForTest beforePublishForTest;
    private final boolean quarantineFailedIo;
    private volatile boolean failAfterR89PrepareForTest;

    private R93OfflineNonblockingWorldTurnoverGate(
        World world,R89OfflineDurableCaptureAdmission admitted,
        boolean quarantineFailedIo){
        this.world=Objects.requireNonNull(world,"world");
        this.admitted=Objects.requireNonNull(admitted,"admitted");
        this.quarantineFailedIo=quarantineFailedIo;
    }

    static R93OfflineNonblockingWorldTurnoverGate installForTest(
        World world,R90OfflineProducerContinuity.Session producer){
        return install(world,producer,false);
    }

    /** R94: explicit offline quarantine mode, still no live integration. */
    static R93OfflineNonblockingWorldTurnoverGate
            installFailureQuarantineForTest(
        World world,R90OfflineProducerContinuity.Session producer){
        return install(world,producer,true);
    }

    private static R93OfflineNonblockingWorldTurnoverGate install(
        World world,R90OfflineProducerContinuity.Session producer,
        boolean quarantineFailedIo){
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(producer,"producer");
        R91OfflineWorldCaptureGate.installCaptureProvenanceForTest(
            world,producer);
        R93OfflineNonblockingWorldTurnoverGate gate=
            new R93OfflineNonblockingWorldTurnoverGate(
                world,producer.gate,quarantineFailedIo);
        world.installOfflineUnregisterAdmissionForTest(gate);
        world.persistence().installOfflineSaveAdmissionForTest(gate);
        return gate;
    }

    void installBeforePublishHookForTest(BeforePublishForTest hook){
        if(beforePublishForTest!=null)
            throw new IllegalStateException(
                "R93_TEST_HOOK_DENIED already installed");
        beforePublishForTest=Objects.requireNonNull(hook,"hook");
    }

    /** R94 only: controlled failure *after* durable R89 PREPARED. */
    void failAfterR89PrepareForTest(){
        if(!quarantineFailedIo||failAfterR89PrepareForTest)
            throw new IllegalStateException(
                "R94_CUTPOINT_DENIED fixture not ready");
        failAfterR89PrepareForTest=true;
    }

    boolean quarantinedForTest(WorldPlayer actor,long generation){
        synchronized(owners){return state(actor,generation).uncertain;}
    }

    private Lease state(WorldPlayer actor,long generation){
        Map<Long,Lease> generations=owners.get(actor);
        if(generations==null){
            generations=new HashMap<>();
            owners.put(actor,generations);
        }
        Lease lease=generations.get(generation);
        if(lease==null){
            lease=new Lease();
            generations.put(generation,lease);
        }
        return lease;
    }

    /** Invoked under World lifecycle lock, before actual unregister. */
    @Override public boolean mayUnregister(
        WorldPlayer player,long generation){
        synchronized(owners){
            Lease lease=state(player,generation);
            // Once retirement is requested, later saves are inadmissible.
            lease.retiring=true;
            return lease.active==0 &&
                !(quarantineFailedIo && lease.uncertain) &&
                (!lease.durableWitnessRequired ||
                    lease.durableUnregisterPermitted);
        }
    }

    /**
     * R96: freeze new saves while still holding the short World lifecycle
     * lock. A failed/active lease is refused before touching R96 files.
     */
    void beginDurableRetirementForTest(
        WorldPlayer actor,long generation)throws IOException{
        boolean current=world.withOpenPlayerOwnershipIfCurrent(
            actor,generation,()->{
                synchronized(owners){
                    Lease lease=state(actor,generation);
                    if(lease.retiring || lease.active!=0 ||
                            lease.uncertain ||
                            lease.durableWitnessRequired)
                        throw new IOException(
                            "R96_RETIRE_DENIED in-flight/uncertain owner");
                    lease.retiring=true;
                    lease.durableWitnessRequired=true;
                    lease.durableUnregisterPermitted=false;
                }
            });
        if(!current)
            throw new IOException(
                "R96_RETIRE_DENIED actor not currently World-owned");
    }

    /** Only the cooperating R96 offline witness flow calls this after
     * a durable PREPARED retirement marker has been published. */
    void permitDurableUnregisterForTest(
        WorldPlayer actor,long generation)throws IOException{
        boolean current=world.withOpenPlayerOwnershipIfCurrent(
            actor,generation,()->{
                synchronized(owners){
                    Lease lease=state(actor,generation);
                    if(!lease.durableWitnessRequired || !lease.retiring ||
                            lease.uncertain || lease.active!=0 ||
                            lease.durableUnregisterPermitted)
                        throw new IOException(
                            "R96_RETIRE_DENIED absent prepared lease");
                    lease.durableUnregisterPermitted=true;
                }
            });
        if(!current)
            throw new IOException(
                "R96_RETIRE_DENIED actor not currently World-owned");
    }

    int activeSavesForTest(WorldPlayer actor,long generation){
        synchronized(owners){return state(actor,generation).active;}
    }

    boolean retiringForTest(WorldPlayer actor,long generation){
        synchronized(owners){return state(actor,generation).retiring;}
    }

    @Override public void withAdmission(
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        throw new IOException(
            "R93_TURNOVER_DENIED unstamped save");
    }

    @Override public void withOrderedAdmission(
        long ordinal,PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        throw new IOException(
            "R93_TURNOVER_DENIED unstamped ordered save");
    }

    @Override public void withStampedAdmission(
        long ordinal,PlayerSnapshot snapshot,
        WorldPlayerPersistence.OfflineCaptureStamp stamp,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(rawWriter,"rawWriter");
        if(stamp==null || stamp.capturedOwner==null)
            throw new IOException(
                "R93_TURNOVER_DENIED missing captured World actor");
        WorldPlayer actor=stamp.capturedOwner;
        long generation=stamp.worldPlayerGeneration;

        // World.lifecycleLock is held only for this short in-memory
        // membership+lease registration. It serializes with unregister.
        boolean current=world.withOpenPlayerOwnershipIfCurrent(
            actor,generation,()->{
                if(!snapshot.username().equals(actor.username()))
                    throw new IOException(
                        "R93_TURNOVER_DENIED wrong account for actor");
                synchronized(owners){
                    Lease lease=state(actor,generation);
                    if(quarantineFailedIo && lease.uncertain)
                        throw new IOException(
                            "R94_IO_UNCERTAIN_QUARANTINED prior writer failed");
                    if(lease.retiring)
                        throw new IOException(
                            "R93_TURNOVER_DENIED owner retiring");
                    if(lease.active==Integer.MAX_VALUE)
                        throw new IOException(
                            "R93_TURNOVER_DENIED lease overflow");
                    lease.active++;
                }
            });
        if(!current)
            throw new IOException(
                "R93_TURNOVER_DENIED actor no longer registered");

        boolean completed=false;
        boolean crashHookInstalled=false;
        try{
            // Here World is free to execute commands and ticks. A
            // concurrent unregister returns false, rather than waiting.
            BeforePublishForTest hook=beforePublishForTest;
            if(hook!=null)hook.run();
            if(failAfterR89PrepareForTest){
                R89OfflineDurableCaptureAdmission.installCrashHookForTest(
                    stage->{
                        if("AFTER_PREPARE_BEFORE_ACCOUNT".equals(stage))
                            throw new IOException(
                                "R94_INJECTED_R89_PREPARED_IO_FAILURE");
                    });
                crashHookInstalled=true;
            }
            admitted.withStampedWorldCaptureForTest(
                ordinal,snapshot,stamp,rawWriter);
            completed=true;
        }finally{
            if(crashHookInstalled)
                R89OfflineDurableCaptureAdmission.clearCrashHookForTest();
            synchronized(owners){
                Lease lease=state(actor,generation);
                if(lease.active<1)
                    throw new IllegalStateException(
                        "R93_TURNOVER_LEASE_CORRUPT unexpected release");
                // A failed write could have passed durable PREPARED or
                // account publication before throwing. Even if the
                // failure was earlier, this gate deliberately cannot
                // prove no disk effect, and quarantines conservatively.
                if(!completed && quarantineFailedIo)
                    lease.uncertain=true;
                lease.active--;
            }
        }
    }
}
