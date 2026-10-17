package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * G21.303: exact account scope. A forged, nonregular or foreign
 * witness is negative ONLY for the selected matching account, and
 * never a positive receipt or reason to mutate neighboring files.
 */
public final class G21303NegativeFinalWitnessForeignAccountIsolationTest {
    private G21303NegativeFinalWitnessForeignAccountIsolationTest(){}
    private static void check(boolean okay,String reason){
        if(!okay)throw new AssertionError("G21.303 "+reason);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21303-witness-isolation-");
        try{
            FilePlayerRepository repository=new FilePlayerRepository(
                user->root.resolve(user+".properties"));
            repository.save(PlayerSnapshotCodec.capture(
                "selected",new WorldPlayer()));
            repository.save(PlayerSnapshotCodec.capture(
                "unrelated",new WorldPlayer()));
            Path selected=root.resolve("selected.properties");
            Path unrelated=root.resolve("unrelated.properties");
            Path otherWitness=LocalFinalSaveNegativeWitness
                .witnessPath(unrelated);
            // A directory/nonregular forged leaf is STILL a negative,
            // and cannot affect the different exact account path.
            Files.createDirectory(otherWitness);
            Optional<PlayerSnapshot> selectedSnapshot=
                repository.loadForWorldSession("selected");
            check(selectedSnapshot.isPresent()&&
                "selected".equals(selectedSnapshot.get().username()),
                "foreign witness poisoned clean selected account");
            boolean refused=false;
            try{
                repository.loadForWorldSession("unrelated");
            }catch(IOException expected){
                refused=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "NEGATIVE_FINAL_WITNESS_PRESENT_NO_AUTO_RECOVERY");
            }
            check(refused,"foreign malformed witness was ignored");
            check(Files.isDirectory(otherWitness,
                    LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(selected,LinkOption.NOFOLLOW_LINKS)&&
                Files.isRegularFile(unrelated,LinkOption.NOFOLLOW_LINKS)&&
                !Files.exists(LocalFinalSaveNegativeWitness
                    .witnessPath(selected),LinkOption.NOFOLLOW_LINKS),
                "session inspection changed account/witness source leaves");
            System.out.println(
                "G21303_FINAL_WITNESS_EXACT_ACCOUNT_ISOLATION_PASS"+
                " genuineSessionLoadSelected=true"+
                " foreignNonregularWitnessDenied=true"+
                " selectedUnaffected=true"+
                " noAutomaticCleanup=true"+
                " noGrantAuthority=true");
        }finally{
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }
}
