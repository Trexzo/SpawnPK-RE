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

/**
 * G21.261: truly independent JVM ignores cooperating account locks
 * and creates hardlink alias of the old account just before move.
 * Existing G21.230 optional nlink gate rechecked by G21.260 must veto.
 */
public final class G21261ForeignJvmLateRawHardlinkCountVetoTest {
    private static final String MARKER=
        "G21261_FOREIGN_JVM_ACCOUNT_HARDLINK_ALIAS_CREATED";
    private G21261ForeignJvmLateRawHardlinkCountVetoTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.261 "+message);
    }
    private static void child(Path account,Path alias)throws Exception{
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "original account absent in second JVM");
        check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
            "alias already existed before child");
        Files.createLink(alias,account);
        check(Files.isRegularFile(alias,LinkOption.NOFOLLOW_LINKS),
            "child did not create hardlink");
        check(Arrays.equals(
                Files.readAllBytes(account),Files.readAllBytes(alias)),
            "child hardlink not exact current old-account bytes");
        System.out.println(MARKER);
    }
    private static void foreignAlias(Path account,Path alias)
        throws IOException{
        Path java=Path.of(System.getProperty("java.home"),
            "bin","java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder pb=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21261ForeignJvmLateRawHardlinkCountVetoTest.class.getName(),
            "--child",account.toString(),alias.toString());
        pb.redirectErrorStream(true);
        Process p=pb.start();
        try{
            if(!p.waitFor(25L,TimeUnit.SECONDS)){
                p.destroyForcibly();
                p.waitFor(5L,TimeUnit.SECONDS);
                throw new IOException("G21.261 foreign JVM timed out");
            }
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException(
                "G21.261 foreign JVM wait interrupted",interrupted);
        }
        String output=new String(p.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0&&output.contains(MARKER),
            "foreign hardlink process failed: "+output);
    }
    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> files=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(files.noneMatch(x->x.getFileName().toString()
                .startsWith(prefix)),"owned RAW temp remains");
        }
    }
    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g21261-foreign-nlink-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer player=new WorldPlayer();
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path account=AccountStore.accountFile();
            byte[] oldBytes=Files.readAllBytes(account);
            Path alias=account.resolveSibling(
                account.getFileName().toString()+".g21261-child-hardlink");
            final boolean[] childDone={false};
            LocalOptInRawTemp.setBeforeMoveProbeForTest(selected->{
                check(account.equals(selected),
                    "injected forced RAW changed account path");
                foreignAlias(account,alias);
                childDone[0]=true;
            });
            boolean denied=false;
            try{
                AccountStore.save(player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState());
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }finally{
                LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            }
            check(childDone[0]&&denied,
                "foreign JVM alias not refused before atomic move");
            check(Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "foreign hardlink was silently removed");
            check(Arrays.equals(oldBytes,Files.readAllBytes(account))&&
                Arrays.equals(oldBytes,Files.readAllBytes(alias)),
                "forced writer overwrote original account or alias bytes");
            noTemp(account);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "raw writer reservation leaked");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "publication lease leaked");
            Files.delete(alias);
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            noTemp(account);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0&&
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "normal post-alias write left account locks");
            System.out.println(
                "G21261_FOREIGN_JVM_LATE_RAW_HARDLINK_COUNT_VETO_PASS"+
                " independentJavaProcess=true"+
                " foreignAliasCreatedAfterEarlyCheck=true"+
                " optInLateNlinkCheckDeniedAtomicMove=true"+
                " oldAccountAndAliasPreserved=true"+
                " healthyAfterTestAliasRemoved=true"+
                " tempAndLocksClean=true"+
                " noAtomicExternalLinkFence=true"+
                " durableAccountOwnerEpoch=false");
        }finally{
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
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
        else if(args.length==0)parent();
        else throw new IllegalArgumentException(
            "G21.261 unexpected arguments");
    }
}
