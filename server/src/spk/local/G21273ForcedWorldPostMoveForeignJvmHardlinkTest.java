package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.273: genuinely independent JVM creates hardlink of a newly
 * published forced World account inode after actual ATOMIC_MOVE.
 * Writer must expose uncertainty rather than completing SaveTicket.
 * G21.274 reuses the fixture with an unrelated named account inode.
 */
public final class G21273ForcedWorldPostMoveForeignJvmHardlinkTest {
    static final String CHILD_MARKER=
        "G21273_FOREIGN_CHILD_ACCOUNT_HARDLINK_CREATED";
    private G21273ForcedWorldPostMoveForeignJvmHardlinkTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.273/274 "+why);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21273",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }
    private static void child(Path account,Path alias)throws Exception{
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "foreign child source inode not regular");
        check(!Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
            "foreign alias preexists");
        Files.createLink(alias,account);
        check(Arrays.equals(Files.readAllBytes(account),
                            Files.readAllBytes(alias)),
            "foreign child alias bytes do not match");
        System.out.println(CHILD_MARKER);
    }
    private static void foreignLink(Path account,Path alias)
        throws IOException{
        Path java=Path.of(System.getProperty("java.home"),"bin",
            "java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder pb=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21273ForcedWorldPostMoveForeignJvmHardlinkTest.class.getName(),
            "--child",account.toString(),alias.toString());
        pb.redirectErrorStream(true);
        Process p=pb.start();
        try{
            if(!p.waitFor(35L,TimeUnit.SECONDS)){
                p.destroyForcibly();
                p.waitFor(5L,TimeUnit.SECONDS);
                throw new IOException("G21.273 foreign JVM timeout");
            }
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("G21.273 foreign JVM interrupted",
                interrupted);
        }
        String output=new String(p.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(p.exitValue()==0&&output.contains(CHILD_MARKER),
            "second JVM hardlink writer failed "+output);
    }
    static void run(boolean unrelated)throws Exception{
        final String username=unrelated
            ?"g21274-selected":"g21273-selected";
        final String otherName=unrelated
            ?"g21274-other":"g21273-other";
        Path root=Files.createTempDirectory(unrelated
            ?"g21274-world-other-link-":"g21273-world-postmove-link-");
        String[] flags={"spk.local.accountFile",
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY};
        String[] old=new String[flags.length];
        for(int i=0;i<flags.length;i++)old[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(flags[1],"true");
        System.setProperty(flags[2],"true");
        World world=null;
        WorldPlayer live=null;
        long generation=-1L;
        try{
            final Path account=LocalAccountProfiles.accountFile(username);
            final Path other=LocalAccountProfiles.accountFile(otherName);
            final Path aliased=unrelated?other:account;
            final Path alias=aliased.resolveSibling(
                aliased.getFileName().toString()+
                (unrelated?".g21274-foreign-link":
                           ".g21273-foreign-link"));
            check(!account.equals(other)&&
                !Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "account identities not distinct");
            AtomicReference<byte[]> moved=new AtomicReference<>();
            final FilePlayerRepository repository=
                new FilePlayerRepository(
                    LocalAccountProfiles::accountFile,
                    a->{},a->{},a->{},a->{},a->{},
                    a->{
                        check(username.equals(a),
                            "foreign child injected into wrong account");
                        moved.set(Files.readAllBytes(account));
                        foreignLink(aliased,alias);
                    });
            repository.save(snapshot(username,"BASE"));
            repository.save(snapshot(otherName,"OTHER_UNCHANGED"));
            byte[] baseline=Files.readAllBytes(account);
            byte[] otherBaseline=Files.readAllBytes(other);
            world=World.isolatedForTest(60000L,repository);
            live=new WorldPlayer();
            generation=world.registerPlayer(live,username);
            world.start();
            final World owningWorld=world;
            final WorldPlayer owner=live;
            final long gen=generation;
            final AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(live,gen,()->{
                owner.snapshotExtensions().replaceNamespace(
                    "core_g21273",
                    Collections.singletonMap("phase","PUBLISHED"));
                ticket.set(owningWorld.persistence().captureAndSave(
                    username,owner,gen,0,
                    "[G21.273] ","FORCED_WORLD_FOREIGN_HARDLINK"));
            },5000L);
            check(ticket.get()!=null,
                "no real World-backed SaveTicket created");
            boolean failedAsUncertain=false;
            try{
                ticket.get().completion.get(70L,TimeUnit.SECONDS);
            }catch(ExecutionException expected){
                Throwable failure=expected.getCause();
                failedAsUncertain=failure!=null&&
                    String.valueOf(failure.getMessage()).contains(
                        "G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW")&&
                    failure.getCause()!=null&&
                    String.valueOf(failure.getCause().getMessage()).contains(
                        "MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
                if(unrelated)throw expected;
            }
            check(moved.get()!=null&&
                !Arrays.equals(baseline,moved.get())&&
                Arrays.equals(moved.get(),Files.readAllBytes(account)),
                "actual new World account postimage not preserved");
            check(Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "foreign JVM link was silently unlinked");
            Optional<PlayerSnapshot> forensic=repository.load(username);
            check(forensic.isPresent()&&
                "PUBLISHED".equals(forensic.get().values().get(
                    "extension.core_g21273.phase")),
                "new World snapshot missing on disk");
            if(unrelated){
                check(!failedAsUncertain,
                    "foreign alias to different account caused global veto");
                check(Arrays.equals(otherBaseline,Files.readAllBytes(other))&&
                    Arrays.equals(otherBaseline,Files.readAllBytes(alias)),
                    "unrelated account/foreign alias data altered");
                check(repository.forcedWorldPostimageCountForTest()==1,
                    "healthy World save was not counted as confirmed");
            }else{
                check(failedAsUncertain,
                    "foreign new-inode hardlink incorrectly acknowledged");
                check(Arrays.equals(moved.get(),Files.readAllBytes(alias)),
                    "selected account and foreign alias bytes diverged");
                check(Arrays.equals(otherBaseline,Files.readAllBytes(other)),
                    "unrelated account mutated by negative case");
                check(repository.forcedWorldPostimageCountForTest()==0,
                    "uncertain World SaveTask counted as verified");
            }
            try(Stream<Path> entries=Files.list(account.getParent())){
                String prefix=account.getFileName().toString()+".g21170-world-";
                check(entries.noneMatch(p->p.getFileName().toString()
                    .startsWith(prefix)),"owned World temporary file leaked");
            }
            // Only the test fixture created the alias; only it removes it.
            Files.delete(alias);
            System.out.println(unrelated
                ?"G21274_FOREIGN_JVM_UNRELATED_WORLD_POSTMOVE_HARDLINK_CONTROL_PASS"+
                    " secondJavaProcess=true"+
                    " foreignOtherAccountAliased=true"+
                    " selectedWorldSaveVerified=true"+
                    " unrelatedBytesPreserved=true"+
                    " noGlobalHardlinkVeto=true"+
                    " noOwnedWorldTempLeak=true"
                :"G21273_FOREIGN_JVM_FORCED_WORLD_POSTMOVE_HARDLINK_UNCERTAIN_PASS"+
                    " secondJavaProcess=true"+
                    " newWorldAccountInodeAliasedAfterMove=true"+
                    " failureMarkedUncertainNotRollback=true"+
                    " accountAndForeignAliasPostimageRetained=true"+
                    " noFalseVerifiedSaveCount=true"+
                    " noOwnedWorldTempLeak=true"+
                    " durableOwnerEpoch=false");
        }finally{
            if(world!=null){
                if(live!=null&&generation>0)
                    world.unregisterPlayer(live,generation);
                world.close();
            }
            for(int i=0;i<flags.length;i++){
                if(old[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==3&&"--child".equals(args[0]))
            child(Path.of(args[1]),Path.of(args[2]));
        else if(args.length==0)run(false);
        else throw new IllegalArgumentException("G21.273 bad arguments");
    }
}
