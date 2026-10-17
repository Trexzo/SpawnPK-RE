package spk.local;

/**
 * G18.9.3: installed terminal observer is invoked once per genuinely
 * finalized NPC. Re-presenting the SAME finalized result to its own
 * correctly bound World must not double-run stage G15 or diagnostics.
 * A separate newly spawned genuine NPC must still progress normally.
 */
public final class G1893OptinTerminalExactEventReplayVetoTest {
    private static final String USER="g1893-one-event";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(player,USER);
            LocalLabTaskScrollRuntime g15=world.localLabTaskScrolls();
            check(g15.assign(USER,world.clock().tick()).assignedNow,
                "real G15 objective active");

            LocalLabTerminalStageIsolation dispatcher=
                new LocalLabTerminalStageIsolation();
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(
                    world,dispatcher);
            MonsterSpawnerService service=installed.service();
            service.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            service.selectRow(USER,0);
            service.activate(USER,1);
            WorldNpc first=installed.runtime().spawnAndBind(
                USER,USER,3088,3495,0).spawn.combat.spawn.npc;

            check(world.npcLifecycle().applyDamage(
                    first.id,99,9401L).newlyDied,
                "first actual NPC death");
            MonsterSpawnerPvmRuntime.FinalizeResult eventA=
                installed.runtime().finalizeIfOwned(first);
            check(eventA!=null&&eventA.sourceWorld==world&&
                    eventA.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    eventA.settlement!=null&&
                    eventA.settlement.groundItems.size()==1&&
                    world.pvmRecords().receiptCount()==1&&
                    g15.active(USER).objective.progress==1L&&
                    dispatcher.diagnosticSnapshot().observedCount==1L,
                "installed observer delivers first canonical event once");

            for(int repeat=0;repeat<2;repeat++){
                boolean veto=false;
                try{
                    dispatcher.deliver(world,eventA);
                }catch(IllegalStateException expected){
                    veto="TERMINAL_STAGE_EVENT_ALREADY_DELIVERED_NO_REPLAY"
                        .equals(expected.getMessage());
                }
                check(veto&&
                        g15.active(USER).objective.progress==1L&&
                        world.pvmRecords().receiptCount()==1&&
                        dispatcher.diagnosticSnapshot().observedCount==1L&&
                        dispatcher.diagnosticSnapshot().stage(
                            LocalLabTerminalStageIsolation.Stage.G15)
                            .returned==1L,
                    "same terminal cannot replay callback/report "+repeat);
            }

            world.events().runDue(
                9401L+G1MonsterSpawnerPvmLoopService.RESPAWN_DELAY_TICKS);
            MonsterSpawnerService.SessionSnapshot session=
                service.getSession(USER);
            check(session!=null&&session.spawnedNpcIds.size()==1,
                "genuine respawn after canonical finalization");
            WorldNpc second=world.npcs().byId(
                session.spawnedNpcIds.get(0));
            check(second!=null&&!second.id.equals(first.id)&&
                    world.npcLifecycle().applyDamage(
                        second.id,99,9411L).newlyDied,
                "second genuine newly allocated NPC dies");
            MonsterSpawnerPvmRuntime.FinalizeResult eventB=
                installed.runtime().finalizeIfOwned(second);
            check(eventB!=null&&eventB!=eventA&&
                    eventB.sourceWorld==world&&
                    eventB.status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                    g15.active(USER).objective.progress==2L&&
                    world.pvmRecords().receiptCount()==2&&
                    dispatcher.diagnosticSnapshot().observedCount==2L&&
                    dispatcher.diagnosticSnapshot().stage(
                        LocalLabTerminalStageIsolation.Stage.G15)
                        .returned==2L&&
                    installed.runtime().finalizeIfOwned(first).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED,
                "new event still delivers after first claimed once");

            int coins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(coins==2,"two ground settlements, no replay grants");
            System.out.println(
                "G1893_OPTIN_TERMINAL_EXACT_EVENT_REPLAY_VETO_PASS"+
                " firstRealDeath=true"+
                " sameFinalizedJavaEventRejectedTwice=true"+
                " laterDistinctRealNpcStillCredits=true"+
                " g15ObjectiveProgressTwoNotFour=true"+
                " g1ReceiptsTwo=true"+
                " groundCoinsTwo=true"+
                " stageReportsTwo=true"+
                " durableAck=false"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    private static void check(boolean result,String why){
        if(!result)throw new AssertionError("G18.9.3 "+why);
    }

    private G1893OptinTerminalExactEventReplayVetoTest(){}
}
