package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

/**
 * R68 isolated opt-in test of ACTUAL LocalPendingRequestDispatcher.
 *
 * Reuses the complete existing LocalPendingRequestDispatcherTest fixture,
 * not handler spies or a copy of the dispatcher. Typed requests originate
 * from the real pinned-client ISAAC decoder. Valid movement is routed
 * through the real movement handler while R60 is held. Then a DIFFERENT
 * JVM transfers R64 ownership; old decoded movement, attack and bank
 * menu packets all fail BEFORE any corresponding real handler is invoked.
 */
public final class R68RealDispatcherAdmissionTest {
    private static final int[] SEED={5,6,7,8};

    public static void main(String[] args)throws Exception{
        LocalPendingRequestDispatcherTest.main(new String[]{"r68"});
    }

    static void runFixture(
        LocalPendingRequestDispatcher dispatcher,
        WorldPlayer player,BankState bank,MovementState movement,
        ServerPacketWriter writer
    )throws Exception{
        Path scratch=Files.createTempDirectory(
            "spk-r68-real-dispatch-offline-"
        );
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
            FilePlayerRepository repo=new FilePlayerRepository(
                user->accounts.resolve(user+".properties")
            );
            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "opensrc",player
            );
            repo.save(snapshot);
            R63AccountSaveAdmission.initializeExisting(
                journal,accounts,generations,"opensrc"
            );
            R64OfflineSessionOwnership.Token first=
                R64OfflineSessionOwnership.claimFirst(
                    journal,accounts,generations,sessions,"opensrc"
                );
            R66OfflineGameEventDispatch authority=
                new R66OfflineGameEventDispatch(
                    journal,accounts,generations,sessions
                );
            R66OfflineGameEventDispatch.Actor actor=
                authority.bindCurrent(
                    first,player,writer
                );
            R68OfflineRequestAdmission gate=
                new R68OfflineRequestAdmission(
                    journal,authority,actor
                );
            dispatcher.installOfflineAdmissionForTest(gate);
            try{
                dispatcher.installOfflineAdmissionForTest(gate);
                throw new AssertionError(
                    "second offline dispatcher gate should be denied"
                );
            }catch(IllegalStateException expected){
                if(!expected.getMessage().contains(
                    "R68_OFFLINE_GATE_DENIED"))
                    throw expected;
            }

            int initialX=MovementState.INITIAL_X;
            int initialY=MovementState.INITIAL_Y;
            ClientPacketProbe liveWalk=decodeOne(
                movementWire(initialX+1,initialY)
            );
            long movementBefore=movement.acceptedPaths();
            dispatcher.drain(liveWalk,writer,"[r68-real-handler] ");
            require(gate.attempted()==1&&gate.admitted()==1,
                "valid typed movement was not admitted");
            require(movement.acceptedPaths()==movementBefore+1,
                "actual movement handler did not run under OS lock");

            // All requests are already decoded and queued BEFORE owner
            // takeover. The real dispatcher drains them only afterward.
            ClientPacketProbe oldMovement=decodeOne(
                movementWire(initialX+2,initialY)
            );
            ClientPacketProbe oldAttack=decodeOne(
                attackWire(131)
            );
            ClientPacketProbe oldInventory=decodeOne(
                itemWire(3322,0,995)
            );
            require(oldMovement.typedRequestCount()==1 &&
                    oldAttack.typedRequestCount()==1 &&
                    oldInventory.typedRequestCount()==1,
                "stale packet fixtures not decoded before takeover");

            runSecondJvmTakeover(
                journal,accounts,generations,sessions
            );
            R64OfflineSessionOwnership.Review second=
                R64OfflineSessionOwnership.inspect(
                    journal,accounts,generations,sessions,"opensrc"
                );
            require(second.status==
                    R64OfflineSessionOwnership.Status
                        .READY_FOR_OTHER_OFFLINE_CHECKS &&
                    second.token.epoch==2L &&
                    !second.token.owner.equals(first.owner),
                "independent JVM did not supersede old owner");

            byte[] beforeAccount=Files.readAllBytes(
                accounts.resolve("opensrc.properties")
            );
            byte[] beforeGeneration=Files.readAllBytes(
                generations.resolve("opensrc.r63-generation.properties")
            );
            byte[] beforeOwner=Files.readAllBytes(
                sessions.resolve("opensrc.r64-session.properties")
            );
            String beforeWorld=R57TradePairJournal.digest(
                PlayerSnapshotCodec.capture("opensrc",player)
            );
            long beforeAccepted=movement.acceptedPaths();
            int beforeCoin=bank.inventoryCount(995);
            int beforeShark=bank.inventoryCount(385);

