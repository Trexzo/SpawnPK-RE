package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import spk.local.R87OfflineActivePvpProjectionReceiptsTest.Case;
import spk.local.R87OfflineActivePvpProjectionReceiptsTest.Scenario;

/**
 * R88 OFFLINE ONLY: real active PvP input and three independent projection
 * stores, then real World-thread account snapshot -> bounded IO worker ->
 * FilePlayerRepository -> child JVM account reload.
 *
 * Original CombatOutcome has NO authenticated, restart-stable operation ID.
 * The single account snapshot does not transactionally commit Objective,
 * Match or Collection Log mutations. No observed snapshot or copied public
 * event fields can safely authorize automatic replay or compensation.
 */
public final class R88OfflineActivePvpRestartBoundaryTest {
    private static final String PROFILE="r88isolated";
    private static final String EXTENSION="r88audit";
    private static final String EXTENSION_KEY=
        "extension."+EXTENSION+".claim";
    private static final String UNTRUSTED_CLAIM=
        "COMMITTED_BY_CALLER_NOT_AUTHENTICATED";

    private static final class Expected {
        final Scenario scenario;
        final int capacity;
        final long objective;
        final long participant;
        final long team;
        final Long collection;
        final int resolverCalls;
        final int errors;
        Expected(Scenario scenario,int capacity,long objective,
                 long participant,long team,Long collection,
                 int resolverCalls,int errors){
            this.scenario=scenario;
            this.capacity=capacity;
            this.objective=objective;
            this.participant=participant;
            this.team=team;
            this.collection=collection;
            this.resolverCalls=resolverCalls;
            this.errors=errors;
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length>0){
            childReload(args);
            return;
        }

        assertNoDurableCombatOperationIdInSourceFact();

        runCase(new Expected(
            Scenario.COMPLETE,8,1L,2L,3L,7L,1,0
        ));
        runCase(new Expected(
            Scenario.AFTER_OBJECTIVE_TERMINAL,8,1L,0L,0L,null,0,0
        ));
        runCase(new Expected(
            Scenario.BEFORE_MATCH_TEAM_TERMINAL,8,1L,2L,0L,null,0,1
        ));
        runCase(new Expected(
            Scenario.COLLECTION_RESOLVER_FAILURE,8,
            1L,2L,3L,null,1,1
        ));
        runCase(new Expected(
            Scenario.COMPLETE,1,1L,2L,3L,7L,1,0
        ));

        System.out.println(
            "R88_OFFLINE_ACTIVE_PVP_RESTART_BOUNDARY_PASS "+
            "fiveRealActivePvpAndConsumerScenarios=true "+
            "realPlayer81AttackAndHp134=true "+
            "sourceKillDeathWithoutDurableId=true "+
            "R80AndR81OwnerReceiptGuardsUsed=true "+
            "realWorldThreadAccountCapture=true "+
            "realFilePlayerRepositoryAndIoWorker=true "+
            "twoOrderedCapturesPerScenario=true "+
            "separateJvmSnapshotReloadAndRestore=true "+
            "originalThreeProjectionWritesNotRestored=true "+
            "objectiveMatchCollectionPartialFailureBoundaries=true "+
            "callerClaimedCommittedExtensionUntrusted=true "+
            "noTrustedCrossRestartCombatReplayKey=true "+
            "allRecoveryScenariosQuarantined=true "+
            "noAutomaticReplayOrRollback=true "+
            "noLiveLocalSessionAccountsOrRewards=true"
        );
    }

