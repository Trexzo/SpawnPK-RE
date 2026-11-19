package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
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
 * R90 OFFLINE TEST ONLY. Reserves a unique test-owned ATTACK ATTEMPT
 * identity inside the REAL active handler's already accepted, paired
 * World-ownership critical section, BEFORE canonical HP mutation.
 *
 * This is NOT a recovered SpawnPK producer operation ID, an outcome
 * event ID, a completed damage transaction, or durable kill evidence.
 * A persisted RESERVATION can describe an attack that NEVER applied
 * damage. On restart ALL states remain read-only QUARANTINED.
 */
final class R90OfflineAcceptedAttackJournal {
    private static final String FORMAT="SpawnPK-R90-TestAttackReservation-v1";
    private static final String PHASE="ATTEMPT_RESERVED_ONLY";
    private static final int MAX_BYTES=8192;
    private static final String[] FIELDS={
        "format","phase","fixture.operation",
        "actor.attacker","actor.attackerGeneration",
        "actor.target","actor.targetGeneration",
        "attack.worldTick","attack.weaponId"
    };
    private static final Set<String> KEYS=new HashSet<>();
    static{
        KEYS.addAll(Arrays.asList(FIELDS));
        KEYS.add("evidence.sha256");
    }

    enum Fault {
        NONE,
        DENY_BEFORE_RESERVATION,
        INTERRUPT_AFTER_RESERVATION_BEFORE_DAMAGE
    }
    enum Status {
        RESERVED_QUARANTINED,
        MISSING_BLOCKED,
        INDETERMINATE_BLOCKED
    }
    static final class Reservation {
        final UUID syntheticAttemptId;
        final String attacker;
        final String target;
        final long attackerGeneration;
        final long targetGeneration;
        final long worldTick;
        final int weaponId;

        private Reservation(UUID id,String attacker,String target,
            long attackerGeneration,long targetGeneration,
            long worldTick,int weaponId){
            this.syntheticAttemptId=id;
            this.attacker=attacker;
            this.target=target;
            this.attackerGeneration=attackerGeneration;
            this.targetGeneration=targetGeneration;
            this.worldTick=worldTick;
            this.weaponId=weaponId;
        }
    }
    static final class Review {
        final Status status;
        final String reason;
        final String attacker;
        final String target;
        final long worldTick;
        Review(Status status,String reason,Properties evidence){
            this.status=status;
            this.reason=reason;
            this.attacker=evidence==null?null:
                evidence.getProperty("actor.attacker");
            this.target=evidence==null?null:
                evidence.getProperty("actor.target");
            this.worldTick=evidence==null?-1L:
                Long.parseLong(evidence.getProperty("attack.worldTick"));
        }
        boolean attackApplied(){return false;}
        boolean killConfirmed(){return false;}
        boolean replayAllowed(){return false;}
        boolean projectionCommitted(){return false;}
        boolean loginAllowed(){return false;}
        boolean rewardAllowed(){return false;}
    }

    private final Path root;
    private final Fault fault;
    private final int capacity;
    private int written;
    private int refused;
    private Reservation lastReserved;

    R90OfflineAcceptedAttackJournal(
        Path testRoot,Fault fault,int maxReservations
    ){
        this.root=Objects.requireNonNull(testRoot,"root")
            .toAbsolutePath().normalize();
        this.fault=Objects.requireNonNull(fault,"fault");
        if(maxReservations<1||maxReservations>64)
            throw new IllegalArgumentException(
                "R90 invalid test reservation bound"
            );
        this.capacity=maxReservations;
    }

