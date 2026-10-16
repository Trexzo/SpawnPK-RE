package spk.local;

/**
 * G19.6.5: World identity is necessary but not sufficient to accept an
 * invocation-local stage capture. Two collectors for the SAME World must
 * never attribute the other collector's terminal observation as their own.
 * This is diagnostic isolation, never reward/grant authority.
 */
public final class G1965SameWorldSequentialTraceCaptureOwnerTest {
    public static void main(String[] args)throws Exception {
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        final String name="g1965-same-world-trace-owner";
        try {
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(false,100,3087,3495,0);
            LocalMonsterSpawnerActivationRuntime graph=
                LocalLabMonsterSpawnerProvisioning.create(world,null);
            MonsterSpawnerService service=graph.service();
            service.openSession(name,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(name,0);
            service.activate(name,1);
            MonsterSpawnerPvmRuntime.SpawnResult spawned=
                graph.runtime().spawnAndBind(name,name,3088,3495,0);
            WorldNpc npc=spawned.spawn.combat.spawn.npc;
            check(world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                "real canonical NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                graph.runtime().finalizeIfOwned(npc);
            check(terminal.status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    terminal.settlement!=null&&terminal.sourceWorld==world&&
                    graph.sequentialTerminalDiagnosticSnapshot().observed==1L,
                "real installed observer and actual ground settlement");

            LocalLabSequentialTerminalStageTrace a=
                new LocalLabSequentialTerminalStageTrace(world);
            LocalLabSequentialTerminalStageTrace b=
                new LocalLabSequentialTerminalStageTrace(world);
            LocalLabSequentialTerminalStageTrace.Capture capture=a.begin(terminal);
            String rejection=null;
            try {
                b.record(capture);
            }catch(IllegalStateException expected){
                rejection=expected.getMessage();
            }
            check("SEQUENTIAL_TERMINAL_STAGE_CAPTURE_OWNER_MISMATCH"
                    .equals(rejection),
                "same-World foreign collector cannot accept capture");
            check(b.snapshot().observed==0L&&
                    b.snapshot().recent.isEmpty()&&
                    b.snapshot().outcomeCount(
                        LocalLabSequentialTerminalStageTrace.Stage.G4,
                        LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED
                    )==0L,
                "rejected foreign capture cannot affect history or totals");
            a.record(capture);
            check(a.snapshot().g1OutcomeCount(
                    LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)
                    ==1L&&
                b.snapshot().g1OutcomeCount(
                    LocalLabSequentialTerminalStageTrace.G1Outcome.NOT_OBSERVED)
                    ==0L,
                "test collector never fabricates a G1 callback and foreign rejection cannot affect totals");
            check(a.snapshot().observed==1L&&
                    a.snapshot().recent.size()==1&&
                    a.snapshot().outcomeCount(
                        LocalLabSequentialTerminalStageTrace.Stage.G4,
                        LocalLabSequentialTerminalStageTrace.Outcome.NOT_REACHED
                    )==1L,
                "original collector alone consumes its detached diagnostic capture");
            String repeated=null;
            try {
                a.record(capture);
            }catch(IllegalStateException expected){
                repeated=expected.getMessage();
            }
            check("SEQUENTIAL_TERMINAL_STAGE_CAPTURE_ALREADY_RECORDED"
                    .equals(repeated)&&a.snapshot().observed==1L,
                "captured observation cannot be recorded twice");
            check(graph.runtime().finalizeIfOwned(npc).status==
                    MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    world.groundItems().findOwned(
                        995,3088,3495,0,name).amount==1,
                "diagnostics never re-finalize or re-settle");
            System.out.println("G1965_SAME_WORLD_STAGE_CAPTURE_OWNER_PASS"+
                " genuineSettledTerminal=true"+
                " sameWorldCollectorIsolation=true"+
                " failedForeignRecordDoesNotConsumeCapture=true"+
                " noGrantOrReplay=true");
        }finally {
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G19.6.5 "+why);
    }
    private G1965SameWorldSequentialTraceCaptureOwnerTest(){}
}
