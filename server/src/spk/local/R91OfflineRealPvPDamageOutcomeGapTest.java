package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R91 OFFLINE ONLY: real validated active PvP attack, R90 reservation,
 * actual canonical PlayerLifecycleService HP mutation, and interruption
 * before real CombatOutcome publication. A separate JVM reloads both
 * forensic witnesses AND the actual victim's account snapshot.
 *
 * No real account, production network/session or gameplay reward is used.
 */
public final class R91OfflineRealPvPDamageOutcomeGapTest {
    private static final String PROFILE="r91-victim";

    private static final class Case implements AutoCloseable {
        final Path root;
        final R86OfflineActivePvpDomainBridgeTest.Fixture f;
        final R90OfflineAcceptedAttackJournal accepted;
        final R91OfflinePostDamageObservationJournal damage;
        final AtomicReference<IOException> expectedFailure=
            new AtomicReference<>();

        Case(R91OfflinePostDamageObservationJournal.Fault fault)
                throws Exception{
            root=Files.createTempDirectory("r91-pvp-hp-event-gap-");
            PlayerRepository repo=new FilePlayerRepository(
                name->root.resolve("account-"+name+".properties")
            );
            f=new R86OfflineActivePvpDomainBridgeTest.Fixture(
                R86OfflineActivePvpDomainBridgeTest.Mode.NORMAL,repo
            );
            accepted=new R90OfflineAcceptedAttackJournal(
                root,R90OfflineAcceptedAttackJournal.Fault.NONE,4
            );
            damage=new R91OfflinePostDamageObservationJournal(
                root,root,fault
            );
            f.bridge.installOfflineAcceptedAttemptForTest(accepted);
            f.bridge.installOfflinePostDamageForTest(damage);
        }

        void actualLethal()throws Exception{
            f.lethal();
            require(f.killFact.get()!=null&&
                    f.deathFact.get()!=null&&
                    f.highEvents.get()==2&&
                    f.normalEvents.get()==2&&
                    f.bridge.admittedCountForTest()==2&&
                    f.progress()==1L&&
                    f.resolved.get()!=null&&
                    f.resolved.get().contains(
                        "targetHpPacket134=true"),
                "R91 real active PvP semantic and HP presentation failed");
            checkAppliedHp();
        }

        void interruptAfterDamage()throws Exception{
            f.world.events().schedule(1L,()->{
                try{
                    f.handler.tickAttack(
                        20L,f.attackerWriter,f.attackerSync
                    );
                    throw new AssertionError(
                        "R91 expected post-HP fault did not abort resolver"
                    );
                }catch(IOException fault){
                    expectedFailure.set(fault);
                }
            });
            require(f.world.observePulse(
                    System.currentTimeMillis())==1L,
                "R91 real WorldPulse did not execute attack");
            require(expectedFailure.get()!=null&&
                    expectedFailure.get().getMessage().contains(
                        "R91_INJECTED_")&&
                    f.resolved.get()==null&&
                    f.killFact.get()==null&&
                    f.deathFact.get()==null&&
                    f.highEvents.get()==0&&
                    f.normalEvents.get()==0&&
                    f.bridge.issuedCountForTest()==0&&
                    f.bridge.admittedCountForTest()==0&&
                    f.progress()==0L,
                "R91 semantic outcome unexpectedly emitted "+
                "before interrupted post-damage probe");
            checkAppliedHp();
        }

        void checkAppliedHp(){
            require(f.victim.playerState().currentLevel(
                        PlayerState.HITPOINTS)==0&&
                    f.victim.lifecycle().dead()&&
                    accepted.countForTest()==1&&
                    accepted.lastReservedForTest()!=null,
                "R91 actual HP/death mutation did not precede "+
                "semantic observation interruption");
        }

        UUID id(){
            return accepted.lastReservedForTest().syntheticAttemptId;
        }

        void saveAndReloadActualVictim()throws Exception{
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            f.world.events().schedule(2L,()->ticket.set(
                f.world.persistence().captureAndSave(
                    PROFILE,f.victim,f.victimGeneration,0,
                    "R91_OFFLINE","POST_CANONICAL_HP"
                )
            ));
            require(f.world.observePulse(
                    System.currentTimeMillis())==2L&&ticket.get()!=null,
                "R91 real World-owned victim capture failed");
            ticket.get().completion.get(30L,TimeUnit.SECONDS);
            f.world.persistence().awaitOfflineIoDrainForTest(30_000L);
            PlayerSnapshot reload=f.world.persistence().load(PROFILE)
                .orElseThrow(()->new AssertionError(
                    "R91 victim file not persisted through real worker"
                ));
            require("0".equals(reload.value(
                    "skill."+PlayerState.HITPOINTS+".current"
                )),"R91 canonical account did not preserve observed HP=0");
            require(!reload.values().containsKey(
                    "extension.combat-receipt.event-id")&&
                    !reload.values().containsKey(
                    "combat.outcome.id"),
                "R91 invented authenticated durable combat operation");
        }

