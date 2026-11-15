package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R74 OFFLINE, READ-ONLY, NON-AUTHORIZING evidence comparison.
 *
 * Acquires R60's real cross-process OS lock across a bounded inspection
 * of the R73 durable write blocker, R72 evidence phases, R63 canonical
 * account snapshot/hash/generation, R64 RELEASED owner and R59 clear
 * indexed-trade state. Any incomplete/mismatched evidence stays BLOCKED.
 *
 * Even a fully matching ACK is not a proof of safe automatic retirement:
 * there is no deletion, repair, login, promotion or restart authority here.
 * No LocalSession installs or invokes this test-only inspector.
 */
final class R74OfflineReconciliationReview {
    private static final String R73_FORMAT=
        "SpawnPK-R73-WriteAdmission-v1";
    private static final String R72_FORMAT=
        "SpawnPK-R72-InFlightEvidence-v1";
    private static final int MAX_MARKER_BYTES=4096;
    private static final Set<String> R73_KEYS=new HashSet<>(
        Arrays.asList("format","username","owner","epoch",
            "generation","journalRevision","beforeDigest",
            "afterDigest","saveSequence")
    );
    private static final Set<String> R72_KEYS=new HashSet<>(
        Arrays.asList("format","id","phase","sequence","username",
            "owner","epoch","accountGeneration","journalRevision",
            "beforeDigest","afterDigest")
    );
    private static final String[] PHASES={
        "INTENT","ENTERED","CANCEL_OBSERVED",
        "ACK_OBSERVED","FAILURE_OBSERVED"
    };

    enum Status {
        BLOCK_MISSING_WRITE_INTENT,
        BLOCK_UNACKNOWLEDGED_WRITE,
        BLOCK_FAILED_OR_PARTIAL,
        BLOCK_EVIDENCE_MISMATCH,
        BLOCK_OWNER_NOT_RELEASED,
        BLOCK_PENDING_PAIR,
        BLOCK_AMBIGUOUS_EVIDENCE,
        BLOCK_LOCK_OR_PATH_INDETERMINATE,
        BLOCK_CONSISTENT_BUT_QUARANTINED,
        BLOCK_CANCELLED_LATE_ACK_QUARANTINED
    }

    static final class Review {
        final Status status;
        final String reason;
        final UUID observationId;
        final long accountGeneration;

        Review(Status status,String reason,UUID id,long generation){
            this.status=status;
            this.reason=reason;
            observationId=id;
            accountGeneration=generation;
        }
        boolean loginAuthorized(){return false;}
        boolean replayAuthorized(){return false;}
        boolean recoveryAuthorized(){return false;}
        boolean writeIntentRetired(){return false;}
        boolean quarantineReleased(){return false;}
        boolean evidenceModified(){return false;}
    }

    private final Path journal,accounts,generations,sessions,evidence;

    R74OfflineReconciliationReview(
        Path journal,Path accounts,Path generations,
        Path sessions,Path evidence
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(
            generations,"generations"
        );
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        this.evidence=Objects.requireNonNull(evidence,"evidence");
    }

    Review inspect(String username){
        try{
            return R60TradePairCoordinator.withExclusive(
                journal,false,()->inspectUnderLock(username)
            );
        }catch(IOException|RuntimeException error){
            return result(Status.BLOCK_LOCK_OR_PATH_INDETERMINATE,
                "R74_LOCK_OR_PATH_INDETERMINATE",null,0L);
        }
    }

