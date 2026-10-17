package spk.local;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * G21.374–376. Real JVM/account lease and real World registration;
 * strict provider preflight is called at the production one-shot
 * admission boundary, NOT through a forged test-only marker writer.
 * Never simulates a durable final-save ACK or a successful relog.
 */
final class G21374StrictOneShotTokenAdmissionFixture {
    enum Scenario { ABSENT, SELECTED_ALIAS, FOREIGN_ALIAS }
    private G21374StrictOneShotTokenAdmissionFixture() {}
    private static void check(boolean b,String message) {
        if(!b)throw new AssertionError("G21.374–376 "+message);
    }
    static void run(Scenario scenario)throws Exception {
        Path root=Files.createTempDirectory("g21374-real-token-preactive-");
        String[] properties={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalReviewIntentAdmissionVeto.PROPERTY,
            LocalOneShotActiveReviewOnAdmission.PROPERTY,
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY,
            LocalFinalSaveNegativeWitness.PROPERTY,
            LocalJvmAccountSessionLease.HARDLINK_COUNT_GATE_PROPERTY,
            LocalJvmAccountSessionLease.INODE_ALIAS_GATE_PROPERTY
        };
        String[] original=new String[properties.length];
        for(int i=0;i<properties.length;i++)
            original[i]=System.getProperty(properties[i]);
        System.setProperty(properties[0],root.resolve("opensrc.properties").toString());
        for(int i=1;i<=8;i++)System.setProperty(properties[i],"true");
        // Isolate G21.371/372: the unrelated G21.230 generic gate is
        // deliberately OFF, proving strict review has its own veto.
        System.setProperty(properties[9],"false");
        System.setProperty(properties[10],"false");
        World world=null;
        LocalJvmAccountSessionLease.Token token=null;
        WorldPlayer player=null;
        long generation=-1;
        final String user="g21374-locked-user";
        try {
            Path account=LocalAccountProfiles.accountFile(user);
            Path active=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            // Named local profiles map to accounts/profiles/ under the
            // root primary account. Real admission creates this path,
            // but the hardlink fixture must create its parent BEFORE
            // writing a preexisting selected account.
            Files.createDirectories(account.getParent());
            Path alias=root.resolve("account-or-foreign-alias");
            Path other=root.resolve("foreign.properties");
            if(scenario==Scenario.SELECTED_ALIAS) {
                Files.write(account,new byte[]{1,2,3,4});
                Files.createLink(alias,account);
                check(Files.isSameFile(alias,account),
                    "selected existing hardlink setup failed");
            }else if(scenario==Scenario.FOREIGN_ALIAS) {
                Files.write(other,new byte[]{5,6,7,8});
                Files.createLink(alias,other);
                check(Files.isSameFile(alias,other),
                    "foreign account hardlink setup failed");
            }
            boolean providerSupported;
            try {
                Object count=Files.getAttribute(root,"unix:nlink",
                    LinkOption.NOFOLLOW_LINKS);
                providerSupported=count instanceof Number&&
                    ((Number)count).longValue()>=1;
            }catch(UnsupportedOperationException|
                    IllegalArgumentException unsupported) {
                providerSupported=false;
            }
            // G21.374 Windows fixture isolation: hosted Java lacks
            // unix:nlink and may also have null fileKey for the
            // independently experimental G21.221 lock identity check.
            // Disable that separate optional feature ONLY when this
            // provider is unsupported so the test genuinely reaches
            // the G21.371 pre-ACTIVE refusal through a held OS token.
            // Production account lock/identity policy is untouched.
            if(!providerSupported)
                System.setProperty(
                    LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
                    "false");
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            world=World.isolatedForTest(60_000L,repo);
            LocalFinalSaveNegativeWitness.requireAdmissionConfiguration(true);
            LocalReviewIntentAdmissionVeto.requireConfiguration(true,true);
            LocalOneShotActiveReviewOnAdmission.requireConfiguration(
                true,true,true);
            token=LocalJvmAccountSessionLease.acquire(world.persistence(),user);
            check(token!=null,"real session token absent");
            LocalReviewIntentAdmissionVeto.requireAdmissible(true,true,token);
            IllegalStateException denied=null;
            try {
                LocalOneShotActiveReviewOnAdmission.forceBeforeWorldRegistration(
                    true,true,token);
            }catch(IllegalStateException failure) {
                denied=failure;
            }
            boolean selectedAlias=scenario==Scenario.SELECTED_ALIAS;
            if(!providerSupported||selectedAlias) {
                String code=!providerSupported
                    ?"G21.371 STRICT_LINK_PROVIDER_UNAVAILABLE_BEFORE_ACTIVE"
                    :"G21.372 STRICT_PRE_ADMISSION_ACCOUNT_ALIAS_NEGATIVE";
                check(denied!=null&&
                    String.valueOf(denied.getMessage()).contains(
                        "G21.190 ONE_SHOT_INTENT_FORCE_FAILED_NO_ACCOUNT_ADMISSION")&&
                    denied.getCause()!=null&&
                    String.valueOf(denied.getCause().getMessage()).contains(code),
                    "real locked admission did not reject before ACTIVE: "+denied);
                check(!Files.exists(active,LinkOption.NOFOLLOW_LINKS)&&
                    world.players().size()==0,
                    "denied admission wrote irreversible ACTIVE or joined World");
            }else{
                check(denied==null,
                    "supported exact-account preflight denied: "+denied);
                check(Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS),
                    "real admission missed forced ACTIVE before World member");
                LocalDurableAccountReviewIntentPrimitive.requireReviewMarker(
                    account);
                check(world.players().size()==0,
                    "ACTIVE should precede actual World registration");
                player=new WorldPlayer();
                generation=world.registerPlayer(player,user);
                token.bindAdmittedWorldOwner(player,generation);
                check(world.players().owns(player,generation),
                    "real World ownership not admitted after ACTIVE");
                check(Files.isRegularFile(active,LinkOption.NOFOLLOW_LINKS),
                    "registered World owner implicitly retired ACTIVE");
            }
            if(scenario==Scenario.FOREIGN_ALIAS)
                check(Files.isSameFile(alias,other),
                    "foreign alias mutated by selected admission");
            if(scenario==Scenario.ABSENT&&!providerSupported)
                check(!Files.exists(account,LinkOption.NOFOLLOW_LINKS),
                    "failed unsupported provider created account");
            System.out.println("G"+id(scenario)+
                "_REAL_TOKEN_PRE_ACTIVE_PROVIDER_PASS"+
                " providerSupported="+providerSupported+
                " selectedAlias="+selectedAlias+
                " admissionBeforeActiveDenied="+
                    (!providerSupported||selectedAlias)+
                " actualWorldMembership="+
                    (providerSupported&&!selectedAlias)+
                " automaticRecovery=false receipt=false");
        }finally{
            try{
                if(world!=null&&player!=null&&generation>0)
                    world.unregisterPlayer(player,generation);
                if(token!=null)token.releaseAfterInitializationFailure();
                if(world!=null)world.close();
            }finally{
                for(int i=0;i<properties.length;i++){
                    if(original[i]==null)System.clearProperty(properties[i]);
                    else System.setProperty(properties[i],original[i]);
                }
                try(Stream<Path> paths=Files.walk(root)){
                    for(Path p:paths.sorted(Comparator.reverseOrder())
                            .toArray(Path[]::new))Files.deleteIfExists(p);
                }
            }
        }
    }
    private static String id(Scenario scenario){
        return scenario==Scenario.ABSENT?"21374":
               scenario==Scenario.SELECTED_ALIAS?"21375":"21376";
    }
}
