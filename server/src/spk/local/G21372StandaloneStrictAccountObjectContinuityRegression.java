package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R44: a reserved opt-in standalone strict RAW writer must not
 * overwrite a different account file object placed at its original
 * pathname during serialization. Covers existing same-BYTES inode
 * substitution and an absent bootstrap leaf created after admission.
 *
 * No external compare-and-swap, authenticated cross-JVM owner epoch,
 * durable final-save ACK, or race-free hostile path pin is claimed.
 */
public final class G21372StandaloneStrictAccountObjectContinuityRegression {
    private static final String EXISTING="r44-existing";
    private static final String BOOTSTRAP="r44-bootstrap";
    private static final String HEALTHY="r44-healthy";
    private G21372StandaloneStrictAccountObjectContinuityRegression(){}

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(
            "R44_STRICT_ORIGINAL_ACCOUNT_OBJECT "+message);
    }

    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "r44_account_object",Collections.singletonMap(
                "phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }

    private static Object key(Path path)throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(attrs.isRegularFile()&&!attrs.isSymbolicLink()&&
            attrs.fileKey()!=null,"unreadable inode identity "+path);
        return attrs.fileKey();
    }

    private static void noStrictTemps(Path account)throws IOException{
        try(Stream<Path> files=Files.list(account.getParent())){
            String prefix=account.getFileName().toString()+".g2123-";
            check(files.noneMatch(file->file.getFileName().toString()
                .startsWith(prefix)),
                "owned strict temporary file leaked");
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked after strict veto");
        check(LocalJvmAccountSessionLease.activeCountForTest()==0,
            "unexpected World token left active");
    }

    private static void existingSameBytesInodeSwap(
        Path account,FilePlayerRepository.PathResolver resolver
    )throws Exception{
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(resolver);
        strict.saveStrict(snapshot(EXISTING,"BASE"));
        Object originalKey=key(account);
        byte[] original=Files.readAllBytes(account);
        AtomicBoolean substituted=new AtomicBoolean();
        Object[] successorKey={null};
        StrictDurablePlayerSnapshotWriter attacked=
            new StrictDurablePlayerSnapshotWriter(
                resolver,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_ATOMIC_REPLACE){
                        // Old and replacement account have EXACTLY the
                        // same contents. A byte-only preimage check is
                        // insufficient to detect this distinct inode.
                        Path successor=account.resolveSibling(
                            "r44-distinct-successor.tmp");
                        Files.write(successor,original);
                        successorKey[0]=key(successor);
                        check(!originalKey.equals(successorKey[0]),
                            "replacement inode unexpectedly original");
                        Files.move(successor,account,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                        substituted.set(true);
                    }
                });
        boolean rejected=false;
        try{
            attacked.saveStrict(snapshot(EXISTING,"MUST_NOT_PUBLISH"));
        }catch(IOException expected){
            rejected=String.valueOf(expected.getMessage()).contains(
                "R33_RAW_ACCOUNT_OBJECT_CHANGED_BEFORE_PUBLICATION");
        }
        check(substituted.get()&&rejected,
            "strict writer accepted swapped same-bytes inode");
        check(successorKey[0].equals(key(account)),
            "strict writer replaced independently selected successor");
        check(Arrays.equals(original,Files.readAllBytes(account)),
            "same-byte successor was overwritten");
        noStrictTemps(account);

        // A new genuine reservation on the current selected inode
        // can make another healthy strict write. The old reservation
        // was not authority for this operation.
        PlayerSnapshot after=snapshot(EXISTING,"FRESH_RESERVATION");
        check(strict.saveStrict(after).matchesSnapshot(after),
            "fresh healthy reservation did not complete");
        check(!successorKey[0].equals(key(account)),
            "fresh successful strict save did not replace account");
        noStrictTemps(account);
    }

    private static void absentBootstrapSuccessor(
        Path account,FilePlayerRepository.PathResolver resolver
    )throws Exception{
        check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
            "bootstrap account unexpectedly exists");
        AtomicBoolean created=new AtomicBoolean();
        byte[] newOwnerBytes="UNTRUSTED_BOOTSTRAP_SUCCESSOR".getBytes(
            java.nio.charset.StandardCharsets.US_ASCII);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(
                resolver,phase->{
                    if(phase==StrictDurablePlayerSnapshotWriter.Phase
                            .BEFORE_ATOMIC_REPLACE){
                        check(!Files.exists(account,
                            LinkOption.NOFOLLOW_LINKS),
                            "bootstrap appeared earlier than cutpoint");
                        Files.write(account,newOwnerBytes);
                        created.set(true);
                    }
                });
        boolean refused=false;
        try{
            strict.saveStrict(snapshot(BOOTSTRAP,"MUST_NOT_OVERWRITE"));
        }catch(IOException expected){
            refused=String.valueOf(expected.getMessage()).contains(
                "R33_RAW_ACCOUNT_OBJECT_CHANGED_BEFORE_PUBLICATION");
        }
        check(created.get()&&refused,
            "new bootstrap inode bypassed reserved absence");
        check(Arrays.equals(newOwnerBytes,Files.readAllBytes(account)),
            "strict writer overwrote externally introduced bootstrap");
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            "external bootstrap removed");
        noStrictTemps(account);
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r44-strict-account-inode-");
        String[] flags={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[flags.length];
        for(int i=0;i<flags.length;i++)
            previous[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],"true");
        System.setProperty(flags[1],"true");
        System.setProperty(flags[2],"true");
        System.setProperty(flags[3],"false");
        try{
            FilePlayerRepository.PathResolver resolver=user->
                root.resolve(user+".properties");
            Path existing=resolver.resolve(EXISTING);
            Path bootstrap=resolver.resolve(BOOTSTRAP);
            existingSameBytesInodeSwap(existing,resolver);
            absentBootstrapSuccessor(bootstrap,resolver);

            StrictDurablePlayerSnapshotWriter independent=
                new StrictDurablePlayerSnapshotWriter(resolver);
            PlayerSnapshot healthy=snapshot(HEALTHY,"HEALTHY");
            check(independent.saveStrict(healthy).matchesSnapshot(healthy),
                "unrelated strict receipt failed");
            noStrictTemps(resolver.resolve(HEALTHY));
            System.out.println(
                "R44_STANDALONE_STRICT_ACCOUNT_OBJECT_CONTINUITY_PASS"+
                " sameBytesDifferentInodeVetoed=true"+
                " originalExactByteSuccessorPreserved=true"+
                " absentBootstrapSuccessorVetoed=true"+
                " externalBootstrapBytesPreserved=true"+
                " freshReservationSameAccountAllowed=true"+
                " unrelatedStrictHealthyReceipt=true"+
                " tempAndJvmReservationCleanup=true"+
                " nonAtomicExternalSwapRaceStillOpen=true"+
                " authenticatedGlobalOwnerEpoch=false"+
                " crashDurableFinalAck=false"+
                " originalV308ClientUntested=true");
        }finally{
            for(int i=0;i<flags.length;i++){
                if(previous[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],previous[i]);
            }
            try(Stream<Path> files=Files.walk(root)){
                for(Path file:files.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(file);
            }
        }
    }
}
