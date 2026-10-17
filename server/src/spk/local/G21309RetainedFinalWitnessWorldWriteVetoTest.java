package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.309 real World adapter refuses stale final-review evidence pretemp. */
public final class G21309RetainedFinalWitnessWorldWriteVetoTest {
    private G21309RetainedFinalWitnessWorldWriteVetoTest(){}
    private static void require(boolean good,String why){
        if(!good)throw new AssertionError("G21.309 "+why);
    }
    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21309-world-write-negative-");
        try{
            FilePlayerRepository files=new FilePlayerRepository(
                user->root.resolve(user+".properties"));
            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "selected",new WorldPlayer());
            files.save(snapshot);
            Path account=root.resolve("selected.properties");
            Path active=LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            Path witness=LocalFinalSaveNegativeWitness.publishOnce(
                account,"selected",1L,11L);
            byte[] saved=Files.readAllBytes(account);
            byte[] negative=Files.readAllBytes(witness);
            Files.delete(active); // model foreign removal of ACTIVE
            require(!Files.exists(active,LinkOption.NOFOLLOW_LINKS),
                "test requires missing original ACTIVE");
            boolean veto=false;
            try{
                files.saveForWorld(snapshot);
            }catch(IOException expected){
                veto=String.valueOf(expected.getMessage()).contains(
                    "G21.302 NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
            }
            require(veto,"World writer ignored retained final witness");
            require(Arrays.equals(saved,Files.readAllBytes(account))&&
                Arrays.equals(negative,Files.readAllBytes(witness)),
                "pretemp veto changed account or witness bytes");
            try(Stream<Path> entries=Files.list(root)){
                require(entries.noneMatch(path->path.getFileName().toString()
                    .contains(".g21170-world-")),
                    "negative preflight created owned World temp");
            }
            require(files.load("selected").isPresent(),
                "nonadmitting forensic raw read was accidentally blocked");
            System.out.println(
                "G21309_RETAINED_FINAL_WITNESS_WORLD_WRITE_VETO_PASS"+
                " actualGuardedWorldWriter=true"+
                " ACTIVEAlreadyAbsent=true"+
                " exactWitnessPretempVeto=true"+
                " noWorldTempAllocated=true"+
                " accountBytesUnchanged=true"+
                " noAuthenticatedFinalReceipt=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }
}
