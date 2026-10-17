package spk.local;

/**
 * G20.1.2: from one truly finalized/settled NPC, test-only collectors
 * deliberately throw TWO distinct exception categories to prove the
 * fixed-size histogram rejects arbitrary type growth and never affects
 * installed World gameplay, canonical settlement or recipient grants.
 */
public final class G2012BoundedFailureKindsForeignAndOtherTest {
    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long gen=-1L;
        String name="g2012-genuine-settled-terminal";
        try{
            gen=world.registerPlayer(player,name);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            LocalMonsterSpawnerActivationRuntime graph=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService spawner=graph.service();
            spawner.openSession(
                name,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(name,0);
            spawner.activate(name,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime().spawnAndBind(name,name,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(npc!=null&&world.npcLifecycle().applyDamageOwned(
                npc.id,10,world.clock().tick()).newlyDied,
                "canonical real NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                graph.runtime().finalizeIfOwned(npc);
            check(terminal!=null&&terminal.status==
                MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.settlement!=null,
                "genuine canonical finalization and settlement");
            GroundItem drop=world.groundItems().findOwned(
                995,3088,3495,0,name);
            check(drop!=null&&drop.amount==1&&
                world.pvmRecords().receiptCount()==1L,
                "installed healthy player credit/ground item");
            long originalCredit=world.pvmRecords().snapshot(player).kills;

            for(int index=0;index<2;index++){
                final int sample=index;
                LocalLabSequentialTerminalStageTrace isolated=
                    new LocalLabSequentialTerminalStageTrace(world);
                LocalLabSequentialTerminalStageTrace.Capture capture=
                    isolated.begin(terminal);
                capture.g1Returned();
                boolean threw=false;
                try{
                    capture.run(
                        LocalLabSequentialTerminalStageTrace.Stage.G4,
                        ()->{
                            if(sample==0)
                                throw new IllegalArgumentException("test-only");
                            throw new UnsupportedOperationException("test-only");
                        }
                    );
                }catch(IllegalArgumentException ex){
                    threw=sample==0;
                }catch(UnsupportedOperationException ex){
                    threw=sample==1;
                }
                check(threw,"deliberate diagnostic-only exception");
                isolated.record(capture);

                LocalLabSequentialTerminalStageTrace.Snapshot snapshot=
                    isolated.snapshot();
                LocalLabTerminalFirstFailureTypeTally.Kind expected=
                    sample==0
                        ?LocalLabTerminalFirstFailureTypeTally.Kind.ILLEGAL_ARGUMENT
                        :LocalLabTerminalFirstFailureTypeTally.Kind.OTHER_EXCEPTION;
                check(snapshot.firstFailureTypes.count(
                    LocalLabTerminalFailureBoundaryTally.Boundary.G4,
                    expected)==1L&&
                    snapshot.failureBoundaries.count(
                        LocalLabTerminalFailureBoundaryTally.Boundary.G4)==1L&&
                    snapshot.observed==1L,
                    "bounded non-raw category at the correct first G4 boundary");
                LocalLabOriginFirstFailureKindTally.Snapshot typeOrigin=
                    snapshot.originFirstFailureKinds;
                LocalLabSettledDeathEnvelope.OriginStatus current=
                    LocalLabSettledDeathEnvelope.OriginStatus
                        .CURRENT_SPAWN_RECIPIENT;
                check(typeOrigin.count(
                        current,
                        LocalLabTerminalFailureBoundaryTally.Boundary.G4,
                        expected)==1L&&
                    !typeOrigin.hasDurableGrantAcknowledgement()&&
                    !typeOrigin.safeToAutomaticallyReplay(),
                    "two diagnostic-only exception kinds retain true origin");
                for(LocalLabTerminalFirstFailureTypeTally.Kind kind:
                        LocalLabTerminalFirstFailureTypeTally.Kind.values())
                    if(kind!=expected)
                        check(snapshot.firstFailureTypes.count(
                            LocalLabTerminalFailureBoundaryTally.Boundary.G4,
                            kind)==0L&&typeOrigin.count(
                                current,
                                LocalLabTerminalFailureBoundaryTally.Boundary.G4,
                                kind)==0L,
                            "no fabricated alternative exception category");

                // Replace only the third-dimension origin with an otherwise
                // category- and boundary-matching foreign cohort. An audit
                // must detect this mixed-epoch/foreign cross-tab even if the
                // old first-stage/type marginals still match.
                LocalLabOriginFirstFailureKindTally wrongOrigin=
                    new LocalLabOriginFirstFailureKindTally();
                wrongOrigin.acceptValidated(
                    LocalLabSettledDeathEnvelope.OriginStatus.SAME_NAME_REBOUND,
                    LocalLabTerminalFailureBoundaryTally.Boundary.G4,
                    expected);
                LocalLabTerminalEvidenceConsistencyAudit.Result mismatch=
                    LocalLabTerminalEvidenceConsistencyAudit.inspectComponents(
                        snapshot,LocalLabTerminalDeliverySummary.from(snapshot),
                        snapshot.originConsumerOutcomes,
                        snapshot.originFailureBoundaries,wrongOrigin.snapshot());
                check(mismatch.status==
                        LocalLabTerminalEvidenceConsistencyAudit.Status.DRIFT&&
                    !mismatch.findings.isEmpty()&&
                    !mismatch.hasDurableGrantAcknowledgement()&&
                    !mismatch.safeToAutomaticallyReplay(),
                    "foreign origin with identical typed marginal remains DRIFT");

                LocalLabTerminalEvidenceConsistencyAudit.Result audit=
                    LocalLabTerminalEvidenceConsistencyAudit.inspect(snapshot);
                check(audit.status==
                    LocalLabTerminalEvidenceConsistencyAudit.Status.CONSISTENT&&
                    audit.findings.isEmpty()&&
                    !audit.hasDurableGrantAcknowledgement()&&
                    !audit.safeToAutomaticallyReplay(),
                    "real settled event with test-only exception remains non-granting");
            }

            LocalLabTerminalFirstFailureTypeTally isolated=
                new LocalLabTerminalFirstFailureTypeTally();
            boolean badPairRejected=false;
            try{
                isolated.acceptValidated(
                    LocalLabTerminalFailureBoundaryTally.Boundary.NO_THROW_RECORDED,
                    LocalLabTerminalFirstFailureTypeTally.Kind.ILLEGAL_STATE);
            }catch(IllegalArgumentException expected){
                badPairRejected=true;
            }
            LocalLabOriginFirstFailureKindTally originInvalid=
                new LocalLabOriginFirstFailureKindTally();
            boolean tripleRejected=false;
            try{
                originInvalid.acceptValidated(
                    LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT,
                    LocalLabTerminalFailureBoundaryTally.Boundary
                        .NO_CURRENT_RECIPIENT,
                    LocalLabTerminalFirstFailureTypeTally.Kind.ILLEGAL_ARGUMENT);
            }catch(IllegalArgumentException expected){
                tripleRejected=true;
            }
            check(badPairRejected&&tripleRejected&&
                originInvalid.snapshot().count(
                    LocalLabSettledDeathEnvelope.OriginStatus.CURRENT_SPAWN_RECIPIENT,
                    LocalLabTerminalFailureBoundaryTally.Boundary.NO_CURRENT_RECIPIENT,
                    LocalLabTerminalFirstFailureTypeTally.Kind.ILLEGAL_ARGUMENT)==0L&&
                isolated.snapshot().count(
                    LocalLabTerminalFailureBoundaryTally.Boundary.NO_THROW_RECORDED,
                    LocalLabTerminalFirstFailureTypeTally.Kind.ILLEGAL_STATE)==0L,
                "impossible boundary/category triples rejected before mutation");

            check(world.groundItems().findOwned(
                    995,3088,3495,0,name).amount==1&&
                world.pvmRecords().receiptCount()==1L&&
                world.pvmRecords().snapshot(player).kills==originalCredit&&
                graph.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "diagnostic-only test callbacks cannot grant or resettle");
            System.out.println(
                "G2012_BOUNDED_FAILURE_KINDS_FOREIGN_OTHER_PASS"+
                " genuineSettledNpc=true"+
                " testOnlyIllegalArgument=true"+
                " testOnlyOtherException=true"+
                " inconsistentKindRejected=true"+
                " originalCreditAndSettlementUnchanged=true");
        }finally{
            if(gen>=0L&&world.players().owns(player,gen))
                world.unregisterPlayer(player,gen);
            world.close();
        }
    }

    private static void check(boolean yes,String description){
        if(!yes)throw new AssertionError("G20.1.2 "+description);
    }
    private G2012BoundedFailureKindsForeignAndOtherTest(){}
}
