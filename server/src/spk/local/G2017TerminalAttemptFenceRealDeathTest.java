package spk.local;

/**
 * G20.1.7 — actual canonical NPC terminal and World/recipient evidence.
 * The fence is opt-in, process-only and does NOT dispatch any consumer.
 */
public final class G2017TerminalAttemptFenceRealDeathTest {
    static final class Fixture implements AutoCloseable {
        final World world=World.isolatedForTest(60_000L);
        final WorldPlayer player=new WorldPlayer();
        final String name="g2017-fence-recipient";
        final String spawnerOwner="g2017-fence-unregistered-session";
        final LocalMonsterSpawnerActivationRuntime graph;
        final MonsterSpawnerService spawner;
        final long generation;
        int settled;

        Fixture()throws Exception{
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(false,100,3087,3495,0);
            graph=LocalLabMonsterSpawnerProvisioning.create(world,null);
            spawner=graph.service();
            spawner.openSession(spawnerOwner,
                LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
            spawner.selectRow(spawnerOwner,0);
        }

        MonsterSpawnerPvmRuntime.FinalizeResult settle()throws Exception{
            if(spawner.getSession(spawnerOwner)==null){
                spawner.openSession(spawnerOwner,
                    LocalLabMonsterSpawnerProvisioning.SESSION_AUTHORITY);
                spawner.selectRow(spawnerOwner,0);
            }
            spawner.activate(spawnerOwner,1);
            MonsterSpawnerPvmRuntime.SpawnResult started=
                graph.runtime().spawnAndBind(spawnerOwner,name,3088,3495,0);
            WorldNpc npc=started.spawn.combat.spawn.npc;
            check(npc!=null&&world.npcLifecycle().applyDamageOwned(
                    npc.id,10,world.clock().tick()).newlyDied,
                "real canonical lethal NPC");
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=
                graph.runtime().finalizeIfOwned(npc);
            check(terminal!=null&&terminal.status==
                MonsterSpawnerPvmRuntime.FinalizeStatus.FINALIZED&&
                terminal.sourceWorld==world&&terminal.settlement!=null&&
                terminal.finalization!=null&&
                graph.runtime().get(npc.id)==null,
                "genuine finalized, retired, ground-settled NPC");
            settled++;
            GroundItem drop=world.groundItems().findOwned(
                995,3088,3495,0,name);
            check(drop!=null&&drop.amount==settled,
                "exactly one canonical ground drop per terminal");
            return terminal;
        }

        @Override public void close()throws Exception{
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        try(Fixture f=new Fixture()){
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=f.settle();
            LocalLabExperimentalTerminalAttemptFence fence=
                new LocalLabExperimentalTerminalAttemptFence(f.world);
            LocalLabExperimentalTerminalAttemptFence.Attempt first=null;
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:
                LocalLabTerminalDeliveryAssessment.Consumer.values()){
                LocalLabExperimentalTerminalAttemptFence.Attempt token=
                    fence.reserve(terminal,consumer);
                check(token.npcId.equals(terminal.finalization.npcId)&&
                    token.deathTick==terminal.finalization.deathTick&&
                    token.originStatus==
                        LocalLabSettledDeathEnvelope.OriginStatus
                            .CURRENT_SPAWN_RECIPIENT&&
                    token.consumer==consumer,
                    "real settled envelope only");
                if(first==null)first=token;
                else if(consumer==
                        LocalLabTerminalDeliveryAssessment.Consumer.G14)
                    fence.threw(token,new IllegalStateException("fixture-only"));
                else
                    fence.returned(token);
                String rejected=null;
                try{fence.reserve(terminal,consumer);}
                catch(IllegalStateException expected){
                    rejected=expected.getMessage();
                }
                check("TERMINAL_ATTEMPT_DUPLICATE_CONSUMER".equals(rejected),
                    "second reservation of SAME genuine NPC/stage denied");
            }
            LocalLabExperimentalTerminalAttemptFence.Snapshot before=
                fence.snapshot();
            check(before.reserved==6&&!before.capacityExhausted&&
                before.entries.size()==6&&
                before.entries.get(0).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RESERVED_UNCERTAIN&&
                before.entries.get(2).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .THREW_MUTATION_UNCERTAIN&&
                !before.hasDurableGrantAcknowledgement()&&
                !before.safeToAutomaticallyReplay(),
                "one reserved/uncertain G1 and actual all-stage dry-run tokens");
            fence.returned(first);
            check(before.entries.get(0).observation==
                LocalLabExperimentalTerminalAttemptFence.Observation
                    .RESERVED_UNCERTAIN&&
                fence.snapshot().entries.get(0).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RETURNED_UNACKNOWLEDGED,
                "historical snapshot detached from later observation");
            check(f.world.pvmRecords().receiptCount()==1L&&
                    f.graph.runtime().get(
                        terminal.finalization.npcId)==null,
                "fence never calls NPC finalization or credits again");
        }
        System.out.println(
            "G2017_TERMINAL_ATTEMPT_FENCE_REAL_DEATH_PASS"+
            " realCanonicalSettlement=true"+
            " sixConsumersReservedOnce=true"+
            " duplicateDenied=true"+
            " noConsumerDispatchedByFence=true"+
            " restartSafe=false");
    }

    static void check(boolean test,String what){
        if(!test)throw new AssertionError("G20.1.7 "+what);
    }
    private G2017TerminalAttemptFenceRealDeathTest(){}
}
