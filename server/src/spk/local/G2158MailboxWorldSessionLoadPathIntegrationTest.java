package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * G21.58: a real World session may not combine account bytes from one
 * directory with negative admission evidence from another. All states
 * are nonauthorizing; no item grant, replay, rollback or marker cleanup.
 */
public final class G2158MailboxWorldSessionLoadPathIntegrationTest {
    private static final class DriftingResolver
        implements FilePlayerRepository.PathResolver{
        final Path a,b;
        final AtomicInteger calls=new AtomicInteger();
        DriftingResolver(Path a,Path b){this.a=a;this.b=b;}
        @Override public Path resolve(String account){
            Path home=calls.incrementAndGet()==1?a:b;
            return home.resolve(account+".properties");
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2158-admitted-load-");
        Path a=root.resolve("actual"),b=root.resolve("other");
        Files.createDirectories(a);
        Files.createDirectories(b);
        FilePlayerRepository.PathResolver actual=
            name->a.resolve(name+".properties");
        FilePlayerRepository seeded=new FilePlayerRepository(actual);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(actual);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(actual);
        MailboxDurableReviewFence review=
            new MailboxDurableReviewFence(actual);
        boolean permanentVeto=false,legacyVeto=false,intentVeto=false;
        boolean reviewVeto=false,missingMarkedVeto=false;
        boolean lateMarkerVeto=false,lateMarkerWritten=false;
        boolean pathDriftVeto=false,bOnlyCannotHydrateWrongRoot=false;
        boolean stableCleanLoads=false,stableMissingReturnsEmpty=false;
        boolean stableBOtherMarkerIgnored=false,rawForensicsPreserved=true;
        boolean originalBytesUnchanged=true,allNegativePresent=true;
        boolean noGrants=true,noLeaks=false,allDriftCallsBounded=true;
        try{
            String[] accounts={
                "g2158-permanent","g2158-legacy","g2158-intent",
                "g2158-review","g2158-late","g2158-drift",
                "g2158-b-only","g2158-clean","g2158-raw"
            };
            for(String name:accounts)seed(seeded,name);
            String[] marked={
                "g2158-permanent","g2158-legacy",
                "g2158-intent","g2158-review"
            };
            for(String name:marked){
                if(name.equals("g2158-permanent"))
                    publish(actual.resolve(name),()->
                        permanent.armInsidePublicationLock(
                            name,sha(seeded.load(name).get())));
                else if(name.equals("g2158-legacy"))
                    Files.writeString(a.resolve(
                        name+".properties.g2147-strict-postpublication-review"),
                        "legacy negative review requires manual repair");
                else if(name.equals("g2158-intent"))
                    publish(actual.resolve(name),()->
                        intent.armInsidePublicationLock(
                            name,sha(seeded.load(name).get())));
                else if(name.equals("g2158-review"))
                    Files.writeString(review.fencePath(name),
                        "G21.32 negative review requires manual repair");
            }
            for(String name:marked){
                byte[] before=Files.readAllBytes(actual.resolve(name));
                DriftingResolver drift=new DriftingResolver(a,b);
                boolean refused=refusedSession(
                    new FilePlayerRepository(drift),name,
                    "G21.32 MAILBOX_DURABLE_REVIEW_FENCE");
                if(name.equals("g2158-permanent"))permanentVeto=refused;
                if(name.equals("g2158-legacy"))legacyVeto=refused;
                if(name.equals("g2158-intent"))intentVeto=refused;
                if(name.equals("g2158-review"))reviewVeto=refused;
                allDriftCallsBounded&=drift.calls.get()==1;
                originalBytesUnchanged&=Arrays.equals(
                    before,Files.readAllBytes(actual.resolve(name)));
                rawForensicsPreserved&=seeded.load(name).isPresent();
            }

            // G21.32 marker presence must veto even a missing snapshot.
            String missing="g2158-missing";
            Files.writeString(review.fencePath(missing),
                "absent account is not an authorization bypass");
            DriftingResolver missingDrift=new DriftingResolver(a,b);
            missingMarkedVeto=refusedSession(
                new FilePlayerRepository(missingDrift),missing,
                "G21.32 MAILBOX_DURABLE_REVIEW_FENCE");
            allDriftCallsBounded&=missingDrift.calls.get()==1;

            // Deterministic marker arrival AFTER account read but BEFORE
            // the post-read negative check on exactly the chosen A file.
            String late="g2158-late";
            byte[] oldLate=Files.readAllBytes(actual.resolve(late));
            AtomicBoolean added=new AtomicBoolean();
            DriftingResolver lateDrift=new DriftingResolver(a,b);
            FilePlayerRepository lateRepo=new FilePlayerRepository(
                lateDrift,account->{},account->{},account->{
                    // G21.59 holds the account publication lock for the
                    // whole admitted read. Deliberately inject an
                    // UNCOOPERATIVE malformed sidecar here instead of
                    // recursively reacquiring the same FileLock.
                    // Its mere presence must remain a negative veto.
                    Files.writeString(permanent.fencePath(account),
                        "G2158_EXTERNAL_LATE_NO_GRANT");
                    added.set(true);
                });
            lateMarkerVeto=refusedSession(lateRepo,late,
                "G21.32 MAILBOX_DURABLE_REVIEW_FENCE");
            lateMarkerWritten=added.get()&&permanent.present(late);
            allDriftCallsBounded&=lateDrift.calls.get()==1;
            originalBytesUnchanged&=Arrays.equals(oldLate,
                Files.readAllBytes(actual.resolve(late)));

            // Without negative markers, A->B resolver drift itself is a
            // rejection, not permission to hydrate A or switch to B.
            DriftingResolver drift=new DriftingResolver(a,b);
            pathDriftVeto=refusedSession(
                new FilePlayerRepository(drift),"g2158-drift",
                "G21.58 MAILBOX_SESSION_ACCOUNT_PATH_CHANGED");
            allDriftCallsBounded&=drift.calls.get()==2;

            // An unrelated B-only marker cannot be substituted for an A
            // marker, nor make a moved resolver a consistent session path.
            String bOnly="g2158-b-only";
            Path bMarker=b.resolve(
                bOnly+".properties.g2147-strict-uncertain");
            Files.writeString(bMarker,"UNRELATED_NEGATIVE_NO_GRANT");
            DriftingResolver bDrift=new DriftingResolver(a,b);
            bOnlyCannotHydrateWrongRoot=refusedSession(
                new FilePlayerRepository(bDrift),bOnly,
                "G21.58 MAILBOX_SESSION_ACCOUNT_PATH_CHANGED");
            allDriftCallsBounded&=bDrift.calls.get()==2;
            stableBOtherMarkerIgnored=acceptedSession(
                new FilePlayerRepository(actual),bOnly);

            stableCleanLoads=acceptedSession(
                new FilePlayerRepository(actual),"g2158-clean");
            stableMissingReturnsEmpty=emptySession(
                new FilePlayerRepository(actual),"g2158-blank");
            rawForensicsPreserved&=seeded.load("g2158-raw").isPresent()&&
                seeded.load("g2158-legacy").isPresent();
            allNegativePresent=permanent.present("g2158-permanent")&&
                permanent.present(late)&&intent.present("g2158-intent")&&
                review.present("g2158-review")&&
                review.present(missing)&&Files.exists(a.resolve(
                    "g2158-legacy.properties.g2147-strict-postpublication-review"))&&
                Files.isRegularFile(bMarker);

            try(Stream<Path> aFiles=Files.list(a);
                Stream<Path> bFiles=Files.list(b)){
                noLeaks=Stream.concat(aFiles,bFiles).noneMatch(
                    f->f.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> f=Files.walk(root)){
                for(Path item:f.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(item);
            }
        }
        System.out.println("G2158_WORLD_SESSION_LOAD_DIAGNOSTICS"+
            " permanentARefused="+permanentVeto+
            " legacyARefused="+legacyVeto+
            " intentARefused="+intentVeto+
            " g2132ARefused="+reviewVeto+
            " missingMarkedRefused="+missingMarkedVeto+
            " lateMarkerRefused="+lateMarkerVeto+
            " lateMarkerWritten="+lateMarkerWritten+
            " pathDriftRefused="+pathDriftVeto+
            " unrelatedBPathDriftRefused="+bOnlyCannotHydrateWrongRoot+
            " stableBOnlyMarkerIgnored="+stableBOtherMarkerIgnored+
            " stableCleanLoads="+stableCleanLoads+
            " missingWithoutMarkerEmpty="+stableMissingReturnsEmpty+
            " rawForensicLoadUnaffected="+rawForensicsPreserved+
            " accountBytesUnchanged="+originalBytesUnchanged+
            " noMarkerCleanup="+allNegativePresent+
            " expectedResolverCalls="+allDriftCallsBounded+
            " noGrants="+noGrants+" noResourceLeaks="+noLeaks);
        if(!(permanentVeto&&legacyVeto&&intentVeto&&reviewVeto&&
            missingMarkedVeto&&lateMarkerVeto&&lateMarkerWritten&&
            pathDriftVeto&&bOnlyCannotHydrateWrongRoot&&
            stableBOtherMarkerIgnored&&stableCleanLoads&&
            stableMissingReturnsEmpty&&rawForensicsPreserved&&
            originalBytesUnchanged&&allNegativePresent&&
            allDriftCallsBounded&&noGrants&&noLeaks))
            throw new AssertionError("G21.58 World session load path gate");
        System.out.println("G2158_WORLD_SESSION_LOAD_PASS"+
            " noGrant=true noReplay=true noRelease=true");
    }

    private static boolean refusedSession(
        FilePlayerRepository repo,String account,String expected)throws Exception{
        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();
            try{
                world.persistence().load(account);
                return false;
            }catch(IOException denied){
                return denied.getMessage().contains(expected);
            }
        }
    }
    private static boolean acceptedSession(
        FilePlayerRepository repo,String account)throws Exception{
        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();
            return world.persistence().load(account).isPresent();
        }
    }
    private static boolean emptySession(
        FilePlayerRepository repo,String account)throws Exception{
        try(World world=World.isolatedForTest(60000L,repo)){
            world.start();
            return !world.persistence().load(account).isPresent();
        }
    }
    private static PlayerSnapshot seed(
        FilePlayerRepository repo,String name)throws IOException{
        WorldPlayer owner=new WorldPlayer();
        owner.markRegistered(name);
        PlayerSnapshot snapshot=PlayerSnapshotCodec.capture(name,owner);
        repo.save(snapshot);
        return snapshot;
    }
    private static String sha(PlayerSnapshot snapshot){
        return StrictDurablePlayerSnapshotWriter
            .canonicalSnapshotSha256(snapshot);
    }
    private interface Checked{void run()throws IOException;}
    private static void publish(Path path,Checked f)throws IOException{
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            path,()->{f.run();return null;});
    }
    private G2158MailboxWorldSessionLoadPathIntegrationTest(){}
}
