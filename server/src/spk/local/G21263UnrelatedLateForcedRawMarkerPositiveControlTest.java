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
 * G21.263: rechecking account-local negative markers must not reject
 * a healthy forced RAW write merely because another account receives
 * a concurrent manual-review marker in its separate namespace.
 */
public final class G21263UnrelatedLateForcedRawMarkerPositiveControlTest {
    private G21263UnrelatedLateForcedRawMarkerPositiveControlTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.263 "+message);
    }
    private static PlayerSnapshot snapshot(String name,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21263",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(name,player);
    }
    private static void noTemp(Path account)throws IOException{
        try(Stream<Path> entries=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g21246-raw-";
            check(entries.noneMatch(p->p.getFileName().toString()
                .startsWith(prefix)),"owned RAW temp leaked");
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21263-unrelated-marker-");
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
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            String targetUser="g21263-target";
            String unrelatedUser="g21263-unrelated";
            repo.save(snapshot(targetUser,"BEFORE"));
            repo.save(snapshot(unrelatedUser,"BEFORE"));
            Path target=LocalAccountProfiles.accountFile(targetUser);
            Path unrelated=LocalAccountProfiles.accountFile(unrelatedUser);
            Path negative=LocalDurableAccountReviewIntentPrimitive
                .markerPath(unrelated);
            check(!target.equals(unrelated)&&
                !Files.exists(negative,LinkOption.NOFOLLOW_LINKS),
                "preexisting marker or aliased account path");
            byte[] before=Files.readAllBytes(target);
            byte[] otherBefore=Files.readAllBytes(unrelated);
            AtomicBoolean injected=new AtomicBoolean();
            LocalOptInRawTemp.setBeforeMoveProbeForTest(account->{
                check(target.equals(account),
                    "unexpected target of unrelated-marker fixture");
                Files.createFile(negative);
                injected.set(true);
            });
            try{
                repo.save(snapshot(targetUser,"AFTER"));
            }finally{
                LocalOptInRawTemp.setBeforeMoveProbeForTest(null);
            }
            byte[] after=Files.readAllBytes(target);
            check(injected.get()&&!Arrays.equals(before,after),
                "independent target write was rejected or not persisted");
            check(Arrays.equals(otherBefore,Files.readAllBytes(unrelated)),
                "unrelated account bytes were mutated");
            check(Files.exists(negative,LinkOption.NOFOLLOW_LINKS),
                "unrelated negative marker was removed");
            noTemp(target);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation retained after successful save");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "publication lock lease retained after success");
            System.out.println(
                "G21263_UNRELATED_LATE_FORCED_RAW_MARKER_CONTROL_PASS"+
                " exactTargetPathScoped=true"+
                " unrelatedNegativeMarkerPreserved=true"+
                " unrelatedAccountBytesPreserved=true"+
                " selectedAccountPostimageChanged=true"+
                " publicationAndRawLeasesReleased=true"+
                " globalMarkerLockout=false"+
                " durableFinalSaveAck=false");
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