    private static void runCase(Expected expected)throws Exception{
        Path root=Files.createTempDirectory(
            "r88-real-active-pvp-"+expected.scenario.name().toLowerCase()+"-"
        );
        FilePlayerRepository repository=
            new FilePlayerRepository(
                name->root.resolve(name+".properties")
            );

        try(Case c=new Case(
                expected.scenario,expected.capacity,repository
        )){
            c.lethal();
            c.check(
                expected.objective,expected.participant,expected.team,
                expected.collection,expected.resolverCalls,
                expected.errors
            );
            require(c.receipts.receiptCount()>=1&&
                    c.source.killFact.get()!=null&&
                    c.source.killFact.get().context()==
                        CombatOutcomeContext.PLAYER_PVP&&
                    c.source.attackerBytes.size()>0&&
                    c.source.victim.lifecycle().dead(),
                "R88 real active PvP with original receipt missing");

            // This is NOT a synthetic direct codec call: use the same
            // WorldPlayerPersistence captureAndSave worker that the
            // real LocalSession uses. Schedule on an actual World tick.
            WorldPlayerPersistence.SaveTicket first=
                tickCapture(c,2L,false);
            require(first.captureOrdinal>0L&&
                    first.snapshot.version()==
                        PlayerSnapshot.CURRENT_VERSION,
                "R88 first real World capture failed");
            assertNoProjectionOrCombatReceipt(first.snapshot.values());

            WorldPlayerPersistence.SaveTicket withCallerClaim=
                tickCapture(c,3L,true);
            require(withCallerClaim.captureOrdinal>
                    first.captureOrdinal&&
                    UNTRUSTED_CLAIM.equals(
                        withCallerClaim.snapshot.value(EXTENSION_KEY)),
                "R88 caller extension was not captured in correct order");
            assertNoProjectionOrCombatReceipt(
                withCallerClaim.snapshot.values()
            );

            PlayerSnapshot saved=c.source.world.persistence()
                .load(PROFILE)
                .orElseThrow(()->new AssertionError(
                    "R88 real repository did not reload account"
                ));
            require(UNTRUSTED_CLAIM.equals(
                    saved.value(EXTENSION_KEY)),
                "R88 account extension missing after real IO worker");
            assertNoProjectionOrCombatReceipt(saved.values());

            // Account capture is unrelated to the earlier objective,
            // participant/team or collection observer commits.
            c.check(
                expected.objective,expected.participant,expected.team,
                expected.collection,expected.resolverCalls,
                expected.errors
            );
            require(c.source.bridge
                    .isVerifiedFactInCurrentListenerForTest(
                        c.source.killFact.get())==false,
                "R88 expired exact source proof survived callback");
        }

        Path canonical=root.resolve(PROFILE+".properties");
        require(Files.isRegularFile(canonical)&&
                Files.size(canonical)>0L,
            "R88 canonical account snapshot not written");

        // This MUST use a new Java process: no in-memory receipt or
        // projection store can accidentally remain reachable.
        runIndependentJVM(root,expected.scenario.name());
    }

    private static WorldPlayerPersistence.SaveTicket tickCapture(
        Case c,long tick,boolean injectCallerClaim
    )throws Exception{
        AtomicReference<WorldPlayerPersistence.SaveTicket> captured=
            new AtomicReference<>();
        c.source.world.events().schedule(tick,()->{
            if(injectCallerClaim){
                c.source.attacker.snapshotExtensions()
                    .replaceNamespace(
                        EXTENSION,Collections.singletonMap(
                            "claim",UNTRUSTED_CLAIM
                        )
                    );
            }
            captured.set(
                c.source.world.persistence().captureAndSave(
                    PROFILE,c.source.attacker,
                    c.source.attackerGeneration,0,
                    "R88_OFFLINE","POST_ACTIVE_PVP"
                )
            );
        });
        require(c.source.world.observePulse(
                    System.currentTimeMillis())==tick&&
                    captured.get()!=null,
            "R88 real WorldPulse did not capture account");
        WorldPlayerPersistence.SaveTicket save=captured.get();
        save.completion.get(30L,TimeUnit.SECONDS);
        c.source.world.persistence()
            .awaitOfflineIoDrainForTest(30_000L);
        return save;
    }

    private static void runIndependentJVM(
        Path root,String scenario
    )throws Exception{
        Path java=Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase()
                .contains("win")?"java.exe":"java"
        );
        Process child=new ProcessBuilder(
            java.toString(),"-cp",
            System.getProperty("java.class.path"),
            R88OfflineActivePvpRestartBoundaryTest.class.getName(),
            "--child-reload",root.toString(),scenario
        ).redirectErrorStream(true).start();