    private Review inspectUnderLock(String username)throws IOException{
        R60TradePairCoordinator.requireHeldOnCurrentThread(journal);
        validateRoots();
        if(username==null||
           !username.matches("[a-z][a-z0-9_]{0,31}"))
            return result(Status.BLOCK_AMBIGUOUS_EVIDENCE,
                "R74_INVALID_USERNAME",null,0L);

        Path r73=canonical(evidence).resolve(
            "r73-"+username+".intent.properties"
        );
        // An interrupted adjacent atomic stage must never be treated
        // as absence of an old write, even with no canonical marker.
        AtomicAccountPublisher.requireNoAmbiguousStage(r73);
        if(!Files.exists(r73,LinkOption.NOFOLLOW_LINKS))
            return result(Status.BLOCK_MISSING_WRITE_INTENT,
                "R74_MISSING_REGISTERED_WRITE_INTENT",null,0L);

        final Properties blocker;
        try{
            blocker=readStrict(r73,R73_KEYS,R73_FORMAT,"format");
            if(!username.equals(blocker.getProperty("username")))
                throw new IOException("R74_INTENT_ACCOUNT_MISMATCH");
            positive(blocker,"epoch");
            positive(blocker,"generation");
            positive(blocker,"journalRevision");
            positive(blocker,"saveSequence");
            canonicalUuid(blocker.getProperty("owner"));
            digest(blocker.getProperty("beforeDigest"));
            digest(blocker.getProperty("afterDigest"));
        }catch(IOException|RuntimeException invalid){
            return result(Status.BLOCK_AMBIGUOUS_EVIDENCE,
                "R74_INVALID_R73_WRITE_INTENT",null,0L);
        }

        final UUID id;
        final Properties r72;
        try{
            // The R73 blocker has no R72 UUID, so enumerate all R72
            // phase file NAMES, then validate each INTENT. Unknown
            // R72-shaped names, symlink stages and duplicate account
            // transactions block the account instead of being ignored.
            Set<UUID> ids=scanR72Ids();
            Properties match=null;
            UUID matchingId=null;
            for(UUID candidate:ids){
                Path intent=marker(candidate,"INTENT");
                Properties p=readStrict(
                    intent,R72_KEYS,R72_FORMAT,"format"
                );
                if(!candidate.toString().equals(p.getProperty("id"))||
                   !"INTENT".equals(p.getProperty("phase")))
                    throw new IOException("R74_R72_INTENT_ID_MISMATCH");
                validateR72Fields(p);
                R72OfflineInFlightCommitEvidence.Review review=
                    R72OfflineInFlightCommitEvidence.inspect(
                        evidence,candidate
                    );
                if(review.status==
                       R72OfflineInFlightCommitEvidence.ReviewStatus
                           .BLOCK_AMBIGUOUS_EVIDENCE||
                   review.status==
                       R72OfflineInFlightCommitEvidence.ReviewStatus
                           .BLOCK_UNREGISTERED)
                    throw new IOException("R74_AMBIGUOUS_R72_PHASES");
                if(username.equals(p.getProperty("username"))){
                    if(matchingId!=null)
                        throw new IOException(
                            "R74_MULTIPLE_WRITE_INTENTS_ACCOUNT"
                        );
                    matchingId=candidate;
                    match=p;
                }
            }
            if(matchingId==null)
                return result(
                    Status.BLOCK_UNACKNOWLEDGED_WRITE,
                    "R74_R73_BLOCKER_WITHOUT_R72_INTENT",
                    null,0L
                );
            id=matchingId;
            r72=match;
            requireMatchingFields(blocker,r72);
        }catch(IOException|RuntimeException invalid){
            return result(Status.BLOCK_AMBIGUOUS_EVIDENCE,
                "R74_AMBIGUOUS_OR_MISMATCHED_R72_INTENT",
                null,0L);
        }

        R72OfflineInFlightCommitEvidence.Review phases=
            R72OfflineInFlightCommitEvidence.inspect(evidence,id);
        switch(phases.status){
            case BLOCK_REGISTERED_UNCERTAIN:
            case BLOCK_INFLIGHT_UNCERTAIN:
            case BLOCK_CANCELLED_UNCERTAIN:
                return result(Status.BLOCK_UNACKNOWLEDGED_WRITE,
                    "R74_WRITE_OUTCOME_UNACKNOWLEDGED",id,0L);
            case BLOCK_FAILED_OR_PARTIAL:
                return result(Status.BLOCK_FAILED_OR_PARTIAL,
                    "R74_WRITE_REPORTED_FAILURE_PARTIAL_UNKNOWN",id,0L);
            case BLOCK_COMPLETION_NOT_AUTHORIZED:
            case BLOCK_LATE_COMMIT_OBSERVED:
                break;
            default:
                return result(Status.BLOCK_AMBIGUOUS_EVIDENCE,
                    "R74_UNKNOWN_OR_INVALID_R72_EVIDENCE",id,0L);
        }

        // The ACK and canonical/ledger/owner files are read while
        // holding the same R60 cross-process exclusive lock. Do not
        // call R64.inspect here: it would reacquire the held OS lock.
        final R63AccountSaveAdmission.Check canonical;
        try{
            canonical=R63AccountSaveAdmission.inspectUnderLock(
                journal,accounts,generations,username
            );
        }catch(IOException|RuntimeException invalid){
            return result(Status.BLOCK_EVIDENCE_MISMATCH,
                "R74_CANONICAL_ACCOUNT_OR_LEDGER_INDETERMINATE",
                id,0L);
        }
        if(canonical.status==
                R63AccountSaveAdmission.Status.BLOCK_PENDING_PAIR)
            return result(Status.BLOCK_PENDING_PAIR,
                "R74_INDEXED_TRADE_PENDING",id,0L);
        if(canonical.status!=
                R63AccountSaveAdmission.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS||
           canonical.token==null)
            return result(Status.BLOCK_EVIDENCE_MISMATCH,
                "R74_CANONICAL_LEDGER_NOT_CONSISTENT",id,0L);

        final long beforeGeneration;
        try{
            beforeGeneration=positive(blocker,"generation");
        }catch(IOException invalid){
            return result(Status.BLOCK_AMBIGUOUS_EVIDENCE,
                "R74_INVALID_BEFORE_GENERATION",id,0L);
        }
        if(beforeGeneration==Long.MAX_VALUE||
           canonical.token.generation!=beforeGeneration+1L||
           !canonical.token.snapshotDigest.equals(
               blocker.getProperty("afterDigest")
           )||
           canonical.token.journalRevision!=
               positive(blocker,"journalRevision"))
            return result(Status.BLOCK_EVIDENCE_MISMATCH,
                "R74_AFTER_ACCOUNT_GENERATION_DIGEST_REVISION_MISMATCH",
                id,canonical.token.generation);

        final R64OfflineSessionOwnership.Token released;
        try{
            released=
                R64OfflineSessionOwnership.requireReleasedOwnerUnderLock(
                    journal,accounts,generations,sessions,username
                );
        }catch(IOException|RuntimeException mismatch){
            return result(Status.BLOCK_OWNER_NOT_RELEASED,
                "R74_OWNER_NOT_RELEASED_OR_MISMATCHED",id,
                canonical.token.generation);
        }
        if(!released.owner.toString().equals(
                blocker.getProperty("owner"))||
           released.epoch!=positive(blocker,"epoch")||
           released.account.generation!=canonical.token.generation||
           released.account.journalRevision!=
               canonical.token.journalRevision||
           !released.account.snapshotDigest.equals(
               canonical.token.snapshotDigest))
            return result(Status.BLOCK_EVIDENCE_MISMATCH,
                "R74_RELEASED_OWNER_TOKEN_DID_NOT_MATCH_INTENT",id,
                canonical.token.generation);

        boolean cancellation=
            phases.status==
                R72OfflineInFlightCommitEvidence.ReviewStatus
                    .BLOCK_LATE_COMMIT_OBSERVED;
        return result(
            cancellation
                ?Status.BLOCK_CANCELLED_LATE_ACK_QUARANTINED
                :Status.BLOCK_CONSISTENT_BUT_QUARANTINED,
            cancellation
                ?"R74_LATE_ACK_ACCOUNT_CONSISTENT_MANUAL_REVIEW_ONLY"
                :"R74_ACK_ACCOUNT_CONSISTENT_MANUAL_REVIEW_ONLY",
            id,canonical.token.generation
        );
    }

