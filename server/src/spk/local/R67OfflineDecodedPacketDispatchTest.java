package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * R67: exact pinned-client ISAAC/framing/typed decoding (164/72/145)
 * -> delayed test-only dispatcher -> R66/R64/R63/R60 OS owner gate
 * -> instrumented movement/combat/inventory handlers.
 *
 * A real separate JVM takes over after old packets are decoded but
 * before they are dispatched. All three must refuse stale ownership
 * with zero handler invocations and zero in-memory / canonical changes.
 */
public final class R67OfflineDecodedPacketDispatchTest {
    private static final int[] SEED={
        0x01020304,0x11223344,0x55667788,0x13572468
    };
    private static final UUID PENDING=UUID.fromString(
        "11111111-2222-4333-8444-555555555555"
    );

    private static final class HandlerSpy
            implements R67OfflineDecodedPacketDispatch.InstrumentedHandler {
        int walks,attacks,items;
        int lastX=-1,lastY=-1,lastNpc=-1,lastWidget=-1;

        @Override public void accept(
            R66OfflineGameEventDispatch.Actor actor,
            R67OfflineDecodedPacketDispatch.FrozenCommand command
        )throws IOException{
            switch(command.family){
                case MOVEMENT:
                    walks++;
                    lastX=command.x;
                    lastY=command.y;
                    return;
                case NPC_ATTACK:
                    attacks++;
                    lastNpc=command.npcIndex;
                    return;
                case INVENTORY_ACTION:
                    items++;
                    lastWidget=command.widgetId;
                    // Synthetic TEST handler effect. This is NOT the
                    // actual bank menu-action semantics or a trade.
                    actor.player.bank().spawnItem(
                        385,1,actor.packets
                    );
                    return;
                default:
                    throw new IOException(
                        "R67_UNEXPECTED_HANDLER_FAMILY"
                    );
            }
        }

        void untouched(){
            require(walks==0&&attacks==0&&items==0,
                "stale decoded packet reached a gameplay handler");
        }
    }

    public static void main(String[] args)throws Exception{
        Path scratch=Files.createTempDirectory("spk-r67-decoded-dispatch-");
        try{
            Path journal=scratch.resolve("journal");
            Path accounts=scratch.resolve("accounts");
            Path generations=scratch.resolve("generations");
            Path sessions=scratch.resolve("sessions");
            Files.createDirectory(journal);
            Files.createDirectory(accounts);
            Files.createDirectory(generations);
            Files.createDirectory(sessions);
            R60TradePairCoordinator.initializeEmpty(journal);

            WorldPlayer oldPlayer=new WorldPlayer();
            ServerPacketWriter oldPackets=packets();
            oldPlayer.bank().spawnItem(995,100,oldPackets);
            PlayerSnapshot canonical=PlayerSnapshotCodec.capture(
                "opensrc",oldPlayer
            );
            FilePlayerRepository repo=repository(accounts);
            repo.save(canonical);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"opensrc"
            );
            R64OfflineSessionOwnership.Token ownerA=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,"opensrc"
                );
            R66OfflineGameEventDispatch owners=
                new R66OfflineGameEventDispatch(
                    journal,accounts,generations,sessions
                );
            R66OfflineGameEventDispatch.Actor actorA=
                owners.bindCurrent(ownerA,oldPlayer,oldPackets);
            HandlerSpy spy=new HandlerSpy();
            R67OfflineDecodedPacketDispatch dispatch=
                new R67OfflineDecodedPacketDispatch(owners,spy);

            ClientRequest[] decoded=decodeExactWire();
            R67OfflineDecodedPacketDispatch.CapturedRequest[] queued=
                new R67OfflineDecodedPacketDispatch.CapturedRequest[3];
            for(int i=0;i<queued.length;i++){
                queued[i]=dispatch.capture(actorA,decoded[i]);
            }
            require(queued[0].command.family==
                    R67OfflineDecodedPacketDispatch.Family.MOVEMENT &&
                    queued[0].command.opcode==164 &&
                    queued[0].command.x==3088 &&
                    queued[0].command.y==3495 &&
                    queued[1].command.family==
                    R67OfflineDecodedPacketDispatch.Family.NPC_ATTACK &&
                    queued[1].command.npcIndex==131 &&
                    queued[2].command.family==
                    R67OfflineDecodedPacketDispatch.Family
                        .INVENTORY_ACTION &&
                    queued[2].command.widgetId==3322 &&
                    queued[2].command.slot==0 &&
                    queued[2].command.itemId==995,
                "framed packet field transforms or typed FIFO wrong");

