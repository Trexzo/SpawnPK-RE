package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.267: nlink gate tests the EXACT published account, not a
 * different named account whose inode acquires a foreign hardlink.
 */
public final class G21267UnrelatedPostMoveHardlinkPositiveControlTest {
    private G21267UnrelatedPostMoveHardlinkPositiveControlTest(){}
    private static void check(boolean ok,String what){
        if(!ok)throw new AssertionError("G21.267 "+what);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21267",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21267-unrelated-nlink-");
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
            String chosen="g21267-selected";
            String foreignName="g21267-foreign";
            repo.save(snapshot(chosen,"BEFORE"));
            repo.save(snapshot(foreignName,"BEFORE"));
            Path selected=LocalAccountProfiles.accountFile(chosen);
            Path foreign=LocalAccountProfiles.accountFile(foreignName);
            Path alias=foreign.resolveSibling(
                foreign.getFileName().toString()+".g21267-foreign-link");
            check(!selected.equals(foreign)&&
                !Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "aliased selected/foreign preconditions invalid");
            byte[] selectedBefore=Files.readAllBytes(selected);
            byte[] foreignBefore=Files.readAllBytes(foreign);
            AtomicBoolean linked=new AtomicBoolean();
            LocalOptInRawTemp.setAfterMoveProbeForTest(path->{
                check(selected.equals(path),"wrong target in hook");
                Files.createLink(alias,foreign);
                linked.set(true);
            });
            try{
                repo.save(snapshot(chosen,"AFTER"));
            }finally{
                LocalOptInRawTemp.setAfterMoveProbeForTest(null);
            }
            check(linked.get(),"unrelated alias injection absent");
            check(!Arrays.equals(selectedBefore,Files.readAllBytes(selected)),
                "healthy selected account was not updated");
            check(Arrays.equals(foreignBefore,Files.readAllBytes(foreign))&&
                Arrays.equals(foreignBefore,Files.readAllBytes(alias)),
                "unrelated account/alias unexpectedly changed");
            check(Files.exists(alias,LinkOption.NOFOLLOW_LINKS),
                "foreign alias was unlinked by selected RAW writer");
            try(Stream<Path> entries=Files.list(selected.getParent())){
                String prefix=selected.getFileName().toString()+".g21246-raw-";
                check(entries.noneMatch(p->p.getFileName().toString()
                    .startsWith(prefix)),"selected temporary file leaked");
            }
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation leaked");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0,
                "publication lock leaked");
            Files.delete(alias);
            System.out.println(
                "G21267_UNRELATED_POSTMOVE_HARDLINK_CONTROL_PASS"+
                " selectedPostimageVerified=true"+
                " unrelatedNlinkNotGlobalVeto=true"+
                " foreignBytesAndAliasPreserved=true"+
                " selectedRawPublished=true"+
                " optInNlinkGateOn=true"+
                " noOwnedTempOrLockLeak=true"+
                " noCrossAccountAuthority=true");
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
