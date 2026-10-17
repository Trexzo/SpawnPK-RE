package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** G21.310 witness injected AFTER temp serialization under World lock. */
public final class G21310LateFinalWitnessWorldPublicationVetoTest {
    private G21310LateFinalWitnessWorldPublicationVetoTest(){}
    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.310 "+why);
    }
    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21310-late-world-witness-");
        try{
            Path file=root.resolve("selected.properties");
            Path witness=LocalFinalSaveNegativeWitness.witnessPath(file);
            FilePlayerRepository original=new FilePlayerRepository(
                user->root.resolve(user+".properties"));
            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "selected",new WorldPlayer());
            original.save(snapshot);
            byte[] initial=Files.readAllBytes(file);
            AtomicBoolean inserted=new AtomicBoolean(false);
            FilePlayerRepository writer=new FilePlayerRepository(
                user->root.resolve(user+".properties"),
                account->{},account->{
                    require("selected".equals(account),
                        "wrong exact World account at prepublish seam");
                    require(!Files.exists(witness,LinkOption.NOFOLLOW_LINKS),
                        "witness present before deterministic insertion");
                    Files.write(witness,"FOREIGN_UNTRUSTED_NEGATIVE".getBytes(
                        StandardCharsets.US_ASCII),
                        StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
                    inserted.set(true);
                });
            boolean veto=false;
            try{
                writer.saveForWorld(snapshot);
            }catch(IOException expected){
                veto=String.valueOf(expected.getMessage()).contains(
                    "G21.302 NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
            }
            require(inserted.get()&&veto,
                "late foreign negative witness did not veto World replacement");
            require(Arrays.equals(initial,Files.readAllBytes(file))&&
                Files.isRegularFile(witness,LinkOption.NOFOLLOW_LINKS),
                "failed World writer overwrote bytes or cleaned witness");
            boolean unreadable=false;
            try{
                LocalFinalSaveNegativeWitness.requireNoNegativeWitness(file);
            }catch(IOException expected){unreadable=true;}
            require(unreadable,
                "late witness lost later conservative login admission veto");
            System.out.println(
                "G21310_LATE_FINAL_WITNESS_WORLD_PUBLICATION_VETO_PASS"+
                " actualWorldTempSerialized=true"+
                " uncooperativeWitnessInsertedUnderLock=true"+
                " preMoveVeto=true"+
                " accountNotReplaced=true"+
                " witnessNeverRemoved=true"+
                " noOwnerEpochOrFinalAck=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}
