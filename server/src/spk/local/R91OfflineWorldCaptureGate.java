package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * R91 OFFLINE ONLY. Integrates a real World-thread immutable capture stamp
 * with R90's same-R60-lock producer identity fence and R89's atomic
 * watermark admission. No real World, LocalSession or account installs it.
 *
 * Stamps are evidence from this cooperating in-process test seam only:
 * no cryptographic signature or provenance against a hostile JVM.
 */
final class R91OfflineWorldCaptureGate
        implements WorldPlayerPersistence.OfflineStampedSaveAdmission {
    private final R89OfflineDurableCaptureAdmission admitted;
    private final World captureWorld;
    private final boolean requireCurrentWorldOwner;

    private R91OfflineWorldCaptureGate(
        R89OfflineDurableCaptureAdmission admitted,
        World captureWorld,boolean requireCurrentWorldOwner){
        this.admitted=Objects.requireNonNull(admitted,"admitted");
        this.captureWorld=Objects.requireNonNull(captureWorld,"captureWorld");
        this.requireCurrentWorldOwner=requireCurrentWorldOwner;
    }

    static R91OfflineWorldCaptureGate installForTest(
        World world,R90OfflineProducerContinuity.Session source){
        return install(world,source,false);
    }

    /** R92: intentionally opt-in, offline-only strong World ownership lease. */
    static R91OfflineWorldCaptureGate installGenerationFencedForTest(
        World world,R90OfflineProducerContinuity.Session source){
        return install(world,source,true);
    }

    private static R91OfflineWorldCaptureGate install(
        World world,R90OfflineProducerContinuity.Session source,
        boolean requireCurrentWorldOwner){
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(source,"source");
        // No game startup, file mutation or capture occurs here.
        // Existing R72 seeding fails if World has already started.
        source.seedWorldBeforeStartForTest(world);
        source.gate.requireWorldCaptureStampsForTest();
        R91OfflineWorldCaptureGate gate=
            new R91OfflineWorldCaptureGate(
                source.gate,world,requireCurrentWorldOwner);
        world.persistence().installOfflineCaptureIssuerForTest(
            (ordinal,snapshot,playerGeneration)->
                new WorldPlayerPersistence.OfflineCaptureStamp(
                    source.ticket.username,
                    source.ticket.stream,
                    source.ticket.incarnation,
                    source.ticket.ownerEpoch,
                    source.ticket.producerGeneration,
                    ordinal,
                    R57TradePairJournal.digest(snapshot),
                    playerGeneration
                )
        );
        world.persistence().installOfflineSaveAdmissionForTest(gate);
        return gate;
    }

    @Override public void withAdmission(
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        throw new IOException(
            "R91_CAPTURE_DENIED missing World provenance and ordinal");
    }

    @Override public void withOrderedAdmission(
        long ordinal,PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        throw new IOException(
            "R91_CAPTURE_DENIED unstamped save not authorized");
    }

    @Override public void withStampedAdmission(
        long ordinal,PlayerSnapshot snapshot,
        WorldPlayerPersistence.OfflineCaptureStamp stamp,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        if(!requireCurrentWorldOwner){
            admitted.withStampedWorldCaptureForTest(
                ordinal,snapshot,stamp,rawWriter);
            return;
        }
        if(stamp==null||stamp.capturedOwner==null)
            throw new IOException(
                "R92_WORLD_OWNER_DENIED missing captured actor");
        // The real World lifecycle lock prevents unregister/re-register
        // while the existing R60 + R89 + R90 commit is in progress.
        // This intentionally blocks the offline World during disk IO and
        // MUST NOT be enabled in the live low-latency persistence path.
        boolean current=captureWorld.withOpenPlayerOwnershipIfCurrent(
            stamp.capturedOwner,stamp.worldPlayerGeneration,()->{
                if(!snapshot.username().equals(
                        stamp.capturedOwner.username()))
                    throw new IOException(
                        "R92_WORLD_OWNER_DENIED username changed");
                admitted.withStampedWorldCaptureForTest(
                    ordinal,snapshot,stamp,rawWriter);
            });
        if(!current)
            throw new IOException(
                "R92_WORLD_OWNER_DENIED retired generation");
    }
}
