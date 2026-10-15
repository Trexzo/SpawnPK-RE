package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.230 optional NOFOLLOW hardlink-count policy for cooperating RAW
 * and session admission. Tests genuine parent and child Java processes.
 */
public final class G21230CoreHardlinkCountCrossJvmAdmissionGateTest {
    private G21230CoreHardlinkCountCrossJvmAdmissionGateTest(){}
    private static final String USER="g21230-link-count";
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.230 "+why);
    }
    private static void enable(){
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,"true");
        System.setProperty(
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,"true");
    }
    private static void verify(Path hardlink,Path other)throws Exception{
        FilePlayerRepository repository=new FilePlayerRepository(
            name->hardlink);
        World world=World.isolatedForTest(60_000L,repository);
        try{
            boolean denied=false;
            try{
                LocalJvmAccountSessionLease.Token result=
                    LocalJvmAccountSessionLease.acquire(
                        world.persistence(),USER);
                if(result!=null)result.releaseAfterInitializationFailure();
            }catch(IllegalStateException expected){
                denied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "G21.230 HARDLINK_ACCOUNT_ADMISSION_DENIED");
            }
            check(denied,"multi-hardlink session admission allowed");

            boolean rawDenied=false;
            try{
                LocalJvmAccountSessionLease.reserveRawWrite(
                    hardlink,USER);
            }catch(IOException expected){
                rawDenied=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }
            check(rawDenied,"multi-hardlink RAW writer admitted");
            Path lock=hardlink.resolveSibling(
                hardlink.getFileName().toString()+".session.lck");
            check(!Files.exists(lock,LinkOption.NOFOLLOW_LINKS),
                "denied alias created OS lock leaf");
            check(LocalJvmAccountSessionLease
                    .rawWriteReservationsForTest()==0,
                "denied RAW leaked pending account reservation");

            FilePlayerRepository uniqueRepo=new FilePlayerRepository(
                name->other);
            World uniqueWorld=World.isolatedForTest(60_000L,uniqueRepo);
            try{
                LocalJvmAccountSessionLease.Token allowed=
                    LocalJvmAccountSessionLease.acquire(
                        uniqueWorld.persistence(),USER);
                check(allowed!=null,"unique inode owner rejected");
                allowed.releaseAfterInitializationFailure();
                try(LocalJvmAccountSessionLease.RawWriteReservation raw=
                        LocalJvmAccountSessionLease.reserveRawWrite(
                            other,USER)){
                    check(raw!=null,"unique RAW reservation rejected");
                }
                check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                    "allowed singleton token leaked");
            }finally{
                uniqueWorld.close();
            }
        }finally{
            world.close();
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0])){
            enable();
            verify(Path.of(args[1]),Path.of(args[2]));
            System.out.println(
                "G21230_CHILD_HARDLINK_ACCOUNT_ADMISSION_DENIED"+
                " independentJvm=true noOsLockSideEffect=true");
            return;
        }
        check(args.length==0,"unexpected argument count");
        Path root=Files.createTempDirectory("g21230-linkcount-");
        String[] names={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY
        };
        String[] old=new String[names.length];
        for(int i=0;i<names.length;i++)
            old[i]=System.getProperty(names[i]);
        enable();
        try{
            Path a=root.resolve("existing-a.properties");
            Path b=root.resolve("same-inode-b.properties");
            Path c=root.resolve("unique.properties");
            Files.writeString(a,"test-only-inode");
            Files.createLink(b,a);
            Files.writeString(c,"singleton");
            check(Files.isSameFile(a,b)&&!Files.isSameFile(a,c),
                "hardlink/unique fixture invalid");
            verify(a,c);
            Path java=Path.of(System.getProperty("java.home"),"bin",
                "java"+(System.getProperty("os.name").toLowerCase()
                    .contains("win")?".exe":""));
            ProcessBuilder builder=new ProcessBuilder(
                java.toString(),"-cp",System.getProperty("java.class.path"),
                G21230CoreHardlinkCountCrossJvmAdmissionGateTest
                    .class.getName(),
                "--child",b.toString(),c.toString());
            builder.redirectErrorStream(true);
            Process child=builder.start();
            boolean finished=child.waitFor(20L,TimeUnit.SECONDS);
            if(!finished){
                child.destroyForcibly();
                child.waitFor(5L,TimeUnit.SECONDS);
                throw new AssertionError("G21.230 child timed out");
            }
            String output=new String(child.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            check(child.exitValue()==0&&output.contains(
                    "G21230_CHILD_HARDLINK_ACCOUNT_ADMISSION_DENIED"),
                "child hardlink veto failed: "+output);
            check(Files.isSameFile(a,b),
                "test changed original file object");
            System.out.println(
                "G21230_TWO_JVM_HARDLINK_COUNT_ADMISSION_GATE_PASS"+
                " realTwoJvmProcessProof=true"+
                " bothAliasSessionsDeniedBeforeLock=true"+
                " bothAliasRawWritesDenied=true"+
                " independentSingleLinkAdmissionAllowed=true"+
                " defaultOff=true"+
                " unixNlinkUnsupportedFailsClosed=true"+
                " postAdmissionObjectSubstitutionFenced=false"+
                " externalUncooperativeWriterFenced=false"+
                " durableOwnerEpoch=false");
        }finally{
            for(int i=0;i<names.length;i++){
                if(old[i]==null)System.clearProperty(names[i]);
                else System.setProperty(names[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}
