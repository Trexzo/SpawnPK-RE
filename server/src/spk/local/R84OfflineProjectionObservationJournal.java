package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * R84 OFFLINE TEST ONLY: forensic snapshots *AFTER* actual per-observer
 * calls. Each observation is separate from both the R83 PREPARED witness
 * and every mutable Objective/Match/Collection service. It never
 * authenticates a production combat ID, commits a projection, or allows
 * automatic recovery/replay.
 *
 * The only join key is an externally supplied SYNTHETIC fixture UUID
 * and an R82 ordinal verified inside its active test-producer callback.
 * The evidence writes are NOT atomic together with gameplay stores.
 */
final class R84OfflineProjectionObservationJournal
    implements CombatOutcomeObserverGroup.OfflinePostObserverObservation {

    static final String FORMAT="SpawnPK-R84-OfflineProjectionObservation-v1";
    static final String PHASE="OBSERVED_ONLY";
    private static final int MAX_BYTES=8192;
    private static final String[] FIELDS={
        "format","phase","fixture.operation","source.scope",
        "source.ordinal","observer.index","observer.returned",
        "objective.progress","match.participant.score",
        "match.team.score","collection.killCount"
    };
    private static final Set<String> KEYS=new HashSet<>();
    static{
        KEYS.addAll(Arrays.asList(FIELDS));
        KEYS.add("evidence.sha256");
    }

    enum Fault { NONE, DENY_FIRST_EVIDENCE_BEFORE_WRITE }

    enum Status {
        OBSERVED_QUARANTINED,
        MISSING_BLOCKED,
        INDETERMINATE_BLOCKED
    }

    static final class Review {
        final Status status;
        final String reason;
        final Long objectiveProgress;
        final Long participantScore;
        final Long teamScore;
        final Long collectionKillCount;
        final boolean observerReturned;
        Review(Status status,String reason,Properties checked){
            this.status=status;
            this.reason=reason;
            this.objectiveProgress=checked==null?null:Long.valueOf(
                checked.getProperty("objective.progress")
            );
            this.participantScore=checked==null?null:Long.valueOf(
                checked.getProperty("match.participant.score")
            );
            this.teamScore=checked==null?null:Long.valueOf(
                checked.getProperty("match.team.score")
            );
            this.collectionKillCount=checked==null?null:
                ("UNKNOWN".equals(checked.getProperty("collection.killCount"))
                    ?null:Long.valueOf(
                        checked.getProperty("collection.killCount")
                    ));
            this.observerReturned=checked!=null&&
                Boolean.parseBoolean(
                    checked.getProperty("observer.returned")
                );
        }
        boolean replayAllowed(){return false;}
        boolean projectionCommitted(){return false;}
        boolean recoveryAuthorized(){return false;}
        boolean loginAllowed(){return false;}
        boolean rewardAllowed(){return false;}
    }

    private final Path root;
    private final Path preparedRoot;
    private final UUID fixtureOperation;
    private final R82OfflineSourceScopedCombatReceiptLedger source;
    private final ObjectiveProgressService objectives;
    private final String objectiveKey;
    private final MatchSessionService matches;
    private final MatchId matchId;
    private final String participantRef;
    private final MatchTeamId teamId;
    private final CollectionLogService collection;
    private final CollectionLogDefinition.CollectionId collectionId;
    private final Fault fault;
    private int observedCount;
    private int refusedCount;
    private String lastRefusal="NONE";

    R84OfflineProjectionObservationJournal(
        Path observationRoot,Path preparedRoot,UUID syntheticOperation,
        R82OfflineSourceScopedCombatReceiptLedger verifiedSource,
        ObjectiveProgressService objectives,String objectiveKey,
        MatchSessionService matches,MatchId matchId,
        String participantRef,MatchTeamId teamId,
        CollectionLogService collection,
        CollectionLogDefinition.CollectionId collectionId,
        Fault fault
    ){
        this.root=Objects.requireNonNull(observationRoot,"root")
            .toAbsolutePath().normalize();
        this.preparedRoot=Objects.requireNonNull(
            preparedRoot,"preparedRoot").toAbsolutePath().normalize();
        this.fixtureOperation=Objects.requireNonNull(
            syntheticOperation,"syntheticOperation"
        );
        this.source=Objects.requireNonNull(verifiedSource,"source");
        this.objectives=Objects.requireNonNull(objectives,"objectives");
        this.objectiveKey=Objects.requireNonNull(objectiveKey,"objectiveKey");
        this.matches=Objects.requireNonNull(matches,"matches");
        this.matchId=Objects.requireNonNull(matchId,"matchId");
        this.participantRef=Objects.requireNonNull(participantRef,"participantRef");
        this.teamId=Objects.requireNonNull(teamId,"teamId");
        this.collection=Objects.requireNonNull(collection,"collection");
        this.collectionId=Objects.requireNonNull(collectionId,"collectionId");
        this.fault=Objects.requireNonNull(fault,"fault");
    }

    @Override public synchronized void observe(
        CombatOutcome fact,int observerIndex,boolean returnedNormally
    ){
        long ordinal=source.activeVerifiedOrdinalForTest(fact);
        if(ordinal<=0||observerIndex<0||observerIndex>2){
            refusedCount++;
            lastRefusal="R84_OBSERVATION_DENIED_SOURCE_OR_INDEX";
            return;
        }
        try{
            if(R83OfflinePreparedCombatReceiptJournal.review(
                    preparedRoot,fixtureOperation,ordinal
                ).status!=
                R83OfflinePreparedCombatReceiptJournal.Status
                    .PREPARED_QUARANTINED)
                throw new IOException("PREPARED witness is not trusted");

            if(fault==Fault.DENY_FIRST_EVIDENCE_BEFORE_WRITE &&
               ordinal==1L&&observerIndex==0)
                throw new IOException("R84_INJECTED_MISSING_POST_EFFECT_EVIDENCE");

            ObjectiveProgressService.Snapshot objective=
                objectives.get(objectiveKey);
            MatchSession match=matches.get(matchId);
            if(objective==null||match==null)
                throw new IOException("required projection missing");
            MatchSession.Participant participant=match.participant(
                participantRef
            );
            MatchSession.Team team=match.team(teamId);
            if(participant==null||team==null)
                throw new IOException("required match actor/team missing");

            CollectionLogService.ProgressSnapshot log=
                collection.snapshot(collectionId);
            Properties evidence=new Properties();
            evidence.setProperty("format",FORMAT);
            evidence.setProperty("phase",PHASE);
            evidence.setProperty("fixture.operation",
                fixtureOperation.toString());
            evidence.setProperty("source.scope",source.scopeForAudit());
            evidence.setProperty("source.ordinal",Long.toString(ordinal));
            evidence.setProperty("observer.index",
                Integer.toString(observerIndex));
            evidence.setProperty("observer.returned",
                Boolean.toString(returnedNormally));
            evidence.setProperty("objective.progress",
                Long.toString(objective.progress));
            evidence.setProperty("match.participant.score",
                Long.toString(participant.scores.getOrDefault(
                    "kills",0L
                )));
            evidence.setProperty("match.team.score",
                Long.toString(team.scores.getOrDefault(
                    "team_kills",0L
                )));
            evidence.setProperty("collection.killCount",
                log.killCount().isPresent()?
                    Long.toString(log.killCount().getAsLong()):"UNKNOWN");
            evidence.setProperty("evidence.sha256",digest(evidence));

            Files.createDirectories(root);
            if(Files.isSymbolicLink(root)||
               !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("untrusted observation root");
            Path file=recordPath(root,fixtureOperation,
                ordinal,observerIndex);
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))
                throw new IOException("duplicate observer evidence");
            AtomicAccountPublisher.publish(
                file,evidence,
                "R84 OBSERVED_ONLY; NOT a gameplay projection commit"
            );
            readChecked(file,fixtureOperation,ordinal,observerIndex);
            observedCount++;
        }catch(IOException|RuntimeException failure){
            refusedCount++;
            lastRefusal="R84_OBSERVATION_BLOCKED_"+
                failure.getClass().getSimpleName();
            // Original observer side effects have ALREADY happened.
            // No replay, retry, rollback or "projection complete"
            // status may be inferred from evidence creation failure.
        }
    }

    static Review review(
        Path observationRoot,Path preparedRoot,UUID fixtureOperation,
        long ordinal,int observerIndex
    ){
        Objects.requireNonNull(fixtureOperation,"fixtureOperation");
        if(ordinal<=0||observerIndex<0||observerIndex>2)
            return new Review(Status.INDETERMINATE_BLOCKED,
                "INVALID_OBSERVATION_KEY",null);
        Path root=Objects.requireNonNull(
            observationRoot,"observationRoot"
        ).toAbsolutePath().normalize();
        Path prepared=Objects.requireNonNull(
            preparedRoot,"preparedRoot"
        ).toAbsolutePath().normalize();
        try{
            if(R83OfflinePreparedCombatReceiptJournal.review(
                    prepared,fixtureOperation,ordinal
                ).status!=
                R83OfflinePreparedCombatReceiptJournal.Status
                    .PREPARED_QUARANTINED)
                return new Review(Status.INDETERMINATE_BLOCKED,
                    "UNTRUSTED_PREPARED_WITNESS",null);
            if(Files.notExists(root,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_OBSERVATION_ROOT",null);
            if(Files.isSymbolicLink(root)||
               !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.INDETERMINATE_BLOCKED,
                    "UNTRUSTED_OBSERVATION_ROOT",null);
            Path file=recordPath(root,fixtureOperation,
                ordinal,observerIndex);
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.notExists(file,LinkOption.NOFOLLOW_LINKS))
                return new Review(Status.MISSING_BLOCKED,
                    "MISSING_POST_EFFECT_EVIDENCE",null);
            Properties checked=readChecked(
                file,fixtureOperation,ordinal,observerIndex
            );
            return new Review(Status.OBSERVED_QUARANTINED,
                "OBSERVATION_NOT_COMMIT",checked);
        }catch(IOException|RuntimeException failure){
            return new Review(Status.INDETERMINATE_BLOCKED,
                "UNTRUSTED_POST_EFFECT_EVIDENCE",null);
        }
    }

    static Path recordPathForTest(
        Path root,UUID operation,long ordinal,int observerIndex
    ){
        return recordPath(root.toAbsolutePath().normalize(),
            operation,ordinal,observerIndex);
    }

    private static Path recordPath(
        Path root,UUID operation,long ordinal,int observerIndex
    ){
        return root.resolve("r84-"+operation+"-"+ordinal+
            "-observer-"+observerIndex+".properties");
    }

    private static Properties readChecked(
        Path file,UUID operation,long ordinal,int index
    )throws IOException{
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||
           Files.size(file)<=0||Files.size(file)>MAX_BYTES)
            throw new IOException("invalid observation file");
        Properties data=new Properties();
        try(InputStream in=Files.newInputStream(
            file,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            data.load(in);
        }
        if(!data.stringPropertyNames().equals(KEYS)||
           !FORMAT.equals(data.getProperty("format"))||
           !PHASE.equals(data.getProperty("phase"))||
           !operation.toString().equals(data.getProperty(
               "fixture.operation"))||
           !Long.toString(ordinal).equals(data.getProperty(
               "source.ordinal"))||
           !Integer.toString(index).equals(data.getProperty(
               "observer.index"))||
           !digest(data).equals(data.getProperty("evidence.sha256")))
            throw new IOException("observation schema/checksum mismatch");
        try{
            String scope=data.getProperty("source.scope");
            if(!UUID.fromString(scope).toString().equals(scope))
                throw new IllegalArgumentException("noncanonical scope");
            if(!"true".equals(data.getProperty("observer.returned"))&&
               !"false".equals(data.getProperty("observer.returned")))
                throw new IllegalArgumentException("observer result");
            for(String field:Arrays.asList(
                    "objective.progress",
                    "match.participant.score","match.team.score")){
                long value=Long.parseLong(data.getProperty(field));
                if(value<0L)throw new IllegalArgumentException(field);
            }
            String kill=data.getProperty("collection.killCount");
            if(!"UNKNOWN".equals(kill)&&
               Long.parseLong(kill)<0L)
                throw new IllegalArgumentException("collection count");
        }catch(RuntimeException failure){
            throw new IOException("invalid observation evidence values",
                failure);
        }
        return data;
    }

    private static String digest(Properties evidence)throws IOException{
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            for(String field:FIELDS){
                String value=evidence.getProperty(field);
                if(value==null)throw new IOException(
                    "missing observation field "+field
                );
                md.update((field+"="+value+"\n").getBytes(
                    StandardCharsets.UTF_8
                ));
            }
            StringBuilder hex=new StringBuilder(64);
            for(byte value:md.digest())
                hex.append(String.format("%02x",value&0xff));
            return hex.toString();
        }catch(NoSuchAlgorithmException unavailable){
            throw new IOException("SHA-256 unavailable",unavailable);
        }
    }

    int observedCountForTest(){return observedCount;}
    int refusedCountForTest(){return refusedCount;}
    String lastRefusalForTest(){return lastRefusal;}
}