        if(!child.waitFor(30L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            throw new AssertionError(
                "R88 isolated JVM replay audit timed out"
            );
        }
        String result=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                result.contains(
                    "R88_SEPARATE_JVM_ACCOUNT_RELOAD_QUARANTINED"
                ),
            "R88 fresh Java process failed exit="+child.exitValue()+
            " output="+result
        );
    }

    private static void childReload(String[] args){
        require(args.length==3&&
                "--child-reload".equals(args[0]),
            "R88 invalid standalone replay audit");
        Path root=Paths.get(args[1]);
        String scenario=args[2];
        // Child never receives the original Java CombatOutcome
        // object, R81 receipt instance or original aggregate stores.
        FilePlayerRepository repository=
            new FilePlayerRepository(
                name->root.resolve(name+".properties")
            );
        try(World world=World.isolatedForTest(
                60_000L,repository)){
            PlayerSnapshot snapshot=world.persistence().load(PROFILE)
                .orElseThrow(()->new AssertionError(
                    "R88 child cannot load canonical account file"
                ));
            require(UNTRUSTED_CLAIM.equals(
                    snapshot.value(EXTENSION_KEY)),
                "R88 child lost caller-owned extension");
            assertNoProjectionOrCombatReceipt(snapshot.values());

            WorldPlayer restored=new WorldPlayer();
            PlayerSnapshot normalized=
                PlayerSnapshotCodec.applyValidated(snapshot,restored);
            require(UNTRUSTED_CLAIM.equals(
                    normalized.value(EXTENSION_KEY))&&
                    UNTRUSTED_CLAIM.equals(
                        restored.snapshotExtensions()
                            .namespace(EXTENSION).get("claim")),
                "R88 fresh World restore of explicit extension failed");

            ObjectiveProgressService newObjectives=
                new ObjectiveProgressService();
            newObjectives.define(new ObjectiveDefinition(
                "combat:r87-kills",10L,"CUSTOM_LOCALLAB"
            ));
            MatchSessionService newMatches=
                new MatchSessionService();
            CollectionLogDefinition.CollectionId id=
                new CollectionLogDefinition.CollectionId(
                    "collection:r87-test-only"
                );
            CollectionLogService newLog=new CollectionLogService(
                Collections.singletonList(
                    new CollectionLogDefinition(
                        id,
                        new CollectionLogDefinition.CategoryId(
                            "category:r88-reload"
                        ),
                        "R88 independent recovered aggregate",
                        Collections.singleton(
                            new CollectionLogDefinition.EntryId(
                                "entry:r88-not-an-operation"
                            )
                        ),
                        true,
                        CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
                    )
                )
            );

            // A valid saved PLAYER snapshot and a forged "COMMITTED"
            // extension still do NOT confer a source-issued event ID,
            // objective receipt, match commit or Collection Log count.
            require(newObjectives.get("combat:r87-kills").progress==0L&&
                    newMatches.size()==0&&
                    !newLog.snapshot(id).killCount().isPresent(),
                "R88 new service falsely recovered separate projections");
            assertNoDurableCombatOperationIdInSourceFact();

            System.out.println(
                "R88_SEPARATE_JVM_ACCOUNT_RELOAD_QUARANTINED "+
                "scenario="+scenario+
                " originalProjectionEffectsUnknown=true "+
                " accountRestored=true"+
                " callerClaimNotAuthenticated=true"+
                " trustedLogicalEventId=false"+
                " objectiveMatchCollectionCommitReceipts=false"+
                " automaticReplayAuthorized=false"+
                " rollbackAuthorized=false"+
                " rewardAuthorized=false"
            );
        }catch(Exception error){
            throw new AssertionError(
                "R88 child recovery refusal failed",error
            );
        }
    }

    private static void assertNoDurableCombatOperationIdInSourceFact(){
        Set<String> fields=new HashSet<>();
        for(Field field:CombatOutcome.class.getDeclaredFields()){
            if(!Modifier.isStatic(field.getModifiers()))
                fields.add(field.getName());
        }
        Set<String> expected=new HashSet<>(Arrays.asList(
            "attacker","victim","type","context",
            "worldTick","sourceAuthority"
        ));
        require(fields.equals(expected),
            "R88 combat fact schema changed: redo durable ID audit");
    }

    private static void assertNoProjectionOrCombatReceipt(
        Map<String,String> values
    ){
        require(values!=null&&!values.isEmpty(),
            "R88 missing real gameplay snapshot values");
        for(String key:values.keySet()){
            require(!key.startsWith("objective.")&&
                    !key.startsWith("match.")&&
                    !key.startsWith("collection.")&&
                    !key.startsWith("combat.outcome.")&&
                    !key.startsWith("combat.receipt.")&&
                    !key.startsWith("extension.combat-receipt.")&&
                    !key.startsWith("extension.projection-commit."),
                "R88 original account captured unreviewed projection "+
                    "or combat receipt="+key);
        }
        require(!values.containsKey(
                "extension.r88audit.authenticated-event-id"),
            "R88 test fixture claimed nonexistent event id");
    }

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }

    private R88OfflineActivePvpRestartBoundaryTest(){}
}
