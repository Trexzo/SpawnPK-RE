package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.49: operator-only triage of B permanent strict marker and transient
 * write-ahead intent. A matching SHA is NOT a durability/grant receipt.
 * No API here removes fences or changes any World state.
 */
public final class G2149MailboxStrictNegativeForensicsIntegrationTest {
    public static void main(String[] args)throws Exception{
        boolean permanentExact=false,intentExact=false;
        boolean permanentChangedSnapshot=false,missingAccount=false;
        boolean corruptMarker=false,dualPrefersPermanent=false;
        boolean cleanAccountUnfenced=false,restartVeto=false;
        boolean legacyMarkerStillVetoes=false;
        boolean legacyChecksumDivergence=false;
        boolean legacyTamperStillVetoes=false;
        String legacyObservedState="UNSEEN";
        boolean legacyLoginDenied=false;
        boolean noAuthority=true,noCleanup=true,noLeases=true;

        Path root=Files.createTempDirectory("g2149-negative-forensics-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        MailboxDurableReviewFence proposalMarker=
            new MailboxDurableReviewFence(paths);
        MailboxStrictUncertainFence permanent=
            new MailboxStrictUncertainFence(paths);
        MailboxStrictWriteIntentFence intent=
            new MailboxStrictWriteIntentFence(paths);
        try{
            PlayerSnapshot p=seed(repository,"g2149-permanent");
            PlayerSnapshot i=seed(repository,"g2149-intent");
            PlayerSnapshot d=seed(repository,"g2149-dual");
            seed(repository,"g2149-clean");
            PlayerSnapshot legacySnapshot=seed(repository,"g2149-legacy");
            Path legacy=paths.resolve("g2149-legacy.properties"+
                ".g2147-strict-postpublication-review");
            Files.writeString(
                legacy,legacyRecord(
                    "g2149-legacy",
                    StrictDurablePlayerSnapshotWriter
                        .canonicalSnapshotSha256(legacySnapshot)
                )
            );
            String pSha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(p);
            String iSha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(i);
            String dSha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(d);
            publish(paths.resolve("g2149-permanent"),
                ()->permanent.armInsidePublicationLock(
                    "g2149-permanent",pSha
                ));
            publish(paths.resolve("g2149-intent"),
                ()->intent.armInsidePublicationLock(
                    "g2149-intent",iSha
                ));
            publish(paths.resolve("g2149-dual"),()->{
                permanent.armInsidePublicationLock("g2149-dual",dSha);
                intent.armInsidePublicationLock("g2149-dual",dSha);
            });
            try(World world=World.isolatedForTest(60000L,repository)){
                world.start();
                MailboxFencedRestartForensics.Report pr=inspect(
                    world,proposalMarker,"g2149-permanent"
                );
                MailboxFencedRestartForensics.Report ir=inspect(
                    world,proposalMarker,"g2149-intent"
                );
                MailboxFencedRestartForensics.Report dr=inspect(
                    world,proposalMarker,"g2149-dual"
                );
                MailboxFencedRestartForensics.Report cr=inspect(
                    world,proposalMarker,"g2149-clean"
                );
                permanentExact=pr.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY&&
                    pSha.equals(pr.observedSnapshotSha256);
                intentExact=ir.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_INTENT_DIGEST_MATCH_NO_AUTHORITY&&
                    iSha.equals(ir.observedSnapshotSha256);
                dualPrefersPermanent=dr.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIGEST_MATCH_NO_AUTHORITY;
                cleanAccountUnfenced=cr.state==
                    MailboxFencedRestartForensics.State
                        .NO_FENCE_NO_AUTHORITY&&
                    world.persistence().load("g2149-clean").isPresent();
                noAuthority&=nonAuthorizing(pr)&&nonAuthorizing(ir)&&
                    nonAuthorizing(dr)&&nonAuthorizing(cr);
                MailboxFencedRestartForensics.Report lr=inspect(
                    world,proposalMarker,"g2149-legacy"
                );
                legacyObservedState=lr.state.name();
                legacyLoginDenied=denied(world,"g2149-legacy");
                legacyMarkerStillVetoes=
                    lr.state==MailboxFencedRestartForensics.State
                        .STRICT_LEGACY_CHECKSUM_VALID_EXACT_NO_AUTHORITY&&
                    legacyLoginDenied;
                noAuthority&=nonAuthorizing(lr);
                TreeMap<String,String> legacyChanged=
                    new TreeMap<>(legacySnapshot.values());
                legacyChanged.put(
                    "extension.g2149.legacy","manual divergence"
                );
                repository.save(new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,
                    "g2149-legacy",legacyChanged
                ));
                MailboxFencedRestartForensics.Report ld=inspect(
                    world,proposalMarker,"g2149-legacy"
                );
                legacyChecksumDivergence=ld.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_LEGACY_CHECKSUM_VALID_DIVERGENT_NO_AUTHORITY;
                noAuthority&=nonAuthorizing(ld);
                Files.writeString(legacy,"tampered");
                MailboxFencedRestartForensics.Report invalidLegacy=inspect(
                    world,proposalMarker,"g2149-legacy"
                );
                legacyTamperStillVetoes=
                    invalidLegacy.state==
                        MailboxFencedRestartForensics.State
                            .STRICT_LEGACY_INVALID_RECORD_NO_AUTHORITY&&
                    denied(world,"g2149-legacy");
                noAuthority&=nonAuthorizing(invalidLegacy);

                TreeMap<String,String> changed=
                    new TreeMap<>(p.values());
                changed.put("extension.g2149.manual","diverged");
                repository.save(new PlayerSnapshot(
                    PlayerSnapshot.CURRENT_VERSION,
                    "g2149-permanent",changed
                ));
                MailboxFencedRestartForensics.Report mismatch=inspect(
                    world,proposalMarker,"g2149-permanent"
                );
                permanentChangedSnapshot=mismatch.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_UNCERTAIN_DIGEST_MISMATCH_NO_AUTHORITY;
                noAuthority&=nonAuthorizing(mismatch);

                Files.delete(paths.resolve("g2149-intent"));
                MailboxFencedRestartForensics.Report missing=inspect(
                    world,proposalMarker,"g2149-intent"
                );
                missingAccount=missing.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_MARKER_MISSING_ACCOUNT;
                noAuthority&=nonAuthorizing(missing);

                Files.write(
                    permanent.fencePath("g2149-permanent"),
                    "corrupt marker, still deny admission".getBytes(
                        StandardCharsets.US_ASCII
                    )
                );
                MailboxFencedRestartForensics.Report damaged=inspect(
                    world,proposalMarker,"g2149-permanent"
                );
                corruptMarker=damaged.state==
                    MailboxFencedRestartForensics.State
                        .STRICT_MARKER_INVALID;
                noAuthority&=nonAuthorizing(damaged);
                restartVeto=denied(world,"g2149-permanent")&&
                    denied(world,"g2149-intent")&&
                    denied(world,"g2149-dual");
                noCleanup=permanent.present("g2149-permanent")&&
                    permanent.present("g2149-dual")&&
                    intent.present("g2149-intent")&&
                    intent.present("g2149-dual");
            }
            try(Stream<Path> files=Files.list(root)){
                noLeases=files.noneMatch(
                    x->x.getFileName().toString().endsWith(".tmp")
                )&&MailboxAccountPublicationCoordinator
                    .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> pathsToRemove=Files.walk(root)){
                for(Path p:pathsToRemove.sorted(
                        Comparator.reverseOrder()).toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
        System.out.println(
            "G2149_MAILBOX_STRICT_NEGATIVE_FORENSICS_DIAGNOSTICS"+
            " permanentExact="+permanentExact+
            " intentExact="+intentExact+
            " changedSnapshot="+permanentChangedSnapshot+
            " missingAccount="+missingAccount+
            " corruptMarker="+corruptMarker+
            " permanentPriority="+dualPrefersPermanent+
            " cleanAccount="+cleanAccountUnfenced+
            " restartedAccountVeto="+restartVeto+
            " legacyObservedState="+legacyObservedState+
            " legacyLoginDenied="+legacyLoginDenied+
            " legacyMarkerDenied="+legacyMarkerStillVetoes+
            " legacyChecksumDivergence="+legacyChecksumDivergence+
            " legacyTamperDenied="+legacyTamperStillVetoes+
            " noAuthority="+noAuthority+
            " noMarkerCleanup="+noCleanup+
            " noLeaseOrTempLeak="+noLeases
        );
        if(!(permanentExact&&intentExact&&permanentChangedSnapshot&&
            missingAccount&&corruptMarker&&dualPrefersPermanent&&
            cleanAccountUnfenced&&restartVeto&&
            legacyMarkerStillVetoes&&legacyChecksumDivergence&&
            legacyTamperStillVetoes&&noAuthority&&
            noCleanup&&noLeases))
            throw new AssertionError(
                "G21.49 strict negative read-only forensic regression"
            );
        System.out.println(
            "G2149_STRICT_NEGATIVE_FORENSICS_PASS"+
            " noGrant=true noReplay=true noMarkerRelease=true"
        );
    }

    private interface Pending {
        void execute()throws IOException;
    }

    private static void publish(Path accountFile,Pending pending)
        throws IOException{
        MailboxAccountPublicationCoordinator.withExclusivePublication(
            accountFile,()->{
                pending.execute();
                return null;
            }
        );
    }

    private static String legacyRecord(
        String account,String sha
    )throws Exception{
        String payload=
            "SPK-G2147-STRICT-POSTPUBLICATION-UNCERTAIN-V1\\n"+
            "REVIEW_REQUIRED_NO_GRANT\\n"+
            account+"\\n"+sha+"\\n";
        byte[] digest=java.security.MessageDigest
            .getInstance("SHA-256").digest(
                payload.getBytes(StandardCharsets.US_ASCII)
            );
        char[] chars=new char[digest.length*2];
        char[] hex="0123456789abcdef".toCharArray();
        for(int i=0;i<digest.length;i++){
            int b=digest[i]&255;
            chars[2*i]=hex[b>>>4];
            chars[2*i+1]=hex[b&15];
        }
        return payload+new String(chars)+"\\n";
    }

    private static PlayerSnapshot seed(
        FilePlayerRepository repository,String name
    )throws Exception{
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(name);
        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(name,player);
        repository.save(snapshot);
        return snapshot;
    }

    private static MailboxFencedRestartForensics.Report inspect(
        World world,MailboxDurableReviewFence review,String name
    ){
        return MailboxFencedRestartForensics.inspect(
            world.persistence(),review,name
        );
    }

    private static boolean denied(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException refusal){
            return true;
        }
    }

    private static boolean nonAuthorizing(
        MailboxFencedRestartForensics.Report r
    ){
        return !r.grantAuthorized&&!r.replayAuthorized&&
            !r.rollbackAuthorized&&!r.releaseFenceAuthorized&&
            !r.sessionAdmissionAuthorized&&
            !r.fileDurabilityConfirmed&&
            !r.automaticRecoveryAuthorized;
    }

    private G2149MailboxStrictNegativeForensicsIntegrationTest(){}
}
