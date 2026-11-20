package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R89 OFFLINE ONLY. Experimental durable, per-owner World CAPTURE-ordinal
 * gate composed with the actual R60/R63/R64 account protocol.
 *
 * Independent watermark publication and R63/R64 multi-file publication
 * cannot form a single transaction. Therefore EVERY admission first
 * publishes a durable PREPARED intent and preserves it if interrupted.
 * An existing PREPARED intent means NO automatic writes, including no-ops.
 * The only positive recovery modeled is a fully COMMITTED watermark that
 * still matches the canonical R64/R63 account identity.
 *
 * Requires every writer to cooperate. No production World, account, packet
 * dispatcher, login, replay, authenticated session or automatic cleanup
 * is wired here. Never infer hard power-loss durability from file.force().
 */
final class R89OfflineDurableCaptureAdmission
        implements WorldPlayerPersistence.OfflineOrderedSaveAdmission {
    private static final String FORMAT="SpawnPK-R89-DurableCapture-v1";
    private static final int MAX_BYTES=4096;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","username","owner.id","owner.epoch","account.generation",
        "journal.revision","snapshot.sha256","highest.ordinal",
        "phase","pending.ordinal","pending.sha256","record.sha256"
    ));
    enum Phase { COMMITTED, PREPARED }

    @FunctionalInterface interface OfflineCrashHook {
        void reached(String cutpoint)throws IOException;
    }
    private static final ThreadLocal<OfflineCrashHook> TEST_CRASH_HOOK=
        new ThreadLocal<>();

    static void installCrashHookForTest(OfflineCrashHook hook){
        if(TEST_CRASH_HOOK.get()!=null)
            throw new IllegalStateException("R89_TEST_HOOK_DENIED active");
        TEST_CRASH_HOOK.set(Objects.requireNonNull(hook,"hook"));
    }
    static void clearCrashHookForTest(){
        TEST_CRASH_HOOK.remove();
    }
    private static void cut(String stage)throws IOException{
        OfflineCrashHook hook=TEST_CRASH_HOOK.get();
        if(hook!=null)hook.reached(stage);
    }

    static final class Watermark {
        final String username;
        final UUID owner;
        final long epoch,generation,journalRevision,highest,pending;
        final String snapshotDigest,pendingDigest;
        final Phase phase;

        Watermark(R64OfflineSessionOwnership.Token token,long highest,
                  Phase phase,long pending,String pendingDigest){
            this.username=token.username;
            this.owner=token.owner;
            this.epoch=token.epoch;
            this.generation=token.account.generation;
            this.journalRevision=token.account.journalRevision;
            this.snapshotDigest=token.account.snapshotDigest;
            this.highest=highest;
            this.phase=phase;
            this.pending=pending;
            this.pendingDigest=pendingDigest;
        }

        void check()throws IOException{
            if(username==null||
               !username.matches("[a-z][a-z0-9_]{0,31}")||
               owner==null||epoch<1L||generation<1L||
               journalRevision<1L||highest<0L||pending<0L||
               !shaPattern(snapshotDigest)||
               (phase==Phase.COMMITTED &&
                    (pending!=0L||!"-".equals(pendingDigest)))||
               (phase==Phase.PREPARED &&
                    (pending<=highest||!shaPattern(pendingDigest))))
                throw new IOException("R89_WATERMARK_QUARANTINED invalid fields");
        }

        boolean matches(R64OfflineSessionOwnership.Token token){
            return username.equals(token.username)&&
                owner.equals(token.owner)&&epoch==token.epoch&&
                generation==token.account.generation&&
                journalRevision==token.account.journalRevision&&
                snapshotDigest.equals(token.account.snapshotDigest);
        }

        Properties encoded()throws IOException{
            check();
            Properties properties=new Properties();
            properties.setProperty("format",FORMAT);
            properties.setProperty("username",username);
            properties.setProperty("owner.id",owner.toString());
            properties.setProperty("owner.epoch",Long.toString(epoch));
            properties.setProperty("account.generation",
                Long.toString(generation));
            properties.setProperty("journal.revision",
                Long.toString(journalRevision));
            properties.setProperty("snapshot.sha256",snapshotDigest);
            properties.setProperty("highest.ordinal",Long.toString(highest));
            properties.setProperty("phase",phase.name());
            properties.setProperty("pending.ordinal",Long.toString(pending));
            properties.setProperty("pending.sha256",pendingDigest);
            properties.setProperty("record.sha256",checksum(properties));
            return properties;
        }

        static Watermark parse(Properties properties)throws IOException{
            try{
                if(!properties.stringPropertyNames().equals(KEYS)||
                   !FORMAT.equals(properties.getProperty("format"))||
                   !checksum(properties).equals(
                       properties.getProperty("record.sha256")))
                    throw new IllegalArgumentException("schema or checksum");
                String username=properties.getProperty("username");
                UUID owner=UUID.fromString(properties.getProperty("owner.id"));
                if(!owner.toString().equals(
                    properties.getProperty("owner.id")))
                    throw new IllegalArgumentException("noncanonical uuid");
                long epoch=parseLong(properties,"owner.epoch",1L);
                long generation=parseLong(
                    properties,"account.generation",1L);
                long revision=parseLong(
                    properties,"journal.revision",1L);
                long highest=parseLong(
                    properties,"highest.ordinal",0L);
                long pending=parseLong(
                    properties,"pending.ordinal",0L);
                String digest=properties.getProperty("snapshot.sha256");
                R63AccountSaveAdmission.Token account=
                    new R63AccountSaveAdmission.Token(
                        username,generation,revision,digest);
                R64OfflineSessionOwnership.Token ownerToken=
                    new R64OfflineSessionOwnership.Token(
                        username,owner,epoch,
                        R64OfflineSessionOwnership.Phase.ACTIVE,
                        account);
                Watermark watermark=new Watermark(ownerToken,highest,
                    Phase.valueOf(properties.getProperty("phase")),
                    pending,properties.getProperty("pending.sha256"));
                watermark.check();
                return watermark;
            }catch(IllegalArgumentException invalid){
                throw new IOException(
                    "R89_WATERMARK_QUARANTINED malformed or modified",invalid);
            }
        }

        private static long parseLong(Properties p,String key,long min){
            String raw=p.getProperty(key);
            if(raw==null||!raw.matches("0|[1-9][0-9]*"))
                throw new IllegalArgumentException("invalid "+key);
            long parsed=Long.parseLong(raw);
            if(parsed<min)
                throw new IllegalArgumentException("low "+key);
            return parsed;
        }
    }

    private final Path journal,accounts,generations,sessions;
    private R64OfflineSessionOwnership.Token current;
    private long highest;
    private boolean poisoned;
    private final AtomicInteger written=new AtomicInteger();
    private final AtomicInteger identical=new AtomicInteger();
    private final AtomicInteger stale=new AtomicInteger();

    private R89OfflineDurableCaptureAdmission(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token owner,long highest){
        this.journal=journal;this.accounts=accounts;
        this.generations=generations;this.sessions=sessions;
        this.current=owner;this.highest=highest;
    }

    static R89OfflineDurableCaptureAdmission initialize(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token token)throws IOException{
        checkArgs(journal,accounts,generations,sessions,token);
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,token);
            Path file=file(sessions,token.username);
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "R89_WATERMARK_DENIED already initialized");
            publish(file,new Watermark(token,0L,Phase.COMMITTED,0L,"-"));
            return new R89OfflineDurableCaptureAdmission(
                journal,accounts,generations,sessions,token,0L);
        });
    }

    static R89OfflineDurableCaptureAdmission reopen(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token token)throws IOException{
        checkArgs(journal,accounts,generations,sessions,token);
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,token);
            Watermark disk=read(file(sessions,token.username));
            requireCommitted(disk,token);
            return new R89OfflineDurableCaptureAdmission(
                journal,accounts,generations,sessions,token,disk.highest);
        });
    }

    /**
     * Explicit TEST-ONLY rebind after R64's independent owner replacement.
     * An outstanding PREPARED watermark or changed account/ledger blocks
     * the transfer. This is neither a credential nor a production login.
     */
    static R89OfflineDurableCaptureAdmission rebindAfterTakeover(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token previous,
        R64OfflineSessionOwnership.Token successor)throws IOException{
        checkArgs(journal,accounts,generations,sessions,successor);
        Objects.requireNonNull(previous,"previous");
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,successor);
            Path file=file(sessions,successor.username);
            Watermark before=read(file);
            requireCommitted(before,previous);
            if(previous.epoch==Long.MAX_VALUE||
               successor.epoch!=previous.epoch+1L||
               !successor.username.equals(previous.username)||
               successor.owner.equals(previous.owner)||
               !sameAccount(previous,successor))
                throw new IOException(
                    "R89_REBIND_DENIED predecessor or account mismatch");
            publish(file,new Watermark(
                successor,0L,Phase.COMMITTED,0L,"-"));
            return new R89OfflineDurableCaptureAdmission(
                journal,accounts,generations,sessions,successor,0L);
        });
    }

    @Override public void withAdmission(
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter unsafe
    )throws IOException{
        throw new IOException("R89_SAVE_DENIED missing capture ordinal");
    }

    @Override public synchronized void withOrderedAdmission(
        long ordinal,PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter unsafe)
        throws IOException{
        if(poisoned)
            throw new IOException("R89_REOPEN_REQUIRED prior ambiguous commit");
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(unsafe,"unsafe");
        if(ordinal<1L)
            throw new IOException("R89_SAVE_DENIED invalid capture ordinal");
        if(!snapshot.username().equals(current.username))
            throw new IOException("R89_SAVE_DENIED wrong account");

        R60TradePairCoordinator.withExclusive(journal,false,()->{
            // Validate R64/R63/R59 first, then the durably recorded owner
            // and high-water mark WHILE holding the same cross-JVM lock.
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,current);
            Path file=file(sessions,current.username);
            Watermark recorded=read(file);
            requireCommitted(recorded,current);
            if(recorded.highest!=highest){
                poisoned=true;
                throw new IOException("R89_STALE_WATERMARK generation changed");
            }
            if(ordinal<=recorded.highest){
                stale.incrementAndGet();
                throw new IOException("R89_SAVE_DENIED stale capture ordinal");
            }
            String digest=R57TradePairJournal.digest(snapshot);
            if(recorded.highest==Long.MAX_VALUE)
                throw new IOException("R89_SAVE_DENIED ordinal overflow");
            try{
                publish(file,new Watermark(
                    current,recorded.highest,Phase.PREPARED,
                    ordinal,digest));
                cut("AFTER_PREPARE_BEFORE_ACCOUNT");
                boolean changed=!digest.equals(
                    current.account.snapshotDigest);
                R64OfflineSessionOwnership.Token next=current;
                if(changed){
                    next=R64OfflineSessionOwnership.saveOwnedUnderLock(
                        journal,accounts,generations,sessions,
                        current,snapshot,false);
                }
                cut("AFTER_ACCOUNT_BEFORE_WATERMARK");
                publish(file,new Watermark(
                    next,ordinal,Phase.COMMITTED,0L,"-"));
                cut("AFTER_WATERMARK_BEFORE_ACK");
                current=next;
                highest=ordinal;
                if(changed)written.incrementAndGet();
                else identical.incrementAndGet();
                return null;
            }catch(IOException|RuntimeException error){
                // A successfully replaced watermark or account might be
                // present even if this caller did not see acknowledgement.
                // Never retry on this handle; inspect/reopen explicitly.
                poisoned=true;
                throw error;
            }
        });
    }

    synchronized long highestAcceptedOrdinal(){return highest;}
    synchronized R64OfflineSessionOwnership.Token currentToken(){
        return current;
    }
    int committedWrites(){return written.get();}
    int identicalWrites(){return identical.get();}
    int rejectedOldWrites(){return stale.get();}

    private static void checkArgs(
        Path j,Path a,Path g,Path s,R64OfflineSessionOwnership.Token token){
        Objects.requireNonNull(j,"journal");
        Objects.requireNonNull(a,"accounts");
        Objects.requireNonNull(g,"generations");
        Objects.requireNonNull(s,"sessions");
        Objects.requireNonNull(token,"token");
    }

    private static boolean sameAccount(
        R64OfflineSessionOwnership.Token a,
        R64OfflineSessionOwnership.Token b){
        return a.account.generation==b.account.generation&&
            a.account.journalRevision==b.account.journalRevision&&
            a.account.snapshotDigest.equals(b.account.snapshotDigest);
    }

    private static void requireCommitted(
        Watermark watermark,R64OfflineSessionOwnership.Token owner)
        throws IOException{
        if(watermark.phase!=Phase.COMMITTED)
            throw new IOException(
                "R89_PREPARED_QUARANTINED interrupted multi-file save");
        if(!watermark.matches(owner))
            throw new IOException(
                "R89_STALE_WATERMARK owner/generation mismatch");
    }

    private static Path file(Path sessions,String username){
        return sessions.resolve(
            username+".r89-watermark.properties"
        ).toAbsolutePath().normalize();
    }

    private static Watermark read(Path target)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(target);
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "R89_WATERMARK_QUARANTINED missing/nonregular record");
        BasicFileAttributes attrs=Files.readAttributes(
            target,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attrs.size()==0||attrs.size()>MAX_BYTES)
            throw new IOException(
                "R89_WATERMARK_QUARANTINED size");
        Properties p=new Properties();
        try(InputStream input=Files.newInputStream(
                target,LinkOption.NOFOLLOW_LINKS)){
            p.load(input);
        }
        return Watermark.parse(p);
    }

    private static void publish(Path target,Watermark watermark)
        throws IOException{
        AtomicAccountPublisher.publish(
            target,watermark.encoded(),
            "R89 OFFLINE capture watermark, not a session credential"
        );
    }

    private static boolean shaPattern(String value){
        return value!=null&&value.matches("[0-9a-f]{64}");
    }

    private static String checksum(Properties properties){
        StringBuilder canonical=new StringBuilder();
        String[] names={
            "format","username","owner.id","owner.epoch",
            "account.generation","journal.revision","snapshot.sha256",
            "highest.ordinal","phase","pending.ordinal","pending.sha256"
        };
        for(String name:names){
            canonical.append(name).append('=')
                .append(properties.getProperty(name,"<missing>"))
                .append('\n');
        }
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte b:digest)
                hex.append(Character.forDigit((b>>4)&15,16))
                    .append(Character.forDigit(b&15,16));
            return hex.toString();
        }catch(NoSuchAlgorithmException absent){
            throw new IllegalStateException("R89 SHA256 missing",absent);
        }
    }
}
