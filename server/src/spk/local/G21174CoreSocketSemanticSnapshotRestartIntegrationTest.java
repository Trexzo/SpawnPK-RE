package spk.local;

import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.174: stronger version of G21.173's real loopback restart proof.
 * Mutation MUST originate in the World-owned command inbox and MUST
 * survive two independent socket sessions, World instances and disk
 * final saves. No graphical client or historical server parity claim.
 */
public final class G21174CoreSocketSemanticSnapshotRestartIntegrationTest {
    private static final String ACCOUNT="opensrc";
    private static final String NAMESPACE="core_reconnect";
    private static final String KEY="phase";
    private static final String FULL_KEY="extension.core_reconnect.phase";

    private static void require(boolean valid,String reason){
        if(!valid)throw new AssertionError("G21.174 "+reason);
    }

    @FunctionalInterface private interface Condition{
        boolean check()throws Exception;
    }

    private static void await(Condition check,String reason)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6L);
        while(System.nanoTime()<deadline){
            if(check.check())return;
            Thread.sleep(10L);
        }
        require(check.check(),"timed out "+reason);
    }

    private static String inMemoryPhase(WorldPlayer player){
        return player.snapshotExtensions().namespace(NAMESPACE).get(KEY);
    }

    private static void setPhase(World world,WorldPlayer player,
                                 String phase)throws Exception{
        final long generation=player.generation();
        CompletableFuture<Void> queued=world.submit(
            player,generation,()->{
                player.snapshotExtensions().replaceNamespace(
                    NAMESPACE,Collections.singletonMap(KEY,phase));
            });
        queued.get(4L,TimeUnit.SECONDS);
        require(phase.equals(inMemoryPhase(player)),
            "World-owned mutation missing phase="+phase);
    }

    private static String diskPhase(FilePlayerRepository repository)
        throws Exception{
        Optional<PlayerSnapshot> saved=repository.load(ACCOUNT);
        require(saved.isPresent(),"no persisted account snapshot");
        return saved.get().values().get(FULL_KEY);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21174-semantic-restart-");
        String previous=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World firstWorld=null;
        World secondWorld=null;
        try{
            Path accountFile=LocalAccountProfiles.accountFile(ACCOUNT);
            Path accidentalSecondary=LocalAccountProfiles.accountFile("src");
            require(!Files.exists(accountFile,LinkOption.NOFOLLOW_LINKS),
                "test account was not initially empty");
            FilePlayerRepository disk=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            firstWorld=World.isolatedForTest(50L);
            final World first=firstWorld;
            WorldPlayer oldPlayer;

            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(first)){
                try(Socket connected=server.login(ACCOUNT)){
                    await(()->first.players().byName(ACCOUNT)!=null,
                        "initial authenticated World membership");
                    oldPlayer=first.players().byName(ACCOUNT);
                    require(first.players().size()==1,
                        "unexpected first World membership");
                    setPhase(first,oldPlayer,"FIRST_WORLD_MUTATION");
                    await(()->first.clock().tick()>=2,
                        "first World owned ticks");
                }
                await(()->first.players().size()==0,
                    "first final logout/unregister");
                server.waitCompleted();
            }
            require(Files.isRegularFile(
                    accountFile,LinkOption.NOFOLLOW_LINKS),
                "final logout did not write account");
            require("FIRST_WORLD_MUTATION".equals(diskPhase(disk)),
                "first real session saved defaults rather than mutation");
            require(!Files.exists(
                    accidentalSecondary,LinkOption.NOFOLLOW_LINKS),
                "first account moved into secondary alias");

            first.close();
            require(first.closed(),"first World did not close");
            require(first.submit(
                    oldPlayer,oldPlayer.generation(),()->{})
                        .isCompletedExceptionally(),
                "closed first World accepted a stale player command");

            secondWorld=World.isolatedForTest(50L);
            final World second=secondWorld;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(second)){
                try(Socket connected=server.login(ACCOUNT)){
                    await(()->second.players().byName(ACCOUNT)!=null,
                        "fresh World reconnect membership");
                    require(second.players().size()==1,
                        "extra player allocated on fresh World");
                    WorldPlayer reloaded=second.players().byName(ACCOUNT);
                    require(reloaded!=oldPlayer,
                        "same WorldPlayer reused across World close");
                    require("FIRST_WORLD_MUTATION".equals(
                            inMemoryPhase(reloaded)),
                        "actual reconnect did not hydrate first saved value");
                    setPhase(second,reloaded,"SECOND_WORLD_MUTATION");
                    await(()->second.clock().tick()>=2,
                        "second World owned ticks");
                }
                await(()->second.players().size()==0,
                    "second final logout/unregister");
                server.waitCompleted();
            }

            require("SECOND_WORLD_MUTATION".equals(diskPhase(disk)),
                "second logout failed to durably replace first marker");
            require(!Files.exists(
                    accidentalSecondary,LinkOption.NOFOLLOW_LINKS),
                "World restart unexpectedly created secondary account");
            second.close();
            require(second.closed(),"second World did not close");
            boolean leaseFree=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            require(leaseFree,"publication lease/watch leak");

            System.out.println("G21174_SOCKET_SEMANTIC_DIAGNOSTICS"+
                " realSocketLogins=2 worldCommandMutations=2"+
                " fullWorldRestarts=1 exactHydratedValue=true"+
                " finalSaveFirstValue=true finalSaveSecondValue=true"+
                " staleOldWorldCommandRejected=true"+
                " unintendedSecondaryProfiles=0"+
                " oldAndNewWorldPlayersDistinct=true"+
                " independentWorldTicks=true zeroJvmLeases="+leaseFree);
            System.out.println("G21174_SOCKET_SEMANTIC_PASS"+
                " realSocketSessionStateRoundtrip=true"+
                " originalGraphicalClientProven=false"+
                " originalServerGameplayProven=false"+
                " rawWriterExclusion=false"+
                " independentProvenance=false atomicCrashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " sessionRelease=false ack=false");
        }finally{
            if(secondWorld!=null&&!secondWorld.closed())
                secondWorld.close();
            if(firstWorld!=null&&!firstWorld.closed())
                firstWorld.close();
            if(previous==null)
                System.clearProperty("spk.local.accountFile");
            else
                System.setProperty("spk.local.accountFile",previous);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        run();
    }
}
