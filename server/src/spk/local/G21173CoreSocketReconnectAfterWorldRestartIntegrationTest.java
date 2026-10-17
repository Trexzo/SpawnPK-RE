package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.173: exact synthetic v308/v309-format wire login + ISAAC 185,
 * followed by real LocalSession final-save and reconnection after the
 * *entire World* has been closed/replaced. No GUI client is claimed.
 *
 * Core server integration only. Gameplay and historical settlement
 * authority are not inferred from a successful localhost handshake.
 */
public final class G21173CoreSocketReconnectAfterWorldRestartIntegrationTest {
    private static final InetAddress LOOPBACK=loopback();

    private static InetAddress loopback(){
        try{
            return InetAddress.getByName("127.0.0.1");
        }catch(java.net.UnknownHostException impossible){
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private static void require(boolean condition,String message){
        if(!condition)
            throw new AssertionError("G21.173 "+message);
    }

    @FunctionalInterface
    private interface Condition{
        boolean get()throws Exception;
    }

    private static void await(Condition condition,String label,long timeoutMs)
        throws Exception{
        long until=System.nanoTime()+
            TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while(System.nanoTime()<until){
            if(condition.get())return;
            Thread.sleep(10L);
        }
        require(condition.get(),"timed out waiting for "+label);
    }

    private static void i32(OutputStream out,int number)throws IOException{
        out.write(number>>>24);
        out.write(number>>>16);
        out.write(number>>>8);
        out.write(number);
    }

    private static void nl(OutputStream out,String text)throws IOException{
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
        out.write(10);
    }

    static byte[] loginFrame(int[] seeds,String account)
        throws IOException{
        ByteArrayOutputStream inner=new ByteArrayOutputStream();
        inner.write(10);
        for(int seed:seeds)i32(inner,seed);
        i32(inner,748878668); // Current-format existing fixture value.
        i32(inner,307);       // Historical client-version fixture.
        nl(inner,account);
        nl(inner,"localpass");
        nl(inner,"LOCAL-DEVICE");
        nl(inner,"LOCAL-CLIENT");
        byte[] bytes=inner.toByteArray();
        require(bytes.length<256,"login inner block too long");
        ByteArrayOutputStream outer=new ByteArrayOutputStream();
        outer.write(255);
        outer.write(317>>>8);
        outer.write(317);
        outer.write(0);
        for(int i=0;i<9;i++)i32(outer,0);
        outer.write(bytes.length);
        outer.write(bytes);
        return outer.toByteArray();
    }

    private static Socket login(int port,String alias)throws Exception{
        Socket socket=new Socket(LOOPBACK,port);
        boolean accepted=false;
        try{
            socket.setSoTimeout(5000);
            InputStream input=socket.getInputStream();
            OutputStream output=socket.getOutputStream();
            output.write(14);
            output.write(7);
            output.flush();

            byte[] initial=Binary.readExactly(input,9);
            require((initial[8]&255)==0,"prelogin response failed");
            long serverSeed=Binary.i64(Binary.readExactly(input,8),0);
            require(serverSeed==LocalLoginTransport.SERVER_SEED,
                "server seed mismatch");

            int[] seeds={
                0x01020304,0x11223344,
                (int)(serverSeed>>>32),(int)serverSeed
            };
            byte[] frame=loginFrame(seeds,alias);
            require(frame.length<256,"login outer frame too long");
            output.write(16);
            output.write(frame.length);
            output.write(frame);
            output.flush();

            byte[] success=Binary.readExactly(input,3);
            require((success[0]&255)==2&&
                (success[1]&255)==205&&(success[2]&255)==0,
                "exact LocalLab login success triplet mismatch");
            IsaacCipher outgoing=new IsaacCipher(seeds.clone());
            output.write((185+outgoing.nextInt())&255);
            output.write(912>>>8);
            output.write(912);
            output.flush();

            accepted=true;
            return socket;
        }finally{
            if(!accepted)socket.close();
        }
    }

    static final class OneSession implements AutoCloseable{
        final ServerSocket listener;
        final ExecutorService executor;
        final Future<?> task;

        OneSession(World world)throws IOException{
            listener=new ServerSocket(0,1,LOOPBACK);
            executor=Executors.newSingleThreadExecutor();
            task=executor.submit(()->{
                try{
                    new LocalSession(
                        listener.accept(),true,true,world
                    ).run();
                }catch(IOException failure){
                    throw new java.io.UncheckedIOException(failure);
                }
            });
        }

        Socket login(String alias)throws Exception{
            return G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .login(listener.getLocalPort(),alias);
        }

        void waitCompleted()throws Exception{
            task.get(10L,TimeUnit.SECONDS);
        }

        @Override public void close()throws Exception{
            listener.close();
            executor.shutdownNow();
            if(!executor.awaitTermination(5L,TimeUnit.SECONDS))
                throw new AssertionError(
                    "G21.173 LocalSession executor remained live");
        }
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21173-core-restart-");
        String previous=System.getProperty("spk.local.accountFile");
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());

        World original=null;
        World restarted=null;
        try{
            final String account="opensrc";
            final Path accountFile=LocalAccountProfiles.accountFile(account);
            final Path secondary=LocalAccountProfiles.accountFile("src");
            require(!Files.exists(accountFile,LinkOption.NOFOLLOW_LINKS),
                "first-run account unexpectedly preexists");

            original=World.isolatedForTest(50L);
            final World initialWorld=original;
            WorldPlayer originalPlayer;
            long firstGeneration;
            long firstWorldTicks;
            try(OneSession server=new OneSession(initialWorld)){
                try(Socket client=server.login(account)){
                    await(()->initialWorld.players().byName(account)!=null,
                        "first World login membership",5000);
                    require(original.players().size()==1,
                        "first World admitted unexpected player");
                    originalPlayer=original.players().byName(account);
                    firstGeneration=originalPlayer.generation();
                    await(()->initialWorld.clock().tick()>=2L,
                        "first World tick",3000);
                    firstWorldTicks=original.clock().tick();
                }
                await(()->initialWorld.players().size()==0,
                    "first World logout",6000);
                server.waitCompleted();
            }

            require(Files.isRegularFile(accountFile,
                    LinkOption.NOFOLLOW_LINKS),
                "first World logout did not persist account file");
            require(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "first World wrongly created secondary profile");
            FilePlayerRepository offline=new FilePlayerRepository(
                name->LocalAccountProfiles.accountFile(name));
            Optional<PlayerSnapshot> firstSaved=offline.load(account);
            require(firstSaved.isPresent()&&
                firstSaved.get().values().size()>0,
                "first persisted snapshot cannot be read");
            long firstFileBytes=Files.size(accountFile);
            require(firstFileBytes>0L,"first account file empty");
            original.close();
            require(original.closed(),
                "first World did not reach terminal close");

            // Restart means a distinct World, its own WorldPulse and
            // fresh session, not a second session in the same JVM World.
            restarted=World.isolatedForTest(50L);
            final World restartedWorld=restarted;
            try(OneSession server=new OneSession(restarted)){
                try(Socket client=server.login(account)){
                    await(()->restartedWorld.players().byName(account)!=null,
                        "second World reconnect membership",5000);
                    require(restartedWorld.players().size()==1,
                        "reconnect selected an extra profile");
                    WorldPlayer reloaded=
                        restartedWorld.players().byName(account);
                    require(reloaded!=originalPlayer,
                        "new World reused old WorldPlayer object");
                    require(reloaded.generation()>0L&&
                        firstGeneration>0L,
                        "World-owned generation not registered");
                    await(()->restartedWorld.clock().tick()>=2L,
                        "second World clock tick",3000);
                }
                await(()->restartedWorld.players().size()==0,
                    "second World logout",6000);
                server.waitCompleted();
            }
            require(Files.isRegularFile(accountFile,
                LinkOption.NOFOLLOW_LINKS),
                "second World disconnected without saved account");
            require(offline.load(account).isPresent(),
                "persisted account not readable after reconnect");
            require(!Files.exists(secondary,LinkOption.NOFOLLOW_LINKS),
                "reconnect unexpectedly created src profile");
            require(firstWorldTicks>=2L&&firstFileBytes>0L,
                "first World continuity evidence missing");
            restarted.close();
            require(restarted.closed(),
                "restarted World did not close cleanly");
            require(MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1
                    .activeLocalWatchCount()==0,
                "reconnect leaked publication lock or local watch");

            System.out.println(
                "G21173_LOOPBACK_RECONNECT_DIAGNOSTICS"+
                " realSocketLogins=2 realLogouts=2"+
                " worldInstances=2 worldInstancesClosed=2"+
                " sameCanonicalProfile=true secondaryProfileCreated=false"+
                " firstSnapshotPersisted=true accountReadableAfterRestart=true"+
                " originalWorldIdentityReused=false"+
                " independentWorldTicks=true"+
                " zeroJvmLeases=true"
            );
            System.out.println(
                "G21173_LOOPBACK_RECONNECT_PASS"+
                " syntheticRevision317Isaac185=true"+
                " exactGraphicalClientTested=false"+
                " gameplayCompletenessProven=false"+
                " rawWriterExclusion=false"+
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
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        run();
    }
}
