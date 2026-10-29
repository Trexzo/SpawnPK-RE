package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * P5.32 source-only cross-style recovery test. The player explicitly
 * enables BOTH P5.30 mixed PvM and P5.28 three-tick spell fixtures.
 * Does not infer or certify original SpawnPK melee/magic timing.
 */
public final class PlayableSharedPvmRecoveryWindowTest {
    private static final int[] SEED={501,502,503,504};

    private PlayableSharedPvmRecoveryWindowTest(){}

    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P532_SHARED_PVM_RECOVERY "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=
            new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));
        int bytes()throws Exception{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static void addRune(WorldPlayer owner,int id)throws Exception{
        String result=owner.bank().spawnItem(
            id,2,new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{511,512,513,514})));
        require(result!=null&&result.contains("OK"),
            "rune fixture seed failed for "+id);
    }

    private static void pureGateTransitions()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long first=world.registerPlayer(player,"p532-pure");
        long current=first;
        try{
            LocalLabSharedPvmActionGate gate=
                player.localLabSharedPvmActionGate();
            require(player.installLocalLabSharedPvmAdmission(
                world,first),"trial gate install denied");

            LocalLabSharedPvmActionGate.Admission basic=
                gate.begin(world,first,
                    LocalLabSharedPvmActionGate.Kind
                        .CANONICAL_NPC_BASIC,0);
            require(basic.ticket!=null,"basic reservation missing");
            gate.mayHaveCommitted(basic.ticket);
            gate.committed(basic.ticket,4);
            gate.exit(basic.ticket);

            require("REJECTED_SHARED_PVM_ACTION_SAME_TICK".equals(
                    gate.begin(world,first,
                        LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,0)
                        .denial),
                "same-tick mixed hit admitted");
            for(int tick=1;tick<=3;tick++){
                require("REJECTED_SHARED_PVM_ACTION_RECOVERING".equals(
                        gate.begin(world,first,
                            LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,
                            tick).denial),
                    "magic bypassed source basic 4-tick recovery "+tick);
            }
            require("REJECTED_SHARED_PVM_TICK_REGRESSION".equals(
                    gate.begin(world,first,
                        LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,0-0L)
                        .denial)==false,
                "regression check overlaps same-tick semantics");

            LocalLabSharedPvmActionGate.Admission spell=
                gate.begin(world,first,
                    LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,4);
            require(spell.ticket!=null,"4-tick boundary not ready");
            gate.mayHaveCommitted(spell.ticket);
            gate.committed(spell.ticket,3);
            gate.exit(spell.ticket);

            for(int tick=5;tick<=6;tick++){
                require("REJECTED_SHARED_PVM_ACTION_RECOVERING".equals(
                        gate.begin(world,first,
                            LocalLabSharedPvmActionGate.Kind
                                .CANONICAL_NPC_BASIC,tick).denial),
                    "melee bypassed custom 3-tick spell recovery "+tick);
            }

            // A pre-HP cancellation after recovery is READY again on
            // that same tick; the attempt must not extend cooldown.
            LocalLabSharedPvmActionGate.Admission retry=
                gate.begin(world,first,
                    LocalLabSharedPvmActionGate.Kind.CANONICAL_NPC_BASIC,
                    7);
            require(retry.ticket!=null,"spell recovery boundary not open");
            gate.exit(retry.ticket);
            LocalLabSharedPvmActionGate.Admission again=
                gate.begin(world,first,
                    LocalLabSharedPvmActionGate.Kind.CANONICAL_NPC_BASIC,
                    7);
            require(again.ticket!=null,
                "aborted boundary attempt unexpectedly burned cooldown");
            try{
                gate.committed(again.ticket,0);
                throw new AssertionError("zero recovery accepted");
            }catch(IllegalArgumentException expected){
                require(expected.getMessage().contains(
                        "P532_INVALID_SHARED_SOURCE_RECOVERY_TICKS"),
                    "invalid recovery denial mismatch");
            }
            gate.exit(again.ticket);
            require(!gate.faulted(world,first),
                "invalid timing parameter poisoned safe attempt");

            // Reject a regressed tick relative to the last committed
            // one even when no active attempt exists.
            require("REJECTED_SHARED_PVM_TICK_REGRESSION".equals(
                    gate.begin(world,first,
                        LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,
                        3).denial),
                "regressed authoritative World tick admitted");

            // A fresh exact owner must not inherit retired policy.
            require(world.unregisterPlayer(player,first),
                "old generation unregister failed");
            current=world.registerPlayer(player,"p532-pure");
            require(current!=first&&!gate.enabled(world,current),
                "old recovery installed into a new owner");
            require(player.installLocalLabSharedPvmAdmission(
                    world,current),"fresh owner install refused");
            LocalLabSharedPvmActionGate.Admission fresh=
                gate.begin(world,current,
                    LocalLabSharedPvmActionGate.Kind.WIND_STRIKE,0);
            require(fresh.ticket!=null,
                "new generation inherited old 3-tick recovery");
            gate.exit(fresh.ticket);
        }finally{
            if(world.players().owns(player,current))
                world.unregisterPlayer(player,current);
            if(!world.closed())world.close();
        }
    }

    private static void actualCanonicalMeleeSpellTest()throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,"p532-real-combat");
        WorldNpc target=null;
        try{
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            target=world.npcs().spawn(
                definition,3088,3495,0);
            world.npcLifecycle().register(
                target,40,"CUSTOM_LOCALLAB_P532_FORTY_HP");

            NpcRegistry scene=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),player.id());
            NpcEntity visible=scene.spawnMirroredNpc(
                definition,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            require(visible!=null,"canonical scene not projected");
            visible.bindCanonicalId(target.id);
            addRune(player,556);
            addRune(player,558);
            require(player.installLocalLabSharedPvmAdmission(
                    world,generation),
                "source-only cross-style trial install denied");
            require(player.installLocalLabWindStrikeRecovery(
                    world,generation,3),
                "three-tick spell fixture install denied");

            LocalCanonicalNpcAttackHandler melee=
                new LocalCanonicalNpcAttackHandler(
                    world,player,()->generation,
                    player.equipment(),player.combatStyles(),scene);
            LocalLabWindStrikeNpcSettlement magic=
                new LocalLabWindStrikeNpcSettlement(
                    world,player,()->generation,scene);
            NpcAction basic=new NpcAction(72,visible.sceneIndex);
            SpellTargetRequest cast=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                visible.sceneIndex,-1,-1,-1,-1,-1);

            Capture outbound=new Capture();
            LocalCanonicalNpcAttackHandler.Result first=
                melee.handle(basic,visible,outbound.writer);
            require(first.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                first.appliedDamage==10&&
                world.npcLifecycle().get(target.id).hitpoints==30&&
                outbound.bytes()>0,
                "real canonical melee hit missing");
            long nextMelee=player.canonicalNpcAttackNextAllowedTick();
            require(nextMelee>=2&&nextMelee<=32,
                "no meaningful source weapon cadence for cross-style test "+
                nextMelee);

            // The installed shared cooldown must preserve the known
            // melee attack period, not just same-tick duplication.
            String duplicate=magic.settle(cast,outbound.writer);
            require("REJECTED_SHARED_PVM_ACTION_SAME_TICK".equals(
                    duplicate)&&outbound.bytes()==0,
                "spell hit bypassed same-tick melee");
            for(int i=1;i<nextMelee;i++){
                world.clock().advance();
                String blocked=magic.settle(cast,outbound.writer);
                require("REJECTED_SHARED_PVM_ACTION_RECOVERING".equals(
                        blocked)&&
                    outbound.bytes()==0&&
                    world.npcLifecycle().get(target.id).hitpoints==30&&
                    player.bank().inventoryCount(556)==2&&
                    player.bank().inventoryCount(558)==2,
                    "magic bypassed melee cooldown tick="+i+
                    " result="+blocked);
            }

            world.clock().advance();
            require(world.clock().tick()==nextMelee,
                "source World clock did not reach basic cadence");
            String spellReady=magic.settle(cast,outbound.writer);
            require(spellReady.contains(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                world.npcLifecycle().get(target.id).hitpoints==29&&
                player.bank().inventoryCount(556)==1&&
                player.bank().inventoryCount(558)==1&&
                outbound.bytes()>0,
                "source spell did not commit at melee recovery boundary");

            for(int i=1;i<=2;i++){
                world.clock().advance();
                LocalCanonicalNpcAttackHandler.Result blocked=
                    melee.handle(basic,visible,outbound.writer);
                require(blocked.status==
                        LocalCanonicalNpcAttackHandler.Status
                            .SHARED_PVM_BLOCKED&&
                    outbound.bytes()==0&&
                    world.npcLifecycle().get(target.id).hitpoints==29,
                    "real melee bypassed 3-tick spell source policy "+i);
            }

            world.clock().advance();
            LocalCanonicalNpcAttackHandler.Result resumed=
                melee.handle(basic,visible,outbound.writer);
            require(resumed.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                resumed.appliedDamage==10&&
                world.npcLifecycle().get(target.id).hitpoints==19&&
                outbound.bytes()>0,
                "melee did not resume on spell recovery boundary");
            world.clock().advance();
            require("REJECTED_SHARED_PVM_ACTION_RECOVERING".equals(
                    magic.settle(cast,outbound.writer))&&
                outbound.bytes()==0&&
                player.bank().inventoryCount(556)==1,
                "second basic hit did not reestablish shared melee recovery");

            System.out.println(
                "P532_SHARED_PVM_RECOVERY_PASS"+
                " actualCanonicalMeleeHitTen=true"+
                " knownLocalMeleeCadencePreserved="+nextMelee+
                " magicBlockedUntilMeleeRecovery=true"+
                " threeTickMagicBlocksRealMelee=true"+
                " noRejectedNpcHpRuneOrBytes=true"+
                " defaultOffAndOwnerGenerationUnchanged=true"+
                " originalSpawnpkAttackSpeedUnverified=true");
        }finally{
            if(target!=null){
                world.npcLifecycle().unregisterExact(target);
                world.npcs().remove(target.id);
            }
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        pureGateTransitions();
        actualCanonicalMeleeSpellTest();
    }
}
