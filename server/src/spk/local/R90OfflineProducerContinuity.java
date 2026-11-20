package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R90 OFFLINE ONLY. Cooperative producer incarnation continuity for R89.
 * The producer record is NOT a credential or a real World actor identity.
 *
 * An explicit restart advances the durable producer incarnation/generation
 * under R60 while preserving the R89 committed ordinal; a test World must
 * seed its existing R72 capture-order hook BEFORE starting. Old producers
 * are rejected by a same-lock R89 producer fence before any account write.
 *
 * Save ordering is producer PREPARED -> R89 PREPARED -> R64 account/ledger/
 * owner -> R89 COMMITTED -> producer COMMITTED. A killed JVM in any
 * intermediate phase blocks automatic continuation. There is no atomically
 * committed multi-file transaction or directory fsync/power-loss guarantee.
 */
final class R90OfflineProducerContinuity {
    private static final String FORMAT="SpawnPK-R90-OfflineProducer-v1";
    private static final int MAX_BYTES=4096;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","username","owner.id","owner.epoch",
        "account.generation","journal.revision","snapshot.sha256",
        "producer.stream","producer.incarnation","producer.generation",
        "committed.ordinal","phase","pending.ordinal",
        "pending.sha256","record.sha256"
    ));

    enum Phase{ COMMITTED,PREPARED }

    @FunctionalInterface interface OfflineCrashCutpoint{
        void reached(String stage)throws IOException;
    }
    private static final ThreadLocal<OfflineCrashCutpoint> TEST_CUTPOINT=
        new ThreadLocal<>();

    static void installCrashCutpointForTest(OfflineCrashCutpoint hook){
        if(TEST_CUTPOINT.get()!=null)
            throw new IllegalStateException("R90_CRASH_HOOK_DENIED nested");
        TEST_CUTPOINT.set(Objects.requireNonNull(hook,"hook"));
    }
    static void clearCrashCutpointForTest(){TEST_CUTPOINT.remove();}
    private static void cut(String name)throws IOException{
        OfflineCrashCutpoint hook=TEST_CUTPOINT.get();
        if(hook!=null)hook.reached(name);
    }

    static final class Ticket{
        final String username;
        final UUID owner,stream,incarnation;
        final long ownerEpoch,producerGeneration,committedOrdinal;

        Ticket(R64OfflineSessionOwnership.Token owner,
               UUID stream,UUID incarnation,long generation,
               long committedOrdinal){
            this.username=owner.username;
            this.owner=owner.owner;
            this.ownerEpoch=owner.epoch;
            this.stream=stream;
            this.incarnation=incarnation;
            this.producerGeneration=generation;
            this.committedOrdinal=committedOrdinal;
        }

        boolean matches(Record record){
            return username.equals(record.owner.username)&&
                owner.equals(record.owner.owner)&&
                ownerEpoch==record.owner.epoch&&
                stream.equals(record.stream)&&
                incarnation.equals(record.incarnation)&&
                producerGeneration==record.producerGeneration;
        }
    }

    static final class Session{
        final Ticket ticket;
        final R89OfflineDurableCaptureAdmission gate;
        private boolean seeded;
        private Session(Ticket ticket,
                        R89OfflineDurableCaptureAdmission gate){
            this.ticket=ticket;
            this.gate=gate;
        }

        synchronized void seedWorldBeforeStartForTest(World world){
            Objects.requireNonNull(world,"world");
            if(seeded)
                throw new IllegalStateException(
                    "R90_WORLD_SEED_DENIED already seeded");
            // This existing R72 hook refuses a running World, previously
            // captured snapshots and duplicate seeding.
            world.persistence().seedOfflineCaptureOrderForTest(
                ticket.committedOrdinal);
            seeded=true;
        }
    }

    private static final class Record{
        final R64OfflineSessionOwnership.Token owner;
        final UUID stream,incarnation;
        final long producerGeneration,committedOrdinal,pendingOrdinal;
        final Phase phase;
        final String pendingDigest;

        Record(R64OfflineSessionOwnership.Token owner,
               UUID stream,UUID incarnation,long generation,
               long committedOrdinal,Phase phase,long pendingOrdinal,
               String pendingDigest){
            this.owner=Objects.requireNonNull(owner,"owner");
            this.stream=Objects.requireNonNull(stream,"stream");
            this.incarnation=Objects.requireNonNull(incarnation,"incarnation");
            this.producerGeneration=generation;
            this.committedOrdinal=committedOrdinal;
            this.phase=Objects.requireNonNull(phase,"phase");
            this.pendingOrdinal=pendingOrdinal;
            this.pendingDigest=Objects.requireNonNull(
                pendingDigest,"pendingDigest");
        }

        void validate()throws IOException{
            if(owner.username==null||
               !owner.username.matches("[a-z][a-z0-9_]{0,31}")||
               owner.owner==null||owner.epoch<1L||
               owner.account.generation<1L||
               owner.account.journalRevision<1L||
               !digestValid(owner.account.snapshotDigest)||
               producerGeneration<1L||committedOrdinal<0L||
               pendingOrdinal<0L||
               (phase==Phase.COMMITTED&&
                   (pendingOrdinal!=0L||!"-".equals(pendingDigest)))||
               (phase==Phase.PREPARED&&
                   (pendingOrdinal<=committedOrdinal||
                    !digestValid(pendingDigest))))
                throw new IOException(
                    "R90_PRODUCER_QUARANTINED invalid record fields");
        }

        boolean matchesOwner(R64OfflineSessionOwnership.Token token){
            return owner.username.equals(token.username)&&
                owner.owner.equals(token.owner)&&
                owner.epoch==token.epoch&&
                owner.phase==token.phase&&
                owner.account.generation==token.account.generation&&
                owner.account.journalRevision==
                    token.account.journalRevision&&
                owner.account.snapshotDigest.equals(
                    token.account.snapshotDigest);
        }

        Ticket ticket(){
            return new Ticket(owner,stream,incarnation,
                producerGeneration,committedOrdinal);
        }

        Properties properties()throws IOException{
            validate();
            Properties p=new Properties();
            p.setProperty("format",FORMAT);
            p.setProperty("username",owner.username);
            p.setProperty("owner.id",owner.owner.toString());
            p.setProperty("owner.epoch",Long.toString(owner.epoch));
            p.setProperty("account.generation",
                Long.toString(owner.account.generation));
            p.setProperty("journal.revision",
                Long.toString(owner.account.journalRevision));
            p.setProperty("snapshot.sha256",
                owner.account.snapshotDigest);
            p.setProperty("producer.stream",stream.toString());
            p.setProperty("producer.incarnation",incarnation.toString());
            p.setProperty("producer.generation",
                Long.toString(producerGeneration));
            p.setProperty("committed.ordinal",
                Long.toString(committedOrdinal));
            p.setProperty("phase",phase.name());
            p.setProperty("pending.ordinal",Long.toString(pendingOrdinal));
            p.setProperty("pending.sha256",pendingDigest);
            p.setProperty("record.sha256",checksum(p));
            return p;
        }

        static Record parse(Properties p)throws IOException{
            try{
                if(!p.stringPropertyNames().equals(KEYS)||
                   !FORMAT.equals(p.getProperty("format"))||
                   !checksum(p).equals(
                       p.getProperty("record.sha256")))
                    throw new IllegalArgumentException("schema/checksum");
                UUID ownerId=canonicalUuid(p.getProperty("owner.id"));
                UUID stream=canonicalUuid(
                    p.getProperty("producer.stream"));
                UUID incarnation=canonicalUuid(
                    p.getProperty("producer.incarnation"));
                String username=p.getProperty("username");
                long epoch=num(p,"owner.epoch",1);
                long generation=num(p,"account.generation",1);
                long revision=num(p,"journal.revision",1);
                long producer=num(p,"producer.generation",1);
                long committed=num(p,"committed.ordinal",0);
                long pending=num(p,"pending.ordinal",0);
                String sha=p.getProperty("snapshot.sha256");
                R63AccountSaveAdmission.Token account=
                    new R63AccountSaveAdmission.Token(
                        username,generation,revision,sha);
                R64OfflineSessionOwnership.Token token=
                    new R64OfflineSessionOwnership.Token(
                        username,ownerId,epoch,
                        R64OfflineSessionOwnership.Phase.ACTIVE,
                        account);
                Record record=new Record(
                    token,stream,incarnation,producer,committed,
                    Phase.valueOf(p.getProperty("phase")),pending,
                    p.getProperty("pending.sha256"));
                record.validate();
                return record;
            }catch(IllegalArgumentException error){
                throw new IOException(
                    "R90_PRODUCER_QUARANTINED malformed or modified",error);
            }
        }
    }

    private final Path journal,accounts,generations,sessions;
    private R90OfflineProducerContinuity(Path journal,Path accounts,
                                          Path generations,Path sessions){
        this.journal=journal;
        this.accounts=accounts;
        this.generations=generations;
        this.sessions=sessions;
    }

    static Session initialize(Path j,Path a,Path g,Path s,
                              R64OfflineSessionOwnership.Token owner)
        throws IOException{
        R90OfflineProducerContinuity coordinator=
            new R90OfflineProducerContinuity(j,a,g,s);
        Ticket ticket=R60TradePairCoordinator.withExclusive(j,false,()->{
            long high=R89OfflineDurableCaptureAdmission
                .committedOrdinalUnderLock(j,a,g,s,owner);
            Path file=path(s,owner.username);
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "R90_PRODUCER_DENIED already initialized");
            Record created=new Record(owner,UUID.randomUUID(),
                UUID.randomUUID(),1L,high,Phase.COMMITTED,0L,"-");
            publish(file,created);
            return created.ticket();
        });
        return coordinator.openSession(owner,ticket);
    }

    static Session restart(Path j,Path a,Path g,Path s,
                           R64OfflineSessionOwnership.Token owner)
        throws IOException{
        R90OfflineProducerContinuity coordinator=
            new R90OfflineProducerContinuity(j,a,g,s);
        Ticket ticket=R60TradePairCoordinator.withExclusive(j,false,()->{
            long high=R89OfflineDurableCaptureAdmission
                .committedOrdinalUnderLock(j,a,g,s,owner);
            Path file=path(s,owner.username);
            Record previous=read(file);
            requireCommitted(previous,owner,high);
            if(previous.producerGeneration==Long.MAX_VALUE)
                throw new IOException(
                    "R90_PRODUCER_DENIED generation overflow");
            Record next=new Record(owner,previous.stream,
                UUID.randomUUID(),previous.producerGeneration+1L,
                high,Phase.COMMITTED,0L,"-");
            publish(file,next);
            return next.ticket();
        });
        return coordinator.openSession(owner,ticket);
    }

    /**
     * Called only after an explicitly approved OFFLINE R64 takeover and
     * R89 explicit rebind (which resets its capture ordinal to zero).
     */
    static Session rebindAfterOwnerChange(
        Path j,Path a,Path g,Path s,
        R64OfflineSessionOwnership.Token previous,
        R64OfflineSessionOwnership.Token successor)throws IOException{
        R90OfflineProducerContinuity coordinator=
            new R90OfflineProducerContinuity(j,a,g,s);
        Ticket ticket=R60TradePairCoordinator.withExclusive(j,false,()->{
            long high=R89OfflineDurableCaptureAdmission
                .committedOrdinalUnderLock(j,a,g,s,successor);
            Record old=read(path(s,successor.username));
            if(old.phase!=Phase.COMMITTED||
               !old.matchesOwner(previous)||
               !old.owner.username.equals(successor.username)||
               !old.owner.owner.equals(previous.owner)||
               successor.owner.equals(previous.owner)||
               previous.epoch==Long.MAX_VALUE||
               successor.epoch!=previous.epoch+1L||
               old.committedOrdinal<0L||high!=0L||
               previous.account.generation!=
                    successor.account.generation||
               previous.account.journalRevision!=
                    successor.account.journalRevision||
               !previous.account.snapshotDigest.equals(
                    successor.account.snapshotDigest))
                throw new IOException(
                    "R90_REBIND_DENIED predecessor/owner/watermark");
            Record newOwner=new Record(
                successor,UUID.randomUUID(),UUID.randomUUID(),
                1L,0L,Phase.COMMITTED,0L,"-");
            publish(path(s,successor.username),newOwner);
            return newOwner.ticket();
        });
        return coordinator.openSession(successor,ticket);
    }

    private Session openSession(
        R64OfflineSessionOwnership.Token owner,Ticket ticket)
        throws IOException{
        R89OfflineDurableCaptureAdmission gate=
            R89OfflineDurableCaptureAdmission.reopen(
                journal,accounts,generations,sessions,owner);
        if(gate.highestAcceptedOrdinal()!=ticket.committedOrdinal)
            throw new IOException(
                "R90_PRODUCER_DENIED reopen highwater changed");
        gate.installOfflineProducerFenceForTest(
            new Fence(ticket,sessions));
        return new Session(ticket,gate);
    }

    private static final class Fence
        implements R89OfflineDurableCaptureAdmission.OfflineProducerFence{
        private final Ticket ticket;
        private final Path sessions;
        Fence(Ticket ticket,Path sessions){
            this.ticket=ticket;
            this.sessions=sessions;
        }

        @Override public void beforeCommit(
            R64OfflineSessionOwnership.Token owner,long highest,
            long nextOrdinal,String digest,
            WorldPlayerPersistence.OfflineCaptureStamp stamp
        )throws IOException{
            Record stored=read(path(sessions,owner.username));
            if(stored.phase!=Phase.COMMITTED||
               !ticket.matches(stored)||
               !stored.matchesOwner(owner)||
               stored.committedOrdinal!=highest||
               nextOrdinal<=highest||!digestValid(digest))
                throw new IOException(
                    "R90_STALE_PRODUCER prior incarnation or ordinal");
            // R91's optional provenance check happens while R60 is
            // held, BEFORE any PREPARED marker or canonical account write.
            // An absent stamp is permitted only by legacy R90-only
            // fixtures. An R91-enabled R89 gate checks absence itself.
            if(stamp!=null &&
               (!owner.username.equals(stamp.username) ||
                !ticket.stream.equals(stamp.stream) ||
                !ticket.incarnation.equals(stamp.incarnation) ||
                ticket.ownerEpoch!=stamp.ownerEpoch ||
                ticket.producerGeneration!=stamp.producerGeneration ||
                stamp.captureOrdinal!=nextOrdinal ||
                !digest.equals(stamp.snapshotDigest) ||
                stamp.worldPlayerGeneration<1L))
                throw new IOException(
                    "R91_CAPTURE_DENIED wrong producer/ordinal/snapshot");
            publish(path(sessions,owner.username),
                new Record(owner,stored.stream,stored.incarnation,
                    stored.producerGeneration,highest,
                    Phase.PREPARED,nextOrdinal,digest));
            cut("AFTER_PRODUCER_PREPARE");
        }

        @Override public void afterCommit(
            R64OfflineSessionOwnership.Token next,long ordinal,
            String digest)throws IOException{
            Record prepared=read(path(sessions,next.username));
            if(prepared.phase!=Phase.PREPARED||
               !ticket.matches(prepared)||
               prepared.pendingOrdinal!=ordinal||
               !prepared.pendingDigest.equals(digest))
                throw new IOException(
                    "R90_PRODUCER_QUARANTINED missing prepared source");
            publish(path(sessions,next.username),
                new Record(next,prepared.stream,prepared.incarnation,
                    prepared.producerGeneration,ordinal,
                    Phase.COMMITTED,0L,"-"));
            cut("AFTER_PRODUCER_COMMIT_BEFORE_ACK");
        }
    }

    static long inspectCommittedOrdinalForTest(
        Path j,Path a,Path g,Path s,
        R64OfflineSessionOwnership.Token owner)throws IOException{
        return R60TradePairCoordinator.withExclusive(j,false,()->{
            long highest=R89OfflineDurableCaptureAdmission
                .committedOrdinalUnderLock(j,a,g,s,owner);
            Record producer=read(path(s,owner.username));
            requireCommitted(producer,owner,highest);
            return highest;
        });
    }

    /**
     * R96 offline-only: one R60 critical section checks R64/R63, R89
     * highwater, and R90 producer-incarnation identity before recording a
     * separate retirement witness. No production authentication is claimed.
     */
    static void requireCommittedSourceUnderLockForTest(
        Path j,Path a,Path g,Path s,
        R64OfflineSessionOwnership.Token owner,Ticket ticket,
        long expectedOrdinal)throws IOException{
        Objects.requireNonNull(ticket,"ticket");
        R60TradePairCoordinator.requireHeldOnCurrentThread(j);
        long high=R89OfflineDurableCaptureAdmission
            .committedOrdinalUnderLock(j,a,g,s,owner);
        Record producer=read(path(s,owner.username));
        requireCommitted(producer,owner,high);
        if(!ticket.matches(producer)||
                high!=expectedOrdinal||
                expectedOrdinal<1L)
            throw new IOException(
                "R96_SOURCE_DENIED stale producer identity or ordinal");
    }

    /** R96 fresh-process review binding to the recorded producer identity. */
    static void requireCommittedIdentityUnderLockForTest(
        Path j,Path a,Path g,Path s,
        R64OfflineSessionOwnership.Token owner,
        UUID stream,UUID incarnation,long producerGeneration,
        long expectedOrdinal)throws IOException{
        R60TradePairCoordinator.requireHeldOnCurrentThread(j);
        long high=R89OfflineDurableCaptureAdmission
            .committedOrdinalUnderLock(j,a,g,s,owner);
        Record producer=read(path(s,owner.username));
        requireCommitted(producer,owner,high);
        if(!producer.stream.equals(stream)||
                !producer.incarnation.equals(incarnation)||
                producer.producerGeneration!=producerGeneration||
                high!=expectedOrdinal||
                expectedOrdinal<1L)
            throw new IOException(
                "R96_SOURCE_DENIED stale restart identity or ordinal");
    }

    private static void requireCommitted(
        Record record,R64OfflineSessionOwnership.Token owner,long high)
        throws IOException{
        if(record.phase!=Phase.COMMITTED)
            throw new IOException(
                "R90_PREPARED_QUARANTINED interrupted producer commit");
        if(!record.matchesOwner(owner)||
           record.committedOrdinal!=high)
            throw new IOException(
                "R90_STALE_PRODUCER owner/watermark mismatch");
    }

    private static Path path(Path sessions,String username){
        return sessions.resolve(
            username+".r90-producer.properties"
        ).toAbsolutePath().normalize();
    }

    private static Record read(Path file)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(file);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "R90_PRODUCER_QUARANTINED missing/nonregular record");
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attrs.size()<1||attrs.size()>MAX_BYTES)
            throw new IOException("R90_PRODUCER_QUARANTINED record size");
        Properties p=new Properties();
        try(InputStream input=Files.newInputStream(
                file,LinkOption.NOFOLLOW_LINKS)){
            p.load(input);
        }
        return Record.parse(p);
    }

    private static void publish(Path file,Record record)throws IOException{
        AtomicAccountPublisher.publish(
            file,record.properties(),
            "R90 OFFLINE producer source is NOT an authorization credential");
    }

    private static UUID canonicalUuid(String raw){
        UUID value=UUID.fromString(raw);
        if(!value.toString().equals(raw))
            throw new IllegalArgumentException("noncanonical uuid");
        return value;
    }

    private static long num(Properties p,String key,int min){
        String raw=p.getProperty(key);
        if(raw==null||!raw.matches("0|[1-9][0-9]*"))
            throw new IllegalArgumentException("invalid "+key);
        long value=Long.parseLong(raw);
        if(value<min)
            throw new IllegalArgumentException("low "+key);
        return value;
    }

    private static boolean digestValid(String hex){
        return hex!=null&&hex.matches("[0-9a-f]{64}");
    }

    private static String checksum(Properties p){
        String[] fields={
            "format","username","owner.id","owner.epoch",
            "account.generation","journal.revision","snapshot.sha256",
            "producer.stream","producer.incarnation",
            "producer.generation","committed.ordinal","phase",
            "pending.ordinal","pending.sha256"
        };
        StringBuilder text=new StringBuilder();
        for(String key:fields)
            text.append(key).append('=')
                .append(p.getProperty(key,"<missing>")).append('\n');
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                text.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);
            for(byte b:digest){
                hex.append(Character.forDigit((b>>4)&15,16));
                hex.append(Character.forDigit(b&15,16));
            }
            return hex.toString();
        }catch(NoSuchAlgorithmException error){
            throw new IllegalStateException("R90 SHA-256 unavailable",error);
        }
    }
}
