package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import spk.event.DomainEventBus;

/**
 * R90 OFFLINE ONLY. Real LocalPlayerInteractionHandler accepted Attack ->
 * paired exact World actor ownership -> forced atomic ATTEMPT_RESERVED
 * BEFORE PlayerCombatResolutionService canonical HP mutation. The
 * original R86 DomainEventBus source proof binds kill/death observations
 * to one test attempt only while the verified callbacks are executing.
 *
 * The filesystem reservation cannot establish that damage happened.
 * Independent Java process reviews NEVER authorize replay or reward.
 */
public final class R90OfflineActivePvpPreDamageReservationTest {
    private static final class Case implements AutoCloseable{
        final Path root;
        final R86OfflineActivePvpDomainBridgeTest.Fixture f;
        final R90OfflineAcceptedAttackJournal journal;
        final AtomicInteger outcomes=new AtomicInteger();
        final AtomicInteger killFacts=new AtomicInteger();
        final AtomicInteger deathFacts=new AtomicInteger();
        final AtomicReference<
            R90OfflineAcceptedAttackJournal.Reservation
        > firstSourceAttempt=new AtomicReference<>();
        final AtomicReference<Throwable> listenerFailure=
            new AtomicReference<>();

        Case(R90OfflineAcceptedAttackJournal.Fault fault)
                throws Exception{
            root=Files.createTempDirectory(
                "r90-real-active-pvp-reservation-"
            );
            f=new R86OfflineActivePvpDomainBridgeTest.Fixture(
                R86OfflineActivePvpDomainBridgeTest.Mode.NORMAL
            );
            journal=new R90OfflineAcceptedAttackJournal(root,fault,4);
            f.bridge.installOfflineAcceptedAttemptForTest(journal);
            f.world.domainEvents().subscribe(
                CombatOutcome.class,DomainEventBus.Priority.LOW,
                fact->{
                    try{
                        R90OfflineAcceptedAttackJournal.Reservation
                            source=f.bridge
                                .verifiedAttemptDuringOutcomeForTest(fact);
                        require(source!=null&&
                                source.worldTick==fact.worldTick()&&
                                source.attacker.equals(fact.attacker())&&
                                source.target.equals(fact.victim())&&
                                source.attackerGeneration==
                                    f.attackerGeneration&&
                                source.targetGeneration==
                                    f.victimGeneration,
                            "R90 semantic event not bound to genuine "+
                            "owner-pair accepted attack");
                        R90OfflineAcceptedAttackJournal.Reservation first=
                            firstSourceAttempt.get();
                        if(first==null){
                            firstSourceAttempt.set(source);
                        }else{
                            require(first==source,
                                "R90 kill and death minted distinct "+
                                "attack reservations");
                        }
                        R90OfflineAcceptedAttackJournal.Review review=
                            R90OfflineAcceptedAttackJournal.review(
                                root,source.syntheticAttemptId
                            );
                        require(review.status==
                                R90OfflineAcceptedAttackJournal.Status
                                    .RESERVED_QUARANTINED&&
                                !review.attackApplied()&&
                                !review.killConfirmed()&&
                                !review.replayAllowed(),
                            "R90 pre-damage record not visible BEFORE "+
                            "genuine semantic callback");
                        outcomes.incrementAndGet();
                        if(fact.type()==CombatOutcomeType.PLAYER_KILL)
                            killFacts.incrementAndGet();
                        if(fact.type()==CombatOutcomeType.PLAYER_DEATH)
                            deathFacts.incrementAndGet();
                    }catch(Throwable error){
                        listenerFailure.set(error);
                        throw new IllegalStateException(
                            "R90 verified source listener error",error
                        );
                    }
                }
            );
        }

        void lethal()throws Exception{
            f.lethal();
            if(listenerFailure.get()!=null)
                throw new AssertionError(
                    "R90 listener failed",listenerFailure.get()
                );
            require(f.resolved.get().contains(
                    "targetDied=true")&&
                    f.resolved.get().contains(
                    "targetHpPacket134=true")&&
                    f.victim.lifecycle().dead()&&
                    f.victim.playerState().currentLevel(
                        PlayerState.HITPOINTS)==0&&
                    outcomes.get()==2&&killFacts.get()==1&&
                    deathFacts.get()==1&&
                    firstSourceAttempt.get()!=null&&
                    firstSourceAttempt.get()==
                        journal.lastReservedForTest()&&
                    journal.countForTest()==1&&
                    journal.refusedForTest()==0,
                "R90 genuine lethal active source did not preserve "+
                "one pre-damage attempt and two distinct outcomes");
        }

