package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * R63 OFFLINE-ONLY cooperative account-save generation and admission fence.
 *
 * All R63 reads, initializations and saves hold R60's same operating-system
 * FileLock as paired trade registration, R61 replay and R62 certificate
 * review. A save must present a token obtained by a previous validated
 * read (generation, exact snapshot hash and journal revision). Every field
 * is revalidated under the exclusive lock, immediately before mutation.
 *
 * Crash after saving a canonical account but before updating this ledger
 * leaves a hash mismatch, so subsequent R63 inspection refuses admission.
 * It does not invent or automatically repair the missing generation.
 *
 * NO real LocalSession, TradeService or production autosave uses this yet.
 * Only cooperative OFFLINE writers are fenced, and these are not live login
 * authorizations or a physically power-fail-atomic account commit.
 */
final class R63AccountSaveAdmission {
    private static final String FORMAT="SpawnPK-R63-AccountGeneration-v1";
    private static final int MAX_LEDGER_BYTES=4096;
    private static final Set<String> LEDGER_KEYS=new HashSet<>(
        Arrays.asList("format","username","generation","snapshot.sha256")
    );

    enum Status {
        READY_FOR_OTHER_OFFLINE_CHECKS,
        BLOCK_PENDING_PAIR,
        BLOCK_INDETERMINATE
    }

    static final class Token {
        final String username;
        final long generation;
        final long journalRevision;
        final String snapshotDigest;
        Token(String name,long generation,long indexRevision,String digest){
            username=name;
            this.generation=generation;
            journalRevision=indexRevision;
            snapshotDigest=digest;
        }
    }

    static final class Check {
        final Status status;
        final Token token;
        final String reason;
        Check(Status status,Token token,String reason){
            this.status=status;
            this.token=token;
            this.reason=reason;
        }
        boolean loginAuthorized(){return false;}
        boolean automaticRecoveryAttempted(){return false;}
        boolean canonicalAccountChanged(){return false;}
    }

    private static final class Ledger {
        final String username;
        final long generation;
        final String digest;
        Ledger(String username,long generation,String digest){
            this.username=username;
            this.generation=generation;
            this.digest=digest;
        }
    }

