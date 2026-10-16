package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.259: actual second JVM writes uncooperatively to old account
 * inode during opt-in RAW forced save; CAS-like preimage must veto.
 * NOT a recovered owner epoch or atomic external-writer exclusion.
 */
public final class G21259ForeignJvmSameInodeAccountPreimageVetoTest {
    private static final String USER="g21259-cross-jvm-account";
    private static final String CONTROL="g21259-independent";
    private static final String MARKER="G21259_FOREIGN_JVM_SAME_INODE_MUTATED";
    private G21259ForeignJvmSameInodeAccountPreimageVetoTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.259 "+reason);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21259",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
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
    }
    private static void child(Path file)throws Exception{
        check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),
            "cross-JVM old account file absent");
        byte[] original=Files.readAllBytes(file);
        check(original.length>16,"old account too short");
        FileTime oldTime=Files.getLastModifiedTime(file,
            LinkOption.NOFOLLOW_LINKS);
        byte[] modified=original.clone();
        modified[4]^=1;
        try(FileChannel channel=FileChannel.open(file,
                StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            channel.position(4L);
            channel.write(ByteBuffer.wrap(new byte[]{modified[4]}));
        }
        Files.setLastModifiedTime(file,oldTime);
        check(Arrays.equals(modified,Files.readAllBytes(file))&&
            Files.size(file)==original.length&&
            oldTime.equals(Files.getLastModifiedTime(file,
                LinkOption.NOFOLLOW_LINKS)),
            "second JVM did not preserve inode length and mtime");
        System.out.println(MARKER);
    }
    private static String runChild(Path account)throws Exception{
        Path java=Path.of(System.getProperty("java.home"),"bin",
            "java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder pb=new ProcessBuilder(java.toString(),
            "-cp",System.getProperty("java.class.path"),
            G21259ForeignJvmSameInodeAccountPreimageVetoTest
                .class.getName(),"--child",account.toString());
        pb.redirectErrorStream(true);
        Process p=pb.start();
        if(!p.waitFor(25L,TimeUnit.SECONDS)){
            p.destroyForcibly();
            p.waitFor(5L,TimeUnit.SECONDS);
            throw new IOException("G21.259 foreign JVM timed out");
        }
        String output=new String(
            p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        check(p.exitValue()==0&&output.contains(MARKER),
            "foreign JVM failed: "+output);
        return output;
    }
    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> children=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(children.noneMatch(x->x.getFileName().toString()
                .startsWith(prefix)),"owned RAW temp leaked");
        }
    }
    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g21259-crossjvm-preimage-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        flags(root);
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            repo.save(snapshot(USER,"BEFORE"));
            Path account=LocalAccountProfiles.accountFile(USER);
            byte[] before=Files.readAllBytes(account);
            final boolean[] childRan={false};
            FilePlayerRepository mutated=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                name->{},name->{},name->{},name->{},
                name->{
                    try{
                        runChild(account);
                        childRan[0]=true;
                    }catch(IOException io){
                        throw io;
                    }catch(Exception childFailure){
                        throw new IOException(
                            "G21.259 foreign JVM test helper failure",
                            childFailure);
                    }
                });
            boolean denied=false;
            try{mutated.save(snapshot(USER,"AFTER"));}
            catch(IOException veto){
                denied=String.valueOf(veto.getMessage()).contains(
                    "FORCED_RAW_PREIMAGE_DRIFT_NO_MOVE");
            }
            check(childRan[0]&&denied,
                "foreign inode byte edit did not veto RAW publication");
            byte[] foreignBytes=Files.readAllBytes(account);
            byte[] expected=before.clone();
            expected[4]^=1;
            check(Arrays.equals(expected,foreignBytes),
                "forced RAW overwrote foreign JVM account postimage");
            noTemp(account);
            // Distinct account has independent session+publication locks.
            repo.save(snapshot(CONTROL,"UNRELATED_OK"));
            Path other=LocalAccountProfiles.accountFile(CONTROL);
            check(Files.isRegularFile(other,LinkOption.NOFOLLOW_LINKS),
                "unrelated account failed to save");
            noTemp(other);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation retained after foreign mutation");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "G21.39 publication lease retained");
            System.out.println(
                "G21259_FOREIGN_JVM_SAME_INODE_ACCOUNT_PREIMAGE_VETO_PASS"+
                " realIndependentJavaProcess=true"+
                " foreignOldAccountBytesPreserved=true"+
                " sameInodeLengthAndMtimeChangeDetected=true"+
                " forcedRawOldPreimageVeto=true"+
                " unrelatedAccountSaved=true"+
                " noTempOrReservationLeak=true"+
                " durableSessionEpoch=false"+
                " originalSpawnpkPolicyUnknown=true");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> children=Files.walk(root)){
                for(Path p:(Iterable<Path>)children.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0]))
            child(Path.of(args[1]));
        else if(args.length==0)parent();
        else throw new IllegalArgumentException(
            "G21.259 unexpected arguments");
    }
}
