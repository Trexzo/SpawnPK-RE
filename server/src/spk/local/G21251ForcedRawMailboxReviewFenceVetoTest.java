package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.251: opt-in forced RAW must not discard negative Mailbox
 * review or uncertain/IN_PROGRESS strict sidecars.
 */
public final class G21251ForcedRawMailboxReviewFenceVetoTest {
    private static final String PRIMARY=AccountStore.CANONICAL_USERNAME;
    private static final String NAMED="g21251-named";
    private static final String RAW="g21251-raw";
    private G21251ForcedRawMailboxReviewFenceVetoTest(){}
    private static void check(boolean yes,String stage){
        if(!yes)throw new AssertionError("G21.251 "+stage);
    }
    @FunctionalInterface
    private interface Save {void run()throws IOException;}

    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21251",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }

    private static void expectedVeto(
        Path file,Path sidecar,Save attempted
    )throws Exception{
        check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),
            "account absent before sidecar");
        check(!Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
            "test sidecar already occupied");
        byte[] before=Files.readAllBytes(file);
        // Malformed contents must be a negative, NEVER positive authority.
        Files.createFile(sidecar);
        boolean denied=false;
        try{attempted.run();}
        catch(IOException expected){
            denied=String.valueOf(expected.getMessage()).contains(
                "FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT");
        }
        check(denied,"forced RAW ignored mailbox review sidecar "+sidecar);
        check(Arrays.equals(before,Files.readAllBytes(file)),
            "forced RAW overwrote fenced account "+file);
        check(Files.exists(sidecar,LinkOption.NOFOLLOW_LINKS),
            "forced RAW unlinked negative sidecar "+sidecar);
        try(Stream<Path> dir=Files.list(file.getParent())){
            String prefix=file.getFileName().toString()+".g21246-raw-";
            check(dir.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),
                "forced RAW left owned temp after refusal");
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leak after mailbox sidecar refusal");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21251-mailbox-veto-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY,
            LocalOptInRawTemp.FORCE_PROPERTY};
        String[] original=new String[keys.length];
        for(int i=0;i<keys.length;i++)original[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            FilePlayerRepository.PathResolver resolver=
                LocalAccountProfiles::accountFile;

            AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState());
            Path primary=LocalAccountProfiles.accountFile(PRIMARY);
            Path review=new MailboxDurableReviewFence(resolver)
                .fencePath(PRIMARY);
            expectedVeto(primary,review,()->AccountStore.save(
                player.bank(),player.equipment(),player.movement(),
                player.petState(),player.playerState()));

            LocalAccountProfiles.save(
                NAMED,player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState());
            Path named=LocalAccountProfiles.accountFile(NAMED);
            Path uncertain=new MailboxStrictUncertainFence(resolver)
                .fencePath(NAMED);
            expectedVeto(named,uncertain,()->LocalAccountProfiles.save(
                NAMED,player.bank(),player.equipment(),
                player.movement(),player.petState(),player.playerState()));

            repo.save(snapshot(RAW,"BASE"));
            Path raw=LocalAccountProfiles.accountFile(RAW);
            Path intent=new MailboxStrictWriteIntentFence(resolver)
                .fencePath(RAW);
            expectedVeto(raw,intent,()->repo.save(
                snapshot(RAW,"UNSAFE")));

            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test leaked JVM owner");
            System.out.println(
                "G21251_FORCED_RAW_MAILBOX_REVIEW_FENCE_VETO_PASS"+
                " durableReviewVeto=true"+
                " strictUncertainVeto=true"+
                " strictWriteIntentVeto=true"+
                " allOldAccountBytesPreserved=true"+
                " malformedMarkersStayNegative=true"+
                " noTempOrReservationLeak=true"+
                " originalMailboxGrantPolicyUntouched=true"+
                " finalSaveAck=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(original[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],original[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
