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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.277: a genuinely separate, NON-cooperating JVM can durably
 * publish a valid G21.188 ACTIVE marker after the forced World file
 * ATOMIC_MOVE, bypassing the parent's held G21.39 lock. The exact
 * postmove marker witness must make the SaveTicket UNCERTAIN.
 *
 * G21.278: when that foreign JVM targets a different account, the
 * selected World SaveTicket must complete normally.
 */
public final class G21277ForeignJvmWorldPostMoveActiveMarkerTest {
    private static final String CHILD_MARKER=
        "G21277_SECOND_JVM_ACTIVE_MARKER_PUBLISHED";
    private G21277ForeignJvmWorldPostMoveActiveMarkerTest(){}
    private static void check(boolean okay,String reason){
        if(!okay)throw new AssertionError("G21.277/278 "+reason);
    }
    private static PlayerSnapshot snapshot(String user,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21277",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(user,player);
    }
    private static void child(Path account)throws Exception{
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "target account file missing in foreign child");
        Path marker=LocalDurableAccountReviewIntentPrimitive
            .writeActiveReview(account);
        LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
            account);
        check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
            "foreign child did not leave a valid ACTIVE marker");
        System.out.println(CHILD_MARKER);
    }
    private static void launchUncooperative(Path account)
        throws IOException{
        Path java=Path.of(System.getProperty("java.home"),"bin",
            "java"+(System.getProperty("os.name").toLowerCase()
                .contains("win")?".exe":""));
        ProcessBuilder builder=new ProcessBuilder(
            java.toString(),"-cp",System.getProperty("java.class.path"),
            G21277ForeignJvmWorldPostMoveActiveMarkerTest.class.getName(),
            "--child",account.toString());
        builder.redirectErrorStream(true);
        Process process=builder.start();
        try{
            if(!process.waitFor(35L,TimeUnit.SECONDS)){
                process.destroyForcibly();
                process.waitFor(5L,TimeUnit.SECONDS);
                throw new IOException(
                    "G21.277 independent marker JVM timed out");
            }
        }catch(InterruptedException interrupted){
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException(
                "G21.277 independent marker JVM interrupted",interrupted);
        }
        String output=new String(
            process.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        check(process.exitValue()==0&&output.contains(CHILD_MARKER),
            "independent marker JVM failed: "+output);
    }
    static void run(boolean unrelated)throws Exception{
        String selectedName=unrelated
            ?"g21278-world-selected":"g21277-world-selected";
        String otherName=unrelated
            ?"g21278-world-other":"g21277-world-other";
        Path root=Files.createTempDirectory(unrelated
            ?"g21278-foreign-other-active-":
             "g21277-foreign-postmove-active-");
        String[] props={"spk.local.accountFile",
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY};
        String[] previous=new String[props.length];
        for(int i=0;i<props.length;i++)
            previous[i]=System.getProperty(props[i]);
        System.setProperty(props[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(props[1],"true");
        World world=null;
        WorldPlayer live=null;
        long generation=-1;
        try{
            Path account=LocalAccountProfiles.accountFile(selectedName);
            Path other=LocalAccountProfiles.accountFile(otherName);
            Path markerTarget=unrelated?other:account;
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(markerTarget);
            check(!account.equals(other)&&
                !Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "account/sidecar fixture identities invalid");
            AtomicBoolean childCompleted=new AtomicBoolean();
            AtomicReference<byte[]> observedPostMove=new AtomicReference<>();
            FilePlayerRepository repository=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                a->{},a->{},a->{},a->{},a->{},
                a->{
                    check(selectedName.equals(a),
                        "foreign child called at wrong World account");
                    observedPostMove.set(Files.readAllBytes(account));
                    launchUncooperative(markerTarget);
                    childCompleted.set(true);
                });
            repository.save(snapshot(selectedName,"PRE"));
            repository.save(snapshot(otherName,"OTHER_PRE"));
            byte[] previousAccount=Files.readAllBytes(account);
            byte[] previousOther=Files.readAllBytes(other);
            world=World.isolatedForTest(60000L,repository);
            live=new WorldPlayer();
            generation=world.registerPlayer(live,selectedName);
            world.start();
            final World ownedWorld=world;
            final WorldPlayer owner=live;
            final long gen=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> pending=
                new AtomicReference<>();
            world.submitAndWait(live,gen,()->{
                owner.snapshotExtensions().replaceNamespace(
                    "core_g21277",
                    Collections.singletonMap("phase","POST"));
                pending.set(ownedWorld.persistence().captureAndSave(
                    selectedName,owner,gen,0,
                    "[G21.277] ","POSTMOVE_FOREIGN_ACTIVE"));
            },5000L);
            check(pending.get()!=null,"World-owned SaveTicket absent");
            boolean uncertain=false;
            try{
                pending.get().completion.get(70L,TimeUnit.SECONDS);
            }catch(ExecutionException failed){
                Throwable cause=failed.getCause();
                uncertain=cause!=null&&
                    String.valueOf(cause.getMessage()).contains(
                        "G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW")&&
                    cause.getCause()!=null&&
                    String.valueOf(cause.getCause().getMessage()).contains(
                        "G21.275 WORLD_ACTIVE_REVIEW_MARKER_CHANGED_NO_ACK");
                if(unrelated)throw failed;
            }
            check(childCompleted.get()&&observedPostMove.get()!=null,
                "real foreign JVM post-ATOMIC_MOVE seam not observed");
            check(uncertain!=unrelated,
                "selected/foreign account marker classification incorrect");
            check(!Arrays.equals(previousAccount,
                observedPostMove.get())&&
                Arrays.equals(observedPostMove.get(),
                    Files.readAllBytes(account)),
                "postmove World account bytes not preserved");
            Optional<PlayerSnapshot> forensic=repository.load(selectedName);
            check(forensic.isPresent()&&"POST".equals(
                forensic.get().values().get("extension.core_g21277.phase")),
                "forced World postimage missing");
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(markerTarget);
            check(Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS),
                "foreign valid ACTIVE marker was removed");
            check(Arrays.equals(previousOther,Files.readAllBytes(other)),
                "unrelated account bytes changed");
            check(repository.forcedWorldPostimageCountForTest()==
                (unrelated?1:0),
                "false confirmed success or healthy save veto");
            try(Stream<Path> entries=Files.list(account.getParent())){
                String prefix=account.getFileName().toString()+
                    ".g21170-world-";
                check(entries.noneMatch(p->p.getFileName().toString()
                    .startsWith(prefix)),
                    "owned forced World temp leaked");
            }
            System.out.println(unrelated
                ?"G21278_FOREIGN_JVM_UNRELATED_WORLD_ACTIVE_MARKER_CONTROL_PASS"+
                  " realSecondJavaProcess=true"+
                  " foreignValidActiveMarkerRetained=true"+
                  " selectedWorldPostimageVerified=true"+
                  " unrelatedAccountUnchanged=true"+
                  " noGlobalMarkerVeto=true"+
                  " noAutoRecovery=true"
                :"G21277_FOREIGN_JVM_WORLD_POSTMOVE_ACTIVE_MARKER_UNCERTAIN_PASS"+
                  " realSecondJavaProcess=true"+
                  " uncooperativeMarkerPublishedInsideG2139Lock=true"+
                  " actualWorldAccountAlreadyMoved=true"+
                  " validNewActiveMarkerObserved=true"+
                  " saveTicketUncertainNotRollback=true"+
                  " postimageAndMarkerRetained=true"+
                  " zeroFalseVerifiedCount=true"+
                  " noAtomicExternalFenceProven=true");
        }finally{
            if(world!=null){
                if(live!=null&&generation>0)
                    world.unregisterPlayer(live,generation);
                world.close();
            }
            for(int i=0;i<props.length;i++){
                if(previous[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],previous[i]);
            }
            try(Stream<Path> entries=Files.walk(root)){
                for(Path p:(Iterable<Path>)entries.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==2&&"--child".equals(args[0]))
            child(Path.of(args[1]));
        else if(args.length==0)run(false);
        else throw new IllegalArgumentException(
            "G21.277 unexpected arguments");
    }
}