    synchronized Reservation reserve(
        World world,WorldPlayer attacker,long attackerGeneration,
        WorldPlayer target,long targetGeneration,
        long worldTick,int weaponId
    )throws IOException{
        Objects.requireNonNull(world,"world");
        Objects.requireNonNull(attacker,"attacker");
        Objects.requireNonNull(target,"target");
        if(!world.pulse().inExecutionContext()||
           attacker==target||attackerGeneration<=0L||
           targetGeneration<=0L||
           !world.players().owns(attacker,attackerGeneration)||
           !world.players().owns(target,targetGeneration)||
           worldTick<0L||weaponId<0){
            refused++;
            throw new IOException(
                "R90_ATTEMPT_DENIED not current owned World PvP"
            );
        }
        if(written>=capacity){
            refused++;
            throw new IOException("R90_ATTEMPT_DENIED full");
        }
        if(fault==Fault.DENY_BEFORE_RESERVATION){
            refused++;
            throw new IOException(
                "R90_INJECTED_NO_RESERVATION_NO_DAMAGE"
            );
        }

        UUID id=UUID.randomUUID();
        Reservation attempt=new Reservation(
            id,attacker.id().toString(),target.id().toString(),
            attackerGeneration,targetGeneration,worldTick,weaponId
        );
        Path canonical=receiptPath(root,id);
        try{
            Files.createDirectories(root);
            if(Files.isSymbolicLink(root)||
               !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("R90 invalid test-owned root");
            Path mutex=root.resolve(".r90-writer.lock");
            try(FileChannel channel=FileChannel.open(
                    mutex,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                FileLock lock=channel.tryLock()){
                if(lock==null)throw new IOException(
                    "R90 writer lock busy"
                );
                AtomicAccountPublisher.requireNoAmbiguousStage(
                    canonical
                );
                if(Files.exists(canonical,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("R90 duplicate reservation ID");
                Properties data=encode(attempt);
                AtomicAccountPublisher.publish(
                    canonical,data,
                    "R90 TEST ATTEMPT_RESERVED not damage or kill commit"
                );
                readChecked(canonical,id);
                written++;
                lastReserved=attempt;
            }
        }catch(IOException|RuntimeException error){
            refused++;
            throw new IOException(
                "R90_ATTEMPT_DENIED publication ambiguous",error
            );
        }

        if(fault==Fault.INTERRUPT_AFTER_RESERVATION_BEFORE_DAMAGE){
            refused++;
            // The canonical reservation exists. HP has NOT been touched.
            // Simulate a lost attempt before the real resolver executes.
            throw new IOException(
                "R90_INJECTED_RESERVED_BUT_NO_DAMAGE"
            );
        }
        return attempt;
    }

    static Review review(Path testRoot,UUID id){
        Objects.requireNonNull(id,"id");
        Path root=Objects.requireNonNull(testRoot,"root")
            .toAbsolutePath().normalize();
        try{
            if(Files.notExists(root,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_TEST_ROOT",null);
            if(Files.isSymbolicLink(root)||
               !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("R90 invalid root");
            Path canonical=receiptPath(root,id);
            AtomicAccountPublisher.requireNoAmbiguousStage(canonical);
            if(Files.notExists(canonical,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_ATTEMPT",null);
            return new Review(Status.RESERVED_QUARANTINED,
                "RESERVATION_IS_NOT_DAMAGE_COMMIT",
                readChecked(canonical,id));
        }catch(IOException|RuntimeException error){
            return new Review(Status.INDETERMINATE_BLOCKED,
                "UNTRUSTED_OR_AMBIGUOUS_ATTEMPT",null);
        }
    }

    static Path receiptPathForTest(Path root,UUID id){
        return receiptPath(root.toAbsolutePath().normalize(),id);
    }
    private static Path receiptPath(Path root,UUID id){
        return root.resolve("r90-"+id+".properties");
    }

    private static Properties encode(Reservation attempt)
            throws IOException{
        Properties p=new Properties();
        p.setProperty("format",FORMAT);
        p.setProperty("phase",PHASE);
        p.setProperty("fixture.operation",
            attempt.syntheticAttemptId.toString());
        p.setProperty("actor.attacker",attempt.attacker);
        p.setProperty("actor.attackerGeneration",
            Long.toString(attempt.attackerGeneration));
        p.setProperty("actor.target",attempt.target);
        p.setProperty("actor.targetGeneration",
            Long.toString(attempt.targetGeneration));
        p.setProperty("attack.worldTick",
            Long.toString(attempt.worldTick));
        p.setProperty("attack.weaponId",
            Integer.toString(attempt.weaponId));
        p.setProperty("evidence.sha256",digest(p));
        return p;
    }

    private static Properties readChecked(Path file,UUID id)
            throws IOException{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||
           Files.size(file)<=0L||Files.size(file)>MAX_BYTES)
            throw new IOException("R90 invalid attempt file size/type");
        Properties data=new Properties();
        try(InputStream in=Files.newInputStream(
                file,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            data.load(in);
        }
        if(!KEYS.equals(data.stringPropertyNames())||
           !FORMAT.equals(data.getProperty("format"))||
           !PHASE.equals(data.getProperty("phase"))||
           !id.toString().equals(data.getProperty("fixture.operation"))||
           !digest(data).equals(data.getProperty("evidence.sha256")))
            throw new IOException("R90 untrusted attempt evidence");

        try{
            if(data.getProperty("actor.attacker").trim().isEmpty()||
               data.getProperty("actor.target").trim().isEmpty()||
               Long.parseLong(data.getProperty(
                   "actor.attackerGeneration"))<=0L||
               Long.parseLong(data.getProperty(
                   "actor.targetGeneration"))<=0L||
               Long.parseLong(data.getProperty("attack.worldTick"))<0L||
               Integer.parseInt(data.getProperty("attack.weaponId"))<0)
                throw new IllegalArgumentException("R90 invalid fields");
        }catch(RuntimeException invalid){
            throw new IOException("R90 invalid attempt field",invalid);
        }
        return data;
    }

    private static String digest(Properties values)throws IOException{
        try{
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(String field:FIELDS){
                String v=values.getProperty(field);
                if(v==null)
                    throw new IOException("R90 missing field "+field);
                digest.update((field+"="+v+"\n").getBytes(
                    StandardCharsets.UTF_8
                ));
            }
            StringBuilder hex=new StringBuilder(64);
            for(byte b:digest.digest())
                hex.append(String.format("%02x",b&255));
            return hex.toString();
        }catch(NoSuchAlgorithmException impossible){
            throw new IOException("SHA-256 unavailable",impossible);
        }
    }

    int countForTest(){return written;}
    int refusedForTest(){return refused;}
    Reservation lastReservedForTest(){return lastReserved;}
}
