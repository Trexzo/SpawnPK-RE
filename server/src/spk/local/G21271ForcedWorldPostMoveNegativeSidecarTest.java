package spk.local;

import java.io.IOException;
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
 * G21.271: an opt-in forced, real World-owned persistence SaveTask
 * may already replace account bytes when an uncooperative actor adds
 * a NEGATIVE review marker. Must publish uncertainty, not success.
 */
public final class G21271ForcedWorldPostMoveNegativeSidecarTest {
    private G21271ForcedWorldPostMoveNegativeSidecarTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.271 "+reason);
    }
    private static PlayerSnapshot snapshot(String account,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21271",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(account,player);
    }
    private static Path sidecar(String username,int kind)throws IOException{
        Path account=LocalAccountProfiles.accountFile(username);
        FilePlayerRepository.PathResolver resolver=
            LocalAccountProfiles::accountFile;
        switch(kind){
            case 0:
                return LocalDurableAccountReviewIntentPrimitive.markerPath(
                    account);
            case 1:
                return new MailboxDurableReviewFence(resolver)
                    .fencePath(username);
            case 2:
                return new MailboxStrictUncertainFence(resolver)
                    .fencePath(username);
            case 3:
                return new MailboxStrictWriteIntentFence(resolver)
                    .fencePath(username);
            case 4:
                return account.resolveSibling(
                    account.getFileName().toString()+
                    MailboxGuardedDiskCommitRecord.SUFFIX);
            default: throw new IllegalArgumentException("marker kind");
        }
    }
    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> entries=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21170-world-";
            check(entries.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),
                "guarded World temporary file leaked");
        }
    }
    private static void scenario(int kind)throws Exception{
        final String username="g21271-guarded-"+kind;
        final Path account=LocalAccountProfiles.accountFile(username);
        final Path marker=sidecar(username,kind);
        check(!Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
            "marker already present");
        final AtomicReference<byte[]> postMove=new AtomicReference<>();
        final FilePlayerRepository repository=
            new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                a->{},a->{},a->{},a->{},a->{},
                a->{
                    check(username.equals(a),
                        "injected World publication used wrong account");
                    postMove.set(Files.readAllBytes(account));
                    Files.createFile(marker);
                });
        repository.save(snapshot(username,"BEFORE"));
        byte[] before=Files.readAllBytes(account);
        World world=World.isolatedForTest(60000L,repository);
        WorldPlayer player=new WorldPlayer();
        long generation=-1;
        try{
            generation=world.registerPlayer(player,username);
            world.start();
            final long owned=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> pending=
                new AtomicReference<>();
            world.submitAndWait(player,owned,()->{
                player.snapshotExtensions().replaceNamespace(
                    "core_g21271",Collections.singletonMap("phase","AFTER"));
                pending.set(world.persistence().captureAndSave(
                    username,player,owned,0,
                    "[G21.271] ","FORCED_WORLD_POSTMOVE"));
            },5000L);
            check(pending.get()!=null,"real World capture ticket absent");
            boolean uncertain=false;
            try{
                pending.get().completion.get(20L,TimeUnit.SECONDS);
            }catch(ExecutionException expected){
                Throwable failure=expected.getCause();
                uncertain=failure!=null&&
                    String.valueOf(failure.getMessage()).contains(
                        "G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW");
            }
            check(uncertain,
                "World persistence incorrectly acknowledged marker kind="+kind);
            check(postMove.get()!=null&&
                !Arrays.equals(before,postMove.get())&&
                Arrays.equals(postMove.get(),Files.readAllBytes(account)),
                "already moved postimage was rolled back or altered");
            Optional<PlayerSnapshot> forensic=repository.load(username);
            check(forensic.isPresent()&&"AFTER".equals(
                forensic.get().values().get("extension.core_g21271.phase")),
                "exact postmove World snapshot not preserved");
            check(Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "foreign marker deleted after failed World receipt");
            check(repository.forcedWorldPostimageCountForTest()==0,
                "uncertain write counted as verified World success");
            noTemp(account);
        }finally{
            if(generation>0)world.unregisterPlayer(player,generation);
            world.close();
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21271-forced-world-postmove-");
        String[] flags={"spk.local.accountFile",
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY};
        String[] before=new String[flags.length];
        for(int i=0;i<flags.length;i++)before[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(flags[1],"true");
        try{
            for(int i=0;i<5;i++)scenario(i);
            System.out.println(
                "G21271_FORCED_WORLD_POSTMOVE_NEGATIVE_SIDECAR_UNCERTAIN_PASS"+
                " realWorldSaveTasks=5"+
                " activeReviewAfterMoveDenied=true"+
                " mailboxReviewAfterMoveDenied=true"+
                " strictUncertainAfterMoveDenied=true"+
                " strictWriteIntentAfterMoveDenied=true"+
                " diskCommitAfterMoveDenied=true"+
                " alreadyMovedWorldPostimagesPreserved=true"+
                " failedReceiptsNotCountedAsVerified=true"+
                " noWorldTempLeak=true"+
                " noAutomaticMarkerRetirement=true"+
                " durableOwnerEpoch=false");
        }finally{
            for(int i=0;i<flags.length;i++){
                if(before[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],before[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
