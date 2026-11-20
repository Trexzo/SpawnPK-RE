package spk.local;

import java.io.IOException;
import java.io.InputStream;
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
 * R91 OFFLINE TEST ONLY: write one observation of the REAL HP mutation
 * after PlayerLifecycleService.applyDamage but before the first semantic
 * outcome is emitted. R90 ATTEMPT_RESERVED and R91 DAMAGE_OBSERVED
 * are SEPARATE files, not atomically committed with HP or DomainEventBus.
 *
 * An intact DAMAGE_OBSERVED witness neither proves either combat event
 * was published nor authorizes restart replay, login, reward or rollback.
 */
final class R91OfflinePostDamageObservationJournal {
    private static final String FORMAT=
        "SpawnPK-R91-TestPostDamageObservation-v1";
    private static final String PHASE="DAMAGE_OBSERVED_ONLY";
    private static final int MAX_BYTES=8192;
    private static final String[] FIELDS={
        "format","phase","fixture.attempt",
        "actor.attacker","actor.target","attack.worldTick",
        "damage.hpBefore","damage.hpAfter","damage.applied",
        "damage.died","damage.ignoredDead"
    };
    private static final Set<String> KEYS=new HashSet<>();
    static{
        KEYS.addAll(Arrays.asList(FIELDS));
        KEYS.add("evidence.sha256");
    }

    enum Fault {
        NONE,
        FAIL_AFTER_HP_BEFORE_WITNESS,
        FAIL_AFTER_WITNESS_BEFORE_OUTCOME
    }
    enum Status {
        OBSERVED_QUARANTINED,
        MISSING_BLOCKED,
        INDETERMINATE_BLOCKED
    }

    static final class Review {
        final Status status;
        final String reason;
        final Integer observedHpAfter;
        final Boolean observedDeath;
        Review(Status status,String reason,Properties checked){
            this.status=status;
            this.reason=reason;
            this.observedHpAfter=checked==null?null:
                Integer.valueOf(
                    checked.getProperty("damage.hpAfter")
                );
            this.observedDeath=checked==null?null:
                Boolean.valueOf(
                    checked.getProperty("damage.died")
                );
        }
        boolean authoritativeHpCommit(){return false;}
        boolean outcomePublished(){return false;}
        boolean replayAuthorized(){return false;}
        boolean rollbackAuthorized(){return false;}
        boolean projectionCommitted(){return false;}
        boolean rewardAuthorized(){return false;}
        boolean loginAuthorized(){return false;}
    }

    private final Path root;
    private final Path acceptedRoot;
    private final Fault fault;
    private int observed;
    private int refused;
    private UUID lastAttempt;

    R91OfflinePostDamageObservationJournal(
        Path root,Path r90AcceptedRoot,Fault fault
    ){
        this.root=Objects.requireNonNull(root,"root")
            .toAbsolutePath().normalize();
        this.acceptedRoot=Objects.requireNonNull(
            r90AcceptedRoot,"r90AcceptedRoot"
        ).toAbsolutePath().normalize();
        this.fault=Objects.requireNonNull(fault,"fault");
    }

    synchronized void observe(
        R90OfflineAcceptedAttackJournal.Reservation attempt,
        PlayerLifecycleService.DamageResult actual,long worldTick
    )throws IOException{
        Objects.requireNonNull(attempt,"attempt");
        Objects.requireNonNull(actual,"actual");
        if(attempt.worldTick!=worldTick||
           actual.worldTick!=worldTick||
           actual.hpBefore<0||actual.hpAfter<0||
           actual.applied<0||observed!=0||
           attempt.syntheticAttemptId==null){
            refused++;
            throw new IOException(
                "R91_DAMAGE_DENIED invalid or duplicate source observation"
            );
        }
        if(R90OfflineAcceptedAttackJournal.review(
                acceptedRoot,attempt.syntheticAttemptId
            ).status!=R90OfflineAcceptedAttackJournal.Status
                .RESERVED_QUARANTINED){
            refused++;
            throw new IOException(
                "R91_DAMAGE_DENIED missing trusted attempt reservation"
            );
        }
        if(fault==Fault.FAIL_AFTER_HP_BEFORE_WITNESS){
            refused++;
            throw new IOException(
                "R91_INJECTED_HP_APPLIED_NO_WITNESS"
            );
        }
        Properties p=encode(attempt,actual);
        try{
            Files.createDirectories(root);
            if(Files.isSymbolicLink(root)||
               !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("R91 untrusted observation root");
            Path file=path(root,attempt.syntheticAttemptId);
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "R91 duplicate post-damage observation"
                );
            AtomicAccountPublisher.publish(
                file,p,
                "R91 OFFLINE DAMAGE_OBSERVED not outcome commit"
            );
            readChecked(file,attempt.syntheticAttemptId);
            observed++;
            lastAttempt=attempt.syntheticAttemptId;
        }catch(IOException|RuntimeException failure){
            refused++;
            throw new IOException(
                "R91_DAMAGE_DENIED ambiguous observation publication",
                failure
            );
        }
        if(fault==Fault.FAIL_AFTER_WITNESS_BEFORE_OUTCOME){
            refused++;
            throw new IOException(
                "R91_INJECTED_WITNESS_PUBLISHED_NO_OUTCOMES"
            );
        }
    }