        void childCheck(
            R91OfflinePostDamageObservationJournal.Status expected
        )throws Exception{
            Path java=Paths.get(
                System.getProperty("java.home"),"bin",
                System.getProperty("os.name").toLowerCase()
                    .contains("win")?"java.exe":"java"
            );
            Process child=new ProcessBuilder(
                java.toString(),"-cp",
                System.getProperty("java.class.path"),
                R91OfflineRealPvPDamageOutcomeGapTest.class.getName(),
                "--child-read",root.toString(),id().toString(),
                expected.name()
            ).redirectErrorStream(true).start();
            if(!child.waitFor(30L,TimeUnit.SECONDS)){
                child.destroyForcibly();
                throw new AssertionError(
                    "R91 separate-JVM read-only assessment timeout"
                );
            }
            String stdout=new String(
                child.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
            );
            require(child.exitValue()==0&&
                    stdout.contains(
                        "R91_FRESH_JVM_DAMAGE_OUTCOME_GAP_QUARANTINED"),
                "R91 child reload failed "+stdout);
        }

        @Override public void close(){f.close();}
    }

    public static void main(String[] args)throws Exception{
        if(args.length>0){
            childRead(args);
            return;
        }
        genuineCompleteLethalAttack();
        hpAppliedNoEvidenceAndNoOutcome();
        hpAppliedEvidenceButNoOutcome();
        damagedAndAmbiguousWitnessRefused();
        offScopeAndDuplicateInstallDenied();
        System.out.println(
            "R91_OFFLINE_REAL_PVP_DAMAGE_OUTCOME_GAP_PASS "+
            "actualActiveAttackAndWorldActor=true "+
            "actualCanonicalHpZeroAndLifecycleDeath=true "+
            "normalTwoSemanticEventsAndHp134=true "+
            "preOutcomeFaultAfterHpNoEvidence=true "+
            "preOutcomeFaultAfterEvidenceNoEvents=true "+
            "sameObservedWitnessWithAndWithoutDelivery=true "+
            "realVictimAccountSnapshotHpZeroOnAllPaths=true "+
            "freshJvmAccountReloadAndWitnessReview=true "+
            "R90ReservationNotKillCommit=true "+
            "R91ObservationNotOutcomeCommit=true "+
            "sourceScopeAndInstallGuard=true "+
            "tamperAndAbandonedStageQuarantined=true "+
            "allRestartStatesRefuseReplayRollbackRewards=true "+
            "defaultLocalSessionUnchanged=true "+
            "noDurableRealCombatIdOrCrossStoreAtomicity=true"
        );
    }

    private static void genuineCompleteLethalAttack()
            throws Exception{
        try(Case c=new Case(
                R91OfflinePostDamageObservationJournal.Fault.NONE)){
            c.actualLethal();
            require(c.damage.observedForTest()==1&&
                    c.damage.refusedForTest()==0&&
                    c.damage.lastAttemptForTest().equals(c.id()),
                "R91 actual damage observation not recorded");
            review(c.root,c.id(),
                R91OfflinePostDamageObservationJournal.Status
                    .OBSERVED_QUARANTINED);
            c.saveAndReloadActualVictim();
            c.childCheck(
                R91OfflinePostDamageObservationJournal.Status
                    .OBSERVED_QUARANTINED
            );
        }
    }

    private static void hpAppliedNoEvidenceAndNoOutcome()
            throws Exception{
        try(Case c=new Case(
                R91OfflinePostDamageObservationJournal.Fault
                    .FAIL_AFTER_HP_BEFORE_WITNESS)){
            c.interruptAfterDamage();
            require(c.damage.observedForTest()==0&&
                    c.damage.refusedForTest()==1,
                "R91 before-witness failure wrote forensic record");
            review(c.root,c.id(),
                R91OfflinePostDamageObservationJournal.Status
                    .MISSING_BLOCKED);
            c.saveAndReloadActualVictim();
            c.childCheck(
                R91OfflinePostDamageObservationJournal.Status
                    .MISSING_BLOCKED
            );
        }
    }

    private static void hpAppliedEvidenceButNoOutcome()
            throws Exception{
        try(Case c=new Case(
                R91OfflinePostDamageObservationJournal.Fault
                    .FAIL_AFTER_WITNESS_BEFORE_OUTCOME)){
            c.interruptAfterDamage();
            require(c.damage.observedForTest()==1&&
                    c.damage.refusedForTest()==1,
                "R91 after-witness crash did not retain evidence");
            review(c.root,c.id(),
                R91OfflinePostDamageObservationJournal.Status
                    .OBSERVED_QUARANTINED);
            c.saveAndReloadActualVictim();
            c.childCheck(
                R91OfflinePostDamageObservationJournal.Status
                    .OBSERVED_QUARANTINED
            );
        }
    }

