package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R96 OFFLINE ONLY: three durable retirement crash boundaries, each
 * re-inspected by a separate JVM with no in-memory actor or lease table.
 */
public final class R96OfflineDurableRetirementWitnessTest {
    private static final String USER="opensrc";
    private enum Cut { PREPARED_ONLY,AFTER_UNREGISTER,COMMITTED }
    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    public static void main(String[] args)throws Exception{
        if(args.length!=0){
            if(args.length!=3)
                throw new IllegalArgumentException(
                    "R96/R97 invalid child review arguments");
            if("child-review".equals(args[0])){
                freshJvmReadOnlyReview(Paths.get(args[1]),
                    Cut.valueOf(args[2]));
                return;
            }
            if("r97-refusal".equals(args[0])){
                freshJvmR97NegativeReview(Paths.get(args[1]),args[2]);
                return;
            }
            throw new IllegalArgumentException(
                "R96/R97 unrecognized child invocation");
        }
        Path scratch=Files.createTempDirectory("spk-r96-retirement-");
        try{
            verifyNoReceiptWithoutCommittedSource(
                scratch.resolve("unsaved"));
            for(Cut cut:Cut.values())
                runCrashWindow(scratch.resolve(cut.name()),cut);
            runAtomicReplacementFault(
                scratch.resolve("r97-failed-prepare"),false);
            runAtomicReplacementFault(
                scratch.resolve("r97-failed-commit"),true);
            runReceiptIntegrityAndStaleSource(
                scratch.resolve("r97-corruption"));
            System.out.println(
                "R97_OFFLINE_RETIREMENT_WITNESS_FAULT_MATRIX_PASS "+
                "preparedAtomicReplaceFailureKeepsActorFrozen=true "+
                "committedAtomicReplaceFailureKeepsPreparedWitness=true "+
                "stagingFilesCleanedAfterReplaceFailure=true "+
                "digestSchemaAndNonregularRecordsRefused=true "+
                "adjacentR16StageRefusedWithoutCleanup=true "+
                "staleR90IncarnationRefusesCommittedWitness=true "+
                "freshJvmTamperAndStaleSourceRefused=true "+
                "canonicalAccountBytesUnchangedOnDenial=true "+
                "noAutomaticRecoveryOrLogin=true");
            System.out.println(
                "R96_OFFLINE_DURABLE_RETIREMENT_WITNESS_PASS "+
                "R60R89R90ProducerSourceBound=true "+
                "noRetirementReceiptForUncommittedSave=true "+
                "worldFreezeWithoutLifecycleDiskIo=true "+
                "preparedBlocksUnregister=true "+
                "actualUnregisterBeforeCommittedReceipt=true "+
                "interruptedPostUnregisterPreservesPrepared=true "+
                "successfulUnregisterCommitsReceipt=true "+
                "threeIndependentFreshJvmReviews=true "+
                "readOnlyReviewsPreserveCanonicalFiles=true "+
                "noLoginOrAutomaticRecovery=true "+
                "noLiveWorldOrSessionWiring=true"
            );
        }finally{
            try(Stream<Path> files=Files.walk(scratch)){
                files.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){throw new IllegalStateException(
                        "R96 disposable fixture cleanup "+p,e);}
                });
            }
        }
    }


    /** R97: controlled failure *after* R16 forced staging, immediately
     * before PREPARED or COMMITTED atomic replacement. */
    private static void runAtomicReplacementFault(
        Path root,boolean afterUnregister)throws Exception{
        Fixture f=new Fixture(root,true);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate gate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            world.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> saved=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                saved.set(world.persistence().captureAndSave(
                    USER,player,generation,0,"[r97] ",
                    "FINAL_BEFORE_INTERRUPTED_WITNESS"));
            },10_000L);
            require(saved.get()!=null &&
                    saved.get().captureOrdinal==1L,
                "R97 failed to capture real World save");
            saved.get().completion.get(15L,TimeUnit.SECONDS);
            require(f.producerHighwater()==1L,
                "R97 source not committed before injected fault");
            Bytes before=f.bytes();
            R96OfflineDurableRetirementWitness witness=
                f.witness(world,player,generation,1L,gate,producer);
            if(!afterUnregister){
                witness.failNextAtomicReplacementForTest(
                    R96OfflineDurableRetirementWitness.Phase.PREPARED);
                expectDenied(witness::prepareForTest,
                    "R97_INJECTED_ATOMIC_REPLACE_PREPARED");
                require(!Files.exists(f.witnessPath()) &&
                        world.players().owns(player,generation) &&
                        gate.retiringForTest(player,generation) &&
                        !world.unregisterPlayer(player,generation),
                    "R97 injected PREPARED fault allowed retirement");
                R96OfflineDurableRetirementWitness.Review read=
                    R96OfflineDurableRetirementWitness.inspectForTest(
                        f.journal,f.accounts,f.generations,f.sessions,
                        f.ready());
                require(read.status==
                            R96OfflineDurableRetirementWitness.Status
                                .MISSING_UNPROVEN &&
                        !read.loginAuthorized(),
                    "R97 failed PREPARED publication fabricated evidence");
            }else{
                witness.prepareForTest();
                byte[] prepared=Files.readAllBytes(f.witnessPath());
                witness.failNextAtomicReplacementForTest(
                    R96OfflineDurableRetirementWitness.Phase.COMMITTED);
                expectDenied(
                    ()->witness.unregisterAndCommitForTest(false),
                    "R97_INJECTED_ATOMIC_REPLACE_COMMITTED");
                require(!world.players().owns(player,generation) &&
                        Arrays.equals(prepared,
                            Files.readAllBytes(f.witnessPath())),
                    "R97 failed COMMITTED replacement changed PREPARED");
                R96OfflineDurableRetirementWitness.Review read=
                    R96OfflineDurableRetirementWitness.inspectForTest(
                        f.journal,f.accounts,f.generations,f.sessions,
                        f.ready());
                require(read.status==
                            R96OfflineDurableRetirementWitness.Status
                                .PREPARED_QUARANTINED &&
                        !read.loginAuthorized(),
                    "R97 post-unregister replacement fault lost quarantine");
            }
            requireNoR96Stage(f);
            require(before.same(f.bytes()),
                "R97 failed receipt publication changed canonical account");
        }
        if(afterUnregister)
            verifyFreshJvmReview(root,Cut.AFTER_UNREGISTER);
    }

    private static void requireNoR96Stage(Fixture f)throws IOException{
        String name=f.witnessPath().getFileName().toString();
        try(Stream<Path> files=Files.list(f.sessions)){
            require(!files.anyMatch(entry->{
                String n=entry.getFileName().toString();
                return n.startsWith("."+name+".r16-") &&
                    n.endsWith(".stage");
            }),"R97 unclean R16 staging after failed atomic publication");
        }
    }

    private static void runReceiptIntegrityAndStaleSource(
        Path root)throws Exception{
        // Establish an authentic COMMITTED receipt with the *actual*
        // World lifecycle and re-open it after World.close().
        runCrashWindow(root,Cut.COMMITTED);
        Fixture f=new Fixture(root,false);
        Bytes baseline=f.bytes();
        Path target=f.witnessPath();
        byte[] receipt=Files.readAllBytes(target);
        String stored=new String(receipt,StandardCharsets.UTF_8);
        require(stored.contains("phase=COMMITTED") &&
                stored.contains("owner.uuid="),
            "R97 committed properties format unexpected");

        // Tamper one field without updating the hash. The only permitted
        // response is a read-only denial, never repair/promotion.
        Files.writeString(target,stored.replace(
            "phase=COMMITTED","phase=PREPARED"),
            StandardCharsets.UTF_8);
        expectReadOnlyRefusal(f,"R96_WITNESS_QUARANTINED");
        verifyFreshJvmR97Refusal(root,"R96_WITNESS_QUARANTINED");

        // Additional/missing property is a schema violation, independent
        // of whether its replacement text is otherwise parsable.
        Files.writeString(target,stored+"\nunknown.r97.field=1\n",
            StandardCharsets.UTF_8);
        expectReadOnlyRefusal(f,"R96_WITNESS_QUARANTINED");

        // Invalid UUID and empty record both fail closed.
        Files.writeString(target,stored.replace(
            "owner.uuid=","owner.uuid=not-a-uuid-"),
            StandardCharsets.UTF_8);
        expectReadOnlyRefusal(f,"R96_WITNESS_QUARANTINED");
        Files.writeString(target,"",StandardCharsets.UTF_8);
        expectReadOnlyRefusal(f,"R96_WITNESS_QUARANTINED");

        // A leftover adjacent R16 stage is not deleted or adopted by
        // the reader. The canonical receipt remains exactly unchanged.
        Files.write(target,receipt);
        Path staged=f.sessions.resolve(
            "."+target.getFileName().toString()+".r16-r97orphan.stage");
        Files.writeString(staged,"R97 deliberately abandoned stage");
        expectReadOnlyRefusal(f,"R51_AMBIGUOUS_ACCOUNT_STAGING_DENIED");
        require(Files.isRegularFile(staged) &&
                Arrays.equals(receipt,Files.readAllBytes(target)),
            "R97 read path repaired ambiguous R16 stage");
        Files.delete(staged);

        // Nonregular receipt is refused rather than treated as missing.
        Path saved=f.sessions.resolve("r97-witness-original.bytes");
        Files.move(target,saved);
        try{
            Files.createDirectory(target);
            expectReadOnlyRefusal(f,"R96_WITNESS_QUARANTINED");
        }finally{
            Files.deleteIfExists(target);
            Files.move(saved,target);
        }
        require(Arrays.equals(receipt,Files.readAllBytes(target)) &&
                baseline.same(f.bytes()),
            "R97 corruption tests unexpectedly changed original evidence");

        // Rotate only R90's source incarnation under the existing R60
        // lock. The old COMMITTED witness must become unusable, even
        // though the R89 highwater and R64 owner are unchanged.
        R64OfflineSessionOwnership.Token owner=f.ready();
        R90OfflineProducerContinuity.Session next=
            R90OfflineProducerContinuity.restart(
                f.journal,f.accounts,f.generations,f.sessions,owner);
        require(next.ticket.committedOrdinal==1L &&
                !next.ticket.incarnation.equals(
                    R96OfflineDurableRetirementWitness.Receipt.parse(
                        readReceiptProperties(target)).incarnation),
            "R97 producer restart did not rotate incarnation");
        expectReadOnlyRefusal(f,"R96_SOURCE_DENIED");
        verifyFreshJvmR97Refusal(root,"R96_SOURCE_DENIED");
    }

    private static java.util.Properties readReceiptProperties(Path target)
        throws IOException{
        java.util.Properties p=new java.util.Properties();
        try(java.io.InputStream input=Files.newInputStream(target)){
            p.load(input);
        }
        return p;
    }

    private static void expectReadOnlyRefusal(
        Fixture f,String expected)throws Exception{
        Bytes before=f.bytes();
        byte[] receipt=Files.readAllBytes(f.witnessPath());
        try{
            R96OfflineDurableRetirementWitness.inspectForTest(
                f.journal,f.accounts,f.generations,f.sessions,f.ready());
            throw new AssertionError(
                "R97 malformed/stale witness incorrectly accepted");
        }catch(IOException denied){
            require(denied.getMessage().contains(expected),
                "R97 unexpected refusal "+denied+" expected "+expected);
        }
        require(before.same(f.bytes()) &&
                Arrays.equals(receipt,
                    Files.readAllBytes(f.witnessPath())),
            "R97 read-only refusal modified canonical evidence");
    }

    private static void freshJvmR97NegativeReview(
        Path root,String expected)throws Exception{
        Fixture f=new Fixture(root,false);
        expectReadOnlyRefusal(f,expected);
        System.out.println(
            "R97_FRESH_JVM_REFUSAL_PASS reason="+expected+
            " canonicalFilesUnchanged=true noLoginOrRecovery=true");
    }

    private static void verifyFreshJvmR97Refusal(
        Path root,String expected)throws Exception{
        boolean windows=System.getProperty("os.name","")
            .toLowerCase(Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            windows?"java.exe":"java");
        Path log=root.resolve("r97-fresh-jvm-"+expected+".log");
        Process child=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            R96OfflineDurableRetirementWitnessTest.class.getName(),
            "r97-refusal",root.toString(),expected
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            require(child.waitFor(25L,TimeUnit.SECONDS),
                "R97 fresh JVM read-only refusal timed out");
            String output=Files.readString(log,StandardCharsets.UTF_8);
            require(child.exitValue()==0 &&
                    output.contains(
                        "R97_FRESH_JVM_REFUSAL_PASS reason="+expected),
                "R97 fresh JVM incorrectly admitted evidence: "+output);
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(15L,TimeUnit.SECONDS);
            }
        }
    }

    private static void verifyNoReceiptWithoutCommittedSource(
        Path root)throws Exception{
        Fixture f=new Fixture(root,true);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate gate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            world.start();
            R96OfflineDurableRetirementWitness witness=
                f.witness(world,player,generation,1L,gate,producer);
            require(!Files.exists(f.witnessPath()),
                "R96 unexpectedly found a retirement receipt");
            expectDenied(witness::prepareForTest,
                "R96_SOURCE_DENIED");
            require(!world.unregisterPlayer(player,generation) &&
                    world.players().owns(player,generation) &&
                    !Files.exists(f.witnessPath()) &&
                    gate.retiringForTest(player,generation),
                "R96 uncommitted save incorrectly allowed retirement");
        }
    }

    private static void runCrashWindow(Path root,Cut cut)
        throws Exception{
        Fixture f=new Fixture(root,true);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,rawWriterMustFail(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate gate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            world.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> saved=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                saved.set(world.persistence().captureAndSave(
                    USER,player,generation,0,"[r96] ",
                    "R96_FINAL_BEFORE_RETIREMENT"));
            },10_000L);
            require(saved.get()!=null &&
                    saved.get().captureOrdinal==1L,
                "R96 real World capture missing");
            saved.get().completion.get(15L,TimeUnit.SECONDS);
            require(f.producerHighwater()==1L,
                "R96 R89/R90 save did not durably commit first");
            Bytes before=f.bytes();
            R96OfflineDurableRetirementWitness witness=
                f.witness(world,player,generation,1L,gate,producer);
            witness.prepareForTest();
            require(Files.isRegularFile(f.witnessPath()) &&
                    !world.unregisterPlayer(player,generation) &&
                    world.players().owns(player,generation),
                "R96 durable PREPARED failed to fence direct unregister");
            require(before.same(f.bytes()) &&
                    gate.retiringForTest(player,generation) &&
                    gate.activeSavesForTest(player,generation)==0,
                "R96 PREPARED unexpectedly changed account records");
            if(cut==Cut.AFTER_UNREGISTER){
                expectDenied(
                    ()->witness.unregisterAndCommitForTest(true),
                    "R96_INJECTED_AFTER_WORLD_UNREGISTER_BEFORE_COMMIT");
                require(!world.players().owns(player,generation),
                    "R96 injected cut did not actually unregister actor");
            }else if(cut==Cut.COMMITTED){
                witness.unregisterAndCommitForTest(false);
                require(!world.players().owns(player,generation),
                    "R96 COMMITTED witness emitted without unregister");
            }
            R96OfflineDurableRetirementWitness.Review observed=
                R96OfflineDurableRetirementWitness.inspectForTest(
                    f.journal,f.accounts,f.generations,f.sessions,
                    f.ready());
            R96OfflineDurableRetirementWitness.Status wanted=
                cut==Cut.COMMITTED
                ?R96OfflineDurableRetirementWitness.Status
                    .COMMITTED_WITNESS_ONLY
                :R96OfflineDurableRetirementWitness.Status
                    .PREPARED_QUARANTINED;
            require(observed.status==wanted&&
                    observed.ordinal==1L&&
                    observed.worldGeneration==generation&&
                    !observed.loginAuthorized()&&
                    !observed.automaticallyRecovered(),
                "R96 parent read-only inspection misclassified stage");
            require(before.same(f.bytes()),
                "R96 witnessed lifecycle transition changed account bytes");
        }
        // The old World, actor, and in-memory quarantine are now gone.
        // Check durable evidence with a genuinely separate Java process.
        verifyFreshJvmReview(root,cut);
    }

    private static void freshJvmReadOnlyReview(
        Path root,Cut cut)throws Exception{
        Fixture f=new Fixture(root,false);
        Bytes before=f.bytes();
        byte[] receiptBefore=Files.readAllBytes(f.witnessPath());
        R64OfflineSessionOwnership.Review owner=
            R64OfflineSessionOwnership.inspect(
                f.journal,f.accounts,f.generations,f.sessions,USER);
        require(owner.status==R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                owner.token!=null && !owner.loginAuthorized() &&
                !owner.automaticRecoveryAttempted(),
            "R96 fresh JVM R64 owner inspection not read-only");
        R96OfflineDurableRetirementWitness.Review evidence=
            R96OfflineDurableRetirementWitness.inspectForTest(
                f.journal,f.accounts,f.generations,f.sessions,
                owner.token);
        R96OfflineDurableRetirementWitness.Status wanted=
            cut==Cut.COMMITTED
                ?R96OfflineDurableRetirementWitness.Status
                    .COMMITTED_WITNESS_ONLY
                :R96OfflineDurableRetirementWitness.Status
                    .PREPARED_QUARANTINED;
        require(evidence.status==wanted&&evidence.ordinal==1L&&
                !evidence.loginAuthorized()&&
                !evidence.automaticallyRecovered(),
            "R96 fresh JVM upgraded uncertain stage or granted login");
        require(before.same(f.bytes()) &&
                Arrays.equals(receiptBefore,
                    Files.readAllBytes(f.witnessPath())),
            "R96 review mutated durable account or witness bytes");
        System.out.println(
            "R96_FRESH_JVM_WITNESS_REVIEW_PASS cut="+cut+
            " observed="+evidence.status+
            " canonicalBytesUnchanged=true"+
            " noAutomaticLoginOrRecovery=true");
    }

    private static void verifyFreshJvmReview(
        Path root,Cut cut)throws Exception{
        boolean windows=System.getProperty("os.name","")
            .toLowerCase(Locale.ROOT).contains("win");
        Path java=Paths.get(System.getProperty("java.home"),"bin",
            windows?"java.exe":"java");
        Path log=root.resolve("r96-fresh-jvm.log");
        Process child=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            R96OfflineDurableRetirementWitnessTest.class.getName(),
            "child-review",root.toString(),cut.name()
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{
            require(child.waitFor(25L,TimeUnit.SECONDS),
                "R96 fresh JVM review timed out "+cut);
            String output=Files.readString(log,StandardCharsets.UTF_8);
            require(child.exitValue()==0 &&
                    output.contains(
                        "R96_FRESH_JVM_WITNESS_REVIEW_PASS cut="+cut),
                "R96 fresh process review failed: "+output);
        }finally{
            if(child.isAlive()){
                child.destroyForcibly();
                child.waitFor(15L,TimeUnit.SECONDS);
            }
        }
    }

    private static void expectDenied(Checked action,String message)
        throws Exception{
        try{
            action.run();
            throw new AssertionError(
                "R96 expected denial missing: "+message);
        }catch(IOException expected){
            require(expected.getMessage().contains(message),
                "R96 wrong denial: "+expected);
        }
    }

    private static void spawn(WorldPlayer player,int item)
        throws IOException{
        ServerPacketWriter packets=new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(
                new int[]{1,2,3,4}));
        String outcome=player.bank().spawnItem(item,1,packets);
        require(outcome.startsWith("ITEM_SPAWN_OK"),
            "R96 World snapshot bank mutation denied "+outcome);
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
                throw new IOException(
                    "R96_RAW_REPOSITORY_BYPASS_DENIED");
            }
        };
    }

    private static void require(boolean check,String message){
        if(!check)throw new AssertionError(message);
    }

    private static final class Bytes{
        final byte[][] records;
        Bytes(byte[][] records){this.records=records;}
        boolean same(Bytes another){
            if(records.length!=another.records.length)return false;
            for(int i=0;i<records.length;i++)
                if(!Arrays.equals(records[i],another.records[i]))
                    return false;
            return true;
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
                Files.createDirectories(journal);
                Files.createDirectories(accounts);
                Files.createDirectories(generations);
                Files.createDirectories(sessions);
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
        WorldPlayer player()throws IOException{
            WorldPlayer player=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(files().load(USER)
                .orElseThrow(()->new IOException(
                    "R96 missing source account")),player);
            return player;
        }
        R64OfflineSessionOwnership.Token ready(){
            R64OfflineSessionOwnership.Review review=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,USER);
            require(review.status==R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                review.token!=null && !review.loginAuthorized(),
                "R96 source R64 owner not current");
            return review.token;
        }
        R96OfflineDurableRetirementWitness witness(
            World world,WorldPlayer player,long generation,long ordinal,
            R93OfflineNonblockingWorldTurnoverGate gate,
            R90OfflineProducerContinuity.Session producer){
            return R96OfflineDurableRetirementWitness.createForTest(
                world,player,generation,ordinal,gate,ready(),
                producer.ticket,journal,accounts,generations,sessions);
        }
        Path witnessPath(){
            return R96OfflineDurableRetirementWitness.fileForTest(
                sessions,USER);
        }
        long producerHighwater()throws IOException{
            return R90OfflineProducerContinuity
                .inspectCommittedOrdinalForTest(
                    journal,accounts,generations,sessions,ready());
        }
        Bytes bytes()throws IOException{
            return new Bytes(new byte[][]{
                Files.readAllBytes(accounts.resolve(
                    USER+".properties")),
                Files.readAllBytes(generations.resolve(
                    USER+".r63-generation.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r64-session.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r89-watermark.properties")),
                Files.readAllBytes(sessions.resolve(
                    USER+".r90-producer.properties"))
            });
        }
    }

    private R96OfflineDurableRetirementWitnessTest(){}
}
