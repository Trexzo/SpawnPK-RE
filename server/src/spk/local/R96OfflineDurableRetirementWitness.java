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
 * R96 OFFLINE ONLY: conservative durable evidence of one *cooperating*
 * World actor's retirement, after a fully committed R89/R90 save.
 *
 * PREPARED means retirement is uncertain, even after a JVM restart.
 * COMMITTED is emitted only AFTER actual World unregister returns true.
 * No production session/login sees this file or treats it as authority.
 */
final class R96OfflineDurableRetirementWitness {
    private static final String FORMAT="R96_OFFLINE_RETIREMENT_V1";
    private static final int MAX_BYTES=8192;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","username","owner.uuid","owner.epoch",
        "producer.stream","producer.incarnation","producer.generation",
        "world.actor.id","world.generation","committed.ordinal",
        "phase","record.sha256"
    ));

    enum Phase{ PREPARED,COMMITTED }
    enum Status{ MISSING_UNPROVEN,PREPARED_QUARANTINED,COMMITTED_WITNESS_ONLY }

    static final class Review {
        final Status status;
        final long ordinal;
        final long worldGeneration;
        Review(Status status,long ordinal,long worldGeneration){
            this.status=status;this.ordinal=ordinal;
            this.worldGeneration=worldGeneration;
        }
        boolean loginAuthorized(){return false;}
        boolean automaticallyRecovered(){return false;}
    }

    static final class Receipt {
        final String username;
        final UUID owner,stream,incarnation;
        final long ownerEpoch,producerGeneration,actorId,worldGeneration;
        final long ordinal;
        final Phase phase;

        Receipt(R90OfflineProducerContinuity.Ticket ticket,
                WorldPlayer actor,long generation,long ordinal,
                Phase phase){
            this(ticket.username,ticket.owner,ticket.ownerEpoch,
                ticket.stream,ticket.incarnation,
                ticket.producerGeneration,actor.id().value,
                generation,ordinal,phase);
        }

        Receipt(String username,UUID owner,long ownerEpoch,UUID stream,
                UUID incarnation,long producerGeneration,long actorId,
                long worldGeneration,long ordinal,Phase phase){
            this.username=username;this.owner=owner;
            this.ownerEpoch=ownerEpoch;this.stream=stream;
            this.incarnation=incarnation;
            this.producerGeneration=producerGeneration;
            this.actorId=actorId;this.worldGeneration=worldGeneration;
            this.ordinal=ordinal;this.phase=phase;
        }

        Receipt phase(Phase next){
            return new Receipt(username,owner,ownerEpoch,stream,
                incarnation,producerGeneration,actorId,worldGeneration,
                ordinal,next);
        }

        private String canonical(){
            return FORMAT+"\n"+username+"\n"+owner+"\n"+ownerEpoch+
                "\n"+stream+"\n"+incarnation+"\n"+
                producerGeneration+"\n"+actorId+"\n"+worldGeneration+
                "\n"+ordinal+"\n"+phase.name()+"\n";
        }

        private String digest(){
            try{
                byte[] bytes=MessageDigest.getInstance("SHA-256").digest(
                    canonical().getBytes(StandardCharsets.UTF_8));
                final char[] hex="0123456789abcdef".toCharArray();
                StringBuilder result=new StringBuilder(64);
                for(byte b:bytes){
                    result.append(hex[(b&255)>>>4]);
                    result.append(hex[b&15]);
                }
                return result.toString();
            }catch(NoSuchAlgorithmException impossible){
                throw new IllegalStateException(impossible);
            }
        }

        Properties properties(){
            Properties p=new Properties();
            p.setProperty("format",FORMAT);
            p.setProperty("username",username);
            p.setProperty("owner.uuid",owner.toString());
            p.setProperty("owner.epoch",Long.toString(ownerEpoch));
            p.setProperty("producer.stream",stream.toString());
            p.setProperty("producer.incarnation",incarnation.toString());
            p.setProperty("producer.generation",
                Long.toString(producerGeneration));
            p.setProperty("world.actor.id",Long.toString(actorId));
            p.setProperty("world.generation",
                Long.toString(worldGeneration));
            p.setProperty("committed.ordinal",Long.toString(ordinal));
            p.setProperty("phase",phase.name());
            p.setProperty("record.sha256",digest());
            return p;
        }

        static Receipt parse(Properties p)throws IOException{
            if(!p.stringPropertyNames().equals(KEYS)||
                    !FORMAT.equals(p.getProperty("format")))
                throw new IOException(
                    "R96_WITNESS_QUARANTINED unknown/missing fields");
            try{
                Receipt r=new Receipt(
                    p.getProperty("username"),
                    UUID.fromString(p.getProperty("owner.uuid")),
                    Long.parseLong(p.getProperty("owner.epoch")),
                    UUID.fromString(p.getProperty("producer.stream")),
                    UUID.fromString(p.getProperty("producer.incarnation")),
                    Long.parseLong(p.getProperty("producer.generation")),
                    Long.parseLong(p.getProperty("world.actor.id")),
                    Long.parseLong(p.getProperty("world.generation")),
                    Long.parseLong(p.getProperty("committed.ordinal")),
                    Phase.valueOf(p.getProperty("phase"))
                );
                r.validate();
                if(!r.digest().equals(p.getProperty("record.sha256")))
                    throw new IOException(
                        "R96_WITNESS_QUARANTINED digest mismatch");
                return r;
            }catch(IllegalArgumentException|NullPointerException failure){
                throw new IOException(
                    "R96_WITNESS_QUARANTINED malformed receipt",failure);
            }
        }

        void validate()throws IOException{
            if(username==null||
                    !username.matches("[a-z][a-z0-9_]{0,31}")||
                    owner==null||stream==null||incarnation==null||
                    ownerEpoch<1||producerGeneration<1||actorId<1||
                    worldGeneration<1||ordinal<1||phase==null)
                throw new IOException(
                    "R96_WITNESS_QUARANTINED invalid source fields");
        }

        boolean sameSource(Receipt other){
            return username.equals(other.username)&&
                owner.equals(other.owner)&&ownerEpoch==other.ownerEpoch&&
                stream.equals(other.stream)&&
                incarnation.equals(other.incarnation)&&
                producerGeneration==other.producerGeneration&&
                actorId==other.actorId&&
                worldGeneration==other.worldGeneration&&
                ordinal==other.ordinal;
        }
    }

    private final World world;
    private final WorldPlayer actor;
    private final long generation,ordinal;
    private final R93OfflineNonblockingWorldTurnoverGate gate;
    /** R98 optional command-queue final-capture proof, never live. */
    private final R98OfflineFinalCaptureCommandFence finalCaptureFence;
    private final R64OfflineSessionOwnership.Token owner;
    private final R90OfflineProducerContinuity.Ticket source;
    private final Path journal,accounts,generations,sessions;
    private final Receipt expected;
    private boolean prepared,unregistered,completed;
    /** R97 controlled failure after R16 has forced the staging bytes. */
    private Phase failAtomicReplacementForTest;

    private R96OfflineDurableRetirementWitness(
        World world,WorldPlayer actor,long generation,long ordinal,
        R93OfflineNonblockingWorldTurnoverGate gate,
        R98OfflineFinalCaptureCommandFence finalCaptureFence,
        R64OfflineSessionOwnership.Token owner,
        R90OfflineProducerContinuity.Ticket source,
        Path journal,Path accounts,Path generations,Path sessions){
        this.world=Objects.requireNonNull(world,"world");
        this.actor=Objects.requireNonNull(actor,"actor");
        this.generation=generation;this.ordinal=ordinal;
        this.gate=Objects.requireNonNull(gate,"gate");
        this.finalCaptureFence=finalCaptureFence;
        this.owner=Objects.requireNonNull(owner,"owner");
        this.source=Objects.requireNonNull(source,"source");
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        this.expected=new Receipt(source,actor,generation,ordinal,
            Phase.PREPARED);
    }

    static R96OfflineDurableRetirementWitness createForTest(
        World world,WorldPlayer actor,long generation,long ordinal,
        R93OfflineNonblockingWorldTurnoverGate gate,
        R64OfflineSessionOwnership.Token owner,
        R90OfflineProducerContinuity.Ticket source,
        Path journal,Path accounts,Path generations,Path sessions){
        return new R96OfflineDurableRetirementWitness(
            world,actor,generation,ordinal,gate,null,owner,source,
            journal,accounts,generations,sessions);
    }

    /** R98 explicit, opt-in final-capture/command-quiescence requirement. */
    static R96OfflineDurableRetirementWitness
            createCommandFencedForTest(
        World world,WorldPlayer actor,long generation,long ordinal,
        R93OfflineNonblockingWorldTurnoverGate gate,
        R98OfflineFinalCaptureCommandFence finalCaptureFence,
        R64OfflineSessionOwnership.Token owner,
        R90OfflineProducerContinuity.Ticket source,
        Path journal,Path accounts,Path generations,Path sessions){
        return new R96OfflineDurableRetirementWitness(
            world,actor,generation,ordinal,gate,
            Objects.requireNonNull(finalCaptureFence,"finalCaptureFence"),
            owner,source,journal,accounts,generations,sessions);
    }

    static Path fileForTest(Path sessions,String username){
        return sessions.resolve(username+
            ".r96-offline-retirement.properties").toAbsolutePath().normalize();
    }

    /**
     * R97 offline fault injection: refuse the actual R16 atomic replace
     * after its staging file was written and forced. The R16 finally block
     * must remove the stage and preserve the previous canonical receipt.
     */
    synchronized void failNextAtomicReplacementForTest(Phase phase){
        if(failAtomicReplacementForTest!=null||completed||unregistered)
            throw new IllegalStateException(
                "R97_REPLACEMENT_HOOK_DENIED already installed or finished");
        failAtomicReplacementForTest=Objects.requireNonNull(phase,"phase");
    }

    private void publishWithInjectedFaultForTest(Path target,Receipt receipt)
        throws IOException{
        if(failAtomicReplacementForTest==receipt.phase){
            failAtomicReplacementForTest=null;
            receipt.validate();
            AtomicAccountPublisher.publish(
                target,receipt.properties(),
                "R97 OFFLINE injected retirement publication fault",
                (staged,canonical)->{
                    if(!Files.isRegularFile(
                            staged,LinkOption.NOFOLLOW_LINKS))
                        throw new IOException(
                            "R97_STAGE_MISSING before replace");
                    throw new IOException(
                        "R97_INJECTED_ATOMIC_REPLACE_"+receipt.phase);
                });
            throw new AssertionError(
                "R97 injected atomic replacement unexpectedly succeeded");
        }
        publish(target,receipt);
    }

    synchronized void prepareForTest()throws IOException{
        if(prepared||unregistered||completed)
            throw new IOException("R96_RETIRE_DENIED duplicate prepare");
        // R98 refuses to promote an arbitrary committed ordinal to
        // retirement unless it was captured by the quiesced World actor
        // and its real IO completion has succeeded.
        if(finalCaptureFence!=null)
            finalCaptureFence.requireCompletedFinalCaptureForTest(
                actor,generation,ordinal);
        // Lock the World for in-memory mutation only. If the following
        // disk write fails, the source stays frozen: fail closed.
        gate.beginDurableRetirementForTest(actor,generation);
        R60TradePairCoordinator.withExclusive(journal,false,()->{
            R90OfflineProducerContinuity
                .requireCommittedSourceUnderLockForTest(
                    journal,accounts,generations,sessions,owner,
                    source,ordinal);
            Path target=fileForTest(sessions,source.username);
            AtomicAccountPublisher.requireNoAmbiguousStage(target);
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "R96_RETIRE_DENIED witness already exists");
            publishWithInjectedFaultForTest(target,expected);
            return null;
        });
        prepared=true;
    }

    /**
     * Must run in the same cooperating JVM after successful PREPARED
     * publication. A fault after World unregister but before COMMITTED
     * deliberately leaves a PREPARED quarantine on disk.
     */
    synchronized void unregisterAndCommitForTest(
        boolean injectAfterUnregister)throws IOException{
        if(!prepared||unregistered||completed)
            throw new IOException(
                "R96_RETIRE_DENIED missing prepared in-process actor");
        // Admission is an in-memory action only, no IO under World lock.
        gate.permitDurableUnregisterForTest(actor,generation);
        if(!world.unregisterPlayer(actor,generation))
            throw new IOException(
                "R96_RETIRE_DENIED World actor unregister refused");
        unregistered=true;
        if(injectAfterUnregister)
            throw new IOException(
                "R96_INJECTED_AFTER_WORLD_UNREGISTER_BEFORE_COMMIT");
        R60TradePairCoordinator.withExclusive(journal,false,()->{
            R90OfflineProducerContinuity
                .requireCommittedSourceUnderLockForTest(
                    journal,accounts,generations,sessions,owner,
                    source,ordinal);
            Path target=fileForTest(sessions,source.username);
            Receipt disk=readRequired(target);
            if(disk.phase!=Phase.PREPARED||
                    !expected.sameSource(disk))
                throw new IOException(
                    "R96_WITNESS_QUARANTINED expected PREPARED missing");
            publishWithInjectedFaultForTest(
                target,expected.phase(Phase.COMMITTED));
            return null;
        });
        completed=true;
    }

    /** Read-only evidence. Never authorize login or repair PREPARED. */
    static Review inspectForTest(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token current)throws IOException{
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            Path target=fileForTest(sessions,current.username);
            AtomicAccountPublisher.requireNoAmbiguousStage(target);
            if(Files.notExists(target,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_UNPROVEN,0L,0L);
            Receipt record=readRequired(target);
            if(!record.username.equals(current.username)||
                    !record.owner.equals(current.owner)||
                    record.ownerEpoch!=current.epoch)
                throw new IOException(
                    "R96_WITNESS_QUARANTINED mismatched R64 owner");
            R90OfflineProducerContinuity
                .requireCommittedIdentityUnderLockForTest(
                    journal,accounts,generations,sessions,current,
                    record.stream,record.incarnation,
                    record.producerGeneration,record.ordinal);
            return new Review(
                record.phase==Phase.COMMITTED?
                    Status.COMMITTED_WITNESS_ONLY:
                    Status.PREPARED_QUARANTINED,
                record.ordinal,record.worldGeneration);
        });
    }

    private static Receipt readRequired(Path target)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(target);
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "R96_WITNESS_QUARANTINED missing/nonregular receipt");
        BasicFileAttributes attrs=Files.readAttributes(
            target,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(attrs.size()<1||attrs.size()>MAX_BYTES)
            throw new IOException(
                "R96_WITNESS_QUARANTINED invalid record size");
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(
                target,LinkOption.NOFOLLOW_LINKS)){
            p.load(in);
        }
        return Receipt.parse(p);
    }

    private static void publish(Path target,Receipt record)
        throws IOException{
        record.validate();
        AtomicAccountPublisher.publish(
            target,record.properties(),
            "R96 OFFLINE retirement witness is not player login authority");
    }
}