            // Negative controls: reject a malformed metadata/schema pair
            // and an unknown opcode BEFORE accepting a gameplay callback.
            ClientRequest bogus=new NpcActionClientRequest(
                new NpcAction(72,131),
                ClientRequestMetadata.exactCurrent(
                    72,"INCORRECT_SCHEMA","INCORRECT_SOURCE"
                )
            );
            expectDenied(()->dispatch.capture(actorA,bogus),
                "R67_CAPTURE_DENIED metadata mismatch");
            ByteArrayOutputStream unknownWire=new ByteArrayOutputStream();
            IsaacCipher invalidEncoder=new IsaacCipher(SEED.clone());
            writeOpcode(unknownWire,invalidEncoder,255);
            ClientPacketProbe unknown=new ClientPacketProbe(
                new ByteArrayInputStream(unknownWire.toByteArray()),
                new IsaacCipher(SEED.clone()),"[r67-unknown] "
            );
            require(!unknown.readNextKnownPacket()&&
                    !unknown.isAligned()&&
                    unknown.takeTypedRequest()==null,
                "unknown original-client opcode was routed instead of paused");

            String oldMemory=R57TradePairJournal.digest(
                PlayerSnapshotCodec.capture("opensrc",oldPlayer)
            );
            ExecutorService queue=Executors.newFixedThreadPool(3);
            CountDownLatch captured=new CountDownLatch(3);
            CountDownLatch[] fire=new CountDownLatch[]{
                new CountDownLatch(1),
                new CountDownLatch(1),
                new CountDownLatch(1)
            };
            try{
                @SuppressWarnings("unchecked")
                Future<String>[] waiting=new Future[3];
                for(int i=0;i<waiting.length;i++){
                    final int index=i;
                    waiting[i]=queue.submit(()->{
                        captured.countDown();
                        if(!fire[index].await(15,TimeUnit.SECONDS))
                            throw new AssertionError(
                                "queued decoder callback never resumed"
                            );
                        return expectStaleOwner(
                            dispatch,queued[index]
                        );
                    });
                }
                require(captured.await(10,TimeUnit.SECONDS),
                    "decoded callbacks not queued on three threads");
                for(Future<String> future:waiting)
                    require(!future.isDone(),
                        "decoded callback ran before takeover");

                // Real second JVM takes over the account while all three
                // ORIGINAL decoded commands are still queued.
                runTakeover(journal,accounts,generations,sessions);
                R64OfflineSessionOwnership.Token ownerB=
                    requireReadyOwner(
                        journal,accounts,generations,sessions,2L
                    );
                require(!ownerA.owner.equals(ownerB.owner),
                    "second JVM failed to replace owner UUID");

                byte[] accountAtTakeover=Files.readAllBytes(
                    accounts.resolve("opensrc.properties")
                );
                byte[] generationAtTakeover=Files.readAllBytes(
                    generations.resolve(
                        "opensrc.r63-generation.properties"
                    )
                );
                byte[] ownerAtTakeover=Files.readAllBytes(
                    sessions.resolve("opensrc.r64-session.properties")
                );

                WorldPlayer nextPlayer=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(canonical,nextPlayer);
                R66OfflineGameEventDispatch.Actor actorB=
                    owners.bindCurrent(ownerB,nextPlayer,packets());

                for(int i=0;i<waiting.length;i++){
                    fire[i].countDown();
                    require("STALE_OWNER_DENIED".equals(
                        waiting[i].get(15,TimeUnit.SECONDS)),
                        "stale decoded family "+i+" reached its handler");
                }
                spy.untouched();
                require(oldMemory.equals(R57TradePairJournal.digest(
                        PlayerSnapshotCodec.capture("opensrc",oldPlayer))) &&
                    oldMemory.equals(R57TradePairJournal.digest(
                        PlayerSnapshotCodec.capture("opensrc",nextPlayer))),
                    "old decoded requests modified either test-owned actor");
                require(Arrays.equals(accountAtTakeover,Files.readAllBytes(
                        accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(generationAtTakeover,Files.readAllBytes(
                        generations.resolve(
                            "opensrc.r63-generation.properties"
                        ))) &&
                    Arrays.equals(ownerAtTakeover,Files.readAllBytes(
                        sessions.resolve(
                            "opensrc.r64-session.properties"
                        ))),
                    "stale decoded requests wrote account/owner evidence");

                // The current owner can execute a fresh REAL decoded
                // movement, NPC attack and item action in strict order.
                ClientRequest[] valid=decodeExactWire();
                R67OfflineDecodedPacketDispatch.CapturedRequest walk=
                    dispatch.capture(actorB,valid[0]);
                // Freeze decoded values: even deliberate later mutation of
                // a mutable MovementRequest backing array is ignored.
                MovementRequest mutable=
                    ((MovementClientRequest)valid[0]).movement();
                mutable.x[0]=9999;mutable.y[0]=9999;
                dispatch.dispatch(walk);
                require(spy.walks==1&&spy.lastX==3088&&
                        spy.lastY==3495,
                    "frozen movement was not dispatched exactly once");
                expectDenied(()->dispatch.dispatch(walk),
                    "R66_EVENT_DENIED duplicate queued event");

                R67OfflineDecodedPacketDispatch.CapturedRequest attack=
                    dispatch.capture(actorB,valid[1]);
                dispatch.dispatch(attack);
                R67OfflineDecodedPacketDispatch.CapturedRequest inventory=
                    dispatch.capture(actorB,valid[2]);
                dispatch.dispatch(inventory);
                require(spy.walks==1&&spy.attacks==1&&spy.items==1&&
                        spy.lastNpc==131&&spy.lastWidget==3322 &&
                        nextPlayer.bank().inventoryCount(385)==1,
                    "current decoded packet did not reach its test handler");

                // Recheck account generations during dispatch: a decoded
                // request captured with the old canonical generation
                // cannot mutate an actor after a save has advanced it.
                R67OfflineDecodedPacketDispatch.CapturedRequest oldGen=
                    dispatch.capture(actorB,decodeExactWire()[0]);
                R64OfflineSessionOwnership.Token saved=
                    R64OfflineSessionOwnership.saveOwned(
                        journal,accounts,generations,sessions,ownerB,
                        bump(canonical,560),false
                    );
                expectDenied(()->dispatch.dispatch(oldGen),
                    "R64_SESSION_DENIED stale generation");
                require(spy.walks==1,
                    "stale-generation movement reached handler");

                WorldPlayer current=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    repo.load("opensrc").orElseThrow(
                        ()->new IOException("canonical account missing")
                    ),current
                );
                R66OfflineGameEventDispatch.Actor currentActor=
                    owners.bindCurrent(saved,current,packets());
                R67OfflineDecodedPacketDispatch.CapturedRequest oldSlot=
                    dispatch.capture(currentActor,decodeExactWire()[1]);
                WorldPlayer rebound=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    repo.load("opensrc").orElseThrow(
                        ()->new IOException("canonical account missing")
                    ),rebound
                );
                R66OfflineGameEventDispatch.Actor replacement=
                    owners.bindCurrent(saved,rebound,packets());
                expectDenied(()->dispatch.dispatch(oldSlot),
                    "R66_EVENT_DENIED stale actor incarnation");
                require(spy.attacks==1,
                    "superseded actor's combat request reached handler");

                R67OfflineDecodedPacketDispatch.CapturedRequest logoutQueued=
                    dispatch.capture(replacement,decodeExactWire()[2]);
                R64OfflineSessionOwnership.Token loggedOut=
                    R64OfflineSessionOwnership.releaseOwner(
                        journal,accounts,generations,sessions,saved
                    );
                expectDenied(()->dispatch.dispatch(logoutQueued),
                    "R64_SESSION_DENIED stale owner");
                require(spy.items==1,"logout-delayed item handler reached");

                R64OfflineSessionOwnership.Token ownerC=
                    R64OfflineSessionOwnership.replaceOwner(
                        journal,accounts,generations,sessions,loggedOut
                    );
                WorldPlayer third=new WorldPlayer();
                PlayerSnapshotCodec.applyValidated(
                    repo.load("opensrc").orElseThrow(
                        ()->new IOException("canonical account missing")
                    ),third
                );
                R66OfflineGameEventDispatch.Actor actorC=
                    owners.bindCurrent(ownerC,third,packets());
                R67OfflineDecodedPacketDispatch.CapturedRequest pending=
                    dispatch.capture(actorC,decodeExactWire()[0]);
                PlayerSnapshot preparedA=snapshot("opensrc",995,1);
                PlayerSnapshot preparedB=snapshot("zpeer",385,1);
                long revision=R60TradePairCoordinator.registerPrepared(
                    journal,1L,Collections.emptyList(),PENDING,
                    preparedA,bump(preparedA,560),
                    preparedB,bump(preparedB,561)
                );
                require(revision==2L,"pending-pair fixture missing");
                expectDenied(()->dispatch.dispatch(pending),
                    "R64_SESSION_DENIED account/pair not clear");
                require(spy.walks==1,
                    "pending pair did not prevent movement handler");
                System.out.println(
                    "R67_EXACT_DECODED_PACKET_DISPATCH_FENCE_PASS "+
                    "pinnedClientISAAC164_72_145Decoded=true "+
                    "typedRequestMetadataAndFIFOChecked=true "+
                    "threeDecodedCommandsQueuedBeforeCrossJvmTakeover=true "+
                    "staleMovementCombatInventoryNeverReachedHandlers=true "+
                    "bothActorSnapshotsAndCanonicalFilesPreserved=true "+
                    "currentOwnerHandlersInvokedExactlyOnce=true "+
                    "malformedAndUnknownOpcodeRejected=true "+
                    "mutableDecodedPayloadFrozenOnCapture=true "+
                    "staleGenerationActorAndLogoutDenied=true "+
                    "pendingTradeBlocksCapturedDecodedMovement=true "+
                    "noRealSocketOrProductionHandlerHook=true"
                );
            }finally{
                for(CountDownLatch latch:fire)latch.countDown();
                queue.shutdownNow();
                require(queue.awaitTermination(10,TimeUnit.SECONDS),
                    "test-owned decoder callback threads did not stop");
            }
        }finally{
            try(java.util.stream.Stream<Path> entries=Files.walk(scratch)){
                entries.sorted(Comparator.reverseOrder()).forEach(path->{
                    try{Files.deleteIfExists(path);}
                    catch(IOException problem){
                        throw new IllegalStateException(
                            "test-owned cleanup "+path,problem
                        );
                    }
                });
            }
        }
    }

    private static ClientRequest[] decodeExactWire()
            throws Exception{
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        IsaacCipher encoder=new IsaacCipher(SEED.clone());
        // Exact v308 opcode164 variable-length walking body:
        // startX LE_A, startY LE, run flag negated.
        writeOpcode(wire,encoder,164);
        wire.write(5);
        leA(wire,3088);
        le(wire,3495);
        wire.write(0);

        // Exact v308 opcode72 NPC Attack: BE short-A index131.
        writeOpcode(wire,encoder,72);
        beA(wire,131);

        // Exact v308 opcode145 menu option632:
        // widgetId, slot and itemId are BE short-A.
        writeOpcode(wire,encoder,145);
        beA(wire,3322);
        beA(wire,0);
        beA(wire,995);

        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(wire.toByteArray()),
            new IsaacCipher(SEED.clone()),"[r67-decoded-test] "
        );
        for(int i=0;i<3;i++)
            require(probe.readNextKnownPacket(),
                "exact-current packet probe stopped index="+i);
        require(probe.isAligned()&&probe.typedRequestCount()==3,
            "decoder stopped or lost cross-family FIFO");
        ClientRequest[] out=new ClientRequest[3];
        for(int i=0;i<3;i++)
            out[i]=probe.takeTypedRequest();
        require(out[0] instanceof MovementClientRequest&&
                out[1] instanceof NpcActionClientRequest&&
                out[2] instanceof ItemContainerActionClientRequest &&
                probe.takeTypedRequest()==null,
            "exact decoder emitted wrong typed packet order");
        return out;
    }

    private static void writeOpcode(
        OutputStream out,IsaacCipher cipher,int opcode
    )throws IOException{
        out.write((opcode+cipher.nextInt())&255);
    }
    private static void beA(OutputStream out,int value)throws IOException{
        out.write((value>>>8)&255);
        out.write((value+128)&255);
    }
    private static void le(OutputStream out,int value)throws IOException{
        out.write(value&255);
        out.write((value>>>8)&255);
    }
    private static void leA(OutputStream out,int value)throws IOException{
        out.write((value+128)&255);
        out.write((value>>>8)&255);
    }

    private static String expectStaleOwner(
        R67OfflineDecodedPacketDispatch dispatch,
        R67OfflineDecodedPacketDispatch.CapturedRequest command
    )throws IOException{
        try{
            dispatch.dispatch(command);
            throw new AssertionError(
                "old decoded packet entered gameplay handler"
            );
        }catch(IOException expected){
            if(!expected.getMessage().contains(
                    "R64_SESSION_DENIED stale owner"))
                throw expected;
        }
        return "STALE_OWNER_DENIED";
    }

    private static FilePlayerRepository repository(Path accounts){
        return new FilePlayerRepository(
            user->accounts.resolve(user+".properties")
        );
    }
    private static ServerPacketWriter packets(){
        return new ServerPacketWriter(
            new OutboundPacketQueue(),
            new IsaacCipher(new int[]{1,2,3,4})
        );
    }
    private static PlayerSnapshot snapshot(
        String user,int item,int amount)throws IOException{
        WorldPlayer player=new WorldPlayer();
        player.bank().spawnItem(item,amount,packets());
        return PlayerSnapshotCodec.capture(user,player);
    }
    private static PlayerSnapshot bump(
        PlayerSnapshot before,int item)throws IOException{
        WorldPlayer player=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(before,player);
        player.bank().spawnItem(item,1,packets());
        return PlayerSnapshotCodec.capture(before.username(),player);
    }

    private static R64OfflineSessionOwnership.Token requireReadyOwner(
        Path journal,Path accounts,Path generations,Path sessions,long epoch
    ){
        R64OfflineSessionOwnership.Review result=
            R64OfflineSessionOwnership.inspect(
                journal,accounts,generations,sessions,"opensrc"
            );
        require(result.status==
                R64OfflineSessionOwnership.Status
                    .READY_FOR_OTHER_OFFLINE_CHECKS &&
                result.token!=null && result.token.epoch==epoch,
            "test owner not ready epoch="+epoch+
                " status="+result.status+" reason="+result.reason);
        return result.token;
    }

    private static void runTakeover(
        Path journal,Path accounts,Path generations,Path sessions
    )throws Exception{
        Process worker=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker",
            "takeover",journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString()
        ).redirectErrorStream(true).start();
        if(!worker.waitFor(20,TimeUnit.SECONDS)){
            worker.destroyForcibly();
            worker.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError("test-only JVM takeover timed out");
        }
        String output=new String(
            worker.getInputStream().readAllBytes(),StandardCharsets.UTF_8
        );
        require(worker.exitValue()==0&&
                output.contains("R64_SESSION_WORKER_PASS"),
            "takeover worker failed: "+worker.exitValue()+" "+output);
    }

    private static String javaExe(){
        return Paths.get(System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase().contains("win")
                ?"java.exe":"java").toString();
    }
    @FunctionalInterface interface Action{
        void run()throws Exception;
    }
    private static void expectDenied(Action action,String reason)
            throws Exception{
        try{
            action.run();
            throw new AssertionError("expected "+reason);
        }catch(IOException expected){
            require(expected.getMessage().contains(reason),
                "incorrect refusal "+expected+" wanted "+reason);
        }
    }
    private static void require(boolean condition,String error){
        if(!condition)throw new AssertionError(error);
    }
    private R67OfflineDecodedPacketDispatchTest(){}
}
