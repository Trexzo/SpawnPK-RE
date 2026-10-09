package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.57: normal FilePlayerRepository.saveForWorld must use the exact
 * replacement path for *both* negative marker vetoes and the publication
 * lock, even when its injected resolver drifts from directory A to B.
 * No reward grant/claim, marker release, replay or rollback.
 */
public final class G2157MailboxNormalWorldSaveMarkerPathIntegrationTest {
    private static final class DriftingResolver
        implements FilePlayerRepository.PathResolver{
        final Path actual,alternate;
        final AtomicInteger calls=new AtomicInteger();
        DriftingResolver(Path a,Path b){
            actual=a;alternate=b;
        }
        @Override public Path resolve(String account){
            return (calls.incrementAndGet()==1?actual:alternate)
                .resolve(account+".properties");
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2157-normal-save-");
        Path a=root.resolve("actual"),b=root.resolve("other");
        Files.createDirectories(a);
        Files.createDirectories(b);
        FilePlayerRepository.PathResolver actual=
            name->a.resolve(name+".properties");
        FilePlayerRepository stable=new FilePlayerRepository(actual);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(actual);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(actual);
        MailboxDurableReviewFence review=
            new MailboxDurableReviewFence(actual);

        boolean preexistingPermanentVeto=false,legacyVeto=false;
        boolean intentVeto=false,g2132Veto=false;
        boolean latePublicationVeto=false,lateHookFired=false;
        boolean bOnlyNoFalseVeto=false,normalUnfencedSave=false;
        boolean unchangedRefusedAccounts=true,restartVeto=false;
        boolean independentAccountLoads=false,noAutoClear=true;
        boolean exactlyOneResolverLookup=true,noInventoryGrant=true;
        boolean noTempOrPublicationLeases=false;
        try{
            String[] refused={
                "g2157-permanent","g2157-legacy",
                "g2157-intent","g2157-g2132","g2157-late"
            };
            String[] all={
                "g2157-permanent","g2157-legacy","g2157-intent",
                "g2157-g2132","g2157-late","g2157-b-only",
                "g2157-clean","g2157-healthy"
            };
            for(String account:all){
                PlayerSnapshot initial=seed(stable,account);
                noInventoryGrant&=initial.username().equals(account);
            }
            for(String account:refused){
                Path file=actual.resolve(account);
                if(account.equals("g2157-permanent")){
                    publish(file,()->permanent.armInsidePublicationLock(
                        account,sha(stable.load(account).get())));
                }else if(account.equals("g2157-legacy")){
                    Files.writeString(legacy(a,account),
                        "legacy independent review remains negative");
                }else if(account.equals("g2157-intent")){
                    publish(file,()->intent.armInsidePublicationLock(
                        account,sha(stable.load(account).get())));
                }else if(account.equals("g2157-g2132")){
                    Files.writeString(review.fencePath(account),
                        "G21.32 review remains negative");
                }
            }

            for(String account:new String[]{
                "g2157-permanent","g2157-legacy",
                "g2157-intent","g2157-g2132"
            }){
                Path file=actual.resolve(account);
                byte[] original=Files.readAllBytes(file);
                DriftingResolver drift=new DriftingResolver(a,b);
                FilePlayerRepository worldRepository=
                    new FilePlayerRepository(drift);
                boolean rejected=refusedSave(
                    worldRepository,edited(stable.load(account).get()));
                unchangedRefusedAccounts&=Arrays.equals(
                    original,Files.readAllBytes(file));
                exactlyOneResolverLookup&=drift.calls.get()==1;
                if(account.equals("g2157-permanent"))
                    preexistingPermanentVeto=rejected;
                else if(account.equals("g2157-legacy"))
                    legacyVeto=rejected;
                else if(account.equals("g2157-intent"))
                    intentVeto=rejected;
                else if(account.equals("g2157-g2132"))
                    g2132Veto=rejected;
            }

            String late="g2157-late";
            byte[] originalLate=Files.readAllBytes(actual.resolve(late));
            AtomicBoolean injected=new AtomicBoolean();
            DriftingResolver lateDrift=new DriftingResolver(a,b);
            FilePlayerRepository lateWorldRepository=
                new FilePlayerRepository(
                    lateDrift,
                    account->{
                        injected.set(true);
                        publish(actual.resolve(account),()->
                            permanent.armInsidePublicationLock(
                                account,sha(stable.load(account).get())));
                    }
                );
            latePublicationVeto=refusedSave(lateWorldRepository,
                edited(stable.load(late).get()));
            lateHookFired=injected.get()&&permanent.present(late);
            unchangedRefusedAccounts&=Arrays.equals(originalLate,
                Files.readAllBytes(actual.resolve(late)));
            exactlyOneResolverLookup&=lateDrift.calls.get()==1;

            String onlyB="g2157-b-only";
            Path otherMarker=b.resolve(
                onlyB+".properties.g2147-strict-uncertain");
            Files.writeString(otherMarker,"UNRELATED_NEGATIVE_NO_GRANT");
            byte[] untouchedOtherMarker=Files.readAllBytes(otherMarker);
            DriftingResolver bDrift=new DriftingResolver(a,b);
            PlayerSnapshot updatedB=edited(stable.load(onlyB).get());
            new FilePlayerRepository(bDrift).saveForWorld(updatedB);
            bOnlyNoFalseVeto=stable.load(onlyB).get().values()
                .equals(updatedB.values())&&
                Arrays.equals(untouchedOtherMarker,
                    Files.readAllBytes(otherMarker));
            exactlyOneResolverLookup&=bDrift.calls.get()==1;

            String clean="g2157-clean";
            DriftingResolver normalDrift=new DriftingResolver(a,b);
            PlayerSnapshot updatedClean=edited(stable.load(clean).get());
            new FilePlayerRepository(normalDrift).saveForWorld(updatedClean);
            normalUnfencedSave=stable.load(clean).get().values()
                .equals(updatedClean.values());
            exactlyOneResolverLookup&=normalDrift.calls.get()==1;

            try(World restart=World.isolatedForTest(
                60000L,new FilePlayerRepository(actual))){
                restart.start();
                restartVeto=true;
                for(String account:refused)
                    restartVeto&=denied(restart,account);
                independentAccountLoads=
                    restart.persistence().load("g2157-healthy").isPresent()&&
                    restart.persistence().load(clean).isPresent()&&
                    restart.persistence().load(onlyB).isPresent();
            }
            noAutoClear=permanent.present("g2157-permanent")&&
                permanent.present(late)&&
                intent.present("g2157-intent")&&
                Files.exists(legacy(a,"g2157-legacy"))&&
                review.present("g2157-g2132")&&
                Files.exists(otherMarker);
            try(Stream<Path> left=Files.list(a);
                Stream<Path> right=Files.list(b)){
                noTempOrPublicationLeases=Stream.concat(left,right)
                    .noneMatch(file->file.getFileName().toString()
                        .endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> paths=Files.walk(root)){
                for(Path f:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }
        System.out.println("G2157_NORMAL_WORLD_SAVE_MARKER_PATH_DIAGNOSTICS"+
            " permanentARefused="+preexistingPermanentVeto+
            " legacyARefused="+legacyVeto+
            " intentARefused="+intentVeto+
            " g2132ARefused="+g2132Veto+
            " lateMarkerARefused="+latePublicationVeto+
            " publicationSeamFired="+lateHookFired+
            " markerAtBDoesNotBlockA="+bOnlyNoFalseVeto+
            " normalWorldSave="+normalUnfencedSave+
            " rejectedABytesUnchanged="+unchangedRefusedAccounts+
            " restartVeto="+restartVeto+
            " independentAccounts="+independentAccountLoads+
            " noAutoRelease="+noAutoClear+
            " oneResolverCallPerSave="+exactlyOneResolverLookup+
            " noInventoryGrant="+noInventoryGrant+
            " noTempOrLeaseLeaks="+noTempOrPublicationLeases);
        if(!(preexistingPermanentVeto&&legacyVeto&&intentVeto&&
            g2132Veto&&latePublicationVeto&&lateHookFired&&
            bOnlyNoFalseVeto&&normalUnfencedSave&&
            unchangedRefusedAccounts&&restartVeto&&
            independentAccountLoads&&noAutoClear&&
            exactlyOneResolverLookup&&noInventoryGrant&&
            noTempOrPublicationLeases))
            throw new AssertionError("G21.57 normal World-save marker path");
        System.out.println("G2157_NORMAL_WORLD_SAVE_MARKER_PATH_PASS"+
            " grant=false replay=false release=false");
    }

    private interface Checked{void run()throws IOException;}
    private static void publish(Path file,Checked task)throws IOException{
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            file,()->{task.run();return null;});
    }
    private static Path legacy(Path parent,String account){
        return parent.resolve(
            account+".properties.g2147-strict-postpublication-review");
    }
    private static boolean refusedSave(
        FilePlayerRepository repo,PlayerSnapshot changed)throws Exception{
        try{
            repo.saveForWorld(changed);
            return false;
        }catch(IOException rejected){
            return rejected.getMessage().contains(
                "G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO");
        }
    }
    private static boolean denied(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException refused){return true;}
    }
    private static PlayerSnapshot seed(
        FilePlayerRepository repo,String account)throws IOException{
        WorldPlayer owner=new WorldPlayer();
        owner.markRegistered(account);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(account,owner);
        repo.save(snapshot);
        return snapshot;
    }
    private static PlayerSnapshot edited(PlayerSnapshot source){
        TreeMap<String,String> values=new TreeMap<>(source.values());
        values.put("extension.g2157.normal","changed");
        return new PlayerSnapshot(
            PlayerSnapshot.CURRENT_VERSION,source.username(),values);
    }
    private static String sha(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }
    private G2157MailboxNormalWorldSaveMarkerPathIntegrationTest(){}
}