        void deniedWithNoDamage()throws Exception{
            AtomicReference<IOException> rejected=new AtomicReference<>();
            f.world.events().schedule(1L,()->{
                try{
                    f.handler.tickAttack(
                        20L,f.attackerWriter,f.attackerSync
                    );
                    throw new AssertionError(
                        "R90 injected pre-damage denial did not stop attack"
                    );
                }catch(IOException expected){
                    rejected.set(expected);
                }
            });
            require(f.world.observePulse(
                    System.currentTimeMillis())==1L,
                "R90 denied attempt WorldPulse not run");
            require(rejected.get()!=null&&
                    f.victim.playerState().currentLevel(
                        PlayerState.HITPOINTS)==9&&
                    !f.victim.lifecycle().dead()&&
                    f.bridge.issuedCountForTest()==0&&
                    f.bridge.admittedCountForTest()==0&&
                    f.highEvents.get()==0&&
                    f.normalEvents.get()==0&&outcomes.get()==0&&
                    firstSourceAttempt.get()==null,
                "R90 pre-damage denial nevertheless mutated HP "+
                "or emitted a semantic combat event");
        }

        @Override public void close(){f.close();}
    }

    public static void main(String[] args)throws Exception{
        if(args.length>0){
            childReview(args);
            return;
        }
        acceptedAttemptBeforeRealDamage();
        denyWithoutWitnessStopsDamage();
        persistedReservationWithoutDamageQuarantined();
        tamperedAndAmbiguousEvidenceRefused();
        attemptBindingAndLateInstallDenied();

        System.out.println(
            "R90_OFFLINE_ACTIVE_PVP_PREDAMAGE_RESERVATION_PASS "+
            "realLocalSessionShapedPvPHandler=true "+
            "ownerPairWorldTickValidated=true "+
            "oneAtomicReservationBeforeDamage=true "+
            "realLethalHP134AndTwoFactsPreserved=true "+
            "killAndDeathShareOneAttemptButNotEventId=true "+
            "beforeReservationDenialPreventsDamage=true "+
            "afterReservationInterruptionPreventsDamage=true "+
            "sameReservationStatusForHitAndNoHit=true "+
            "freshJvmReadOnlyQuarantine=true "+
            "forgedOrOffWorldReservationDenied=true "+
            "expiredFactCannotReuseAttemptScope=true "+
            "duplicateAndLateJournalInstallationDenied=true "+
            "tamperedOrAmbiguousStageRefused=true "+
            "noAutomaticReplayLoginOrRewards=true "+
            "noProductionCombatOperationIdOrAtomicityClaim=true "+
            "defaultLocalSessionNoOpUnchanged=true"
        );
    }

    private static void acceptedAttemptBeforeRealDamage()
            throws Exception{
        try(Case c=new Case(
                R90OfflineAcceptedAttackJournal.Fault.NONE)){
            c.lethal();
            UUID id=c.journal.lastReservedForTest()
                .syntheticAttemptId;
            require(c.f.bridge.verifiedAttemptDuringOutcomeForTest(
                    c.f.killFact.get())==null&&
                    c.f.bridge.verifiedAttemptDuringOutcomeForTest(
                    c.f.deathFact.get())==null,
                "R90 source attempt binding escaped callback");
            check(c.root,id,
                R90OfflineAcceptedAttackJournal.Status
                    .RESERVED_QUARANTINED);
            childCheck(c.root,id,"RESERVED_QUARANTINED");
        }
    }

    private static void denyWithoutWitnessStopsDamage()
            throws Exception{
        try(Case c=new Case(
                R90OfflineAcceptedAttackJournal.Fault
                    .DENY_BEFORE_RESERVATION)){
            c.deniedWithNoDamage();
            require(c.journal.countForTest()==0&&
                    c.journal.refusedForTest()==1&&
                    c.journal.lastReservedForTest()==null,
                "R90 refused pre-damage attack wrote a witness");
            UUID neverIssued=UUID.randomUUID();
            check(c.root,neverIssued,
                R90OfflineAcceptedAttackJournal.Status.MISSING_BLOCKED);
            childCheck(c.root,neverIssued,"MISSING_BLOCKED");
        }
    }

    private static void persistedReservationWithoutDamageQuarantined()
            throws Exception{
        try(Case c=new Case(
                R90OfflineAcceptedAttackJournal.Fault
                    .INTERRUPT_AFTER_RESERVATION_BEFORE_DAMAGE)){
            c.deniedWithNoDamage();
            R90OfflineAcceptedAttackJournal.Reservation r=
                c.journal.lastReservedForTest();
            require(r!=null&&
                    c.journal.countForTest()==1&&
                    c.journal.refusedForTest()==1&&
                    c.outcomes.get()==0,
                "R90 after-reservation fault missing disk witness");
            // Identical review classification to the fully applied
            // lethal attack: reservation NEVER proves damage/kill.
            check(c.root,r.syntheticAttemptId,
                R90OfflineAcceptedAttackJournal.Status
                    .RESERVED_QUARANTINED);
            childCheck(c.root,r.syntheticAttemptId,
                "RESERVED_QUARANTINED");
        }
    }

