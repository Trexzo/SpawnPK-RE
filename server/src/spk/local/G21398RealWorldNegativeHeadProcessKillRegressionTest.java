package spk.local;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * R70: REAL authenticated TCP World owner and G21.202 immutable
 * capture, hard killed at TWO R50 negative HEAD publication cutpoints.
 *
 * R69 covers immutable object forced BEFORE HEAD. This covers HEAD
 * file force BEFORE parent directory force and completed HEAD directory
 * force BEFORE the publisher's exact readback/return. A process kill
 * is not machine power loss and neither permits auto-recovery/ACK.
 */
public final class G21398RealWorldNegativeHeadProcessKillRegressionTest {
    private G21398RealWorldNegativeHeadProcessKillRegressionTest(){}
    private static final String USER="r70-killed-real-head";
    private static final String DENY=
        "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY";
    private static final LocalNegativeAccountHeadPrototype.Stage[]
        CUTS={
            LocalNegativeAccountHeadPrototype.Stage
                .HEAD_FILE_FORCED_BEFORE_DIRECTORY,
            LocalNegativeAccountHeadPrototype.Stage
                .HEAD_DIRECTORY_FORCED_BEFORE_READBACK
        };
    private static void check(boolean ok,String msg){
        if(!ok)throw new AssertionError("R70_HEAD_KILL "+msg);
    }
    private static boolean has(Throwable e,String term){
        for(Throwable t=e;t!=null;t=t.getCause())
            if(String.valueOf(t.getMessage()).contains(term))return true;
        return false;
    }
    private static String javaExe(){
        return System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
    }
    private static void configure(Path root,boolean on){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        for(String flag:new String[]{
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease
                .CAPTURED_NEGATIVE_INTENT_LAB_PROPERTY
        }){
            if(on)System.setProperty(flag,"true");
            else System.clearProperty(flag);
        }
    }
    @FunctionalInterface interface Condition{
        boolean ok()throws Exception;
    }
    private static void until(Condition condition,String why)
        throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
        while(System.nanoTime()<deadline){
            if(condition.ok())return;
            Thread.sleep(10);
        }
        check(condition.ok(),"timeout "+why);
    }
    private static void holder(
        Path root,Path ready,LocalNegativeAccountHeadPrototype.Stage cut
    )throws Exception{
        configure(root,true);
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World world=World.isolatedForTest(50L,repository);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(world)){
            try(Socket client=server.login(USER)){
                until(()->world.players().byName(USER)!=null,
                    "real World owner registration");
                WorldPlayer owner=world.players().byName(USER);
                long generation=owner.generation();
                check(generation>0&&
                    LocalJvmAccountSessionLease.activeCountForTest()==1,
                    "real registered owner missing session lease");
                // Guarantee new captured state differs from OLD disk
                // before deliberately stopping at negative HEAD force.
                world.submit(owner,generation,()->
                    owner.snapshotExtensions().replaceNamespace(
                        "r70_head_cut",
                        Collections.singletonMap("phase",cut.name()))
                ).get(5,TimeUnit.SECONDS);
                AtomicReference<WorldPlayerPersistence.CapturedSave>
                    captured=new AtomicReference<>();
                world.submit(owner,generation,()->
                    captured.set(world.persistence().captureDeferredSave(
                        USER,owner,generation,0,"[r70] ",
                        "R70_REAL_FINAL_CANDIDATE"))
                ).get(5,TimeUnit.SECONDS);
                WorldPlayerPersistence.CapturedSave exact=captured.get();
                check(exact!=null&&exact.ownerWitness!=null&&
                    cut.name().equals(exact.ticket.snapshot.values().get(
                        "extension.r70_head_cut.phase")),
                    "immutable capture did not contain World mutation");
                LocalJvmAccountSessionLease
                    .publishCapturedFinalNegativeIntentUnderOriginalLockForLab(
                        LocalAccountProfiles.accountFile(USER),owner,
                        generation,exact.ticket.snapshot,
                        exact.ownerWitness,stage->{
                            if(stage!=cut)return;
                            Files.write(ready,new byte[]{1},
                                StandardOpenOption.CREATE_NEW);
                            System.out.println("R70_REAL_HEAD_CUTPOINT "+cut);
                            System.out.flush();
                            try{Thread.sleep(120000L);}
                            catch(InterruptedException interrupted){
                                Thread.currentThread().interrupt();
                                throw new IOException(
                                    "R70_UNEXPECTED_INTERRUPT",interrupted);
                            }
                            throw new AssertionError("hard kill missing");
                        });
            }
        }finally{
            if(!world.closed())world.close();
        }
        throw new AssertionError("R70 holder was not killed");
    }
    private static String hash(byte[] content)throws Exception{
        StringBuilder h=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256")
                .digest(content)){
            String pair=Integer.toHexString(b&255);
            if(pair.length()==1)h.append('0');
            h.append(pair);
        }
        return h.toString();
    }
    private static int freshTcpResponse(int port)throws Exception{
        try(Socket socket=new Socket(
                InetAddress.getByName("127.0.0.1"),port)){
            socket.setSoTimeout(5000);
            InputStream in=socket.getInputStream();
            OutputStream out=socket.getOutputStream();
            out.write(14);out.write(7);out.flush();
            byte[] pre=Binary.readExactly(in,9);
            check((pre[8]&255)==0,"prelogin unexpected");
            long seed=Binary.i64(Binary.readExactly(in,8),0);
            check(seed==LocalLoginTransport.SERVER_SEED,
                "login seed mismatch");
            int[] seeds={0x01020304,0x11223344,
                (int)(seed>>>32),(int)seed};
            byte[] frame=
                G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .loginFrame(seeds,USER);
            out.write(16);out.write(frame.length);
            out.write(frame);out.flush();
            return in.read();
        }
    }
    private static void freshProbe(
        Path root,LocalNegativeAccountHeadPrototype.Stage cut
    )throws Exception{
        configure(root,false);
        Path account=LocalAccountProfiles.accountFile(USER);
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "OLD legacy account missing");
        check(Files.isRegularFile(
                LocalNegativeAccountHeadPrototype.headPath(account),
                LinkOption.NOFOLLOW_LINKS),
            "forced negative HEAD missing after process kill");
        LocalNegativeAccountHeadPrototype.NegativeObservation observed=
            LocalNegativeAccountHeadPrototype.inspectNegative(account);
        byte[] exact=Files.readAllBytes(observed.immutableObject);
        check(USER.equals(observed.username)&&
            observed.snapshotLength==exact.length&&
            observed.snapshotSha256.equals(hash(exact))&&
            !observed.admissionAuthorized&&!observed.releaseAuthorized,
            "forced HEAD and immutable capture object mismatch");
        check(new String(exact,StandardCharsets.UTF_8)
                .contains(cut.name()),
            "forced immutable capture lost World mutation");
        boolean denied=false;
        try{new FilePlayerRepository(LocalAccountProfiles::accountFile)
                .loadForWorldSession(USER);}
        catch(IOException e){denied=has(e,DENY);}
        check(denied,"fresh JVM hydrated stale World account");
        WorldPlayer direct=new WorldPlayer();
        denied=false;
        try{
            LocalAccountProfiles.load(USER,direct.bank(),
                direct.equipment(),direct.movement(),
                direct.petState(),direct.playerState());
        }catch(IOException e){denied=has(e,DENY);}
        check(denied,"direct legacy load bypassed retained HEAD");
        // A second actual socket login, not just a repository probe,
        // must not install another World owner from old account bytes.
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        World replacement=World.isolatedForTest(50L,repository);
        try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                .OneSession server=
                new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession(replacement)){
            int code=freshTcpResponse(server.listener.getLocalPort());
            check(code!=2&&replacement.players().size()==0,
                "real TCP stale reconnect accepted despite negative HEAD");
            server.waitCompleted();
        }finally{
            if(!replacement.closed())replacement.close();
        }
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        try(LocalCrossJvmAccountSessionLock.Handle available=
                LocalCrossJvmAccountSessionLock.acquire(account)){
            available.requireOriginalLockNameStillPresent();
        }
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "killed/restarted process has live World lease");
        System.out.println("R70_FRESH_JVM_NEGATIVE_HEAD_RECONNECT_VETO"+
            " stage="+cut);
    }
    private static Process spawn(String mode,Path root,Path ready,
                                  Path log,
                                  LocalNegativeAccountHeadPrototype.Stage cut)
        throws IOException{
        ProcessBuilder builder=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21398RealWorldNegativeHeadProcessKillRegressionTest.class
                .getName(),"--child",mode,root.toString(),
                ready.toString(),cut.name());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        return builder.start();
    }
    private static String finished(Process process,Path log)
        throws Exception{
        if(!process.waitFor(25,TimeUnit.SECONDS)){
            process.destroyForcibly();process.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("R70 child timeout");
        }
        String out=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(process.exitValue()==0,"child exited unexpectedly: "+out);
        return out;
    }
    private static void one(
        Path root,LocalNegativeAccountHeadPrototype.Stage cut
    )throws Exception{
        Files.createDirectories(root);
        configure(root,false);
        FilePlayerRepository repository=new FilePlayerRepository(
            LocalAccountProfiles::accountFile);
        repository.save(PlayerSnapshotCodec.capture(
            USER,new WorldPlayer()));
        Path account=LocalAccountProfiles.accountFile(USER);
        byte[] before=Files.readAllBytes(account);
        Path ready=root.resolve("ready.flag");
        Path holderLog=root.resolve("holder.log");
        Process holder=spawn("holder",root,ready,holderLog,cut);
        try{
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(25);
            while(holder.isAlive()&&System.nanoTime()<deadline&&
                  !Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS))
                Thread.sleep(10);
            check(holder.isAlive()&&Files.isRegularFile(ready,
                    LinkOption.NOFOLLOW_LINKS),
                "holder did not force HEAD at "+cut+" log="+
                (Files.exists(holderLog)?new String(
                    Files.readAllBytes(holderLog),
                    StandardCharsets.UTF_8):"missing"));
            holder.destroyForcibly();
            check(holder.waitFor(10,TimeUnit.SECONDS)&&
                holder.exitValue()!=0,"holder survived hard kill");
            check(Arrays.equals(before,Files.readAllBytes(account)),
                "HEAD publication modified old account bytes");
            Path probeLog=root.resolve("probe.log");
            check(finished(spawn("probe",root,ready,probeLog,cut),
                    probeLog).contains(
                        "R70_FRESH_JVM_NEGATIVE_HEAD_RECONNECT_VETO"),
                "second JVM did not reject stale TCP reconnect");
            check(Arrays.equals(before,Files.readAllBytes(account)),
                "probe changed old account bytes");
            System.out.println("R70_REAL_WORLD_HEAD_KILL_PASS"+
                " cut="+cut+" oldBytesPreserved=true"+
                " freshJvmTcpAndDirectAdmissionDenied=true");
        }finally{
            if(holder.isAlive()){
                holder.destroyForcibly();
                holder.waitFor(10,TimeUnit.SECONDS);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==5&&"--child".equals(args[0])){
            LocalNegativeAccountHeadPrototype.Stage cut=
                LocalNegativeAccountHeadPrototype.Stage.valueOf(args[4]);
            if("holder".equals(args[1]))
                holder(Paths.get(args[2]),Paths.get(args[3]),cut);
            else if("probe".equals(args[1]))
                freshProbe(Paths.get(args[2]),cut);
            else throw new IllegalArgumentException("mode");
            return;
        }
        check(args.length==0,"args");
        Path root=Files.createTempDirectory("r70-real-head-cuts-");
        final String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalJvmAccountSessionLease
                .CAPTURED_NEGATIVE_INTENT_LAB_PROPERTY
        };
        String[] before=new String[props.length];
        for(int i=0;i<props.length;i++)
            before[i]=System.getProperty(props[i]);
        try{
            for(LocalNegativeAccountHeadPrototype.Stage cut:CUTS)
                one(root.resolve(cut.name()),cut);
            System.out.println("R70_REAL_WORLD_HEAD_PUBLICATION_KILL_MATRIX_PASS"+
                " actualWorldG21202Capture=true"+
                " killedJvmHeadFileForced=true"+
                " killedJvmHeadDirectoryForced=true"+
                " immutableObjectAndHeadConsistent=true"+
                " defaultOffFreshJvmTcpReconnectDenied=true"+
                " defaultOffDirectLoadDenied=true"+
                " noAutomaticReplayOrCleanup=true"+
                " noPositiveFinalAckOrRelease=true"+
                " machinePowerLossNotProven=true");
        }finally{
            for(int i=0;i<props.length;i++){
                if(before[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],before[i]);
            }
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
