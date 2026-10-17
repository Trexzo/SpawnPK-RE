package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Chat 1 / G21.175: real TCP login must FAIL-CLOSED on unsupported
 * future snapshot version. The attempt must not overwrite the account,
 * select a secondary profile, or register a default player.
 *
 * Manual restoration below is a TEST FIXTURE, not a recovery API.
 * Synthetic revision317 login is not proof of original client GUI.
 */
public final class G21175CoreSocketUnsupportedAccountVersionIntegrationTest {
    private static final String USER="opensrc";
    private static final int TEST_RUN_ENERGY=37;

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.175 "+why);
    }

    @FunctionalInterface private interface Condition{
        boolean test()throws Exception;
    }

    private static void await(Condition c,String label)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6L);
        while(System.nanoTime()<deadline){
            if(c.test())return;
            Thread.sleep(10L);
        }
        check(c.test(),"timeout "+label);
    }

    private static void mutateRunEnergy(
        World world,WorldPlayer player,int energy)throws Exception{
        CompletableFuture<Void> applied=world.submit(
            player,player.generation(),()->{
                player.movement().setRunEnergy(energy);
            });
        applied.get(4L,TimeUnit.SECONDS);
        check(player.movement().runEnergy()==energy,
            "World command mutation not applied");
    }

    private static int attemptUnsupportedLogin(int port,String account)
        throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            OutputStream out=socket.getOutputStream();
            InputStream in=socket.getInputStream();
            out.write(14);
            out.write(7);
            out.flush();

            byte[] initial=Binary.readExactly(in,9);
            check((initial[8]&255)==0,"prelogin failed unexpectedly");
            long serverSeed=Binary.i64(Binary.readExactly(in,8),0);
            check(serverSeed==LocalLoginTransport.SERVER_SEED,
                "server seed differs from existing real-login fixture");
            int[] seeds={
                0x01020304,0x11223344,
                (int)(serverSeed>>>32),(int)serverSeed
            };
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,account);
            check(frame.length<256,"synthetic login frame too long");
            out.write(16);
            out.write(frame.length);
            out.write(frame);
            out.flush();

            // Accepted logins respond with the 3-byte 2/205/0
            // triplet. A future-version account must never receive
            // positive 2. Normal LocalSession error teardown closes
            // the transport, so EOF is an expected negative result.
            return in.read();
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21175-account-version-");
        String previous=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        World original=null;
        World restarted=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            Path secondary=LocalAccountProfiles.accountFile("src");
            check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                "fixture unexpectedly has an account");
            original=World.isolatedForTest(50L);
            final World first=original;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(first)){
                try(Socket connected=server.login(USER)){
                    await(()->first.players().byName(USER)!=null,
                        "first authenticated session");
                    WorldPlayer player=first.players().byName(USER);
                    mutateRunEnergy(first,player,TEST_RUN_ENERGY);
                }
                await(()->first.players().size()==0,
                    "first session final unregister");
                server.waitCompleted();
            }
            check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
                "first real socket final-save missing");
            byte[] originalBytes=Files.readAllBytes(account);
            check(originalBytes.length>0,
                "first real socket account empty");
            FilePlayerRepository disk=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            check(disk.load(USER).isPresent()&&
                String.valueOf(TEST_RUN_ENERGY).equals(
                    disk.load(USER).get().value("movement.runEnergy")),
                "first final-save did not persist real movement energy");
            first.close();
            check(first.closed(),"original World did not close");

            // Test-only uncooperative edit simulates a newer client/
            // server writing a future schema which this server cannot
            // parse. Keep actual bytes as evidence after failed login.
            Properties future=new Properties();
            try(InputStream in=Files.newInputStream(account)){
                future.load(in);
            }
            future.setProperty("format.version","999");
            try(OutputStream out=Files.newOutputStream(account)){
                future.store(out,"G21.175 unsupported-version fixture");
            }
            byte[] rejectedFileBytes=Files.readAllBytes(account);
            check(!Arrays.equals(originalBytes,rejectedFileBytes),
                "test did not create unsupported snapshot version");
            restarted=World.isolatedForTest(50L);
            final World newWorld=restarted;
            int refusal;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(newWorld)){
                refusal=attemptUnsupportedLogin(
                    server.listener.getLocalPort(),USER);
                server.waitCompleted();
                check(newWorld.players().size()==0&&
                    newWorld.players().byName(USER)==null,
                    "bad account registered a default WorldPlayer");
            }
            check(refusal!=2,
                "unsupported future snapshot received login success");
            check(Arrays.equals(
                rejectedFileBytes,Files.readAllBytes(account)),
                "refused login overwrote or repaired future-version bytes");
            check(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "refused login created a secondary account");

            // Explicit fixture/operator action restores the known good
            // original bytes. NO automatic recovery is introduced.
            Files.write(account,originalBytes);
            check(Arrays.equals(originalBytes,Files.readAllBytes(account)),
                "operator fixture restore did not reproduce original");
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession server=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(newWorld)){
                try(Socket connected=server.login(USER)){
                    await(()->newWorld.players().byName(USER)!=null,
                        "post-restore authenticated login");
                    WorldPlayer loaded=newWorld.players().byName(USER);
                    check(loaded.movement().runEnergy()==TEST_RUN_ENERGY,
                        "valid account movement was not rehydrated");
                }
                await(()->newWorld.players().size()==0,
                    "post-restore final unregister");
                server.waitCompleted();
            }
            check(disk.load(USER).isPresent()&&
                String.valueOf(TEST_RUN_ENERGY).equals(
                    disk.load(USER).get().value("movement.runEnergy")),
                "post-restore final save lost gameplay movement energy");
            check(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "post-restore login created src profile");
            newWorld.close();
            check(newWorld.closed(),"restarted World not closed");
            boolean zeroLeases=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(zeroLeases,"session created JVM lock/watch leaks");
            System.out.println(
                "G21175_SOCKET_FUTURE_VERSION_DIAGNOSTICS"+
                " realSocketSuccessfulLogins=2"+
                " unsupportedVersionLoginRefused=1"+
                " unsupportedSnapshotVersion=999"+
                " invalidBytesUnchanged=true"+
                " absentInvalidWorldMembership=true"+
                " secondaryProfileCreated=false"+
                " operatorOnlyFixtureRestore=true"+
                " actualRunEnergyHydrated="+TEST_RUN_ENERGY+
                " worldInstances=2 zeroJvmLeases="+zeroLeases
            );
            System.out.println(
                "G21175_SOCKET_FUTURE_VERSION_PASS"+
                " schemaVersionFailClosed=true"+
                " failedLoginDidNotRewriteAccount=true"+
                " realGameplayFieldRoundtrip=true"+
                " automaticRecoveryAuthority=false"+
                " graphicalClientProven=false"+
                " fullRawWriterExclusion=false"+
                " independentProvenance=false atomicCrashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " sessionRelease=false ack=false"
            );
        }finally{
            if(restarted!=null&&!restarted.closed())
                restarted.close();
            if(original!=null&&!original.closed())
                original.close();
            if(previous==null)
                System.clearProperty("spk.local.accountFile");
            else
                System.setProperty("spk.local.accountFile",previous);
            try(Stream<Path> all=Files.walk(root)){
                for(Path path:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run();
    }
}