    private static void damagedAndAmbiguousWitnessRefused()
            throws Exception{
        try(Case c=new Case(
                R91OfflinePostDamageObservationJournal.Fault.NONE)){
            c.actualLethal();
            Path file=R91OfflinePostDamageObservationJournal
                .pathForTest(c.root,c.id());
            Files.write(file,"tamper".getBytes(StandardCharsets.UTF_8));
            review(c.root,c.id(),
                R91OfflinePostDamageObservationJournal.Status
                    .INDETERMINATE_BLOCKED);
            c.saveAndReloadActualVictim();
            c.childCheck(
                R91OfflinePostDamageObservationJournal.Status
                    .INDETERMINATE_BLOCKED);
        }
        try(Case c=new Case(
                R91OfflinePostDamageObservationJournal.Fault.NONE)){
            c.actualLethal();
            Path file=R91OfflinePostDamageObservationJournal
                .pathForTest(c.root,c.id());
            Path stage=c.root.resolve(
                "."+file.getFileName()+".r16-incomplete.stage"
            );
            Files.write(stage,"unpublished".getBytes(
                StandardCharsets.UTF_8
            ));
            review(c.root,c.id(),
                R91OfflinePostDamageObservationJournal.Status
                    .INDETERMINATE_BLOCKED);
            require(Files.exists(stage),
                "R91 forensic review silently removed staging evidence");
            c.saveAndReloadActualVictim();
            c.childCheck(
                R91OfflinePostDamageObservationJournal.Status
                    .INDETERMINATE_BLOCKED);
        }
    }

    private static void offScopeAndDuplicateInstallDenied()
            throws Exception{
        try(Case c=new Case(
                R91OfflinePostDamageObservationJournal.Fault.NONE)){
            try{
                c.f.bridge.installOfflinePostDamageForTest(c.damage);
                throw new AssertionError(
                    "R91 duplicate journal installation accepted"
                );
            }catch(IllegalStateException expected){
                require(expected.getMessage().contains(
                    "duplicate/late"),
                    "R91 duplicate gate failure mismatch");
            }
            try{
                c.f.bridge.afterCanonicalDamageBeforeOutcomesForTest(
                    c.f.victim,20L,
                    new PlayerLifecycleService.DamageResult(
                        9,9,9,0,true,false,20L,"forged"
                    )
                );
                throw new AssertionError(
                    "R91 off-scope fake damage was accepted"
                );
            }catch(IOException expected){
                require(expected.getMessage().contains(
                    "no active exact accepted source"),
                    "R91 off-world forged damage denial mismatch");
            }
            c.actualLethal();
            require(c.damage.observedForTest()==1&&
                    c.damage.refusedForTest()==0,
                "R91 refused off-scope attempt affected real source");
        }
    }

    private static void review(
        Path root,UUID id,
        R91OfflinePostDamageObservationJournal.Status expected
    ){
        require(R90OfflineAcceptedAttackJournal.review(
                    root,id
                ).status==R90OfflineAcceptedAttackJournal.Status
                    .RESERVED_QUARANTINED,
            "R91 lost original R90 accepted-attempt witness");
        R91OfflinePostDamageObservationJournal.Review r=
            R91OfflinePostDamageObservationJournal.review(
                root,root,id
            );
        require(r.status==expected&&
                !r.authoritativeHpCommit()&&
                !r.outcomePublished()&&
                !r.replayAuthorized()&&
                !r.rollbackAuthorized()&&
                !r.projectionCommitted()&&
                !r.rewardAuthorized()&&
                !r.loginAuthorized(),
            "R91 review claimed a non-existent committed outcome");
        if(expected==R91OfflinePostDamageObservationJournal.Status
                .OBSERVED_QUARANTINED)
            require(r.observedHpAfter==0&&r.observedDeath,
                "R91 observed genuine HP state mismatch");
    }

    private static void childRead(String[] args){
        require(args.length==4&&
                "--child-read".equals(args[0]),
            "R91 invalid fresh-JVM read invocation");
        Path root=Paths.get(args[1]);
        UUID id=UUID.fromString(args[2]);
        R91OfflinePostDamageObservationJournal.Status status=
            R91OfflinePostDamageObservationJournal.Status.valueOf(
                args[3]
            );
        review(root,id,status);
        PlayerRepository repo=new FilePlayerRepository(
            name->root.resolve("account-"+name+".properties")
        );
        try(World world=World.isolatedForTest(60_000L,repo)){
            PlayerSnapshot snapshot=world.persistence().load(PROFILE)
                .orElseThrow(()->new AssertionError(
                    "R91 child account file not found"
                ));
            require("0".equals(snapshot.value(
                    "skill."+PlayerState.HITPOINTS+".current"
                )),"R91 restarted JVM missed real saved HP mutation");
            require(!snapshot.values().containsKey(
                    "extension.combat-receipt.event-id"),
                "R91 source invented authenticated event ID");
            System.out.println(
                "R91_FRESH_JVM_DAMAGE_OUTCOME_GAP_QUARANTINED "+
                "witness="+status+
                " canonicalAccountHpZero=true"+
                " killDeliveryUnknown=true"+
                " deathDeliveryUnknown=true"+
                " projectionCommitUnknown=true"+
                " replay=false rollback=false rewards=false"
            );
        }catch(Exception failure){
            throw new AssertionError(
                "R91 fresh JVM account review failed",failure
            );
        }
    }

    private static void require(boolean yes,String message){
        if(!yes)throw new AssertionError(message);
    }

    private R91OfflineRealPvPDamageOutcomeGapTest(){}
}