    /**
     * Explicit first registration of an EXISTING account, NEVER a default
     * profile. Only on a known, clear journal and a missing state file.
     */
    static Token initializeExisting(
        Path journalRoot,Path accountRoot,Path stateRoot,String account
    )throws IOException{
        return R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->{
                validateRoots(journalRoot,accountRoot,stateRoot);
                validateUsername(account);
                R59TradePairIntentIndex.Result journal=
                    R59TradePairIntentIndex.inspect(journalRoot,account);
                if(journal.status!=
                        R59TradePairIntentIndex.Status.NO_INDEXED_PENDING_PAIR)
                    throw new IOException(
                        "R63_INITIALIZE_DENIED unresolved/ambiguous pair"
                    );
                Path ledger=ledgerPath(stateRoot,account);
                if(Files.exists(ledger,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException(
                        "R63_INITIALIZE_DENIED state already registered"
                    );
                // An ambiguous staged first-generation record is NOT a
                // reason to invent a new ledger or delete the stage.
                AtomicAccountPublisher.requireNoAmbiguousStage(ledger);
                PlayerSnapshot snapshot=loadExisting(accountRoot,account);
                String hash=R57TradePairJournal.digest(snapshot);
                publishLedger(ledger,new Ledger(account,1L,hash));
                return new Token(account,1L,journal.revision,hash);
            }
        );
    }

    /** Lookup is observational and always locks the same authority. */
    static Check inspect(
        Path journalRoot,Path accountRoot,Path stateRoot,String account
    ){
        try{
            return R60TradePairCoordinator.withExclusive(
                journalRoot,false,
                ()->inspectUnderLock(
                    journalRoot,accountRoot,stateRoot,account
                )
            );
        }catch(IOException|RuntimeException unavailable){
            return new Check(
                Status.BLOCK_INDETERMINATE,null,
                "R63_ACCOUNT_FENCE_UNAVAILABLE"
            );
        }
    }

    /**
     * Test-only single-account mutation under the same lock as R60.
     * The caller must present an unexpired token, and the proposed snapshot
     * must match the account/version. A revision mismatch fails before
     * changing canonical bytes.
     */
    static Token saveIfCurrent(
        Path journalRoot,Path accountRoot,Path stateRoot,
        Token expected,PlayerSnapshot proposed,boolean injectAfterAccountSave
    )throws IOException{
        Objects.requireNonNull(expected,"expected token");
        Objects.requireNonNull(proposed,"proposed snapshot");
        return R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->{
                Check current=inspectUnderLock(
                    journalRoot,accountRoot,stateRoot,expected.username
                );
                if(current.status!=Status.READY_FOR_OTHER_OFFLINE_CHECKS||
                   current.token==null)
                    throw new IOException(
                        "R63_SAVE_DENIED account blocked or indeterminate"
                    );
                Token actual=current.token;
                if(actual.generation!=expected.generation||
                   actual.journalRevision!=expected.journalRevision||
                   !actual.snapshotDigest.equals(expected.snapshotDigest))
                    throw new IOException(
                        "R63_SAVE_DENIED stale generation/hash/index revision"
                    );
                if(!expected.username.equals(proposed.username())||
                   proposed.version()!=PlayerSnapshot.CURRENT_VERSION)
                    throw new IOException(
                        "R63_SAVE_DENIED foreign account/version"
                    );
                if(actual.generation==Long.MAX_VALUE)
                    throw new IOException(
                        "R63_SAVE_DENIED generation overflow"
                    );
                String nextDigest=R57TradePairJournal.digest(proposed);
                if(nextDigest.equals(actual.snapshotDigest))
                    throw new IOException(
                        "R63_SAVE_DENIED unchanged snapshot"
                    );

                FilePlayerRepository repository=repository(accountRoot);
                // The pending R16 stage protocol refuses ambiguous
                // publication. If save succeeds and the ledger update
                // does not, all subsequent R63 inspections see a digest
                // mismatch and fail closed. No auto-resync or rollback.
                repository.save(proposed);
                PlayerSnapshot saved=loadExisting(
                    accountRoot,expected.username
                );
                if(!nextDigest.equals(
                        R57TradePairJournal.digest(saved)))
                    throw new IOException(
                        "R63_SAVE_DENIED post-save verification failed"
                    );
                if(injectAfterAccountSave)
                    throw new IOException(
                        "R63_INJECTED_AFTER_CANONICAL_SAVE_BEFORE_LEDGER"
                    );
                publishLedger(
                    ledgerPath(stateRoot,expected.username),
                    new Ledger(expected.username,
                        actual.generation+1L,nextDigest)
                );
                return new Token(
                    expected.username,actual.generation+1L,
                    actual.journalRevision,nextDigest
                );
            }
        );
    }

    private static Check inspectUnderLock(
        Path journalRoot,Path accountRoot,Path stateRoot,String account
    )throws IOException{
        validateRoots(journalRoot,accountRoot,stateRoot);
        validateUsername(account);
        R59TradePairIntentIndex.Result journal=
            R59TradePairIntentIndex.inspect(journalRoot,account);
        if(journal.status==
                R59TradePairIntentIndex.Status.BLOCK_INDEXED_PAIR)
            return new Check(
                Status.BLOCK_PENDING_PAIR,null,
                "R63_PENDING_PAIR_BLOCKS_ALL_ACCOUNT_WRITES"
            );
        if(journal.status!=
                R59TradePairIntentIndex.Status.NO_INDEXED_PENDING_PAIR)
            return new Check(
                Status.BLOCK_INDETERMINATE,null,
                "R63_JOURNAL_INDETERMINATE"
            );

        Ledger ledger=readLedger(ledgerPath(stateRoot,account));
        PlayerSnapshot canonical=loadExisting(accountRoot,account);
        String currentDigest=R57TradePairJournal.digest(canonical);
        if(!ledger.username.equals(account)||
           !ledger.digest.equals(currentDigest))
            return new Check(
                Status.BLOCK_INDETERMINATE,null,
                "R63_CANONICAL_GENERATION_HASH_MISMATCH"
            );
        return new Check(
            Status.READY_FOR_OTHER_OFFLINE_CHECKS,
            new Token(
                account,ledger.generation,
                journal.revision,currentDigest
            ),
            "R63_OFFLINE_FENCE_ONLY_NOT_REAL_LOGIN_AUTHORIZATION"
        );
    }

