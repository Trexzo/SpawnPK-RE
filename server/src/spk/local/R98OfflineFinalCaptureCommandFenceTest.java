package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R98 OFFLINE ONLY: actual World command inbox, queued-before-quiesce
 * dispatch, final immutable capture, R89/R90 commit and R96 retirement.
 */
public final class R98OfflineFinalCaptureCommandFenceTest {
    private static final String USER="opensrc";

    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R98 has no child actions");
        Path scratch=Files.createTempDirectory("spk-r98-final-capture-");
        try{
            finalCaptureAndDurableRetirement(
                scratch.resolve("final-capture"));
            unquiescedSaveDoesNotAuthorizeRetirement(
                scratch.resolve("unquiesced"));
            System.out.println(
                "R98_OFFLINE_FINAL_CAPTURE_COMMAND_FENCE_PASS "+
                "queuedBeforeFreezeDeniedAtDispatch=true "+
                "submittedAfterFreezeDeniedAtEnqueue=true "+
                "noPostSnapshotGameplayCommandMutatedPlayer=true "+
                "quiescenceAndSnapshotCapturedInSingleWorldAction=true "+
                "capturedWorldOwnerAndOrdinalBound=true "+
                "R96RequiresCompletedQuiescedFinalSave=true "+
                "wrongCaptureOrdinalRejectedBeforeRetirement=true "+
                "unquiescedSaveCannotAuthorizeRetirement=true "+
                "R89R90FinalSnapshotCommitted=true "+
                "R96RetirementWitnessCommittedAfterUnregister=true "+
                "noProductionWorldOrSessionWiring=true");
        }finally{
            try(Stream<Path> walk=Files.walk(scratch)){
                walk.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException ex){throw new IllegalStateException(
                        "R98 scratch cleanup "+p,ex);}
                });
            }
        }
    }

    private static void finalCaptureAndDurableRetirement(
        Path root)throws Exception{
        Fixture f=new Fixture(root);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,forbidRawSave(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate ioGate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            R98OfflineFinalCaptureCommandFence ingress=
                new R98OfflineFinalCaptureCommandFence(world);
            ingress.installForTest();
            world.start();

            AtomicInteger executedAfterFinal=new AtomicInteger();
            AtomicReference<CompletableFuture<Void>> queuedBefore=
                new AtomicReference<>();
            AtomicReference<WorldPlayerPersistence.SaveTicket> finalSave=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                // This is enqueued while the current World handler is
                // running, and therefore cannot execute until afterward.
                queuedBefore.set(world.submit(player,generation,()->{
                    executedAfterFinal.incrementAndGet();
                    spawn(player,385);
                }));
                spawn(player,560);
                finalSave.set(
                    ingress.quiesceAndCaptureFinalInsideActorForTest(
                        player,generation,USER,0,
                        "[r98] ","QUIESCED_FINAL"));
            },10_000L);
            require(ingress.quiescedForTest(player,generation) &&
                    queuedBefore.get()!=null&&finalSave.get()!=null,
                "R98 actual World actor did not quiesce/capture");
            expectCommandDenied(queuedBefore.get(),
                "R74_COMMAND_QUIESCED before handler");
            CompletableFuture<Void> after=world.submit(
                player,generation,()->{
                    executedAfterFinal.incrementAndGet();
                    spawn(player,565);
                });
            expectCommandDenied(after,
                "R74_COMMAND_QUIESCED before enqueue");
            require(executedAfterFinal.get()==0,
                "R98 queued mutation executed after final snapshot");
            WorldPlayerPersistence.SaveTicket ticket=finalSave.get();
            require(ticket.captureOrdinal==1L &&
                    ticket.provenance!=null &&
                    ticket.provenance.capturedOwner==player &&
                    ticket.provenance.worldPlayerGeneration==generation,
                "R98 final save missing real World capture provenance");
            ticket.completion.get(15L,TimeUnit.SECONDS);
            require(f.producerHighwater()==1L,
                "R98 final save not committed through R89/R90");

            R96OfflineDurableRetirementWitness wrong=f.witness(
                world,player,generation,2L,ioGate,ingress,producer);
            expectIoDenied(wrong::prepareForTest,
                "R98_RETIRE_DENIED");
            require(!Files.exists(f.witnessPath()) &&
                    world.players().owns(player,generation),
                "R98 wrong ordinal changed retirement state");

            R96OfflineDurableRetirementWitness witness=f.witness(
                world,player,generation,1L,ioGate,ingress,producer);
            witness.prepareForTest();
            require(!world.unregisterPlayer(player,generation) &&
                    world.players().owns(player,generation),
                "R98 direct unregister bypassed R96 PREPARED state");
            witness.unregisterAndCommitForTest(false);
            require(!world.players().owns(player,generation),
                "R98 World unregister did not complete");
            R96OfflineDurableRetirementWitness.Review inspected=
                R96OfflineDurableRetirementWitness.inspectForTest(
                    f.journal,f.accounts,f.generations,f.sessions,
                    f.ready());
            require(inspected.status==
                    R96OfflineDurableRetirementWitness.Status
                        .COMMITTED_WITNESS_ONLY &&
                    inspected.ordinal==1L &&
                    !inspected.loginAuthorized()&&
                    executedAfterFinal.get()==0,
                "R98 post-capture mutation or invalid retirement witness");
            PlayerSnapshot persisted=f.files().load(USER).orElseThrow(
                ()->new IOException("R98 committed account missing"));
            require(R57TradePairJournal.digest(persisted).equals(
                    R57TradePairJournal.digest(ticket.snapshot)),
                "R98 persisted canonical snapshot differed from final World capture");
        }
    }

    private static void unquiescedSaveDoesNotAuthorizeRetirement(
        Path root)throws Exception{
        Fixture f=new Fixture(root);
        R90OfflineProducerContinuity.Session producer=f.initialize();
        try(World world=World.isolatedForTest(
                60_000L,forbidRawSave(f.files()))){
            WorldPlayer player=f.player();
            long generation=world.registerPlayer(player,USER);
            R93OfflineNonblockingWorldTurnoverGate ioGate=
                R93OfflineNonblockingWorldTurnoverGate
                    .installFailureQuarantineForTest(world,producer);
            R98OfflineFinalCaptureCommandFence ingress=
                new R98OfflineFinalCaptureCommandFence(world);
            ingress.installForTest();
            world.start();
            AtomicReference<WorldPlayerPersistence.SaveTicket> save=
                new AtomicReference<>();
            world.submitAndWait(player,generation,()->{
                spawn(player,560);
                save.set(world.persistence().captureAndSave(
                    USER,player,generation,0,"[r98] ","UNQUIESCED_SAVE"));
            },10_000L);
            save.get().completion.get(15L,TimeUnit.SECONDS);
            require(f.producerHighwater()==1L &&
                    !ingress.quiescedForTest(player,generation),
                "R98 unquiesced fixture not initialized");
            R96OfflineDurableRetirementWitness forged=f.witness(
                world,player,generation,1L,ioGate,ingress,producer);
            expectIoDenied(forged::prepareForTest,
                "R98_RETIRE_DENIED");
            require(!Files.exists(f.witnessPath()) &&
                    world.players().owns(player,generation),
                "R98 arbitrary committed save authorized retirement");
            AtomicInteger mutation=new AtomicInteger();
            world.submitAndWait(player,generation,()->{
                mutation.incrementAndGet();
                spawn(player,385);
            },10_000L);
            require(mutation.get()==1,
                "R98 non-quiesced World command unexpectedly fenced");
        }
    }

    @FunctionalInterface private interface Checked {
        void run()throws Exception;
    }

    private static void expectIoDenied(
        Checked action,String expected)throws Exception{
        try{
            action.run();
            throw new AssertionError("R98 expected refusal missing");
        }catch(IOException denied){
            require(denied.getMessage().contains(expected),
                "R98 wrong witness refusal "+denied);
        }
    }

    private static void expectCommandDenied(
        CompletableFuture<Void> task,String expected)throws Exception{
        try{
            task.get(10L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R98 gameplay command ran despite final capture");
        }catch(ExecutionException|CancellationException denied){
            require(denied.toString().contains(expected),
                "R98 wrong command refusal "+denied);
        }
    }

    private static void spawn(WorldPlayer player,int item)
        throws IOException{
        ServerPacketWriter packets=new ServerPacketWriter(
            new OutboundPacketQueue(),new IsaacCipher(
                new int[]{1,2,3,4}));
        String result=player.bank().spawnItem(item,1,packets);
        require(result.startsWith("ITEM_SPAWN_OK"),
            "R98 item mutation denied "+result);
    }

    private static PlayerRepository forbidRawSave(
        FilePlayerRepository files){
        return new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(
                String username)throws IOException{
                return files.load(username);
            }
            @Override public void save(PlayerSnapshot snapshot)
                throws IOException{
                throw new IOException(
                    "R98_RAW_REPOSITORY_BYPASS_DENIED");
            }
        };
    }

    private static void require(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }

    private static final class Fixture {
        final Path root,journal,accounts,generations,sessions;
        Fixture(Path root)throws IOException{
            this.root=root;
            journal=root.resolve("journal");
            accounts=root.resolve("accounts");
            generations=root.resolve("generations");
            sessions=root.resolve("sessions");
            Files.createDirectories(journal);
            Files.createDirectories(accounts);
            Files.createDirectories(generations);
            Files.createDirectories(sessions);
        }
        FilePlayerRepository files(){
            return new FilePlayerRepository(
                user->accounts.resolve(user+".properties"));
        }
        R90OfflineProducerContinuity.Session initialize()
            throws IOException{
            WorldPlayer seed=new WorldPlayer();
            spawn(seed,995);
            files().save(PlayerSnapshotCodec.capture(USER,seed));
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
            PlayerSnapshotCodec.applyValidated(
                files().load(USER).orElseThrow(
                    ()->new IOException("R98 missing seed account")),
                player);
            return player;
        }
        R64OfflineSessionOwnership.Token ready(){
            R64OfflineSessionOwnership.Review review=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,USER);
            require(review.status==R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                review.token!=null&&!review.loginAuthorized(),
                "R98 R64 owner was not ready");
            return review.token;
        }
        long producerHighwater()throws IOException{
            return R90OfflineProducerContinuity
                .inspectCommittedOrdinalForTest(
                    journal,accounts,generations,sessions,ready());
        }
        R96OfflineDurableRetirementWitness witness(
            World world,WorldPlayer player,long generation,long ordinal,
            R93OfflineNonblockingWorldTurnoverGate ioGate,
            R98OfflineFinalCaptureCommandFence ingress,
            R90OfflineProducerContinuity.Session producer){
            return R96OfflineDurableRetirementWitness
                .createCommandFencedForTest(
                    world,player,generation,ordinal,ioGate,ingress,
                    ready(),producer.ticket,journal,accounts,
                    generations,sessions);
        }
        Path witnessPath(){
            return R96OfflineDurableRetirementWitness.fileForTest(
                sessions,USER);
        }
    }

    private R98OfflineFinalCaptureCommandFenceTest(){}
}
