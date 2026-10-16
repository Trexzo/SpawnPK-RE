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
 * G21.265: a negative sidecar on an unrelated account appearing
 * after the selected RAW account's atomic move is not a global
 * reason to reject the selected account's verified completion.
 */
public final class G21265UnrelatedPostMoveRawMarkerControlTest {
    private G21265UnrelatedPostMoveRawMarkerControlTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.265 "+reason);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21265",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21265-unrelated-postmove-");
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
            String selected="g21265-selected",other="g21265-other";
            repo.save(snapshot(selected,"PRE"));
            repo.save(snapshot(other,"PRE"));
            Path current=LocalAccountProfiles.accountFile(selected);
            Path foreign=LocalAccountProfiles.accountFile(other);
            byte[] oldCurrent=Files.readAllBytes(current);
            byte[] oldForeign=Files.readAllBytes(foreign);
            Path negative=LocalDurableAccountReviewIntentPrimitive
                .markerPath(foreign);
            check(!current.equals(foreign)&&
                !Files.exists(negative,LinkOption.NOFOLLOW_LINKS),
                "preexisting unrelated negative marker");
            AtomicBoolean injected=new AtomicBoolean();
            LocalOptInRawTemp.setAfterMoveProbeForTest(account->{
                check(current.equals(account),
                    "wrong account in postmove positive control");
                Files.createFile(negative);
                injected.set(true);
            });
            try{
                repo.save(snapshot(selected,"POST"));
            }finally{
                LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            }
            byte[] updated=Files.readAllBytes(current);
            check(injected.get()&&!Arrays.equals(oldCurrent,updated),
                "selected RAW account did not persist independently");
            check(Arrays.equals(oldForeign,Files.readAllBytes(foreign)),
                "unrelated account bytes changed");
            check(Files.exists(negative,LinkOption.NOFOLLOW_LINKS),
                "unrelated marker was unlinked");
            try(Stream<Path> entries=Files.list(current.getParent())){
                String prefix=current.getFileName().toString()+".g21246-raw-";
                check(entries.noneMatch(p->p.getFileName().toString()
                    .startsWith(prefix)),"owned temporary file leaked");
            }
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation leaked");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "publication lease leaked");
            System.out.println(
                "G21265_UNRELATED_POSTMOVE_RAW_MARKER_CONTROL_PASS"+
                " exactAccountMarkerScope=true"+
                " selectedVerifiedPostimage=true"+
                " unrelatedAccountBytesPreserved=true"+
                " unrelatedNegativeMarkerPreserved=true"+
                " normalSelectedWriteSucceeded=true"+
                " noOwnedTempOrLeaseLeak=true"+
                " externalAtomicMarkerFence=false"+
                " finalWorldAck=false");
        }finally{
            LocalOptInRawTemp.setAfterMoveProbeForTest(null);
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
