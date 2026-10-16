package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * G21.272: a marker inserted for an unrelated account AFTER forced
 * World atomic account replacement cannot globally veto the selected
 * account's verified postimage. No marker deletion/recovery authority.
 */
public final class G21272UnrelatedForcedWorldPostMoveMarkerControlTest {
    private G21272UnrelatedForcedWorldPostMoveMarkerControlTest(){}
    private static void check(boolean yes,String message){
        if(!yes)throw new AssertionError("G21.272 "+message);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21272",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        Path root=Files.createTempDirectory("g21272-world-other-review-");
        String[] flags={"spk.local.accountFile",
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY};
        String[] prior=new String[flags.length];
        for(int i=0;i<flags.length;i++)prior[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(flags[1],"true");
        final String selectedName="g21272-selected";
        final String unrelatedName="g21272-unrelated";
        final Path selected=LocalAccountProfiles.accountFile(selectedName);
        final Path unrelated=LocalAccountProfiles.accountFile(unrelatedName);
        final Path marker=LocalDurableAccountReviewIntentPrimitive
            .markerPath(unrelated);
        World world=null;
        WorldPlayer player=null;
        long generation=-1;
        try{
            check(!selected.equals(unrelated)&&
                !Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "selected and unrelated account paths collide");
            AtomicReference<byte[]> observed=new AtomicReference<>();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                a->{},a->{},a->{},a->{},a->{},
                a->{
                    check(selectedName.equals(a),
                        "test injected into unrelated World");
                    observed.set(Files.readAllBytes(selected));
                    Files.createFile(marker);
                });
            repo.save(snapshot(selectedName,"BASE"));
            repo.save(snapshot(unrelatedName,"UNCHANGED"));
            byte[] originalSelected=Files.readAllBytes(selected);
            byte[] originalUnrelated=Files.readAllBytes(unrelated);

            world=World.isolatedForTest(60000L,repo);
            player=new WorldPlayer();
            generation=world.registerPlayer(player,selectedName);
            world.start();
            final World ownerWorld=world;
            final WorldPlayer live=player;
            final long owned=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(live,owned,()->{
                live.snapshotExtensions().replaceNamespace(
                    "core_g21272",
                    Collections.singletonMap("phase","PUBLISHED"));
                ticket.set(ownerWorld.persistence().captureAndSave(
                    selectedName,live,owned,0,
                    "[G21.272] ","UNRELATED_POSTMOVE_REVIEW"));
            },5000L);
            check(ticket.get()!=null,
                "real World-backed save was not enqueued");
            ticket.get().completion.get(20L,TimeUnit.SECONDS);
            check(observed.get()!=null&&
                !Arrays.equals(originalSelected,observed.get())&&
                Arrays.equals(observed.get(),Files.readAllBytes(selected)),
                "selected account was not published exactly");
            Optional<PlayerSnapshot> forensic=repo.load(selectedName);
            check(forensic.isPresent()&&
                "PUBLISHED".equals(forensic.get().values().get(
                    "extension.core_g21272.phase")),
                "selected forced World postimage missing");
            check(Arrays.equals(originalUnrelated,
                                Files.readAllBytes(unrelated))&&
                Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "unrelated negative account marker or bytes changed");
            check(repo.forcedWorldPostimageCountForTest()==1,
                "healthy exact selected World postimage not counted");
            check(ownerWorld.persistence().failedCount()==0,
                "healthy World save unexpectedly failed");
            try(Stream<Path> entries=Files.list(selected.getParent())){
                String prefix=selected.getFileName().toString()+".g21170-world-";
                check(entries.noneMatch(x->x.getFileName().toString()
                    .startsWith(prefix)),
                    "forced World temp leaked on positive save");
            }
            System.out.println(
                "G21272_UNRELATED_FORCED_WORLD_POSTMOVE_MARKER_CONTROL_PASS"+
                " realWorldSaveTask=true"+
                " selectedPostimageVerified=true"+
                " unrelatedNegativeMarkerPreserved=true"+
                " unrelatedAccountBytesPreserved=true"+
                " forcedSuccessCountOne=true"+
                " noGlobalMarkerVeto=true"+
                " noAutoReleaseOrGrant=true");
        }finally{
            if(world!=null){
                if(player!=null&&generation>0)
                    world.unregisterPlayer(player,generation);
                world.close();
            }
            for(int i=0;i<flags.length;i++){
                if(prior[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],prior[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path p:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }
}
