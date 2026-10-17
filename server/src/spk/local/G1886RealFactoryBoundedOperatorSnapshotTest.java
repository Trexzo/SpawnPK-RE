package spk.local;

import java.util.ArrayList;
import java.util.List;

/**
 * G18.8.6 — 130 genuinely distinct installed real-NPC terminals must
 * retain only 128 read-only operator-facing diagnostics. Dropping old
 * diagnostic entries must not alter G1 receipts, canonical NPC identity,
 * or settled coins. A second World has no shared report history.
 */
public final class G1886RealFactoryBoundedOperatorSnapshotTest {
    private static final String USER="g1886-operator-bound";
    private static final int COUNT=
        LocalLabTerminalStageIsolation.MAX_REPORTS+2;
    private static final String PROPERTY=
        LocalLabMonsterSpawnerProvisioning
            .ISOLATE_TERMINAL_STAGES_PROPERTY;

    public static void main(String[] args)throws Exception{
        String prior=System.getProperty(PROPERTY);
        World world=null;
        World secondWorld=null;
        WorldPlayer owner=new WorldPlayer();
        long generation=-1L;
        try{
            System.setProperty(PROPERTY,"true");
            world=World.isolatedForTest(60_000L);
            generation=world.registerPlayer(owner,USER);
            LocalMonsterSpawnerActivationRuntime installed=
                LocalLabMonsterSpawnerProvisioning.create(world);
            check(installed.terminalDiagnosticSnapshot()!=null,
                "real public factory enabled diagnostics on first World");
            System.clearProperty(PROPERTY);

            MonsterSpawnerService spawner=installed.service();
            spawner.openSession(
                USER,LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(USER,0);
            spawner.activate(USER,1);
            MonsterSpawnerPvmRuntime.SpawnResult firstSpawn=
                installed.runtime().spawnAndBind(
                    USER,USER,3088,3495,0);
            WorldNpc npc=firstSpawn.spawn.combat.spawn.npc;
            WorldNpc veryFirst=npc;
            List<EntityId> uniqueIds=new ArrayList<EntityId>();

            for(int index=0;index<COUNT;index++){
                long tick=4101L+10L*index;
                EntityId id=npc.id;
                check(!uniqueIds.contains(id),
                    "NPC identity not reused within same World "+index);
                uniqueIds.add(id);
                check(world.npcLifecycle().applyDamage(
                        id,99,tick).newlyDied,
                    "real damage and NPC death "+index);
                MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                    installed.runtime().finalizeIfOwned(npc);
                check(terminal!=null&&
                        terminal.status==
                            MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                        terminal.settlement!=null&&
                        terminal.settlement.groundItems.size()==1&&
                        terminal.settlement.groundItems.get(0)
                            .settledAmount==1&&
                        installed.runtime().get(id)==null,
                    "one canonical finalize and drop settlement "+index);

                LocalLabTerminalStageIsolation.DiagnosticSnapshot view=
                    installed.terminalDiagnosticSnapshot();
                check(view!=null&&
                        view.observedCount==index+1L&&
                        view.evictedCount==
                            Math.max(0,index+1-LocalLabTerminalStageIsolation
                                .MAX_REPORTS)&&
                        view.recentReports.size()==
                            Math.min(index+1,LocalLabTerminalStageIsolation
                                .MAX_REPORTS),
                    "bounded detached operator snapshot "+index);

                if(index+1<COUNT){
                    world.events().runDue(
                        tick+G1MonsterSpawnerPvmLoopService
                            .RESPAWN_DELAY_TICKS);
                    MonsterSpawnerService.SessionSnapshot session=
                        spawner.getSession(USER);
                    check(session!=null&&
                            session.spawnedNpcIds.size()==1,
                        "real G1 respawn schedule "+index);
                    npc=world.npcs().byId(
                        session.spawnedNpcIds.get(0));
                    check(npc!=null&&!npc.id.equals(id),
                        "new canonical NPC from real respawn "+index);
                }
            }
            LocalLabTerminalStageIsolation.DiagnosticSnapshot full=
                installed.terminalDiagnosticSnapshot();
            check(full.observedCount==COUNT&&
                    full.evictedCount==COUNT-
                        LocalLabTerminalStageIsolation.MAX_REPORTS&&
                    full.recentReports.size()==
                        LocalLabTerminalStageIsolation.MAX_REPORTS&&
                    full.recentReports.get(0).npcId.equals(
                        uniqueIds.get(COUNT-
                            LocalLabTerminalStageIsolation.MAX_REPORTS))&&
                    full.recentReports.get(
                        full.recentReports.size()-1).npcId.equals(
                            uniqueIds.get(COUNT-1)),
                "first two operator reports gone, latest 128 retained");

            int coins=world.groundItems().snapshot().stream()
                .filter(item->item.itemId==995&&
                    USER.equals(item.owner))
                .mapToInt(item->item.amount).sum();
            check(coins==COUNT&&
                    world.pvmRecords().receiptCount()==COUNT&&
                    world.pvmRecords().snapshot(owner).kills==COUNT&&
                    installed.runtime().finalizeIfOwned(veryFirst).status==
                        MonsterSpawnerPvmRuntime.FinalizeStatus.NOT_OWNED&&
                    installed.terminalDiagnosticSnapshot()
                        .observedCount==COUNT,
                "evicted reports never replay or delete rewards");

            System.setProperty(PROPERTY,"true");
            secondWorld=World.isolatedForTest(60_000L);
            LocalMonsterSpawnerActivationRuntime second=
                LocalLabMonsterSpawnerProvisioning.create(secondWorld);
            LocalLabTerminalStageIsolation.DiagnosticSnapshot fresh=
                second.terminalDiagnosticSnapshot();
            check(fresh!=null&&fresh.observedCount==0L&&
                    fresh.evictedCount==0L&&
                    fresh.recentReports.isEmpty()&&
                    full.observedCount==COUNT,
                "independent World has its own empty report container");

            boolean readOnly=false;
            try{
                full.recentReports.remove(0);
            }catch(UnsupportedOperationException expected){
                readOnly=true;
            }
            check(readOnly&&
                    installed.terminalDiagnosticSnapshot()
                        .recentReports.size()==
                            LocalLabTerminalStageIsolation.MAX_REPORTS,
                "external diagnostic snapshot cannot mutate live journal");

            System.out.println(
                "G1886_REAL_FACTORY_BOUNDED_OPERATOR_SNAPSHOT_PASS"+
                " actualNpcDeaths="+COUNT+
                " g1Receipts="+world.pvmRecords().receiptCount()+
                " actualCoinSettlements="+coins+
                " reportsRetained="+full.recentReports.size()+
                " reportsEvicted="+full.evictedCount+
                " secondWorldIsolated=true"+
                " noOldNpcReplay=true"+
                " noWritableJournalHandle=true"+
                " durableOutbox=false"+
                " originalSpawnpkPolicy=false");
        }finally{
            if(secondWorld!=null)
                secondWorld.close();
            if(world!=null){
                if(generation>=0L&&world.players().owns(owner,generation))
                    world.unregisterPlayer(owner,generation);
                world.close();
            }
            if(prior==null)
                System.clearProperty(PROPERTY);
            else
                System.setProperty(PROPERTY,prior);
        }
    }

    private static void check(boolean result,String message){
        if(!result)throw new AssertionError("G18.8.6 "+message);
    }

    private G1886RealFactoryBoundedOperatorSnapshotTest(){}
}
