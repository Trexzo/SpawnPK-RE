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
 * R45: STRICT standalone RAW's optional G21.230 nlink gate cannot be
 * limited to admission-time. A foreign actor may create a hardlink
 * to the selected OLD inode during strict serialization, or to the
 * published NEW inode after atomic rename. The first is pre-move veto;
 * the second is manual-review uncertainty, not rollback.
 *
 * Linux/POSIX nlink observation; this is not an atomic external
 * hardlink-creation fence or an authenticated owner epoch.
 */
public final class G21373StandaloneStrictLateHardlinkPublicationRegression {
    private static final String USER="r45-hardlinked";
    private static final String OTHER="r45-unrelated";
    private G21373StandaloneStrictLateHardlinkPublicationRegression(){}

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError(
            "R45_STANDALONE_STRICT_LATE_HARDLINK "+reason);
    }
    private static PlayerSnapshot snapshot(String user,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "r45_strict_nlink",Collections.singletonMap(
                "phase",phase));
        return PlayerSnapshotCodec.capture(user,player);
    }

    private static void clean(Path account)throws IOException{
        try(Stream<Path> files=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g2123-";
            check(files.noneMatch(file->file.getFileName().toString()
                .startsWith(prefix)),
                "owned strict temporary file leaked");
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        if(System.getProperty("os.name").toLowerCase().contains("win")){
            System.out.println(
                "R45_STANDALONE_STRICT_HARDLINK_WINDOWS_NOT_EXERCISED"+
                " noUnixNlinkProviderProof=true");
            return;
        }
        Path root=Files.createTempDirectory("r45-strict-hardlink-");
        String[] keys={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] old=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            old[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],"true");
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"false");
        try{
            FilePlayerRepository.PathResolver resolver=user->
                root.resolve(user+".properties");
            Path selected=resolver.resolve(USER);
            Path beforeAlias=root.resolve("r45-old-alias.properties");
            Path afterAlias=root.resolve("r45-new-alias.properties");
            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(resolver);
            PlayerSnapshot base=snapshot(USER,"ORIGINAL");
            check(strict.saveStrict(base).matchesSnapshot(base),
                "baseline strict account was not saved");
            byte[] beforeBytes=Files.readAllBytes(selected);
            check(!Files.exists(beforeAlias,LinkOption.NOFOLLOW_LINKS),
                "preexisting beforeAlias");

            // The account's inode is unchanged; only the number of
            // directory entries naming it changes after admission.
            AtomicBoolean preInjected=new AtomicBoolean();
            StrictDurablePlayerSnapshotWriter preMove=
                new StrictDurablePlayerSnapshotWriter(
                    resolver,phase->{
                        if(phase==StrictDurablePlayerSnapshotWriter.Phase
                                .BEFORE_ATOMIC_REPLACE){
                            Files.createLink(beforeAlias,selected);
                            preInjected.set(true);
                        }
                    });
            boolean denied=false;
            try{
                preMove.saveStrict(snapshot(USER,"DENIED_LATE_OLD_ALIAS"));
            }catch(IOException expected){
                denied=String.valueOf(expected.getMessage()).contains(
                    "MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }
            check(preInjected.get()&&denied,
                "old inode gained a hardlink but strict replaced it");
            check(Files.isSameFile(selected,beforeAlias),
                "old account alias did not name selected inode");
            check(Arrays.equals(beforeBytes,Files.readAllBytes(selected))&&
                Arrays.equals(beforeBytes,Files.readAllBytes(beforeAlias)),
                "denied strict writer modified old hardlinked bytes");
            clean(selected);

            // Fixture-only unlink: no production review/repair flow.
            Files.delete(beforeAlias);
            PlayerSnapshot healthy=snapshot(USER,"NEW_HEALTHY");
            check(strict.saveStrict(healthy).matchesSnapshot(healthy),
                "new healthy reservation did not succeed");
            byte[] secondBaseline=Files.readAllBytes(selected);
            check(!Arrays.equals(beforeBytes,secondBaseline),
                "positive strict write did not change account bytes");

            // After atomic replacement, but before the final strict
            // Receipt, an uncooperative actor names the NEW inode.
            // The existing package-only RAW postmove seam runs before
            // R45's nlink postimage observation.
            AtomicBoolean postInjected=new AtomicBoolean();
            LocalJvmAccountSessionLease.setAfterRawPublicationProbeForTest(
                actual->{
                    check(selected.equals(actual),
                        "postmove probe targeted foreign account");
                    Files.createLink(afterAlias,selected);
                    postInjected.set(true);
                });
            boolean uncertain=false;
            try{
                strict.saveStrict(snapshot(USER,"MOVED_BUT_UNCERTAIN"));
            }catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected){
                uncertain=String.valueOf(expected.getMessage()).contains(
                    "R45 STRICT_RAW_POST_MOVE_HARDLINK_UNCONFIRMED_MANUAL_REVIEW")&&
                    expected.getCause()!=null&&
                    String.valueOf(expected.getCause().getMessage())
                        .contains("MULTIPLE_ACCOUNT_HARDLINKS_DENIED");
            }finally{
                LocalJvmAccountSessionLease
                    .setAfterRawPublicationProbeForTest(null);
            }
            check(postInjected.get()&&uncertain,
                "new inode hardlink produced a confirmed strict Receipt");
            check(!Arrays.equals(secondBaseline,Files.readAllBytes(selected)),
                "uncertain postmove fixture did not replace account");
            check(Files.isSameFile(selected,afterAlias)&&
                Arrays.equals(Files.readAllBytes(selected),
                    Files.readAllBytes(afterAlias)),
                "postmove alias was lost or silently rolled back");
            clean(selected);

            // A separate account is unaffected by the exact-account
            // hardlink negative and should still get a strict Receipt.
            PlayerSnapshot independent=snapshot(OTHER,"HEALTHY");
            check(strict.saveStrict(independent).matchesSnapshot(independent),
                "unrelated strict account was rejected");
            check(Files.isRegularFile(resolver.resolve(OTHER),
                LinkOption.NOFOLLOW_LINKS),
                "unrelated strict account missing");
            clean(resolver.resolve(OTHER));
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "strict test leaked World owner");
            System.out.println(
                "R45_STRICT_LATE_HARDLINK_PUBLICATION_PASS"+
                " realPreMoveOldAccountHardlinkDenied=true"+
                " oldAccountBytesPreserved=true"+
                " freshReservationAfterFixtureCleanup=true"+
                " realPostMoveNewAccountHardlinkUncertain=true"+
                " alreadyMovedBytesNeverClaimedRolledBack=true"+
                " unrelatedStrictReceiptHealthy=true"+
                " strictTempsAndRawReservationsClean=true"+
                " optInG21230Only=true"+
                " externallyAtomicHardlinkFence=false"+
                " crossProcessOwnerEpoch=false"+
                " crashDurableFinalAck=false");
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(old[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],old[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
