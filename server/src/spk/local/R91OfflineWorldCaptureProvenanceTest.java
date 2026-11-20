package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R91 OFFLINE ONLY. Captures actual World-thread snapshots and proves their
 * immutable source stamp crosses deferred IO, coalesced tick checkpoint and
 * terminal save without being replaced by an IO-enqueue identity.
 *
 * Security boundary: these are cooperating in-process test hooks only.
 * A forged package-local stamp is NOT a cryptographic World attestation.
 */
public final class R91OfflineWorldCaptureProvenanceTest {
    private static final String USER="opensrc";

    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException(
                "R91 must not launch a child JVM");
        Path scratch=Files.createTempDirectory("spk-r91-world-stamp-");
        try{
            deferredCheckpointAndFinalFromRealWorld(
                scratch.resolve("world-capture"));
            oldWorldTicketDeniedAfterIncarnationRotation(
                scratch.resolve("old-world"));
            wrongStampRejectedBeforePublication(
                scratch.resolve("wrong-stream"),"STREAM");
            wrongStampRejectedBeforePublication(
                scratch.resolve("wrong-incarnation"),"INCARNATION");
            wrongStampRejectedBeforePublication(
                scratch.resolve("wrong-ordinal"),"ORDINAL");
            wrongStampRejectedBeforePublication(
                scratch.resolve("wrong-digest"),"DIGEST");
            missingStampDeniedWithoutDiskMutation(
                scratch.resolve("missing-stamp"));
            invalidStampIssuerRejectedAtWorldCapture(
                scratch.resolve("bad-issuer"));
            System.out.println(
                "R91_OFFLINE_WORLD_CAPTURE_PROVENANCE_PASS "+
                "realWorldThreadDeferredAndFinalCapture=true "+
                "worldThreadSnapshotDigestAndOrdinalBound=true "+
                "actualCheckpointStampFlowsThroughIoWorker=true "+
                "sourceStampPreservedAcrossDeferredSubmission=true "+
                "sameOwnerOldWorldTicketRefusedAfterRotation=true "+
                "newWorldSeededCaptureOrdinalContinues=true "+
                "wrongStreamIncarnationOrdinalAndDigestDenied=true "+
                "missingStampDeniedBeforeAccountWrite=true "+
                "badIssuerRejectedOnWorldCaptureThread=true "+
                "noRawRepositoryBypass=true "+
                "refusedWritesPreserveAllCanonicalBytes=true "+
                "realR60R63R64R89R90OfflineComposition=true "+
                "noCryptographicAuthenticationClaim=true "+
                "noProductionRuntimeOrClientWiring=true"
            );
        }finally{
            try(Stream<Path> walk=Files.walk(scratch)){
                walk.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException e){throw new IllegalStateException(
                        "R91 disposable cleanup failed "+path,e);
                    }
                });
            }
        }
    }

    private static void deferredCheckpointAndFinalFromRealWorld(
        Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        R90OfflineProducerContinuity.Session session=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R91OfflineWorldCaptureGate.installForTest(world,session);
            world.start();
            AtomicReference<WorldPlayerPersistence.CapturedSave> first=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                first.set(world.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r91] ","STAMPED_DEFERRED"
                ));
            },10_000L);
            require(first.get()!=null &&
                    first.get().ticket.captureOrdinal==1L,
                "R91 source-deferred ordinal not assigned on World");
            assertStamp(session,first.get().ticket,generation);
            awaitSuccess(world.persistence().submitCapturedWithBackpressure(
                first.get(),5_000L));
            require(session.gate.highestAcceptedOrdinal()==1L,
                "R91 stamped deferred save did not publish ordinal1");

            world.submitAndWait(player,generation,()->{
                spawn(player,565);
                world.persistence().checkpointDue(100L);
            },10_000L);
            world.persistence().awaitOfflineIoDrainForTest(15_000L);
            long deadline=System.nanoTime()+
                TimeUnit.SECONDS.toNanos(12);
            while(world.persistence().checkpointWrittenCount()<1L &&
                  System.nanoTime()<deadline)
                Thread.sleep(20L);
            require(world.persistence().checkpointWrittenCount()>=1L &&
                    session.gate.highestAcceptedOrdinal()==2L &&
                    world.persistence().checkpointRejectedCount()==0L,
                "R91 autosave checkpoint lost stamped World source");

            AtomicReference<WorldPlayerPersistence.CapturedSave> end=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,385);
                end.set(world.persistence().captureDeferredFinalSave(
                    USER,player,generation,0,"[r91] ","STAMPED_FINAL"
                ));
            },10_000L);
            require(end.get()!=null&&end.get().ticket.captureOrdinal==3L,
                "R91 final World snapshot ordinal wrong");
            assertStamp(session,end.get().ticket,generation);
            awaitSuccess(world.persistence().submitCapturedWithBackpressure(
                end.get(),5_000L));
            require(session.gate.highestAcceptedOrdinal()==3L,
                "R91 final save did not advance durable ordinal");
        }
        require(f.inspectProducer(f.ready())==3L,
            "R91 producer highwater did not survive final World save");
        R90OfflineProducerContinuity.Session restored=
            f.restart(f.ready());
        require(restored.ticket.committedOrdinal==3L&&
                restored.ticket.producerGeneration==2L,
            "R91 durable World provenance lineage not resumable");
    }

    private static void oldWorldTicketDeniedAfterIncarnationRotation(
        Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        R90OfflineProducerContinuity.Session oldSession=f.initialize();
        try(World oldWorld=World.isolatedForTest(
                60_000L,rawWriterMustFail(f.files()))){
            WorldPlayer player=f.player();
            long generation=oldWorld.registerPlayer(player,USER);
            R91OfflineWorldCaptureGate.installForTest(oldWorld,oldSession);
            oldWorld.start();
            AtomicReference<WorldPlayerPersistence.CapturedSave> held=
                new AtomicReference<>();
            oldWorld.submitAndWait(player,generation,()->{
                spawn(player,560);
                held.set(oldWorld.persistence().captureDeferredSave(
                    USER,player,generation,0,"[r91] ","HELD_OLD_WORLD"
                ));
            },10_000L);
            assertStamp(oldSession,held.get().ticket,generation);
            R90OfflineProducerContinuity.Session successor=
                f.restart(f.ready());
            require(successor.ticket.owner.equals(
                    oldSession.ticket.owner) &&
                    successor.ticket.ownerEpoch==
                    oldSession.ticket.ownerEpoch &&
                    successor.ticket.producerGeneration==
                    oldSession.ticket.producerGeneration+1L,
                "R91 failed same-owner incarnation rotation");
            Bytes afterRotation=f.bytes();
            WorldPlayerPersistence.SaveTicket rejected=
                oldWorld.persistence().submitCapturedWithBackpressure(
                    held.get(),5_000L);
            expectTicketDenied(rejected,"R90_STALE_PRODUCER");
            require(afterRotation.same(f.bytes()),
                "R91 old World write changed canonical records");

            try(World freshWorld=World.isolatedForTest(
                    60_000L,rawWriterMustFail(f.files()))){
                WorldPlayer fresh=f.player();
                long freshGeneration=freshWorld.registerPlayer(
                    fresh,USER);
                R91OfflineWorldCaptureGate.installForTest(
                    freshWorld,successor);
                freshWorld.start();
                AtomicReference<WorldPlayerPersistence.SaveTicket> next=
                    new AtomicReference<>();
                freshWorld.submitAndWait(fresh,freshGeneration,()->{
                    spawn(fresh,565);
                    next.set(freshWorld.persistence().captureAndSave(
                        USER,fresh,freshGeneration,0,"[r91] ",
                        "STAMPED_NEW_WORLD"
                    ));
                },10_000L);
                require(next.get()!=null &&
                        next.get().captureOrdinal==1L,
                    "R91 fresh World failed to resume seeded ordinal");
                assertStamp(successor,next.get(),freshGeneration);
                awaitSuccess(next.get());
            }
            require(f.inspectProducer(f.ready())==1L,
                "R91 new World failed to publish first incarnation capture");
        }
    }

    private static void wrongStampRejectedBeforePublication(
        Path root,String variant)throws Exception{
        Fixture f=new Fixture(root,true);
        R90OfflineProducerContinuity.Session session=f.initialize();
        WorldPlayerPersistence.OfflineCaptureStamp correct=
            stamp(session,1L,f.baseSnapshot(),1L);
        WorldPlayerPersistence.OfflineCaptureStamp modified;
        if("STREAM".equals(variant))
            modified=new WorldPlayerPersistence.OfflineCaptureStamp(
                correct.username,UUID.randomUUID(),
                correct.incarnation,correct.ownerEpoch,
                correct.producerGeneration,correct.captureOrdinal,
                correct.snapshotDigest,correct.worldPlayerGeneration);
        else if("INCARNATION".equals(variant))
            modified=new WorldPlayerPersistence.OfflineCaptureStamp(
                correct.username,correct.stream,UUID.randomUUID(),
                correct.ownerEpoch,correct.producerGeneration,
                correct.captureOrdinal,correct.snapshotDigest,
                correct.worldPlayerGeneration);
        else if("ORDINAL".equals(variant))
            modified=new WorldPlayerPersistence.OfflineCaptureStamp(
                correct.username,correct.stream,correct.incarnation,
                correct.ownerEpoch,correct.producerGeneration,
                2L,correct.snapshotDigest,correct.worldPlayerGeneration);
        else if("DIGEST".equals(variant))
            modified=new WorldPlayerPersistence.OfflineCaptureStamp(
                correct.username,correct.stream,correct.incarnation,
                correct.ownerEpoch,correct.producerGeneration,
                correct.captureOrdinal,"0".repeat(64),
                correct.worldPlayerGeneration);
        else throw new IllegalArgumentException("R91 invalid test variant");
        session.gate.requireWorldCaptureStampsForTest();
        Bytes baseline=f.bytes();
        expectFailure(()->session.gate.withStampedWorldCaptureForTest(
            1L,f.baseSnapshot(),modified,()->{
                throw new IOException("R91_RAW_REPOSITORY_BYPASS");
            }),"R91_CAPTURE_DENIED wrong producer/ordinal/snapshot");
        require(baseline.same(f.bytes()),
            "R91 forged "+variant+" stamp reached canonical files");
    }

    private static void missingStampDeniedWithoutDiskMutation(
        Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        R90OfflineProducerContinuity.Session session=f.initialize();
        session.gate.requireWorldCaptureStampsForTest();
        Bytes before=f.bytes();
        expectFailure(()->session.gate.withStampedWorldCaptureForTest(
            1L,f.baseSnapshot(),null,()->{
                throw new IOException("R91_RAW_REPOSITORY_BYPASS");
            }),"R91_CAPTURE_DENIED missing capture stamp");
        expectFailure(()->session.gate.withOrderedAdmission(
            1L,f.baseSnapshot(),()->{
                throw new IOException("R91_RAW_REPOSITORY_BYPASS");
            }),"R91_CAPTURE_DENIED unstamped direct admission");
        require(before.same(f.bytes()),
            "R91 missing stamp changed account or watermark");
    }

    private static void invalidStampIssuerRejectedAtWorldCapture(
        Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        f.initialize();
        Bytes before=f.bytes();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            world.persistence().installOfflineCaptureIssuerForTest(
                (ordinal,snapshot,worldGeneration)->
                    new WorldPlayerPersistence.OfflineCaptureStamp(
                        USER,UUID.randomUUID(),UUID.randomUUID(),
                        1L,1L,ordinal,
                        "0".repeat(64),worldGeneration
                    ));
            world.start();
            AtomicReference<String> refused=new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                try{
                    world.persistence().captureDeferredSave(
                        USER,player,generation,0,
                        "[r91] ","MALFORMED_ISSUER"
                    );
                    refused.set("unexpected-success");
                }catch(IllegalStateException expected){
                    refused.set(expected.getMessage());
                }
            },10_000L);
            require(refused.get()!=null &&
                    refused.get().contains("R91_CAPTURE_STAMP_DENIED"),
                "R91 invalid issuer failed to block on World capture");
        }
        require(before.same(f.bytes()),
            "R91 World-capture issue failed after modifying account files");
    }

    private static WorldPlayerPersistence.OfflineCaptureStamp stamp(
        R90OfflineProducerContinuity.Session session,long ordinal,
        PlayerSnapshot snapshot,long generation){
        return new WorldPlayerPersistence.OfflineCaptureStamp(
            session.ticket.username,session.ticket.stream,
            session.ticket.incarnation,session.ticket.ownerEpoch,
            session.ticket.producerGeneration,ordinal,
            R57TradePairJournal.digest(snapshot),generation);
    }

    private static void assertStamp(
        R90OfflineProducerContinuity.Session session,
        WorldPlayerPersistence.SaveTicket ticket,long generation){
        WorldPlayerPersistence.OfflineCaptureStamp stamp=
            ticket.provenance;
        require(stamp!=null &&
                stamp.captureOrdinal==ticket.captureOrdinal &&
                stamp.worldPlayerGeneration==generation &&
                stamp.username.equals(ticket.snapshot.username()) &&
                stamp.stream.equals(session.ticket.stream) &&
                stamp.incarnation.equals(session.ticket.incarnation) &&
                stamp.producerGeneration==
                    session.ticket.producerGeneration &&
                stamp.ownerEpoch==session.ticket.ownerEpoch &&
                stamp.snapshotDigest.equals(
                    R57TradePairJournal.digest(ticket.snapshot)),
            "R91 ticket provenance not bound to World snapshot/issuer");
    }

    private static void awaitSuccess(
        WorldPlayerPersistence.SaveTicket ticket)throws Exception{
        ticket.completion.get(15L,TimeUnit.SECONDS);
    }
    private static void expectTicketDenied(
        WorldPlayerPersistence.SaveTicket ticket,String code)
        throws Exception{
        try{
            ticket.completion.get(15L,TimeUnit.SECONDS);
            throw new AssertionError("R91 expected World write refusal");
        }catch(ExecutionException e){
            require(e.getCause() instanceof IOException &&
                    e.getCause().getMessage().contains(code),
                "R91 World save rejected incorrectly: "+e.getCause());
        }
    }
    private static void expectFailure(Checked work,String code)
        throws Exception{
        try{
            work.run();
            throw new AssertionError("R91 expected refusal "+code);
        }catch(IOException e){
            require(e.getMessage().contains(code),
                "R91 unexpected refusal "+e+" wanted "+code);
        }
    }
    private static PlayerRepository rawWriterMustFail(
        FilePlayerRepository files){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username)throws IOException{
                return files.load(username);
            }
            @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
                throw new IOException("R91_RAW_REPOSITORY_BYPASS");
            }
        };
    }
    private static void spawn(WorldPlayer player,int item){
        String result=player.bank().spawnItem(item,1,
            new ServerPacketWriter(new OutboundPacketQueue(),
                new IsaacCipher(new int[]{1,2,3,4})));
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R91 test-only bank change was not accepted "+result);
    }
    private static void require(boolean passed,String message){
        if(!passed)throw new AssertionError(message);
    }

    private static final class Bytes{
        final byte[] account,ledger,owner,watermark,producer;
        Bytes(byte[] a,byte[] l,byte[] o,byte[] w,byte[] p){
            account=a;ledger=l;owner=o;watermark=w;producer=p;
        }
        boolean same(Bytes b){
            return Arrays.equals(account,b.account)&&
                Arrays.equals(ledger,b.ledger)&&
                Arrays.equals(owner,b.owner)&&
                Arrays.equals(watermark,b.watermark)&&
                Arrays.equals(producer,b.producer);
        }
    }
    private static final class Fixture{
        final Path root,journal,accounts,generations,sessions;
        Fixture(Path root,boolean create)throws IOException{
            this.root=root;
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            if(create){
                Files.createDirectory(root);
                Files.createDirectory(journal);
                Files.createDirectory(accounts);
                Files.createDirectory(generations);
                Files.createDirectory(sessions);
            }
        }
        FilePlayerRepository files(){
            return new FilePlayerRepository(
                username->accounts.resolve(username+".properties"));
        }
        R90OfflineProducerContinuity.Session initialize()
            throws IOException{
            WorldPlayer first=new WorldPlayer();
            spawn(first,995);
            files().save(PlayerSnapshotCodec.capture(USER,first));
            R60TradePairCoordinator.initializeEmpty(journal);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,USER);
            R64OfflineSessionOwnership.Token owner=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,USER);
            R89OfflineDurableCaptureAdmission.initialize(
                journal,accounts,generations,sessions,owner);
            return R90OfflineProducerContinuity.initialize(
                journal,accounts,generations,sessions,owner);
        }
        R64OfflineSessionOwnership.Token ready(){
            R64OfflineSessionOwnership.Review review=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,USER);
            require(review.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    review.token!=null&&!review.loginAuthorized(),
                "R91 R64 fixture not consistent");
            return review.token;
        }
        R90OfflineProducerContinuity.Session restart(
            R64OfflineSessionOwnership.Token owner)throws IOException{
            return R90OfflineProducerContinuity.restart(
                journal,accounts,generations,sessions,owner);
        }
        long inspectProducer(
            R64OfflineSessionOwnership.Token owner)throws IOException{
            return R90OfflineProducerContinuity
                .inspectCommittedOrdinalForTest(
                    journal,accounts,generations,sessions,owner);
        }
        PlayerSnapshot baseSnapshot()throws IOException{
            return files().load(USER).orElseThrow(
                ()->new IOException("R91 fixture account missing"));
        }
        WorldPlayer player()throws IOException{
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(
                baseSnapshot(),player);
            return player;
        }
        Bytes bytes()throws IOException{
            return new Bytes(
                Files.readAllBytes(accounts.resolve(USER+".properties")),
                Files.readAllBytes(generations.resolve(
                    USER+".r63-generation.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r64-session.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r89-watermark.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r90-producer.properties")));
        }
    }
    private R91OfflineWorldCaptureProvenanceTest(){}
}