    private void requireMatchingFields(
        Properties r73,Properties r72
    )throws IOException{
        String[][] fields={
            {"username","username"},
            {"owner","owner"},
            {"epoch","epoch"},
            {"generation","accountGeneration"},
            {"journalRevision","journalRevision"},
            {"beforeDigest","beforeDigest"},
            {"afterDigest","afterDigest"},
            {"saveSequence","sequence"}
        };
        for(String[] pair:fields)
            if(!Objects.equals(
                    r73.getProperty(pair[0]),
                    r72.getProperty(pair[1])))
                throw new IOException(
                    "R74_R72_R73_FIELD_MISMATCH "+pair[0]
                );
    }

    private Set<UUID> scanR72Ids()throws IOException{
        Set<UUID> ids=new HashSet<>();
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(
                canonical(evidence)
        )){
            for(Path entry:entries){
                String name=entry.getFileName().toString();
                if(name.startsWith(".r72-")||
                   name.startsWith(".r73-"))
                    throw new IOException(
                        "R74_UNRESOLVED_ATOMIC_STAGE_NAME"
                    );
                if(!name.startsWith("r72-"))continue;
                if(!Files.isRegularFile(
                        entry,LinkOption.NOFOLLOW_LINKS)||
                   Files.isSymbolicLink(entry)||
                   !name.matches(
                       "r72-[0-9a-f-]{36}-"+
                       "(INTENT|ENTERED|CANCEL_OBSERVED|"+
                       "ACK_OBSERVED|FAILURE_OBSERVED)\\.properties"))
                    throw new IOException(
                        "R74_UNKNOWN_R72_EVIDENCE_FILENAME"
                    );
                UUID id=canonicalUuid(name.substring(4,40));
                ids.add(id);
            }
        }
        for(UUID id:ids){
            for(String phase:PHASES)
                AtomicAccountPublisher.requireNoAmbiguousStage(
                    marker(id,phase)
                );
        }
        return ids;
    }

    private Path marker(UUID id,String phase){
        return canonical(evidence).resolve(
            "r72-"+id+"-"+phase+".properties"
        );
    }

    private static Properties readStrict(
        Path file,Set<String> keys,String format,String field
    )throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(file);
        BasicFileAttributes attributes=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attributes.isRegularFile()||
           attributes.size()<=0||
           attributes.size()>MAX_MARKER_BYTES)
            throw new IOException("R74_INVALID_EVIDENCE_TYPE_OR_SIZE");
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(
                file,LinkOption.NOFOLLOW_LINKS
        )){
            p.load(in);
        }
        if(!p.stringPropertyNames().equals(keys)||
           !format.equals(p.getProperty(field)))
            throw new IOException("R74_INVALID_EVIDENCE_SCHEMA");
        return p;
    }

    private static void validateR72Fields(Properties p)
            throws IOException{
        if(!p.getProperty("username","").matches(
                "[a-z][a-z0-9_]{0,31}"))
            throw new IOException("R74_INVALID_R72_ACCOUNT");
        canonicalUuid(p.getProperty("owner"));
        positive(p,"sequence");
        positive(p,"epoch");
        positive(p,"accountGeneration");
        positive(p,"journalRevision");
        digest(p.getProperty("beforeDigest"));
        digest(p.getProperty("afterDigest"));
    }

    private static UUID canonicalUuid(String raw)throws IOException{
        try{
            UUID id=UUID.fromString(raw);
            if(!id.toString().equals(raw))
                throw new IllegalArgumentException("noncanonical UUID");
            return id;
        }catch(IllegalArgumentException|NullPointerException invalid){
            throw new IOException("R74_INVALID_UUID",invalid);
        }
    }

    private static void digest(String raw)throws IOException{
        if(raw==null||!raw.matches("[0-9a-f]{64}"))
            throw new IOException("R74_INVALID_SHA256");
    }

    private static long positive(Properties p,String key)
            throws IOException{
        String text=p.getProperty(key);
        if(text==null||!text.matches("[1-9][0-9]*"))
            throw new IOException("R74_INVALID_POSITIVE "+key);
        try{return Long.parseLong(text);}
        catch(NumberFormatException invalid){
            throw new IOException("R74_INVALID_NUMBER "+key,invalid);
        }
    }

    private void validateRoots()throws IOException{
        Path[] roots={
            canonical(journal),canonical(accounts),
            canonical(generations),canonical(sessions),
            canonical(evidence)
        };
        for(int i=0;i<roots.length;i++){
            for(Path p=roots[i];p!=null;p=p.getParent())
                if(Files.isSymbolicLink(p))
                    throw new IOException(
                        "R74_SYMLINK_ROOT_DENIED"
                    );
            BasicFileAttributes attributes=Files.readAttributes(
                roots[i],BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
            );
            if(!attributes.isDirectory())
                throw new IOException("R74_INVALID_EVIDENCE_ROOT");
            for(int j=0;j<i;j++)
                if(roots[i].startsWith(roots[j])||
                   roots[j].startsWith(roots[i]))
                    throw new IOException("R74_OVERLAPPING_ROOTS");
        }
    }

    private static Path canonical(Path p){
        return p.toAbsolutePath().normalize();
    }
    private static Review result(
        Status status,String reason,UUID id,long generation
    ){
        return new Review(status,reason,id,generation);
    }

}
