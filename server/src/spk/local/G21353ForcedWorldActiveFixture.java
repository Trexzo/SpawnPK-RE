package spk.local;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Real guarded FilePlayerRepository save, opt-in only, original disk bytes. */
final class G21353ForcedWorldActiveFixture {
    enum Scenario { MISSING, LATE_DELETED }
    private G21353ForcedWorldActiveFixture(){}
    private static void check(boolean b,String why){
        if(!b)throw new AssertionError("G21.353-354 "+why);
    }
    static void run(Scenario scenario)throws Exception{
        Path root=Files.createTempDirectory("g21353-world-active-");
        String force=FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String neg=LocalFinalSaveNegativeWitness.PROPERTY;
        String oldForce=System.getProperty(force),oldNeg=System.getProperty(neg);
        System.clearProperty(force);System.clearProperty(neg);
        try{
            Path selected=root.resolve("selected.properties");
            Path active=LocalDurableAccountReviewIntentPrimitive
                .markerPath(selected);
            AtomicBoolean inside=new AtomicBoolean(false);
            AtomicBoolean before=new AtomicBoolean(false);
            FilePlayerRepository repo=new FilePlayerRepository(
                username->root.resolve(username+".properties"),
                username->before.set(true),
                username->{
                    inside.set(true);
                    if(scenario==Scenario.LATE_DELETED){
                        check(Files.isRegularFile(active,
                            LinkOption.NOFOLLOW_LINKS),
                            "real ACTIVE not present in publication hook");
                        Files.delete(active);
                    }
                });
            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "selected",new WorldPlayer());
            repo.save(snapshot);
            byte[] original=Files.readAllBytes(selected);
            if(scenario==Scenario.LATE_DELETED)
                LocalDurableAccountReviewIntentPrimitive.writeActiveReview(
                    selected);
            check(Files.isRegularFile(selected,LinkOption.NOFOLLOW_LINKS)&&
                (scenario==Scenario.LATE_DELETED)==
                    Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS),
                "account/ACTIVE setup mismatch");
            System.setProperty(force,"true");
            System.setProperty(neg,"true");
            boolean failed=false;
            try{repo.saveForWorld(snapshot);}
            catch(IOException expected){failed=true;}
            check(failed,"missing ACTIVE reported World save success");
            check((scenario==Scenario.LATE_DELETED)==inside.get()&&
                (scenario==Scenario.LATE_DELETED)==before.get(),
                "early or locked cutpoint was not honored");
            check(Arrays.equals(original,Files.readAllBytes(selected))&&
                !Files.exists(active,LinkOption.NOFOLLOW_LINKS)&&
                repo.forcedWorldPostimageCountForTest()==0L&&
                !Files.exists(LocalFinalSaveNegativeWitness.witnessPath(
                    selected),LinkOption.NOFOLLOW_LINKS),
                "uncertain save replaced old account or made final witness");
            String id=scenario==Scenario.MISSING?"21353":"21354";
            System.out.println("G"+id+"_FORCED_WORLD_MISSING_ACTIVE_PREMOVE_PASS"+
                " actualGuardedRepositorySave=true"+
                " injectionInsidePublication="+inside.get()+
                " originalAccountBytesUnchanged=true"+
                " noForcedPostimageOrFinalWitness=true"+
                " automaticRecovery=false");
        }finally{
            if(oldForce==null)System.clearProperty(force);
            else System.setProperty(force,oldForce);
            if(oldNeg==null)System.clearProperty(neg);
            else System.setProperty(neg,oldNeg);
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