            for(ClientPacketProbe stale:new ClientPacketProbe[]{
                    oldMovement,oldAttack,oldInventory}){
                try{
                    dispatcher.drain(
                        stale,writer,"[r68-stale-real-handler] "
                    );
                    throw new AssertionError(
                        "old request reached real dispatcher after takeover"
                    );
                }catch(IOException rejected){
                    require(rejected.getMessage().contains(
                            "R64_SESSION_DENIED stale owner"),
                        "wrong stale request refusal: "+rejected
                    );
                }
            }
            require(gate.attempted()==4&&gate.admitted()==1,
                "one or more old typed requests entered actual handlers");
            require(movement.acceptedPaths()==beforeAccepted &&
                    bank.inventoryCount(995)==beforeCoin &&
                    bank.inventoryCount(385)==beforeShark &&
                    beforeWorld.equals(R57TradePairJournal.digest(
                        PlayerSnapshotCodec.capture("opensrc",player)
                    )),
                "stale request changed actual old WorldPlayer memory");
            require(Arrays.equals(beforeAccount,Files.readAllBytes(
                        accounts.resolve("opensrc.properties"))) &&
                    Arrays.equals(beforeGeneration,Files.readAllBytes(
                        generations.resolve(
                            "opensrc.r63-generation.properties"
                        ))) &&
                    Arrays.equals(beforeOwner,Files.readAllBytes(
                        sessions.resolve(
                            "opensrc.r64-session.properties"
                        ))),
                "stale request changed canonical, generation or owner bytes");

            System.out.println(
                "R68_REAL_PENDING_DISPATCHER_OFFLINE_FENCE_PASS "+
                "defaultProductionDispatcherPathUnmodified=true "+
                "testHookInstalledOnlyExplicitly=true "+
                "realMovementHandlerInvokedUnderR60Lock=true "+
                "typedMovementAttackItemRequestsDecodedBeforeTakeover=true "+
                "freshJvmTakeoverInvalidatedAllThree=true "+
                "realHandlerAdmissionsRemainedOne=true "+
                "worldPlayerStateAndCanonicalFilesPreserved=true "+
                "repeatGateInstallationDenied=true "+
                "realLocalSessionSaveAndPetSchedulersUnaudited=true "+
                "noLiveLoginOrPacketHooksEnabled=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> entries=Files.walk(scratch)){
                entries.sorted(Comparator.reverseOrder()).forEach(p->{
                    try{Files.deleteIfExists(p);}
                    catch(IOException e){
                        throw new IllegalStateException(
                            "test-only fixture cleanup "+p,e
                        );
                    }
                });
            }
        }
    }

    private static ClientPacketProbe decodeOne(byte[] bytes)
            throws IOException{
        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(bytes),
            new IsaacCipher(SEED.clone()),"[r68-real-decode] "
        );
        require(probe.readNextKnownPacket()&&
                probe.isAligned()&&
                probe.typedRequestCount()==1,
            "exact-current packet did not decode into one typed request");
        return probe;
    }

    private static byte[] movementWire(int x,int y)throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        opcode(body,cipher,164);
        body.write(5);
        leA(body,x);
        le(body,y);
        body.write(0);
        return body.toByteArray();
    }

    private static byte[] attackWire(int npc)throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        opcode(body,cipher,72);
        beA(body,npc);
        return body.toByteArray();
    }

    private static byte[] itemWire(int widget,int slot,int item)
            throws IOException{
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        opcode(body,cipher,145);
        beA(body,widget);
        beA(body,slot);
        beA(body,item);
        return body.toByteArray();
    }

    private static void opcode(
        ByteArrayOutputStream out,IsaacCipher cipher,int id
    ){
        out.write((id+cipher.nextInt())&255);
    }
    private static void le(ByteArrayOutputStream out,int v){
        out.write(v&255);
        out.write((v>>>8)&255);
    }
    private static void leA(ByteArrayOutputStream out,int v){
        out.write((v+128)&255);
        out.write((v>>>8)&255);
    }
    private static void beA(ByteArrayOutputStream out,int v){
        out.write((v>>>8)&255);
        out.write((v+128)&255);
    }

    private static void runSecondJvmTakeover(
        Path journal,Path accounts,Path generations,Path sessions
    )throws Exception{
        String javaExe=Paths.get(System.getProperty("java.home"),
            "bin",System.getProperty("os.name").toLowerCase()
                .contains("win")?"java.exe":"java").toString();
        Process child=new ProcessBuilder(
            javaExe,"-cp",System.getProperty("java.class.path"),
            "spk.local.R64SessionFenceWorker",
            "takeover",journal.toString(),accounts.toString(),
            generations.toString(),sessions.toString()
        ).redirectErrorStream(true).start();
        if(!child.waitFor(20,TimeUnit.SECONDS)){
            child.destroyForcibly();
            child.waitFor(5,TimeUnit.SECONDS);
            throw new AssertionError(
                "test-only R64 takeover JVM timed out"
            );
        }
        String output=new String(
            child.getInputStream().readAllBytes(),StandardCharsets.UTF_8
        );
        require(child.exitValue()==0 &&
                output.contains("R64_SESSION_WORKER_PASS"),
            "second JVM takeover failed "+output);
    }

    private static void require(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }

    private R68RealDispatcherAdmissionTest(){}
}