    private static FilePlayerRepository repository(Path root){
        return new FilePlayerRepository(
            account->root.toAbsolutePath().normalize().resolve(
                account+".properties"
            )
        );
    }

    private static PlayerSnapshot loadExisting(Path root,String account)
            throws IOException{
        Optional<PlayerSnapshot> snapshot=repository(root).load(account);
        if(!snapshot.isPresent()||
           !account.equals(snapshot.get().username())||
           snapshot.get().version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new IOException(
                "R63_ACCOUNT_DENIED missing/untrusted existing snapshot"
            );
        return snapshot.get();
    }

    private static void validateUsername(String name)throws IOException{
        if(name==null||!name.matches("[a-z][a-z0-9_]{0,31}"))
            throw new IOException("R63_ACCOUNT_DENIED invalid identity");
    }

    private static Path ledgerPath(Path stateRoot,String account){
        return stateRoot.toAbsolutePath().normalize().resolve(
            account+".r63-generation.properties"
        );
    }

    private static void validateRoots(
        Path journalRoot,Path accountRoot,Path stateRoot
    )throws IOException{
        if(journalRoot==null||accountRoot==null||stateRoot==null)
            throw new IOException("R63_ROOT_DENIED missing directory");
        Path[] dirs=new Path[]{
            journalRoot.toAbsolutePath().normalize(),
            accountRoot.toAbsolutePath().normalize(),
            stateRoot.toAbsolutePath().normalize()
        };
        for(Path path:dirs){
            for(Path p=path;p!=null;p=p.getParent())
                if(Files.isSymbolicLink(p))
                    throw new IOException(
                        "R63_ROOT_DENIED symlink ancestor"
                    );
            BasicFileAttributes attrs=Files.readAttributes(
                path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
            if(!attrs.isDirectory())
                throw new IOException(
                    "R63_ROOT_DENIED non-directory"
                );
        }
        for(int a=0;a<dirs.length;a++){
            for(int b=a+1;b<dirs.length;b++){
                if(dirs[a].startsWith(dirs[b])||
                   dirs[b].startsWith(dirs[a]))
                    throw new IOException(
                        "R63_ROOT_DENIED overlapping directories"
                    );
            }
        }
    }

    private static void publishLedger(Path file,Ledger value)
            throws IOException{
        Properties p=new Properties();
        p.setProperty("format",FORMAT);
        p.setProperty("username",value.username);
        p.setProperty("generation",Long.toString(value.generation));
        p.setProperty("snapshot.sha256",value.digest);
        AtomicAccountPublisher.publish(
            file,p,"R63 offline account generation ledger"
        );
    }

    private static Ledger readLedger(Path file)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(file);
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<=0||
           attrs.size()>MAX_LEDGER_BYTES)
            throw new IOException(
                "R63_LEDGER_DENIED bad generation record"
            );
        Properties p=new Properties();
        try(InputStream input=Files.newInputStream(
                file,LinkOption.NOFOLLOW_LINKS)){
            p.load(input);
        }
        if(!p.stringPropertyNames().equals(LEDGER_KEYS)||
           !FORMAT.equals(p.getProperty("format")))
            throw new IOException(
                "R63_LEDGER_DENIED malformed schema"
            );
        String name=p.getProperty("username");
        validateUsername(name);
        String raw=p.getProperty("generation");
        if(raw==null||!raw.matches("[1-9][0-9]*"))
            throw new IOException(
                "R63_LEDGER_DENIED invalid generation"
            );
        long generation;
        try{
            generation=Long.parseLong(raw);
        }catch(NumberFormatException invalid){
            throw new IOException(
                "R63_LEDGER_DENIED overflow generation",invalid
            );
        }
        String digest=p.getProperty("snapshot.sha256");
        if(digest==null||!digest.matches("[0-9a-f]{64}"))
            throw new IOException(
                "R63_LEDGER_DENIED invalid snapshot hash"
            );
        return new Ledger(name,generation,digest);
    }

    private R63AccountSaveAdmission(){}
}
