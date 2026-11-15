package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * R72 offline-only durable observation of a final save which may continue
 * after WorldPlayerPersistence.close() has rejected its SaveTicket.
 *
 * Each registered test-owned ticket gets an immutable INTENT marker. Its
 * actual I/O thread publishes ENTERED before invoking the existing R71
 * final-save owner fence. A forced shutdown writes CANCEL_OBSERVED from
 * the close thread WITHOUT acquiring R60 or R71's held monitor, because
 * the commit worker can be blocked inside an uninterruptible repository.
 * After the original commit returns, it writes ACK_OBSERVED. None of
 * these markers authorize login, rollback, replay or cleanup.
 *
 * Every marker is an independent R16 atomic replace, not a single durable
 * transaction. If a process crashes between markers, the inspector fails
 * closed. Physical power-loss and directory fsync remain unproven.
 */
final class R72OfflineInFlightCommitEvidence
    implements WorldPlayerPersistence.OfflineIoWriteAdmission {

    enum ReviewStatus {
        BLOCK_UNREGISTERED,
        BLOCK_REGISTERED_UNCERTAIN,
        BLOCK_INFLIGHT_UNCERTAIN,
        BLOCK_CANCELLED_UNCERTAIN,
        BLOCK_LATE_COMMIT_OBSERVED,
        BLOCK_COMPLETION_NOT_AUTHORIZED,
        BLOCK_FAILED_OR_PARTIAL,
        BLOCK_AMBIGUOUS_EVIDENCE
    }

    static final class Review {
        final ReviewStatus status;
        final String reason;
        Review(ReviewStatus status,String reason){
            this.status=status;
            this.reason=reason;
        }
        boolean loginAuthorized(){return false;}
        boolean replayAuthorized(){return false;}
        boolean quarantineReleased(){return false;}
    }

    private static final String FORMAT="SpawnPK-R72-InFlightEvidence-v1";
    private static final String INTENT="INTENT";
    private static final String ENTERED="ENTERED";
    private static final String CANCEL="CANCEL_OBSERVED";
    private static final String ACK="ACK_OBSERVED";
    private static final String FAILURE="FAILURE_OBSERVED";

    private static final class TicketEvidence {
        final UUID id;
        final long sequence;
        final R64OfflineSessionOwnership.Token owner;
        final String afterDigest;
        final AtomicBoolean entered=new AtomicBoolean();

        TicketEvidence(
            UUID id,long sequence,
            R64OfflineSessionOwnership.Token owner,String afterDigest
        ){
            this.id=id;
            this.sequence=sequence;
            this.owner=owner;
            this.afterDigest=afterDigest;
        }
    }

    private final Path evidenceRoot;
    private final R71OfflineFinalSaveCheckpointFence delegate;
    private final Map<Long,TicketEvidence> tickets=
        new ConcurrentHashMap<>();

    R72OfflineInFlightCommitEvidence(
        Path evidenceRoot,R71OfflineFinalSaveCheckpointFence delegate
    ){
        this.evidenceRoot=Objects.requireNonNull(
            evidenceRoot,"evidence root"
        ).toAbsolutePath().normalize();
        this.delegate=Objects.requireNonNull(delegate,"delegate");
    }

    UUID registerFinalForTest(
        WorldPlayerPersistence.CapturedSave captured,
        R64OfflineSessionOwnership.Token owner
    )throws IOException{
        Objects.requireNonNull(captured,"captured");
        Objects.requireNonNull(owner,"owner");
        final String digest=R57TradePairJournal.digest(
            captured.ticket.snapshot
        );
        if(!captured.ticket.snapshot.username().equals(owner.username)||
           owner.phase!=R64OfflineSessionOwnership.Phase.ACTIVE)
            throw new IOException(
                "R72_REGISTER_DENIED foreign/inactive owner"
            );
        UUID id=UUID.randomUUID();
        TicketEvidence ticket=new TicketEvidence(
            id,captured.ticket.sequence,owner,digest
        );
        if(tickets.putIfAbsent(ticket.sequence,ticket)!=null)
            throw new IOException(
                "R72_REGISTER_DENIED duplicate ticket sequence"
            );
        try{
            // Pre-I/O evidence is persisted BEFORE the final snapshot is
            // exposed to a writer. Never silently accept evidence failure.
            writeMarker(ticket,INTENT);
            delegate.sealFinalSaveForTest(captured,owner);
            return id;
        }catch(IOException|RuntimeException problem){
            tickets.remove(ticket.sequence,ticket);
            throw problem;
        }
    }

    @Override public void persistAtCommit(
        long sequence,PlayerSnapshot snapshot,PlayerRepository repository,
        boolean checkpoint
    )throws IOException{
        if(checkpoint)throw new IOException(
            "R72_COMMIT_DENIED checkpoint must carry captured token"
        );
        Objects.requireNonNull(repository,"repository");
        TicketEvidence ticket=tickets.get(sequence);
        if(ticket==null)
            throw new IOException(
                "R72_COMMIT_DENIED unregistered final ticket"
            );
        if(!ticket.owner.username.equals(snapshot.username())||
           !ticket.afterDigest.equals(
               R57TradePairJournal.digest(snapshot)
           )||
           !ticket.entered.compareAndSet(false,true))
            throw new IOException(
                "R72_COMMIT_DENIED mismatched/replayed final ticket"
            );

        // BEFORE entering R71's same-lock owner/repository commit.
        writeMarker(ticket,ENTERED);
        try{
            delegate.persistAtCommit(
                sequence,snapshot,repository,false
            );
        }catch(IOException|RuntimeException|Error failed){
            try{writeMarker(ticket,FAILURE);}
            catch(IOException evidenceFailure){
                failed.addSuppressed(evidenceFailure);
            }
            throw failed;
        }
        // The real repository may return AFTER close() canceled the
        // SaveTicket. This proves only that the worker returned, not
        // that the future was successful or that login is safe.
        writeMarker(ticket,ACK);
    }

    @Override public void persistCheckpointAtCommit(
        long sequence,PlayerSnapshot snapshot,PlayerRepository repository,
        R64OfflineSessionOwnership.Token owner
    )throws IOException{
        delegate.persistCheckpointAtCommit(
            sequence,snapshot,repository,owner
        );
    }

    /**
     * Called by shutdown/backpressure thread. DO NOT call delegate's
     * onDroppedSaveForTest on an in-flight ticket: R71 may be holding
     * its sealLock through an uninterruptible filesystem write. The
     * close thread must not deadlock waiting for that same monitor.
     */
    @Override public void onDroppedSaveForTest(long sequence){
        TicketEvidence ticket=tickets.get(sequence);
        if(ticket==null){
            delegate.onDroppedSaveForTest(sequence);
            return;
        }
        try{
            writeMarker(ticket,CANCEL);
        }catch(IOException error){
            // A failed observation marker must never turn into a
            // success/cleanup claim; any missing marker is INDETERMINATE.
            System.err.println(
                "R72_CANCEL_EVIDENCE_WRITE_FAILED sequence="+sequence+
                " error="+error
            );
        }
        if(!ticket.entered.get()){
            delegate.onDroppedSaveForTest(sequence);
        }
    }

    /**
     * Fresh-reader status: marker presence and well-formed matching
     * fields only. Do not read canonical account data to infer a write
     * was or was not committed without a cooperative OS lock. Do not
     * delete any marker, repair account files or infer a safe login.
     */
    static Review inspect(Path evidenceRoot,UUID id){
        try{
            Path root=Objects.requireNonNull(
                evidenceRoot,"evidence root"
            ).toAbsolutePath().normalize();
            Properties intent=readMarker(root,id,INTENT);
            if(intent==null)
                return new Review(
                    ReviewStatus.BLOCK_UNREGISTERED,
                    "missing immutable intent"
                );
            validate(intent,id,INTENT,null);
            boolean entered=checkMarker(root,id,ENTERED,intent);
            boolean cancelled=checkMarker(root,id,CANCEL,intent);
            boolean ack=checkMarker(root,id,ACK,intent);
            boolean failed=checkMarker(root,id,FAILURE,intent);
            if((ack&&failed)||(ack&&!entered)||
               (failed&&!entered)||(cancelled&&ack&&!entered))
                return new Review(
                    ReviewStatus.BLOCK_AMBIGUOUS_EVIDENCE,
                    "conflicting or incomplete evidence phases"
                );
            if(failed)
                return new Review(
                    ReviewStatus.BLOCK_FAILED_OR_PARTIAL,
                    "worker reported error; partial canonical write unknown"
                );
            if(cancelled&&ack)
                return new Review(
                    ReviewStatus.BLOCK_LATE_COMMIT_OBSERVED,
                    "I/O completed after or around cancelled SaveTicket"
                );
            if(cancelled)
                return new Review(
                    ReviewStatus.BLOCK_CANCELLED_UNCERTAIN,
                    "shutdown rejected SaveTicket; I/O may still write"
                );
            if(ack)
                return new Review(
                    ReviewStatus.BLOCK_COMPLETION_NOT_AUTHORIZED,
                    "worker returned; no login or quarantine authority"
                );
            if(entered)
                return new Review(
                    ReviewStatus.BLOCK_INFLIGHT_UNCERTAIN,
                    "worker entered commit; final outcome unknown"
                );
            return new Review(
                ReviewStatus.BLOCK_REGISTERED_UNCERTAIN,
                "ticket registered but no durable I/O entry"
            );
        }catch(IOException|RuntimeException error){
            return new Review(
                ReviewStatus.BLOCK_AMBIGUOUS_EVIDENCE,
                "invalid or inaccessible immutable marker: "+error
            );
        }
    }

    private static boolean checkMarker(
        Path root,UUID id,String phase,Properties intent
    )throws IOException{
        Properties marker=readMarker(root,id,phase);
        if(marker==null)return false;
        validate(marker,id,phase,intent);
        return true;
    }

    private void writeMarker(
        TicketEvidence ticket,String phase
    )throws IOException{
        Path target=path(evidenceRoot,ticket.id,phase);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "R72_EVIDENCE_DENIED marker already exists "+phase
            );
        Properties marker=new Properties();
        marker.setProperty("format",FORMAT);
        marker.setProperty("id",ticket.id.toString());
        marker.setProperty("phase",phase);
        marker.setProperty("sequence",Long.toString(ticket.sequence));
        marker.setProperty("username",ticket.owner.username);
        marker.setProperty("owner",ticket.owner.owner.toString());
        marker.setProperty("epoch",Long.toString(ticket.owner.epoch));
        marker.setProperty(
            "accountGeneration",
            Long.toString(ticket.owner.account.generation)
        );
        marker.setProperty(
            "journalRevision",
            Long.toString(ticket.owner.account.journalRevision)
        );
        marker.setProperty(
            "beforeDigest",ticket.owner.account.snapshotDigest
        );
        marker.setProperty("afterDigest",ticket.afterDigest);
        AtomicAccountPublisher.publish(
            target,marker,"R72 offline non-authoritative I/O evidence"
        );
    }

    private static Properties readMarker(
        Path root,UUID id,String phase
    )throws IOException{
        Path p=path(root,id,phase);
        AtomicAccountPublisher.requireNoAmbiguousStage(p);
        if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))
            return null;
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||
           Files.isSymbolicLink(p))
            throw new IOException(
                "R72_EVIDENCE_DENIED invalid marker type"
            );
        Properties props=new Properties();
        try(java.io.InputStream in=Files.newInputStream(p)){
            props.load(in);
        }
        return props;
    }

    private static Path path(Path root,UUID id,String phase){
        return root.resolve(
            "r72-"+id.toString()+"-"+phase+".properties"
        );
    }

    private static void validate(
        Properties marker,UUID id,String phase,Properties intent
    )throws IOException{
        if(marker.size()!=11||
           !FORMAT.equals(marker.getProperty("format"))||
           !id.toString().equals(marker.getProperty("id"))||
           !phase.equals(marker.getProperty("phase"))||
           !marker.getProperty("username","").matches(
               "[a-z][a-z0-9_]{0,31}"
           )||
           !marker.getProperty("beforeDigest","").matches(
               "[0-9a-f]{64}"
           )||
           !marker.getProperty("afterDigest","").matches(
               "[0-9a-f]{64}"
           ))
            throw new IOException(
                "R72_EVIDENCE_DENIED invalid marker schema"
            );
        try{
            UUID.fromString(marker.getProperty("owner"));
            if(Long.parseLong(marker.getProperty("sequence"))<=0||
               Long.parseLong(marker.getProperty("epoch"))<=0||
               Long.parseLong(marker.getProperty("accountGeneration"))<=0||
               Long.parseLong(marker.getProperty("journalRevision"))<=0)
                throw new NumberFormatException("nonpositive field");
        }catch(IllegalArgumentException e){
            throw new IOException(
                "R72_EVIDENCE_DENIED invalid owner or numeric field",e
            );
        }
        if(intent!=null){
            for(String key: new String[]{
                "format","id","sequence","username","owner",
                "epoch","accountGeneration","journalRevision",
                "beforeDigest","afterDigest"
            }){
                if(!Objects.equals(
                    intent.getProperty(key),marker.getProperty(key)
                ))
                    throw new IOException(
                        "R72_EVIDENCE_DENIED mismatched "+key
                    );
            }
        }
    }
}
