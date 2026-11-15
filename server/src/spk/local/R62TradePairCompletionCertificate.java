package spk.local;

import java.io.IOException;
import java.io.InputStream;
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
 * R62 OFFLINE-ONLY candidate for human-reviewed transaction completion.
 *
 * A separate immutable certificate is published ONLY after R61's read-only
 * verifier proves both canonical accounts exactly match the same committed
 * pair, the payloads, the indexed PREPARED journal, and the R59 revision.
 * R60's cooperative OS FileLock covers each whole check/publish and review.
 *
 * A candidate is NOT a release! The R59 pending intent remains indexed,
 * both real logins are still blocked and no game account is changed here.
 * The caller must use test-owned directories; no live integration exists.
 */
final class R62TradePairCompletionCertificate {
    private static final String FORMAT="SpawnPK-R62-OfflineCompletion-v1";
    private static final String PHASE="VERIFIED_OFFLINE";
    private static final int MAX_CERTIFICATE_BYTES=4096;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","phase","transaction.id","participant.a",
        "participant.b","a.after.sha256","b.after.sha256",
        "index.revision"
    ));

    enum Status {
        OFFLINE_COMPLETION_CANDIDATE,
        BLOCK_INDETERMINATE
    }

    static final class Review {
        final Status status;
        final String reason;
        final long indexRevision;
        Review(Status status,String reason,long revision){
            this.status=status;
            this.reason=reason;
            indexRevision=revision;
        }
        boolean loginAuthorized(){return false;}
        boolean journalQuarantineReleased(){return false;}
        boolean automaticAccountRecoveryAttempted(){return false;}
    }

    private static final class Certificate {
        final UUID id;
        final String a,b,hashA,hashB;
        final long revision;
        Certificate(UUID id,String a,String b,String hashA,
                    String hashB,long revision){
            this.id=id;this.a=a;this.b=b;
            this.hashA=hashA;this.hashB=hashB;
            this.revision=revision;
        }
    }

    static void publishCandidate(
        Path journalRoot,Path accountRoot,Path payloadRoot,
        Path completionRoot,UUID id
    )throws IOException{
        Objects.requireNonNull(id,"id");
        R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->{
                Path completion=checkedCompletionRoot(
                    journalRoot,accountRoot,payloadRoot,completionRoot
                );
                Path target=certificatePath(completion,id);
                if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException(
                        "R62_CERTIFICATE_DENIED duplicate/immutable decision"
                    );
                R61TradePairReplay.VerifiedPair pair=
                    R61TradePairReplay.verifyFullyPublishedUnderLock(
                        journalRoot,accountRoot,payloadRoot,id
                    );
                Properties p=new Properties();
                p.setProperty("format",FORMAT);
                p.setProperty("phase",PHASE);
                p.setProperty("transaction.id",id.toString());
                p.setProperty("participant.a",pair.first);
                p.setProperty("participant.b",pair.second);
                p.setProperty("a.after.sha256",pair.firstPostHash);
                p.setProperty("b.after.sha256",pair.secondPostHash);
                p.setProperty("index.revision",
                    Long.toString(pair.indexRevision));
                // Only the completion fixture directory is written; do
                // not touch accounts, journal or R59's index.
                AtomicAccountPublisher.publish(
                    target,p,"R62 offline candidate - NOT login release"
                );
                // Reload exact certificate while STILL holding the lock.
                Certificate fresh=readCertificate(target);
                verifyMatches(pair,fresh);
                return null;
            }
        );
    }

    /**
     * Read-only eligibility. Every failure (busy lock, missing certificate,
     * mismatched revision, untrusted account state, malformed stage) BLOCKS.
     * Even successful review never grants login or retires the index.
     */
    static Review inspectCandidate(
        Path journalRoot,Path accountRoot,Path payloadRoot,
        Path completionRoot,UUID id
    ){
        if(id==null)
            return blocked("INVALID_TRANSACTION");
        try{
            return R60TradePairCoordinator.withExclusive(
                journalRoot,false,()->{
                    Path completion=checkedCompletionRoot(
                        journalRoot,accountRoot,payloadRoot,completionRoot
                    );
                    Certificate cert=readCertificate(
                        certificatePath(completion,id)
                    );
                    if(!id.equals(cert.id))
                        throw new IOException(
                            "R62_REVIEW_DENIED transaction mismatch"
                        );
                    R61TradePairReplay.VerifiedPair pair=
                        R61TradePairReplay.verifyFullyPublishedUnderLock(
                            journalRoot,accountRoot,payloadRoot,id
                        );
                    verifyMatches(pair,cert);
                    return new Review(
                        Status.OFFLINE_COMPLETION_CANDIDATE,
                        "CANDIDATE_ONLY_REQUIRES_EXTERNAL_AUTHORITY",
                        pair.indexRevision
                    );
                }
            );
        }catch(IOException|RuntimeException failure){
            return blocked("COMPLETION_EVIDENCE_OR_LOCK_INDETERMINATE");
        }
    }

    private static void verifyMatches(
        R61TradePairReplay.VerifiedPair pair,Certificate cert
    )throws IOException{
        if(!pair.id.equals(cert.id)||
           !pair.first.equals(cert.a)||
           !pair.second.equals(cert.b)||
           !pair.firstPostHash.equals(cert.hashA)||
           !pair.secondPostHash.equals(cert.hashB)||
           pair.indexRevision!=cert.revision)
            throw new IOException(
                "R62_REVIEW_DENIED certificate vs committed pair drift"
            );
    }

    private static Review blocked(String reason){
        return new Review(Status.BLOCK_INDETERMINATE,reason,-1L);
    }

    private static Path certificatePath(Path dir,UUID id){
        return dir.resolve(
            "completion-"+id.toString()+".properties"
        );
    }

    private static Path checkedCompletionRoot(
        Path journalRoot,Path accountRoot,Path payloadRoot,
        Path completionRoot
    )throws IOException{
        if(completionRoot==null||journalRoot==null||
           accountRoot==null||payloadRoot==null)
            throw new IOException(
                "R62_PATH_DENIED missing directory"
            );
        Path root=completionRoot.toAbsolutePath().normalize();
        if(root.equals(journalRoot.toAbsolutePath().normalize())||
           root.equals(accountRoot.toAbsolutePath().normalize())||
           root.equals(payloadRoot.toAbsolutePath().normalize()))
            throw new IOException(
                "R62_PATH_DENIED shared directory"
            );
        for(Path p=root;p!=null;p=p.getParent()){
            if(Files.isSymbolicLink(p))
                throw new IOException(
                    "R62_PATH_DENIED symlink ancestor"
                );
        }
        BasicFileAttributes attrs=Files.readAttributes(
            root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isDirectory())
            throw new IOException(
                "R62_PATH_DENIED not a directory"
            );
        return root;
    }

    private static Certificate readCertificate(Path file)
            throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(file);
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<=0||
           attrs.size()>MAX_CERTIFICATE_BYTES)
            throw new IOException(
                "R62_CERTIFICATE_DENIED invalid size/type"
            );
        Properties p=new Properties();
        try(InputStream input=Files.newInputStream(
                file,LinkOption.NOFOLLOW_LINKS)){
            p.load(input);
        }
        if(!p.stringPropertyNames().equals(KEYS)||
           !FORMAT.equals(p.getProperty("format"))||
           !PHASE.equals(p.getProperty("phase")))
            throw new IOException(
                "R62_CERTIFICATE_DENIED malformed schema/phase"
            );

        UUID id;
        String raw=p.getProperty("transaction.id");
        try{
            id=UUID.fromString(raw);
            if(!id.toString().equals(raw))
                throw new IllegalArgumentException("noncanonical");
        }catch(IllegalArgumentException issue){
            throw new IOException(
                "R62_CERTIFICATE_DENIED invalid UUID",issue
            );
        }
        String a=p.getProperty("participant.a");
        String b=p.getProperty("participant.b");
        if(a==null||b==null||
           !a.matches("[a-z][a-z0-9_]{0,31}")||
           !b.matches("[a-z][a-z0-9_]{0,31}")||
           a.compareTo(b)>=0)
            throw new IOException(
                "R62_CERTIFICATE_DENIED invalid participant pair"
            );
        String hashA=p.getProperty("a.after.sha256");
        String hashB=p.getProperty("b.after.sha256");
        if(hashA==null||hashB==null||
           !hashA.matches("[0-9a-f]{64}")||
           !hashB.matches("[0-9a-f]{64}"))
            throw new IOException(
                "R62_CERTIFICATE_DENIED invalid snapshot hashes"
            );
        String revision=p.getProperty("index.revision");
        if(revision==null||!revision.matches("[1-9][0-9]*"))
            throw new IOException(
                "R62_CERTIFICATE_DENIED invalid revision"
            );
        long number;
        try{
            number=Long.parseLong(revision);
        }catch(NumberFormatException issue){
            throw new IOException(
                "R62_CERTIFICATE_DENIED revision overflow",issue
            );
        }
        return new Certificate(
            id,a,b,hashA,hashB,number
        );
    }

    private R62TradePairCompletionCertificate(){}
}
