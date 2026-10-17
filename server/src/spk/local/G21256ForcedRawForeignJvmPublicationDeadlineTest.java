package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** G21.256 real OS publication lock deadline and unrelated-account progress. */
public final class G21256ForcedRawForeignJvmPublicationDeadlineTest {
    private static final String A="g21256-held";
    private static final String B="g21256-unrelated";
    private static final String READY="G21256_FOREIGN_PUBLICATION_LOCK_HELD";
    private G21256ForcedRawForeignJvmPublicationDeadlineTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.256 "+reason);
    }
    private static void flags(Path root){
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        System.setProperty(LocalJvmAccountSessionLease.PROPERTY,"true");
        System.setProperty(LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.PROPERTY,"true");
        System.setProperty(LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,"true");
        System.setProperty(LocalOptInRawTemp.PROPERTY,"true");
        System.setProperty(LocalOptInRawTemp.FORCE_PROPERTY,"true");
        System.setProperty(
            LocalOptInRawTemp.PUBLICATION_WAIT_PROPERTY,"150");
    }
    private static PlayerSnapshot snapshot(String name,String phase){
        WorldPlayer p=new WorldPlayer();
        p.snapshotExtensions().replaceNamespace(
            "core_g21256",java.util.Collections.singletonMap(
                "phase",phase));
        return PlayerSnapshotCodec.capture(name,p);
    }
    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> files=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(files.noneMatch(f->f.getFileName().toString()
                .startsWith(prefix)),"orphaned RAW temp "+account);
        }
    }
    private static void child(Path root,Path ready,Path release)
        throws Exception{
        flags(root);
        Path account=LocalAccountProfiles.accountFile(A);
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            account,()->{
                Files.writeString(ready,READY,StandardCharsets.US_ASCII);
                long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
                while(!Files.exists(release,LinkOption.NOFOLLOW_LINKS)&&
                      System.nanoTime()<until){
                    try{Thread.sleep(20L);}
                    catch(InterruptedException e){
                        Thread.currentThread().interrupt();
                        throw new IOException("G21.256 child interrupted",e);
                    }
                }
                if(!Files.exists(release,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("G21.256 child release timeout");
                return null;
            });
        System.out.println(
            "G21256_FOREIGN_JVM_PUBLICATION_LOCK_RELEASE_PASS");
    }
    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g21256-foreign-deadline-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY,
            LocalOptInRawTemp.PUBLICATION_WAIT_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        flags(root);
        Process victim=null;
        Path release=root.resolve("release-foreign");
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(snapshot(A,"INITIAL"));
            repo.save(snapshot(B,"OTHER_INITIAL"));
            Path a=LocalAccountProfiles.accountFile(A);
            Path b=LocalAccountProfiles.accountFile(B);
            byte[] beforeA=Files.readAllBytes(a);
            Path ready=root.resolve("foreign-held");
            Path java=Path.of(System.getProperty("java.home"),
                "bin","java"+(System.getProperty("os.name")
                    .toLowerCase().contains("win")?".exe":""));
            ProcessBuilder pb=new ProcessBuilder(
                java.toString(),"-cp",System.getProperty(
                    "java.class.path"),
                G21256ForcedRawForeignJvmPublicationDeadlineTest
                    .class.getName(),
                "--child",root.toString(),
                ready.toString(),release.toString());
            pb.redirectErrorStream(true);
            victim=pb.start();
            // G21.262 CI fixture: an existing signal filename can
            // precede completion of Files.writeString; poll the exact
            // complete payload, not mere file existence. Under hosted
            // build contention, JVM startup may also exceed 15 seconds.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(35L);
            boolean signaled=false;
            while(victim.isAlive()&&System.nanoTime()<deadline){
                if(Files.exists(ready,LinkOption.NOFOLLOW_LINKS)&&
                   READY.equals(Files.readString(ready,
                       StandardCharsets.US_ASCII))){
                    signaled=true;
                    break;
                }
                Thread.sleep(20L);
            }
            if(!signaled){
                String status=victim.isAlive()
                    ?"FOREIGN_JVM_ALIVE_NO_COMPLETE_READY"
                    :new String(victim.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                throw new AssertionError(
                    "G21.262 foreign JVM publication lock handshake absent "+
                    status);
            }
            check(victim.isAlive(),"foreign JVM exited too early");
            long started=System.nanoTime();
            boolean bounded=false;
            try{repo.save(snapshot(A,"MUST_NOT_PUBLISH"));}
            catch(IOException expected){
                bounded=String.valueOf(expected.getMessage()).contains(
                    "RECOVERY_PUBLICATION_BUSY_NO_GRANT");
            }
            long elapsed=TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime()-started);
            check(bounded&&elapsed<4000L,
                "foreign G21.39 lock not bounded: "+elapsed+"ms");
            check(Arrays.equals(beforeA,Files.readAllBytes(a)),
                "held account overwritten by forced RAW");
            noTemp(a);
            repo.save(snapshot(B,"OTHER_WRITTEN_WHILE_HELD"));
            check(!Arrays.equals(
                    Files.readAllBytes(b),
                    Files.readAllBytes(a)),
                "unrelated account did not progress");
            check(Arrays.equals(beforeA,Files.readAllBytes(a)),
                "other account save modified held account");
            Files.createFile(release);
            check(victim.waitFor(30L,TimeUnit.SECONDS),
                "foreign lock owner did not terminate after release");
            String output=new String(victim.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
            check(victim.exitValue()==0&&output.contains(
                "G21256_FOREIGN_JVM_PUBLICATION_LOCK_RELEASE_PASS"),
                "foreign JVM failed "+output);
            repo.save(snapshot(A,"AFTER_FOREIGN_RELEASE"));
            check(!Arrays.equals(beforeA,Files.readAllBytes(a)),
                "account not writable after foreign release");
            noTemp(a);noTemp(b);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation leak");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "G21.39 lease leak");
            System.out.println(
                "G21256_FORCED_RAW_FOREIGN_JVM_PUBLICATION_DEADLINE_PASS"+
                " independentJvmHeldRealOsMarkerLock=true"+
                " boundedRawDenialNoOverwrite=true"+
                " unrelatedAccountContinues=true"+
                " recoversAfterForeignLockRelease=true"+
                " rawAndPublisherLeasesClean=true"+
                " durableOwnerEpoch=false");
        }finally{
            if(!Files.exists(release,LinkOption.NOFOLLOW_LINKS))
                Files.createFile(release);
            if(victim!=null&&victim.isAlive()){
                if(!victim.waitFor(3L,TimeUnit.SECONDS)){
                    victim.destroyForcibly();
                    victim.waitFor(5L,TimeUnit.SECONDS);
                }
            }
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path p:(Iterable<Path>)files.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0]))
            child(Path.of(args[1]),Path.of(args[2]),Path.of(args[3]));
        else if(args.length==0)parent();
        else throw new IllegalArgumentException(
            "G21.256 invalid test arguments");
    }
}
