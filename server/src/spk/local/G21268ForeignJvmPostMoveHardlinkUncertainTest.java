package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.268 and G21.269 share a real second-JVM post-ATOMIC_MOVE
 * filesystem injection seam; no child participates in in-JVM or OS
 * account leases. Negative/positive results are scoped to exact inode.
 */
public final class G21268ForeignJvmPostMoveHardlinkUncertainTest {
    private static final String MARKER=
        "G21268_SECOND_JVM_POSTMOVE_HARDLINK_CREATED";
    private G21268ForeignJvmPostMoveHardlinkUncertainTest(){}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.268/269 "+why);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21268",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }
    private static void child(Path target,Path alias)throws Exception{
        check(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS),
            "child source inode missing");
        check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
            "child alias preexists");
        Files.createLink(alias,target);
        check(Arrays.equals(Files.readAllBytes(target),
                            Files.readAllBytes(alias)),
            "foreign child alias points to different bytes");
        System.out.println(MARKER);
    }
    private static void createForeignAlias(
        Path target,Path alias
    )throws IOException{
        Path java=Path.of(System.getProperty("java.home"),"bin",
            "java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder pb=new ProcessBuilder(java.toString(),"-cp",
            System.getProperty("java.class.path"),
            G21268ForeignJvmPostMoveHardlinkUncertainTest.class.getName(),
            "--child",target.toString(),alias.toString());
        pb.redirectErrorStream(true);
        Process p=pb.start();
        try{
            if(!p.waitFor(35L,TimeUnit.SECONDS)){
                p.destroyForcibly();
                p.waitFor(5L,TimeUnit.SECONDS);
                throw new IOException("G21.268 child process timed out");
            }
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("G21.268 child wait interrupted",
                interrupted);
        }
        String output=new String(p.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0&&output.contains(MARKER),
            "independent child failed: "+output);
    }
    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> files=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(files.noneMatch(x->x.getFileName().toString()
                .startsWith(prefix)),"owned RAW temporary leaked");
        }
    }
    static void run(boolean unrelated)throws Exception{
        Path root=Files.createTempDirectory(
            unrelated?"g21269-foreign-control-":"g21268-foreign-postmove-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            String selectedName=unrelated
                ?"g21269-selected":"g21268-selected";
            String otherName=unrelated
                ?"g21269-other":"g21268-other";
            repo.save(snapshot(selectedName,"BEFORE"));
            repo.save(snapshot(otherName,"BEFORE"));
            Path account=LocalAccountProfiles.accountFile(selectedName);
            Path other=LocalAccountProfiles.accountFile(otherName);
            Path aliased=unrelated?other:account;
            Path alias=aliased.resolveSibling(
                aliased.getFileName().toString()+
                (unrelated?".g21269-second-jvm-alias":
                           ".g21268-second-jvm-alias"));
            byte[] oldAccount=Files.readAllBytes(account);
            byte[] oldOther=Files.readAllBytes(other);
            byte[][] observed={null};
            final boolean[] injected={false};
            LocalOptInRawTemp.setAfterMoveProbeForTest(current->{
                check(account.equals(current),
                    "after-move injection on wrong account");
                observed[0]=Files.readAllBytes(account);
                createForeignAlias(aliased,alias);
                injected[0]=true;
            });
            boolean uncertain=false;
            try{
                repo.save(snapshot(selectedName,"AFTER"));
            }catch(IOException expected){
                uncertain=String.valueOf(expected.getMessage()).contains(
                    "RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW")&&
                    expected.getCause()!=null&&
                    String.valueOf(expected.getCause().getMessage())
                        .contains("MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
                if(unrelated)throw expected;
            }finally{
                LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            }
            check(injected[0]&&observed[0]!=null,
                "foreign JVM postmove injection not reached");
            check(!Arrays.equals(oldAccount,Files.readAllBytes(account))&&
                Arrays.equals(observed[0],Files.readAllBytes(account)),
                "selected actual RAW postimage not retained");
            check(Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "foreign JVM-created alias was deleted");
            if(unrelated){
                check(!uncertain,
                    "unrelated JVM hardlink caused global account veto");
                check(Arrays.equals(oldOther,Files.readAllBytes(other))&&
                    Arrays.equals(oldOther,Files.readAllBytes(alias)),
                    "unrelated foreign account/alias bytes changed");
            }else{
                check(uncertain,
                    "real second-JVM selected postmove alias acknowledged");
                check(Arrays.equals(observed[0],Files.readAllBytes(alias)),
                    "new selected account inode and alien link diverged");
                check(Arrays.equals(oldOther,Files.readAllBytes(other)),
                    "unrelated account modified during negative test");
            }
            noTemp(account);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW writer reservation leaked");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "publication lease leaked");
            Files.delete(alias);
            repo.save(snapshot(selectedName,"HEALTHY"));
            noTemp(account);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0&&
                  MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "healthy post-alias writer leaked reservations");
            if(!unrelated)
                System.out.println(
                    "G21268_SECOND_JVM_POSTMOVE_NEW_INODE_HARDLINK_UNCERTAIN_PASS"+
                    " independentJvm=true"+
                    " aliasCreatedAfterAtomicMove=true"+
                    " newAccountNlinkGateObserved=true"+
                    " uncertainNotRollback=true"+
                    " foreignAliasAndAccountPostimagePreserved=true"+
                    " healthyAfterTestAliasRemoval=true"+
                    " noOwnedTempOrLeaseLeak=true"+
                    " noExternalAtomicFence=true");
            else
                System.out.println(
                    "G21269_SECOND_JVM_UNRELATED_POSTMOVE_HARDLINK_CONTROL_PASS"+
                    " independentJvm=true"+
                    " foreignOtherInodeAliased=true"+
                    " exactSelectedRawPostimageSucceeded=true"+
                    " foreignBytesAndAliasPreserved=true"+
                    " healthyAfterTestAliasRemoval=true"+
                    " noGlobalNlinkVeto=true"+
                    " noOwnedTempOrLeaseLeak=true");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
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
        if(args.length==3&&"--child".equals(args[0]))
            child(Path.of(args[1]),Path.of(args[2]));
        else if(args.length==0)run(false);
        else throw new IllegalArgumentException(
            "G21.268 unexpected arguments");
    }
}
