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
 * G21.364-366: real FilePlayerRepository opt-in guarded World save
 * with same-inode selected account link-growth, before account move.
 */
final class G21364ForcedWorldAccountHardlinkFixture {
    enum Scenario { EXISTING, LATE, FOREIGN }
    private G21364ForcedWorldAccountHardlinkFixture() {}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.364-366 "+message);
    }
    static void run(Scenario scenario)throws Exception{
        Path root=Files.createTempDirectory("g21364-world-account-link-");
        String forced=FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY;
        String neg=LocalFinalSaveNegativeWitness.PROPERTY;
        String oldForced=System.getProperty(forced),oldNeg=System.getProperty(neg);
        System.clearProperty(forced);System.clearProperty(neg);
        try{
            Path selected=root.resolve("selected.properties");
            Path alias=root.resolve("selected-inode-alias");
            Path foreign=root.resolve("foreign.properties");
            Path foreignAlias=root.resolve("foreign-inode-alias");
            Path active=LocalDurableAccountReviewIntentPrimitive.markerPath(selected);
            AtomicBoolean hook=new AtomicBoolean(false);
            FilePlayerRepository repository=new FilePlayerRepository(
                name->root.resolve(name+".properties"),
                name->{},
                name->{
                    hook.set(true);
                    if(scenario==Scenario.LATE){
                        byte[] selectedBytes=Files.readAllBytes(selected);
                        Object originalKey=Files.readAttributes(selected,
                            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                            .fileKey();
                        Files.createLink(alias,selected);
                        check(Files.isSameFile(alias,selected)&&
                            Objects.equals(originalKey,Files.readAttributes(
                                selected,BasicFileAttributes.class,
                                LinkOption.NOFOLLOW_LINKS).fileKey())&&
                            Arrays.equals(selectedBytes,Files.readAllBytes(selected)),
                            "late selected-account alias was not installed");
                    }
                });
            PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(
                "selected",new WorldPlayer());
            repository.save(snapshot);
            byte[] originalBytes=Files.readAllBytes(selected);
            Object originalKey=Files.readAttributes(selected,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
            LocalDurableAccountReviewIntentPrimitive.writeActiveReview(selected);
            byte[] activeBytes=Files.readAllBytes(active);
            if(scenario==Scenario.EXISTING){
                Files.createLink(alias,selected);
                check(Files.isSameFile(alias,selected),
                    "preexisting selected-account hardlink missing");
            }else if(scenario==Scenario.FOREIGN){
                Files.write(foreign,new byte[]{4,3,2,1});
                Files.createLink(foreignAlias,foreign);
                check(Files.isSameFile(foreignAlias,foreign),
                    "foreign account alias not created");
            }
            System.setProperty(forced,"true");System.setProperty(neg,"true");
            IOException failure=null;
            try{repository.saveForWorld(snapshot);}
            catch(IOException expected){failure=expected;}
            if(scenario==Scenario.FOREIGN){
                check(failure==null&&hook.get()&&
                    repository.forcedWorldPostimageCountForTest()==1&&
                    Files.isSameFile(foreign,foreignAlias),
                    "foreign account link blocked healthy selected save: "+failure);
            }else{
                String expected=scenario==Scenario.EXISTING
                    ?"G21.364 WORLD_ACCOUNT_EXISTING_ALIAS_NEGATIVE"
                    :"G21.365 WORLD_ACCOUNT_LATE_ALIAS_NEGATIVE";
                check(failure!=null&&String.valueOf(failure.getMessage())
                    .contains(expected),"missing selected account alias veto: "+failure);
                check(Arrays.equals(originalBytes,Files.readAllBytes(selected))&&
                    Objects.equals(originalKey,Files.readAttributes(selected,
                        BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                        .fileKey())&&Files.isSameFile(alias,selected)&&
                    repository.forcedWorldPostimageCountForTest()==0,
                    "foreign alias or World writer changed original selected inode");
                check(hook.get()==(scenario==Scenario.LATE),
                    "wrong pre-temp / late-publication boundary");
                if(scenario==Scenario.EXISTING){
                    try(Stream<Path> files=Files.list(root)){
                        check(files.noneMatch(path->
                            path.getFileName().toString()
                            .contains(".g21170-world-")),
                            "preexisting account hardlink still allocated World temp");
                    }
                }
            }
            check(Arrays.equals(activeBytes,Files.readAllBytes(active))&&
                !Files.exists(LocalFinalSaveNegativeWitness.witnessPath(selected),
                    LinkOption.NOFOLLOW_LINKS),
                "negative ACTIVE changed or false final review receipt invented");
            String id=scenario==Scenario.EXISTING?"21364":
                scenario==Scenario.LATE?"21365":"21366";
            System.out.println("G"+id+"_FORCED_WORLD_ACCOUNT_ALIAS_PASS"+
                " realGuardedWorldSave=true"+
                " negativeSelectedAliasBlocked="+(scenario!=Scenario.FOREIGN)+
                " foreignAccountUnrelated="+(scenario==Scenario.FOREIGN)+
                " noAuthenticatedReceipt=true");
        }finally{
            if(oldForced==null)System.clearProperty(forced);
            else System.setProperty(forced,oldForced);
            if(oldNeg==null)System.clearProperty(neg);
            else System.setProperty(neg,oldNeg);
            try(Stream<Path> stream=Files.walk(root)){
                for(Path file:stream.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
}
