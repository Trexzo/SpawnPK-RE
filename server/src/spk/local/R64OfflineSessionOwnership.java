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
 * R64 OFFLINE session-ownership and account-save composition.
 *
 * Every R64 operation uses R60's shared cooperative OS FileLock. A session
 * token binds one owner UUID and monotonically increasing ownership epoch
 * to R63's account generation, snapshot SHA256 and R59 journal revision.
 * Stale save/late logout operations after an explicit successor takeover
 * refuse BEFORE changing canonical account files.
 *
 * The record is an observational/test-only authority. UUID possession is
 * NOT authentication, takeover requires an explicit prior token, and
 * no real LocalSession, live login, save, trade or recovery uses R64.
 * Incompletely published account+ledger+owner records remain quarantined,
 * never auto-repaired or granted login.
 */
final class R64OfflineSessionOwnership {
    private static final String FORMAT="SpawnPK-R64-OfflineSessionOwner-v1";
    private static final int MAX_RECORD_BYTES=4096;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","username","owner.id","owner.epoch","phase",
        "account.generation","journal.revision","snapshot.sha256"
    ));

    enum Phase {ACTIVE,RELEASED}
    enum Status{
        READY_FOR_OTHER_OFFLINE_CHECKS,
        BLOCK_RELEASED,
        BLOCK_PENDING_PAIR,
        BLOCK_INDETERMINATE
    }

    static final class Token {
        final String username;
        final UUID owner;
        final long epoch;
        final Phase phase;
        final R63AccountSaveAdmission.Token account;

        Token(String username,UUID owner,long epoch,Phase phase,
              R63AccountSaveAdmission.Token account){
            this.username=username;this.owner=owner;this.epoch=epoch;
            this.phase=phase;this.account=account;
        }
    }

    static final class Review {
        final Status status;
        final Token token;
        final String reason;
        Review(Status status,Token token,String reason){
            this.status=status;this.token=token;this.reason=reason;
        }
        boolean loginAuthorized(){return false;}
        boolean automaticRecoveryAttempted(){return false;}
        boolean accountFilesModified(){return false;}
    }

    /** Explicitly register an EXISTING R63 generation under a first owner. */
    static Token claimFirst(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,String username
    )throws IOException{
        return R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->{
                validateRoots(journalRoot,accountRoot,
                    generationRoot,sessionRoot);
                validateUsername(username);
                Path record=recordPath(sessionRoot,username);
                if(Files.exists(record,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException(
                        "R64_SESSION_DENIED already initialized"
                    );
                AtomicAccountPublisher.requireNoAmbiguousStage(record);
                R63AccountSaveAdmission.Token current=
                    requireAccountReady(journalRoot,accountRoot,
                        generationRoot,username);
                Token owner=new Token(
                    username,UUID.randomUUID(),1L,Phase.ACTIVE,current
                );
                writeOwner(record,owner);
                return owner;
            }
        );
    }

    /**
     * Explicit transfer based on a CURRENT prior owner record; this
     * deliberately supports ACTIVE-to-ACTIVE takeover to simulate a stale
     * disconnected session. NO credential/session authentication implied.
     */
    static Token replaceOwner(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,Token expectedPrior
    )throws IOException{
        Objects.requireNonNull(expectedPrior,"expected prior owner");
        return R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->{
                validateRoots(journalRoot,accountRoot,
                    generationRoot,sessionRoot);
                Token stored=readOwner(
                    recordPath(sessionRoot,expectedPrior.username)
                );
                requireMatches(stored,expectedPrior);
                R63AccountSaveAdmission.Token current=
                    requireAccountReady(journalRoot,accountRoot,
                        generationRoot,stored.username);
                requireAccountMatches(stored.account,current);
                if(stored.epoch==Long.MAX_VALUE)
                    throw new IOException(
                        "R64_SESSION_DENIED ownership epoch overflow"
                    );
                Token successor=new Token(
                    stored.username,UUID.randomUUID(),
                    stored.epoch+1L,Phase.ACTIVE,current
                );
                writeOwner(recordPath(sessionRoot,stored.username),successor);
                return successor;
            }
        );
    }

    /** Save, ledger update and owner-token advancement share ONE R60 lock. */
    static Token saveOwned(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,Token expected,PlayerSnapshot next,
        boolean injectAfterLedgerBeforeOwner
    )throws IOException{
        Objects.requireNonNull(expected,"expected owner");
        Objects.requireNonNull(next,"next snapshot");
        return R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->saveOwnedUnderLock(
                journalRoot,accountRoot,generationRoot,sessionRoot,
                expected,next,injectAfterLedgerBeforeOwner
            )
        );
    }

    /**
     * R65 composition: MUST be invoked from the thread currently holding
     * R60's real OS lock, so a deferred callback can make owner check,
     * account/ledger publication and optional logout one critical section.
     */
    static Token saveOwnedUnderLock(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,Token expected,PlayerSnapshot next,
        boolean injectAfterLedgerBeforeOwner
    )throws IOException{
        Objects.requireNonNull(expected,"expected owner");
        Objects.requireNonNull(next,"next snapshot");
        R60TradePairCoordinator.requireHeldOnCurrentThread(journalRoot);
        validateRoots(journalRoot,accountRoot,generationRoot,sessionRoot);
        Token stored=readOwner(recordPath(sessionRoot,expected.username));
        requireMatches(stored,expected);
        if(stored.phase!=Phase.ACTIVE)
            throw new IOException(
                "R64_SESSION_DENIED released owner cannot save"
            );
        R63AccountSaveAdmission.Token current=requireAccountReady(
            journalRoot,accountRoot,generationRoot,stored.username
        );
        requireAccountMatches(stored.account,current);
        R63AccountSaveAdmission.Token after=
            R63AccountSaveAdmission.saveIfCurrentUnderLock(
                journalRoot,accountRoot,generationRoot,
                stored.account,next,false
            );
        if(injectAfterLedgerBeforeOwner)
            throw new IOException(
                "R64_INJECTED_AFTER_LEDGER_BEFORE_OWNER_RECORD"
            );
        Token advanced=new Token(
            stored.username,stored.owner,stored.epoch,Phase.ACTIVE,after
        );
        writeOwner(recordPath(sessionRoot,stored.username),advanced);
        return advanced;
    }

    /** Logout fence: old token is unusable for another save after release. */
    static Token releaseOwner(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,Token expected
    )throws IOException{
        Objects.requireNonNull(expected,"expected owner");
        return R60TradePairCoordinator.withExclusive(
            journalRoot,false,()->releaseOwnerUnderLock(
                journalRoot,accountRoot,generationRoot,sessionRoot,expected
            )
        );
    }

    /** R65: validate ownership and persist RELEASED under an already held
     *  R60 OS lock; the prior ACTIVE token becomes unusable afterward. */
    static Token releaseOwnerUnderLock(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,Token expected
    )throws IOException{
        Objects.requireNonNull(expected,"expected owner");
        R60TradePairCoordinator.requireHeldOnCurrentThread(journalRoot);
        validateRoots(journalRoot,accountRoot,generationRoot,sessionRoot);
        Token stored=readOwner(recordPath(sessionRoot,expected.username));
        requireMatches(stored,expected);
        if(stored.phase!=Phase.ACTIVE)
            throw new IOException("R64_SESSION_DENIED already released");
        R63AccountSaveAdmission.Token current=requireAccountReady(
            journalRoot,accountRoot,generationRoot,stored.username
        );
        requireAccountMatches(stored.account,current);
        Token released=new Token(
            stored.username,stored.owner,stored.epoch,
            Phase.RELEASED,current
        );
        writeOwner(recordPath(sessionRoot,stored.username),released);
        return released;
    }

    /**
     * R66 authoritative offline event-dispatch seam. This is READ-ONLY.
     * Only valid while THIS THREAD holds the R60 cross-process OS lock.
     * It rejects a superseded or released owner, a stale canonical account
     * generation/hash or any ambiguous/pending R59 trade evidence BEFORE
     * an in-memory gameplay action is performed.
     */
    static void requireActiveOwnerUnderLock(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,Token expected
    )throws IOException{
        Objects.requireNonNull(expected,"expected session");
        R60TradePairCoordinator.requireHeldOnCurrentThread(journalRoot);
        validateRoots(journalRoot,accountRoot,generationRoot,sessionRoot);
        Token recorded=readOwner(
            recordPath(sessionRoot,expected.username)
        );
        requireMatches(recorded,expected);
        if(recorded.phase!=Phase.ACTIVE)
            throw new IOException(
                "R64_SESSION_DENIED inactive owner cannot dispatch event"
            );
        R63AccountSaveAdmission.Token canonical=requireAccountReady(
            journalRoot,accountRoot,generationRoot,recorded.username
        );
        requireAccountMatches(recorded.account,canonical);
    }

    /** Passive inspection never authorizes actual player login. */
    static Review inspect(
        Path journalRoot,Path accountRoot,Path generationRoot,
        Path sessionRoot,String username
    ){
        try{
            return R60TradePairCoordinator.withExclusive(
                journalRoot,false,()->{
                    validateRoots(journalRoot,accountRoot,
                        generationRoot,sessionRoot);
                    validateUsername(username);
                    R63AccountSaveAdmission.Check account=
                        R63AccountSaveAdmission.inspectUnderLock(
                            journalRoot,accountRoot,generationRoot,username
                        );
                    if(account.status==
                            R63AccountSaveAdmission.Status.BLOCK_PENDING_PAIR)
                        return new Review(
                            Status.BLOCK_PENDING_PAIR,null,
                            "R64_PENDING_PAIR_BLOCKS_SESSION"
                        );
                    if(account.status!=
                            R63AccountSaveAdmission.Status
                                .READY_FOR_OTHER_OFFLINE_CHECKS||
                       account.token==null)
                        return blocked("R64_ACCOUNT_GENERATION_INDETERMINATE");
                    Token stored=readOwner(recordPath(sessionRoot,username));
                    requireAccountMatches(stored.account,account.token);
                    if(stored.phase==Phase.RELEASED)
                        return new Review(
                            Status.BLOCK_RELEASED,null,
                            "R64_OWNER_RELEASED"
                        );
                    return new Review(
                        Status.READY_FOR_OTHER_OFFLINE_CHECKS,stored,
                        "R64_OFFLINE_ONLY_NOT_REAL_LOGIN_AUTHORIZATION"
                    );
                }
            );
        }catch(IOException|RuntimeException error){
            return blocked("R64_SESSION_OWNER_OR_LOCK_INDETERMINATE");
        }
    }

    private static Review blocked(String why){
        return new Review(Status.BLOCK_INDETERMINATE,null,why);
    }

    private static R63AccountSaveAdmission.Token requireAccountReady(
        Path journalRoot,Path accountRoot,Path generationRoot,String username
    )throws IOException{
        R63AccountSaveAdmission.Check check=
            R63AccountSaveAdmission.inspectUnderLock(
                journalRoot,accountRoot,generationRoot,username
            );
        if(check.status!=
                R63AccountSaveAdmission.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS||
           check.token==null)
            throw new IOException(
                "R64_SESSION_DENIED account/pair not clear"
            );
        return check.token;
    }

    private static void requireMatches(Token actual,Token expected)
            throws IOException{
        if(!actual.username.equals(expected.username)||
           !actual.owner.equals(expected.owner)||
           actual.epoch!=expected.epoch||
           actual.phase!=expected.phase)
            throw new IOException(
                "R64_SESSION_DENIED stale owner/epoch/phase"
            );
        requireAccountMatches(actual.account,expected.account);
    }

    private static void requireAccountMatches(
        R63AccountSaveAdmission.Token actual,
        R63AccountSaveAdmission.Token expected
    )throws IOException{
        if(!actual.username.equals(expected.username)||
           actual.generation!=expected.generation||
           actual.journalRevision!=expected.journalRevision||
           !actual.snapshotDigest.equals(expected.snapshotDigest))
            throw new IOException(
                "R64_SESSION_DENIED stale generation/hash/index revision"
            );
    }

    private static void validateUsername(String username)throws IOException{
        if(username==null||
           !username.matches("[a-z][a-z0-9_]{0,31}"))
            throw new IOException("R64_SESSION_DENIED invalid account");
    }

    private static Path recordPath(Path root,String username){
        return root.toAbsolutePath().normalize().resolve(
            username+".r64-session.properties"
        );
    }

    private static void validateRoots(Path journal,Path accounts,
                                      Path generations,Path sessions)
            throws IOException{
        if(journal==null||accounts==null||generations==null||sessions==null)
            throw new IOException("R64_PATH_DENIED missing root");
        Path[] roots=new Path[]{
            journal.toAbsolutePath().normalize(),
            accounts.toAbsolutePath().normalize(),
            generations.toAbsolutePath().normalize(),
            sessions.toAbsolutePath().normalize()
        };
        for(int i=0;i<roots.length;i++){
            Path root=roots[i];
            for(Path p=root;p!=null;p=p.getParent()){
                if(Files.isSymbolicLink(p))
                    throw new IOException("R64_PATH_DENIED symlink ancestor");
            }
            BasicFileAttributes attrs=Files.readAttributes(
                root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
            if(!attrs.isDirectory())
                throw new IOException("R64_PATH_DENIED non-directory");
            for(int j=0;j<i;j++){
                if(root.startsWith(roots[j])||
                   roots[j].startsWith(root))
                    throw new IOException(
                        "R64_PATH_DENIED overlapping roots"
                    );
            }
        }
    }

    private static void writeOwner(Path file,Token owner)throws IOException{
        Properties p=new Properties();
        p.setProperty("format",FORMAT);
        p.setProperty("username",owner.username);
        p.setProperty("owner.id",owner.owner.toString());
        p.setProperty("owner.epoch",Long.toString(owner.epoch));
        p.setProperty("phase",owner.phase.name());
        p.setProperty("account.generation",
            Long.toString(owner.account.generation));
        p.setProperty("journal.revision",
            Long.toString(owner.account.journalRevision));
        p.setProperty("snapshot.sha256",owner.account.snapshotDigest);
        AtomicAccountPublisher.publish(
            file,p,"R64 offline session owner"
        );
    }

    private static Token readOwner(Path file)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(file);
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<=0||
           attrs.size()>MAX_RECORD_BYTES)
            throw new IOException("R64_SESSION_DENIED owner record type/size");
        Properties p=new Properties();
        try(InputStream stream=Files.newInputStream(
                file,LinkOption.NOFOLLOW_LINKS)){
            p.load(stream);
        }
        if(!p.stringPropertyNames().equals(KEYS)||
           !FORMAT.equals(p.getProperty("format")))
            throw new IOException("R64_SESSION_DENIED invalid record schema");
        String name=p.getProperty("username");
        validateUsername(name);
        String rawOwner=p.getProperty("owner.id");
        UUID owner;
        try{
            owner=UUID.fromString(rawOwner);
            if(!owner.toString().equals(rawOwner))
                throw new IllegalArgumentException("noncanonical");
        }catch(IllegalArgumentException invalid){
            throw new IOException("R64_SESSION_DENIED invalid owner UUID",invalid);
        }
        String epochString=p.getProperty("owner.epoch");
        String generationString=p.getProperty("account.generation");
        String revisionString=p.getProperty("journal.revision");
        long epoch=positive(epochString,"epoch");
        long generation=positive(generationString,"generation");
        long revision=positive(revisionString,"revision");
        String hash=p.getProperty("snapshot.sha256");
        if(hash==null||!hash.matches("[0-9a-f]{64}"))
            throw new IOException("R64_SESSION_DENIED invalid snapshot digest");
        Phase phase;
        try{
            phase=Phase.valueOf(p.getProperty("phase"));
        }catch(IllegalArgumentException|NullPointerException invalid){
            throw new IOException("R64_SESSION_DENIED invalid phase",invalid);
        }
        return new Token(
            name,owner,epoch,phase,
            new R63AccountSaveAdmission.Token(
                name,generation,revision,hash
            )
        );
    }

    private static long positive(String value,String label)
            throws IOException{
        if(value==null||!value.matches("[1-9][0-9]*"))
            throw new IOException("R64_SESSION_DENIED invalid "+label);
        try{return Long.parseLong(value);}
        catch(NumberFormatException overflow){
            throw new IOException(
                "R64_SESSION_DENIED overflow "+label,overflow
            );
        }
    }

    private R64OfflineSessionOwnership(){}
}
