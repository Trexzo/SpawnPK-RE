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
 * G21.262: negative markers introduced AFTER the first G21.250/251
 * checks but before RAW account replacement must veto all three RAW
 * writer families. This is an observed seam, not an external atomic
 * inode/marker ownership protocol and not a final logout ACK.
 */
public final class G21262LateForcedRawReviewMarkerInsertionVetoTest {
    private G21262LateForcedRawReviewMarkerInsertionVetoTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.262 "+message);
    }
    @FunctionalInterface
    private interface Save {void run()throws IOException;}

    private static PlayerSnapshot snapshot(String name,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21262",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(name,player);
    }

    private static void assertNoTemp(Path account)throws IOException{
        try(Stream<Path> entries=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(entries.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),"owned RAW temp leaked for "+account);
        }
    }

    private static void expectLateVeto(
        Path account,Path negative,String expectedMessage,Save save
    )throws Exception{
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "missing baseline account "+account);
        check(!Files.exists(negative,LinkOption.NOFOLLOW_LINKS),
            "negative marker unexpectedly preexisted "+negative);
        byte[] before=Files.readAllBytes(account);
        AtomicBoolean injected=new AtomicBoolean();
        LocalOptInRawTemp.setBeforeMoveProbeForTest(actual->{
            check(account.equals(actual),"late hook targeted foreign account");
            Files.createFile(negative);
            injected.set(true);
        });
        boolean denied=false;
        try{
            save.run();
        }catch(IOException expected){
            denied=String.valueOf(expected.getMessage())
                .contains(expectedMessage);
        }finally{
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
        }
        check(injected.get()&&denied,
            "late negative marker did not veto publication "+negative);
        check(Arrays.equals(before,Files.readAllBytes(account)),
            "account overwritten after negative marker "+account);
        check(Files.exists(negative,LinkOption.NOFOLLOW_LINKS),
            "writer removed foreign negative marker "+negative);
        assertNoTemp(account);
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation retained after veto");
        check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
            "publication lease retained after veto");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21262-late-review-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)prior[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            FilePlayerRepository.PathResolver resolver=
                LocalAccountProfiles::accountFile;

            // Primary legacy RAW: G21.188 ACTIVE appears in the late seam.
            AccountStore.save(player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path primary=AccountStore.accountFile();
            expectLateVeto(primary,
                LocalDurableAccountReviewIntentPrimitive.markerPath(primary),
                "ACCOUNT_REVIEW_INTENT_PRESENT_NO_AUTO_RECOVERY",
                ()->AccountStore.save(player.bank(),player.equipment(),
                    player.movement(),player.petState(),
                    player.playerState()));

            // Named legacy RAW: malformed G21.32 review marker is negative.
            String namedUser="g21262-named";
            LocalAccountProfiles.save(namedUser,
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path named=LocalAccountProfiles.accountFile(namedUser);
            expectLateVeto(named,
                new MailboxDurableReviewFence(resolver).fencePath(namedUser),
                "FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT",
                ()->LocalAccountProfiles.save(namedUser,
                    player.bank(),player.equipment(),player.movement(),
                    player.petState(),player.playerState()));

            // Direct RAW repository: G21.36 uncertain marker appears late.
            String uncertainUser="g21262-uncertain";
            repo.save(snapshot(uncertainUser,"BEFORE"));
            Path uncertain=LocalAccountProfiles.accountFile(uncertainUser);
            expectLateVeto(uncertain,
                new MailboxStrictUncertainFence(resolver)
                    .fencePath(uncertainUser),
                "FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT",
                ()->repo.save(snapshot(uncertainUser,"AFTER")));

            // Distinct direct RAW account: G21.40 write-intent appears late.
            String intentUser="g21262-intent";
            repo.save(snapshot(intentUser,"BEFORE"));
            Path intent=LocalAccountProfiles.accountFile(intentUser);
            expectLateVeto(intent,
                new MailboxStrictWriteIntentFence(resolver)
                    .fencePath(intentUser),
                "FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT",
                ()->repo.save(snapshot(intentUser,"AFTER")));

            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "leaked admitted World lease");
            System.out.println(
                "G21262_LATE_FORCED_RAW_NEGATIVE_MARKER_VETO_PASS"+
                " lateActiveReviewDenied=true"+
                " lateMailboxReviewDenied=true"+
                " lateStrictUncertainDenied=true"+
                " lateWriteIntentDenied=true"+
                " allPriorAccountsPreserved=true"+
                " externalNegativeMarkersRetained=true"+
                " noLeaseOrTempLeak=true"+
                " finalOwnerEpoch=false"+
                " lastCheckMoveAtomic=false");
        }finally{
            LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(prior[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],prior[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                    Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
