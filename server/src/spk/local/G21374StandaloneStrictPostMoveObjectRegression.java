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

/** R46: a postmove same-byte inode swap or unlink is UNKNOWN, not a Receipt.
 * Optional observed RAW object continuity is not a cross-JVM account epoch.
 */
public final class G21374StandaloneStrictPostMoveObjectRegression {
    private G21374StandaloneStrictPostMoveObjectRegression(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R46_STRICT_POSTMOVE "+reason);
    }
    private static PlayerSnapshot snapshot(String account,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "r46_strict_object",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(account,player);
    }
    private static Object inode(Path file)throws IOException{
        BasicFileAttributes a=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        check(a.isRegularFile()&&!a.isSymbolicLink()&&a.fileKey()!=null,
            "unsupported test fixture inode");
        return a.fileKey();
    }
    private static void noLeaks(Path selected)throws IOException{
        try(Stream<Path> paths=Files.list(selected.getParent())){
            String prefix=selected.getFileName()+".g2123-";
            check(paths.noneMatch(p->p.getFileName().toString()
                    .startsWith(prefix)),"strict temporary file leaked");
        }
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            "RAW reservation leaked");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("r46-strict-postmove-");
        String[] keys={
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY
        };
        String[] previous=new String[keys.length];
        for(int i=0;i<keys.length;i++)
            previous[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],"true");
        System.setProperty(keys[1],"true");
        System.setProperty(keys[2],"true");
        System.setProperty(keys[3],"false");
        System.setProperty(keys[4],"false");
        try{
            FilePlayerRepository.PathResolver resolver=user->
                root.resolve(user+".properties");
            StrictDurablePlayerSnapshotWriter writer=
                new StrictDurablePlayerSnapshotWriter(resolver);
            String name="r46-same-bytes";
            Path selected=resolver.resolve(name);
            Path copy=root.resolve("r46-independent-inode.tmp");
            PlayerSnapshot baseline=snapshot(name,"BASELINE");
            check(writer.saveStrict(baseline).matchesSnapshot(baseline),
                "initial strict save not confirmed");
            byte[] oldBytes=Files.readAllBytes(selected);
            AtomicBoolean swapped=new AtomicBoolean();
            LocalJvmAccountSessionLease.setAfterRawPublicationProbeForTest(
                actual->{
                    if(!selected.equals(actual))
                        throw new IOException("wrong account in probe");
                    Object before=inode(actual);
                    Files.copy(actual,copy);
                    if(before.equals(inode(copy))||
                            !Arrays.equals(Files.readAllBytes(actual),
                                           Files.readAllBytes(copy)))
                        throw new IOException("impostor not independent byte copy");
                    Files.move(copy,actual,StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                    swapped.set(true);
                });
            boolean unknown=false;
            try{
                writer.saveStrict(snapshot(name,"ATTEMPTED_NEW"));
            }catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected){
                unknown=String.valueOf(expected.getMessage()).contains(
                    "R46 STRICT_RAW_POST_MOVE_OBJECT_UNCONFIRMED_MANUAL_REVIEW")&&
                    expected.getCause()!=null&&
                    String.valueOf(expected.getCause().getMessage()).contains(
                        "R46 STRICT_RAW_POST_MOVE_OBJECT_CHANGED");
            }finally{
                LocalJvmAccountSessionLease
                    .setAfterRawPublicationProbeForTest(null);
            }
            check(swapped.get()&&unknown,
                "byte-identical postmove inode replacement was ACKed");
            check(!Arrays.equals(oldBytes,Files.readAllBytes(selected)),
                "postmove test incorrectly claimed old bytes persisted");
            noLeaks(selected);

            String vanished="r46-vanished";
            Path missing=resolver.resolve(vanished);
            AtomicBoolean removed=new AtomicBoolean();
            LocalJvmAccountSessionLease.setAfterRawPublicationProbeForTest(
                actual->{
                    if(!missing.equals(actual))
                        throw new IOException("wrong account in unlink probe");
                    Files.delete(actual);
                    removed.set(true);
                });
            boolean missingUnknown=false;
            try{
                writer.saveStrict(snapshot(vanished,"JUST_DELETED"));
            }catch(StrictDurablePlayerSnapshotWriter
                    .UnconfirmedCommitException expected){
                missingUnknown=String.valueOf(expected.getMessage()).contains(
                    "R46 STRICT_RAW_POST_MOVE_OBJECT_UNCONFIRMED_MANUAL_REVIEW");
            }finally{
                LocalJvmAccountSessionLease
                    .setAfterRawPublicationProbeForTest(null);
            }
            check(removed.get()&&missingUnknown&&
                    !Files.exists(missing,LinkOption.NOFOLLOW_LINKS),
                "missing postmove account was ACKed");
            noLeaks(missing);

            PlayerSnapshot independent=snapshot("r46-unrelated","HEALTHY");
            check(writer.saveStrict(independent).matchesSnapshot(independent),
                "unrelated strict writer was denied");
            noLeaks(resolver.resolve(independent.username()));
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "session lease leaked");
            System.out.println("R46_STRICT_POSTMOVE_OBJECT_PASS"+
                " sameBytesNewInodeUnconfirmed=true"+
                " missingPostmoveInodeUnconfirmed=true"+
                " unrelatedAccountHealthy=true"+
                " cleanReservations=true"+
                " optInOnly=true"+
                " externalAtomicCAS=false"+
                " crashDurableEpoch=false"+
                " originalV308Runtime=false");
        }finally{
            LocalJvmAccountSessionLease
                .setAfterRawPublicationProbeForTest(null);
            for(int i=0;i<keys.length;i++){
                if(previous[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],previous[i]);
            }
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
