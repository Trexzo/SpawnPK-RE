package spk.local;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.367-370: exact real World-owned temp inode alias, publication
 * cutpoints, and unrelated foreign filesystem controls.
 */
final class G21367ForcedWorldTempHardlinkFixture {
    enum Scenario { PRELOCK, INSIDE, POSTMOVE, FOREIGN }
    private G21367ForcedWorldTempHardlinkFixture() {}

    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError("G21.367-370 "+message);
    }

    private static Path onlyWorldTemp(Path directory) throws IOException {
        try(Stream<Path> stream=Files.list(directory)) {
            Path[] found=stream.filter(path->
                path.getFileName().toString().contains(
                    "selected.properties.g21170-world-") &&
                path.getFileName().toString().endsWith(".tmp"))
                .toArray(Path[]::new);
            check(found.length==1,"expected exactly one World temp, got "+found.length);
            return found[0];
        }
    }

    static void run(Scenario scenario)throws Exception {
        Path root=Files.createTempDirectory("g21367-temp-link-");
        String force=FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String negative=LocalFinalSaveNegativeWitness.PROPERTY;
        String oldForce=System.getProperty(force),oldNeg=System.getProperty(negative);
        System.clearProperty(force);System.clearProperty(negative);
        try{
            Path selected=root.resolve("selected.properties");
            Path active=LocalDurableAccountReviewIntentPrimitive.markerPath(selected);
            Path alias=root.resolve("world-temp-external-alias");
            Path foreign=root.resolve("foreign-independent-file");
            Path foreignAlias=root.resolve("foreign-independent-alias");
            AtomicBoolean prelockSeen=new AtomicBoolean();
            AtomicBoolean insideSeen=new AtomicBoolean();
            AtomicBoolean postmoveSeen=new AtomicBoolean();
            FilePlayerRepository repo=new FilePlayerRepository(
                name->root.resolve(name+".properties"),
                name->{
                    prelockSeen.set(true);
                    if(scenario==Scenario.PRELOCK){
                        Path temp=onlyWorldTemp(root);
                        byte[] before=Files.readAllBytes(temp);
                        BasicFileAttributes original=Files.readAttributes(temp,
                            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                        Files.createLink(alias,temp);
                        check(Files.isSameFile(alias,temp) &&
                            Objects.equals(original.fileKey(),
                                Files.readAttributes(temp,
                                    BasicFileAttributes.class,
                                    LinkOption.NOFOLLOW_LINKS).fileKey()) &&
                            Arrays.equals(before,Files.readAllBytes(alias)),
                            "prelock World temp alias not the original inode");
                    }
                },
                name->{
                    insideSeen.set(true);
                    if(scenario==Scenario.INSIDE){
                        Path temp=onlyWorldTemp(root);
                        byte[] originalBytes=Files.readAllBytes(temp);
                        Files.createLink(alias,temp);
                        check(Files.isSameFile(alias,temp) &&
                            Arrays.equals(originalBytes,Files.readAllBytes(temp)),
                            "inside publication temp alias missing");
                    }
                },
                name->{},
                name->{},
                name->{},
                name->{
                    postmoveSeen.set(true);
                    if(scenario==Scenario.POSTMOVE){
                        check(Files.isRegularFile(selected,LinkOption.NOFOLLOW_LINKS),
                            "postmove selected publication absent");
                        Files.createLink(alias,selected);
                        check(Files.isSameFile(alias,selected),
                            "postmove alias does not name published account inode");
                    }
                });
            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "selected",new WorldPlayer());
            repo.save(snapshot);
            byte[] prior=Files.readAllBytes(selected);
            Object originalKey=Files.readAttributes(selected,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(selected);
            byte[] originalActive=Files.readAllBytes(active);
            if(scenario==Scenario.FOREIGN){
                Files.write(foreign,new byte[]{5,4,3,2,1});
                Files.createLink(foreignAlias,foreign);
                check(Files.isSameFile(foreign,foreignAlias),
                    "foreign independent hardlink not created");
            }
            System.setProperty(force,"true");
            System.setProperty(negative,"true");
            IOException failure=null;
            try{repo.saveForWorld(snapshot);}
            catch(IOException blocked){failure=blocked;}

            if(scenario==Scenario.PRELOCK||scenario==Scenario.INSIDE){
                check(failure!=null &&
                    String.valueOf(failure.getMessage()).contains(
                        "G21.367 WORLD_TEMP_ALIAS_PREMOVE_NEGATIVE"),
                    "missing before-move World temp hardlink veto: "+failure);
                check(Arrays.equals(prior,Files.readAllBytes(selected)) &&
                    Objects.equals(originalKey,Files.readAttributes(selected,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                        .fileKey()) && Files.isRegularFile(alias,
                        LinkOption.NOFOLLOW_LINKS) &&
                    repo.forcedWorldPostimageCountForTest()==0,
                    "original selected inode overwritten before temp alias veto");
                check(prelockSeen.get() && insideSeen.get() && !postmoveSeen.get(),
                    "not the real World temp pre-publication cutpoint");
            }else if(scenario==Scenario.POSTMOVE){
                check(failure!=null &&
                    String.valueOf(failure.getMessage()).contains(
                        "G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW") &&
                    failure.getCause()!=null &&
                    String.valueOf(failure.getCause().getMessage()).contains(
                        "G21.369 WORLD_POSTIMAGE_ALIAS_UNCERTAIN"),
                    "missing uncertain postmove account nlink veto: "+failure);
                check(prelockSeen.get() && insideSeen.get() && postmoveSeen.get() &&
                    Files.isSameFile(alias,selected) &&
                    !Objects.equals(originalKey,Files.readAttributes(selected,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                        .fileKey()) &&
                    repo.forcedWorldPostimageCountForTest()==0,
                    "postmove alias outcome not uncertain or no physical publication");
            }else{
                check(failure==null && prelockSeen.get() &&
                    insideSeen.get() && postmoveSeen.get() &&
                    Files.isSameFile(foreign,foreignAlias) &&
                    repo.forcedWorldPostimageCountForTest()==1,
                    "foreign independent link blocked healthy selected save: "+failure);
            }
            check(Arrays.equals(originalActive,Files.readAllBytes(active)) &&
                !Files.exists(LocalFinalSaveNegativeWitness.witnessPath(selected),
                    LinkOption.NOFOLLOW_LINKS),
                "ACTIVE or negative final-review evidence altered by temp alias test");
            String id=scenario==Scenario.PRELOCK?"21367":
                scenario==Scenario.INSIDE?"21368":
                scenario==Scenario.POSTMOVE?"21369":"21370";
            System.out.println("G"+id+"_WORLD_TEMP_ALIAS_PASS"+
                " preMoveDenied="+
                    (scenario==Scenario.PRELOCK||scenario==Scenario.INSIDE)+
                " postMoveUncertain="+(scenario==Scenario.POSTMOVE)+
                " unrelatedHealthy="+(scenario==Scenario.FOREIGN)+
                " forcedSuccessCount="+repo.forcedWorldPostimageCountForTest()+
                " finalReceipt=false replay=false");
        }finally{
            if(oldForce==null)System.clearProperty(force);
            else System.setProperty(force,oldForce);
            if(oldNeg==null)System.clearProperty(negative);
            else System.setProperty(negative,oldNeg);
            try(Stream<Path> stream=Files.walk(root)){
                for(Path p:stream.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