    static Review review(
        Path observationRoot,Path acceptedRoot,UUID attempt
    ){
        Objects.requireNonNull(attempt,"attempt");
        Path root=Objects.requireNonNull(
            observationRoot,"observationRoot"
        ).toAbsolutePath().normalize();
        try{
            if(R90OfflineAcceptedAttackJournal.review(
                    acceptedRoot,attempt
                ).status!=R90OfflineAcceptedAttackJournal.Status
                    .RESERVED_QUARANTINED)
                return new Review(Status.INDETERMINATE_BLOCKED,
                    "R90_ATTEMPT_NOT_TRUSTED",null);
            if(Files.notExists(root,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_OBSERVATION_ROOT",null);
            if(Files.isSymbolicLink(root)||
               !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("R91 untrusted root");
            Path file=path(root,attempt);
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.notExists(file,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_POST_HP_WITNESS",null);
            return new Review(Status.OBSERVED_QUARANTINED,
                "DAMAGE_WITNESS_NOT_EVENT_DELIVERY",
                readChecked(file,attempt));
        }catch(IOException|RuntimeException failure){
            return new Review(Status.INDETERMINATE_BLOCKED,
                "UNTRUSTED_OR_AMBIGUOUS_OBSERVATION",null);
        }
    }

    static Path pathForTest(Path root,UUID attempt){
        return path(root.toAbsolutePath().normalize(),attempt);
    }
    private static Path path(Path root,UUID attempt){
        return root.resolve("r91-"+attempt+".properties");
    }

    private static Properties encode(
        R90OfflineAcceptedAttackJournal.Reservation attempt,
        PlayerLifecycleService.DamageResult actual
    )throws IOException{
        Properties p=new Properties();
        p.setProperty("format",FORMAT);
        p.setProperty("phase",PHASE);
        p.setProperty("fixture.attempt",
            attempt.syntheticAttemptId.toString());
        p.setProperty("actor.attacker",attempt.attacker);
        p.setProperty("actor.target",attempt.target);
        p.setProperty("attack.worldTick",
            Long.toString(attempt.worldTick));
        p.setProperty("damage.hpBefore",
            Integer.toString(actual.hpBefore));
        p.setProperty("damage.hpAfter",
            Integer.toString(actual.hpAfter));
        p.setProperty("damage.applied",
            Integer.toString(actual.applied));
        p.setProperty("damage.died",
            Boolean.toString(actual.died));
        p.setProperty("damage.ignoredDead",
            Boolean.toString(actual.ignoredDead));
        p.setProperty("evidence.sha256",digest(p));
        return p;
    }

    private static Properties readChecked(Path file,UUID id)
            throws IOException{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||
           Files.size(file)<=0||Files.size(file)>MAX_BYTES)
            throw new IOException("R91 invalid file type/size");
        Properties p=new Properties();
        try(InputStream in=Files.newInputStream(
                file,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)){
            p.load(in);
        }
        if(!p.stringPropertyNames().equals(KEYS)||
           !FORMAT.equals(p.getProperty("format"))||
           !PHASE.equals(p.getProperty("phase"))||
           !id.toString().equals(p.getProperty("fixture.attempt"))||
           !digest(p).equals(p.getProperty("evidence.sha256")))
            throw new IOException("R91 observation schema/checksum mismatch");
        try{
            if(p.getProperty("actor.attacker").trim().isEmpty()||
               p.getProperty("actor.target").trim().isEmpty())
                throw new IllegalArgumentException("empty actors");
            long tick=Long.parseLong(
                p.getProperty("attack.worldTick")
            );
            int before=Integer.parseInt(
                p.getProperty("damage.hpBefore")
            );
            int after=Integer.parseInt(
                p.getProperty("damage.hpAfter")
            );
            int amount=Integer.parseInt(
                p.getProperty("damage.applied")
            );
            boolean died="true".equals(p.getProperty("damage.died"));
            boolean ignored="true".equals(
                p.getProperty("damage.ignoredDead")
            );
            if(tick<0||before<0||after<0||amount<0||
               after>before||amount!=before-after||
               (died!=(after==0&&!ignored))||
               (!"true".equals(p.getProperty("damage.died"))&&
                !"false".equals(p.getProperty("damage.died")))||
               (!"true".equals(p.getProperty("damage.ignoredDead"))&&
                !"false".equals(p.getProperty("damage.ignoredDead"))))
                throw new IllegalArgumentException(
                    "invalid damage values"
                );
        }catch(RuntimeException bad){
            throw new IOException("R91 invalid damage fields",bad);
        }
        return p;
    }

    private static String digest(Properties p)throws IOException{
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            for(String key:FIELDS){
                String value=p.getProperty(key);
                if(value==null)throw new IOException(
                    "R91 missing "+key
                );
                md.update((key+"="+value+"\n").getBytes(
                    StandardCharsets.UTF_8
                ));
            }
            StringBuilder hex=new StringBuilder(64);
            for(byte b:md.digest())
                hex.append(String.format("%02x",b&255));
            return hex.toString();
        }catch(NoSuchAlgorithmException unavailable){
            throw new IOException("R91 SHA-256 unavailable",unavailable);
        }
    }

    int observedForTest(){return observed;}
    int refusedForTest(){return refused;}
    UUID lastAttemptForTest(){return lastAttempt;}
}
