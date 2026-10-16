package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.264: after account ATOMIC_MOVE, a new negative sidecar must
 * turn normal RAW completion into an uncertain/manual-review result.
 * Never confuse error reporting with a proven rollback.
 */
public final class G21264PostMoveRawNegativeMarkerUncertainTest {
    private G21264PostMoveRawNegativeMarkerUncertainTest(){}
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError("G21.264 "+message);
    }
    @FunctionalInterface
    private interface Save {void run()throws IOException;}

    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21264",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }

    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> entries=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(entries.noneMatch(path->path.getFileName().toString()
                .startsWith(prefix)), "owned temporary file leaked");
        }
    }

    private static void uncertain(
        Path account,Path sidecar,Save writer
    )throws Exception{
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "missing account before postmove test");
        check(!Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
            "negative marker already exists");
        AtomicBoolean injected=new AtomicBoolean();
        byte[][] observedPostMove={null};
        LocalOptInRawTemp.setAfterMoveProbeForTest(actual->{
            check(account.equals(actual),"wrong account at postmove seam");
            observedPostMove[0]=Files.readAllBytes(account);
            Files.createFile(sidecar);
            injected.set(true);
        });
        boolean uncertainResult=false;
        try{
            writer.run();
        }catch(IOException expected){
            uncertainResult=String.valueOf(expected.getMessage())
                .contains("RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW");
            check(expected.getCause()!=null,
                "postmove uncertain failure had no cause");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
        }
        check(injected.get()&&uncertainResult,
            "postmove negative was acknowledged normally "+sidecar);
        check(observedPostMove[0]!=null&&
            Arrays.equals(observedPostMove[0],Files.readAllBytes(account)),
            "account postimage unexpectedly reverted after uncertain result");
        check(Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
            "foreign negative sidecar was deleted");
        noTemp(account);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked on uncertain write");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "publication lock leaked on uncertain write");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21264-postmove-marker-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            FilePlayerRepository.PathResolver resolver=
                LocalAccountProfiles::accountFile;

            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path primary=AccountStore.accountFile();
            uncertain(primary,
                LocalDurableAccountReviewIntentPrimitive.markerPath(primary),
                ()->AccountStore.save(player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState()));

            String namedUser="g21264-named";
            LocalAccountProfiles.save(namedUser,
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path named=LocalAccountProfiles.accountFile(namedUser);
            uncertain(named,
                new MailboxDurableReviewFence(resolver).fencePath(namedUser),
                ()->LocalAccountProfiles.save(namedUser,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()));

            String uncertainUser="g21264-uncertain";
            repo.save(snapshot(uncertainUser,"BEFORE"));
            Path uncertainAccount=LocalAccountProfiles.accountFile(uncertainUser);
            uncertain(uncertainAccount,
                new MailboxStrictUncertainFence(resolver)
                    .fencePath(uncertainUser),
                ()->repo.save(snapshot(uncertainUser,"AFTER")));

            String intentUser="g21264-intent";
            repo.save(snapshot(intentUser,"BEFORE"));
            Path intentAccount=LocalAccountProfiles.accountFile(intentUser);
            uncertain(intentAccount,
                new MailboxStrictWriteIntentFence(resolver)
                    .fencePath(intentUser),
                ()->repo.save(snapshot(intentUser,"AFTER")));

            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "unexpected World-session lease");
            System.out.println(
                "G21264_POSTMOVE_RAW_NEGATIVE_MARKER_UNCERTAIN_PASS"+
                " activeReviewObservedAfterReplace=true"+
                " mailboxReviewObservedAfterReplace=true"+
                " strictUncertainObservedAfterReplace=true"+
                " strictWriteIntentObservedAfterReplace=true"+
                " normalCompletionDenied=true"+
                " observedPostimagesNotRolledBack=true"+
                " negativeMarkersPreserved=true"+
                " noOwnedTempOrLeaseLeak=true"+
                " crashSafeEpoch=false"+
                " durableFinalAck=false");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                    Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
