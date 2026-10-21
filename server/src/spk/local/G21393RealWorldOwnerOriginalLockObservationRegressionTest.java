package spk.local;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * R65: REAL authenticated TCP LocalSession and World registration,
 * existing G21.181/200 Token and G21.185 OS FileLock. Observation
 * remains deliberately NONADMITTING and cannot be replayed as
 * durable owner/epoch/final settlement.
 */
public final class G21393RealWorldOwnerOriginalLockObservationRegressionTest {
    private G21393RealWorldOwnerOriginalLockObservationRegressionTest(){}
    private static final String USER="r65-real-world-owner";
    private static void check(boolean condition,String why){
        if(!condition)throw new AssertionError("R65_WORLD_LOCK "+why);
    }
    @FunctionalInterface interface Condition{
        boolean evaluate()throws Exception;
    }
    private static void until(Condition test,String why)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(System.nanoTime()<deadline){
            if(test.evaluate())return;
            Thread.sleep(10);
        }
        check(test.evaluate(),"timeout "+why);
    }
    private static boolean contains(Throwable error,String marker){
        for(Throwable e=error;e!=null;e=e.getCause())
            if(String.valueOf(e.getMessage()).contains(marker))return true;
        return false;
    }
    private static boolean refused(Path path,WorldPlayer owner,long gen,
                                   String code)throws IOException{
        try{
            LocalJvmAccountSessionLease
                .observeRealWorldOwnerUnderOsLockForLab(path,owner,gen);
            return false;
        }catch(IOException expected){
            return contains(expected,code);
        }
    }
    private static String javaExe(){
        return System.getProperty("java.home")+File.separator+"bin"+
            File.separator+
            (System.getProperty("os.name","").toLowerCase().contains("win")
                ?"java.exe":"java");
    }
    private static void secondProcess(Path account,Path log)
        throws Exception{
        ProcessBuilder builder=new ProcessBuilder(
            javaExe(),"-cp",System.getProperty("java.class.path"),
            G21393RealWorldOwnerOriginalLockObservationRegressionTest
                .class.getName(),"--child",account.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(log.toFile());
        Process p=builder.start();
        if(!p.waitFor(20,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(10,TimeUnit.SECONDS);
            throw new AssertionError("child timeout");
        }
        String output=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0&&output.contains("R65_FRESH_JVM_BUSY"),
            "real World did not exclude other JVM: "+output);
    }
    private static void contender(Path selected)throws Exception{
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        boolean blocked=false;
        try(LocalCrossJvmAccountSessionLock.Handle impossible=
                LocalCrossJvmAccountSessionLock.acquire(selected)){
            // If this happens, the original real World lost its OS
            // exclusion, so the regression must FAIL immediately.
        }catch(IOException expected){
            blocked=contains(expected,"G21.185 ACCOUNT_SESSION_LOCK_BUSY");
        }
        check(blocked,"fresh JVM acquired real World lock");
        System.out.println("R65_FRESH_JVM_BUSY");
    }
    private static void run()throws Exception{
        Path root=Files.createTempDirectory("r65-real-world-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] previous=new String[props.length];
        for(int i=0;i<props.length;i++)
            previous[i]=System.getProperty(props[i]);
        System.setProperty(props[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<props.length;i++)
            System.setProperty(props[i],"true");
        World world=null;
        try{
            Path account=LocalAccountProfiles.accountFile(USER);
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(50L,repository);
            final World liveWorld=world;
            WorldPlayer capturedOwner;
            long admittedGeneration;
            try(G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                    .OneSession listener=
                    new G21173CoreSocketReconnectAfterWorldRestartIntegrationTest
                        .OneSession(world)){
                try(Socket client=listener.login(USER)){
                    until(()->liveWorld.players().byName(USER)!=null,
                        "registered TCP World owner");
                    capturedOwner=world.players().byName(USER);
                    admittedGeneration=capturedOwner.generation();
                    check(admittedGeneration>0&&
                        LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "real World JVM lease absent");
                    LocalJvmAccountSessionLease.WorldOwnerLabObservation obs=
                        LocalJvmAccountSessionLease
                            .observeRealWorldOwnerUnderOsLockForLab(
                                account,capturedOwner,admittedGeneration);
                    check(obs.accountFile.equals(account)&&
                        USER.equals(obs.username)&&
                        obs.observedWorldGeneration==admittedGeneration&&
                        !obs.admissionAuthorized&&!obs.releaseAuthorized,
                        "observation granted a durable account owner");
                    WorldPlayer forged=new WorldPlayer();
                    forged.markRegistered(USER);
                    check(forged.generation()==admittedGeneration,
                        "forged negative generation fixture invalid");
                    check(refused(account,forged,admittedGeneration,
                        "R65_REAL_WORLD_IDENTITY_OR_GENERATION_REJECTED"),
                        "forged WorldPlayer accepted");
                    check(refused(account,capturedOwner,
                        admittedGeneration+1L,
                        "R65_REAL_WORLD_IDENTITY_OR_GENERATION_REJECTED"),
                        "stale generation accepted");
                    Path foreign=LocalAccountProfiles.accountFile(
                        "r65-foreign");
                    check(refused(foreign,capturedOwner,admittedGeneration,
                        "R65_NO_CURRENT_REAL_WORLD_SESSION_TOKEN"),
                        "foreign account reused real lease");
                    secondProcess(account,root.resolve("second.log"));
                    check(LocalJvmAccountSessionLease.activeCountForTest()==1,
                        "read-only observation released real World lease");
                    check(!Files.exists(
                        LocalNegativeAccountHeadPrototype.headPath(account),
                        LinkOption.NOFOLLOW_LINKS),
                        "observation created negative R50 HEAD");
                    LocalReusableLabEvidenceNegativeGate
                        .requireNoLegacyCollision(account);
                    // A forged LAB evidence leaf while the actual
                    // World is alive must veto the observation; the
                    // genuine JVM identity alone is insufficient.
                    Path synthetic=account.resolveSibling(
                        account.getFileName()+".g21386-intent-r65");
                    Files.write(synthetic,new byte[]{1});
                    check(refused(account,capturedOwner,
                            admittedGeneration,
                            "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION"),
                        "real World observation ignored orphan evidence");
                    Files.delete(synthetic); // isolated fixture only
                }
                until(()->liveWorld.players().size()==0,
                    "TCP World unregister");
                listener.waitCompleted();
            }
            until(()->LocalJvmAccountSessionLease.activeCountForTest()==0,
                "confirmed final save released World lease");
            check(refused(account,capturedOwner,admittedGeneration,
                "R65_NO_CURRENT_REAL_WORLD_SESSION_TOKEN"),
                "released World owner still observed");
            try(LocalCrossJvmAccountSessionLock.Handle next=
                    LocalCrossJvmAccountSessionLock.acquire(account)){
                next.requireOriginalLockNameStillPresent();
            }
            check(world.persistence().failedCount()==0,
                "healthy real World final save failed");
            check(!Files.exists(account.resolveSibling(
                account.getFileName()+".g21385-ledger-head"),
                LinkOption.NOFOLLOW_LINKS),
                "R65 unexpectedly created LAB HEAD");
            world.close();
            check(world.closed(),"World did not close");
            System.out.println("R65_REAL_WORLD_ORIGINAL_OS_LOCK_OBSERVATION_PASS"+
                " authenticatedTcpLogin=true"+
                " genuineRegisteredWorldIdentity=true"+
                " exactOriginalSessionFileLock=true"+
                " crossJvmCompetingLockDenied=true"+
                " forgedMatchingGenerationDenied=true"+
                " wrongGenerationAndAccountDenied=true"+
                " originalLockReleasedOnlyAfterFinal=true"+
                " noLedgerWriteOrGrant=true"+
                " noPositiveOwnerOrFinalAck=true"+
                " defaultOffBehaviorUntouched=true");
        }finally{
            if(world!=null&&!world.closed())world.close();
            for(int i=0;i<props.length;i++){
                if(previous[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],previous[i]);
            }
            try(Stream<Path> all=Files.walk(root)){
                for(Path file:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0])){
            contender(Paths.get(args[1]));
            return;
        }
        check(args.length==0,"unexpected args");
        run();
    }
}