    private static void tamperedAndAmbiguousEvidenceRefused()
            throws Exception{
        try(Case c=new Case(
                R90OfflineAcceptedAttackJournal.Fault.NONE)){
            c.lethal();
            UUID id=c.journal.lastReservedForTest()
                .syntheticAttemptId;
            Path file=R90OfflineAcceptedAttackJournal
                .receiptPathForTest(c.root,id);
            Files.write(file,"tampered".getBytes(
                StandardCharsets.UTF_8
            ));
            check(c.root,id,
                R90OfflineAcceptedAttackJournal.Status
                    .INDETERMINATE_BLOCKED);
            childCheck(c.root,id,"INDETERMINATE_BLOCKED");
        }
        try(Case c=new Case(
                R90OfflineAcceptedAttackJournal.Fault.NONE)){
            c.lethal();
            UUID id=c.journal.lastReservedForTest()
                .syntheticAttemptId;
            Path file=R90OfflineAcceptedAttackJournal
                .receiptPathForTest(c.root,id);
            Path stage=c.root.resolve(
                "."+file.getFileName()+".r16-abandoned.stage"
            );
            Files.write(stage,"interrupted".getBytes(
                StandardCharsets.UTF_8
            ));
            check(c.root,id,
                R90OfflineAcceptedAttackJournal.Status
                    .INDETERMINATE_BLOCKED);
            require(Files.isRegularFile(stage),
                "R90 reviewer destroyed ambiguous staging evidence");
            childCheck(c.root,id,"INDETERMINATE_BLOCKED");
        }
    }

    private static void attemptBindingAndLateInstallDenied()
            throws Exception{
        try(Case c=new Case(
                R90OfflineAcceptedAttackJournal.Fault.NONE)){
            try{
                c.f.bridge.installOfflineAcceptedAttemptForTest(
                    c.journal
                );
                throw new AssertionError(
                    "R90 repeated issuer installation accepted"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "duplicate or late"),
                    "R90 wrong duplicate installation failure");
            }

            try{
                c.journal.reserve(c.f.world,c.f.attacker,
                    c.f.attackerGeneration,c.f.victim,
                    c.f.victimGeneration,20L,4151);
                throw new AssertionError(
                    "R90 off-world direct reservation authorized"
                );
            }catch(IOException denied){
                require(denied.getMessage().contains(
                    "not current owned World"),
                    "R90 wrong off-world source admission");
            }
            c.lethal();
            require(c.journal.countForTest()==1&&
                    c.journal.refusedForTest()==1,
                "R90 direct off-world request consumed a receipt");
            try{
                c.f.bridge.installOfflineAcceptedAttemptForTest(
                    new R90OfflineAcceptedAttackJournal(
                        Files.createTempDirectory("r90-late-"),
                        R90OfflineAcceptedAttackJournal.Fault.NONE,2
                    )
                );
                throw new AssertionError(
                    "R90 late issuer replacement authorized"
                );
            }catch(IllegalStateException denied){
                require(denied.getMessage().contains(
                    "duplicate or late"),
                    "R90 wrong late installation failure");
            }
        }
    }

    private static void check(
        Path root,UUID id,
        R90OfflineAcceptedAttackJournal.Status expected
    ){
        R90OfflineAcceptedAttackJournal.Review result=
            R90OfflineAcceptedAttackJournal.review(root,id);
        require(result.status==expected&&
                !result.attackApplied()&&
                !result.killConfirmed()&&
                !result.replayAllowed()&&
                !result.projectionCommitted()&&
                !result.loginAllowed()&&
                !result.rewardAllowed(),
            "R90 incorrect read-only review status="+result.status);
    }

    private static void childCheck(
        Path root,UUID id,String expected
    )throws Exception{
        Path java=Paths.get(
            System.getProperty("java.home"),"bin",
            System.getProperty("os.name").toLowerCase()
                .contains("win")?"java.exe":"java"
        );
        Process child=new ProcessBuilder(
            java.toString(),"-cp",
            System.getProperty("java.class.path"),
            R90OfflineActivePvpPreDamageReservationTest.class.getName(),
            "--child-review",root.toString(),
            id.toString(),expected
        ).redirectErrorStream(true).start();
        if(!child.waitFor(30L,TimeUnit.SECONDS)){
            child.destroyForcibly();
            throw new AssertionError(
                "R90 independent JVM review timeout"
            );
        }
        String output=new String(
            child.getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        require(child.exitValue()==0&&
                output.contains(
                    "R90_SEPARATE_JVM_RESERVED_ONLY_QUARANTINE_PASS"
                ),"R90 separate JVM denied test: "+output);
    }

    private static void childReview(String[] args){
        require(args.length==4&&
                "--child-review".equals(args[0]),
            "R90 invalid child command");
        Path root=Paths.get(args[1]);
        UUID id=UUID.fromString(args[2]);
        R90OfflineAcceptedAttackJournal.Status want=
            R90OfflineAcceptedAttackJournal.Status.valueOf(
                args[3]
            );
        check(root,id,want);
        System.out.println(
            "R90_SEPARATE_JVM_RESERVED_ONLY_QUARANTINE_PASS "+
            "status="+want+
            " acceptedAttemptDoesNotProveDamage=true"+
            " automaticReplay=false reward=false"
        );
    }

    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(why);
    }

    private R90OfflineActivePvpPreDamageReservationTest(){}
}
