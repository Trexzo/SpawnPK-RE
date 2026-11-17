package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R83 OFFLINE TEST ONLY. A caller-provided *synthetic* operation UUID
 * plus the R82 source-local ordinal identify a conservative PREPARED
 * witness in a disposable directory. The real CombatOutcome format
 * contains NO stable producer-issued transaction identifier.
 *
 * A PREPARED witness is persisted via the existing forced-stage and
 * REQUIRED atomic replace helper BEFORE objective/match/collection
 * writes begin. PREPARED is NEVER a completion certificate. After
 * interruption, reboot or mismatch, review always quarantines; it
 * never authorizes replay, rewards, rollback or login.
 *
 * Cooperative OS lock serializes only OTHER compliant R83 writers.
 * Filesystem power-loss durability, directory fsync, non-cooperative
 * processes and atomic cross-projection commits are NOT guaranteed.
 */
final class R83OfflinePreparedCombatReceiptJournal
    implements R82OfflineSourceScopedCombatReceiptLedger.OfflinePreparedAdmission {

    private static final String FORMAT="SpawnPK-R83-OFFLINE-Prepared-v1";
    private static final String PHASE="PREPARED";
    private static final int MAX_RECORD_BYTES=8192;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","phase","fixture.operation","source.scope",
        "source.ordinal","fact.attacker","fact.victim",
        "fact.type","fact.context","fact.worldTick",
        "fact.authority","evidence.sha256"
    ));

    enum Status {
        PREPARED_QUARANTINED,
        MISSING_BLOCKED,
        INDETERMINATE_BLOCKED
    }

    static final class Review {
        final Status status;
        final String reason;
        Review(Status status,String reason){
            this.status=status;
            this.reason=reason;
        }
        boolean replayAllowed(){return false;}
        boolean projectionComplete(){return false;}
        boolean loginAllowed(){return false;}
        boolean rewardAllowed(){return false;}
        @Override public String toString(){
            return "R83Review{"+status+",reason="+reason+"}";
        }
    }

    enum Fault {
        NONE,
        DENY_BEFORE_PREPARE,
        DENY_AFTER_PREPARE_BEFORE_PROJECTION
    }

    private final Path root;
    private final UUID fixtureOperation;
    private final Fault fault;
    private int prepared;
    private int refused;
    private String lastReason="NOT_ATTEMPTED";

    R83OfflinePreparedCombatReceiptJournal(
        Path testOwnedRoot,UUID syntheticOperation,Fault fault
    ){
        this.root=Objects.requireNonNull(
            testOwnedRoot,"root"
        ).toAbsolutePath().normalize();
        this.fixtureOperation=Objects.requireNonNull(
            syntheticOperation,"synthetic operation"
        );
        this.fault=Objects.requireNonNull(fault,"fault");
    }

    @Override public synchronized boolean prepare(
        String sourceScope,long ordinal,CombatOutcome fact
    ){
        Objects.requireNonNull(fact,"fact");
        if(ordinal<=0||sourceScope==null||
           fact.context()!=CombatOutcomeContext.PLAYER_PVP||
           (fact.type()!=CombatOutcomeType.PLAYER_KILL&&
            fact.type()!=CombatOutcomeType.PLAYER_DEATH)){
            refused++;
            lastReason="INVALID_SOURCE";
            return false;
        }

        try{
            String scope=UUID.fromString(sourceScope).toString();
            if(!scope.equals(sourceScope))
                throw new IOException("invalid source scope encoding");
            if(fault==Fault.DENY_BEFORE_PREPARE){
                refused++;
                lastReason="INJECTED_BEFORE_PREPARE";
                return false;
            }
            Files.createDirectories(root);
            if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("fixture directory invalid");
            if(Files.isSymbolicLink(root))
                throw new IOException("fixture root symlink");

            Path target=receiptPath(root,fixtureOperation,ordinal);
            Path mutex=root.resolve(".r83-prepared-writer.lock");
            try(FileChannel lockChannel=FileChannel.open(
                    mutex,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
                try(FileLock lock=lockChannel.tryLock()){
                    if(lock==null)
                        throw new IOException("cooperative lock busy");
                    // Never silently classify a leftover stage as
                    // abandoned or promote it over the canonical.
                    AtomicAccountPublisher.requireNoAmbiguousStage(target);
                    if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
                        throw new IOException("duplicate prepared receipt");

                    Properties evidence=new Properties();
                    evidence.setProperty("format",FORMAT);
                    evidence.setProperty("phase",PHASE);
                    evidence.setProperty("fixture.operation",
                        fixtureOperation.toString());
                    evidence.setProperty("source.scope",scope);
                    evidence.setProperty("source.ordinal",
                        Long.toString(ordinal));
                    evidence.setProperty("fact.attacker",fact.attacker());
                    evidence.setProperty("fact.victim",fact.victim());
                    evidence.setProperty("fact.type",fact.type().name());
                    evidence.setProperty("fact.context",fact.context().name());
                    evidence.setProperty("fact.worldTick",
                        Long.toString(fact.worldTick()));
                    evidence.setProperty("fact.authority",
                        fact.sourceAuthority());
                    evidence.setProperty("evidence.sha256",digest(evidence));

                    AtomicAccountPublisher.publish(
                        target,evidence,
                        "R83 test-only PREPARED; NEVER a completed projection"
                    );

                    // Fail closed unless the *canonical* published bytes
                    // parse correctly before any projection runs.
                    validatePrepared(target,fixtureOperation,ordinal);
                    prepared++;
                }
            }
            if(fault==Fault.DENY_AFTER_PREPARE_BEFORE_PROJECTION){
                refused++;
                lastReason="INJECTED_AFTER_PREPARE_BEFORE_PROJECTION";
                return false;
            }
            lastReason="PREPARED_BEFORE_PROJECTION";
            return true;
        }catch(IllegalArgumentException|IOException|
                OverlappingFileLockException failure){
            refused++;
            lastReason="PREPARE_REFUSED_"+
                failure.getClass().getSimpleName();
            return false;
        }
    }

    static Review review(
        Path testOwnedRoot,UUID fixtureOperation,long ordinal
    ){
        Objects.requireNonNull(fixtureOperation,"operation");
        Path root=Objects.requireNonNull(
            testOwnedRoot,"root"
        ).toAbsolutePath().normalize();
        if(ordinal<=0L)
            return new Review(Status.INDETERMINATE_BLOCKED,
                "INVALID_ORDINAL");
        try{
            if(Files.notExists(root,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_FIXTURE_ROOT");
            if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.INDETERMINATE_BLOCKED,
                    "INVALID_FIXTURE_ROOT");

            Path target=receiptPath(root,fixtureOperation,ordinal);
            AtomicAccountPublisher.requireNoAmbiguousStage(target);
            if(Files.notExists(target,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_PREPARED_WITNESS");
            validatePrepared(target,fixtureOperation,ordinal);
            return new Review(Status.PREPARED_QUARANTINED,
                "PREPARED_IS_NOT_COMMITTED");
        }catch(IOException|RuntimeException failure){
            return new Review(Status.INDETERMINATE_BLOCKED,
                "UNTRUSTED_OR_AMBIGUOUS_WITNESS");
        }
    }

    private static Path receiptPath(
        Path root,UUID operation,long ordinal
    ){
        return root.resolve("r83-"+operation+"-"+ordinal+".properties");
    }

    static Path receiptPathForTest(
        Path root,UUID operation,long ordinal
    ){
        return receiptPath(
            root.toAbsolutePath().normalize(),operation,ordinal
        );
    }

    private static void validatePrepared(
        Path path,UUID operation,long ordinal
    )throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("not a regular prepared witness");
        long size=Files.size(path);
        if(size<=0L||size>MAX_RECORD_BYTES)
            throw new IOException("missing or oversized prepared witness");

        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(
                path,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            p.load(in);
        }
        if(!p.stringPropertyNames().equals(KEYS)||
           !FORMAT.equals(p.getProperty("format"))||
           !PHASE.equals(p.getProperty("phase"))||
           !operation.toString().equals(p.getProperty(
               "fixture.operation"))||
           !Long.toString(ordinal).equals(p.getProperty(
               "source.ordinal")))
            throw new IOException("prepared witness schema mismatch");
        UUID scope;
        try{
            scope=UUID.fromString(p.getProperty("source.scope"));
            if(!scope.toString().equals(
                    p.getProperty("source.scope")))
                throw new IllegalArgumentException("noncanonical scope");
            Long.parseLong(p.getProperty("fact.worldTick"));
            CombatOutcomeType.valueOf(p.getProperty("fact.type"));
            CombatOutcomeContext.valueOf(p.getProperty("fact.context"));
        }catch(RuntimeException failure){
            throw new IOException("invalid prepared fact evidence",failure);
        }
        for(String key:Arrays.asList(
            "fact.attacker","fact.victim","fact.authority")){
            String value=p.getProperty(key);
            if(value==null||value.trim().isEmpty())
                throw new IOException("missing semantic fact metadata");
        }
        if(!digest(p).equals(p.getProperty("evidence.sha256")))
            throw new IOException("prepared evidence checksum mismatch");
        // This checksum detects accidental corruption only: it is
        // not a signature, credential or authenticated event ID.
    }

    private static String digest(Properties p)throws IOException{
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            for(String k:Arrays.asList(
                    "format","phase","fixture.operation","source.scope",
                    "source.ordinal","fact.attacker","fact.victim",
                    "fact.type","fact.context","fact.worldTick",
                    "fact.authority")){
                String value=p.getProperty(k);
                if(value==null)throw new IOException(
                    "missing prepared evidence field"
                );
                byte[] b=(k+"="+value+"\n").getBytes(
                    java.nio.charset.StandardCharsets.UTF_8
                );
                md.update(b);
            }
            byte[] bytes=md.digest();
            StringBuilder hex=new StringBuilder(64);
            for(byte b:bytes)
                hex.append(String.format("%02x",b&0xff));
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IOException("SHA-256 unavailable",impossible);
        }
    }

    int preparedCountForTest(){return prepared;}
    int refusedCountForTest(){return refused;}
    String lastReasonForTest(){return lastReason;}
}
