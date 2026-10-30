package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.38: both mixed-style lethal orders use the *actual* opt-in
 * canonical melee handler and isolated Wind Strike settlement.
 *
 * A spell finishing melee damage may be explicitly recorded as a
 * source kill; a melee finishing spell damage must not reuse an
 * earlier nonlethal spell witness. Neither route invents XP/loot.
 *
 * Not original SpawnPK combat timing, rewards, or GUI acceptance.
 */
public final class PlayableMixedStyleLethalRewardBoundaryTest {
    private PlayableMixedStyleLethalRewardBoundaryTest(){}

    private static void require(boolean yes,String why){
        if(!yes)throw new AssertionError("P538_MIXED_LETHAL "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1001,1002,1003,1004}));
        int bytes()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer player=new WorldPlayer();
        final long generation;
        final WorldNpc target;
        final NpcRegistry scene;
        final NpcEntity visible;
        final LocalCanonicalNpcAttackHandler melee;
        final LocalLabWindStrikeNpcSettlement spell;
        final NpcAction attack;
        final SpellTargetRequest cast;
        final Capture outbound=new Capture();
        final int[] initialXp;
        final String initialInventory;

        Fixture(String name)throws Exception{
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            target=world.npcs().spawn(definition,3088,3495,0);
            world.npcLifecycle().register(
                target,11,"CUSTOM_LOCALLAB_P538_ELEVEN_HP");

            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),player.id());
            visible=scene.spawnMirroredNpc(
                definition,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            require(visible!=null,"canonical viewer scene absent");
            visible.bindCanonicalId(target.id);

            for(int item:new int[]{556,558}){
                String response=player.bank().spawnItem(
                    item,2,G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(new ByteArrayOutputStream()));
                require(response!=null&&response.contains("OK"),
                    "rune seed refused "+item);
            }
            require(player.installLocalLabSharedPvmAdmission(
                    world,generation),
                "opt-in shared combat admission refused");
            require(player.installLocalLabWindStrikeRecovery(
                    world,generation,3),
                "explicit source spell cadence refused");
            melee=new LocalCanonicalNpcAttackHandler(
                world,player,()->generation,
                player.equipment(),player.combatStyles(),scene);
            spell=new LocalLabWindStrikeNpcSettlement(
                world,player,()->generation,scene);
            attack=new NpcAction(72,visible.sceneIndex);
            cast=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                visible.sceneIndex,-1,-1,-1,-1,-1);

            initialXp=xp();
            initialInventory=inventory();
            require(world.pvmRecords().receiptCount()==0&&
                world.pvmRecords().snapshot(player).kills==0&&
                world.groundItems().snapshot().isEmpty(),
                "nonempty starting reward state");
        }

        int[] xp(){
            int[] x=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<x.length;i++)
                x[i]=player.playerState().xp(i);
            return x;
        }

        String inventory(){
            StringBuilder result=new StringBuilder();
            for(int i=0;i<BankState.INVENTORY_CAPACITY;i++){
                BankState.Stack item=player.bank().inventoryAt(i);
                if(item==null)result.append('-');
                else result.append(item.itemId).append(':').append(item.qty);
                result.append('|');
            }
            return result.toString();
        }

        void assertUnreviewedRewardsAbsent(String expectedInventory,
                                           String phase){
            require(Arrays.equals(initialXp,xp()),
                phase+" awarded unreviewed combat XP");
            require(expectedInventory.equals(inventory()),
                phase+" modified unrelated inventory");
            require(world.groundItems().snapshot().isEmpty(),
                phase+" created unreviewed ground loot");
        }

        void assertOnePairSpent(String phase){
            require(player.bank().inventoryCount(556)==1&&
                player.bank().inventoryCount(558)==1,
                phase+" did not spend exactly one air/mind pair");
        }

        void close(){
            world.npcLifecycle().unregisterExact(target);
            world.npcs().remove(target.id);
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }

    private static void meleeThenSpellLethal()throws Exception{
        Fixture f=new Fixture("p538-melee-then-spell");
        try{
            LocalCanonicalNpcAttackHandler.Result basic=
                f.melee.handle(f.attack,f.visible,f.outbound.writer);
            require(basic.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                basic.appliedDamage==10&&!basic.newlyDied&&
                basic.acceptedHit!=null&&
                f.world.npcLifecycle().get(f.target.id).hitpoints==1&&
                f.outbound.bytes()>0,
                "first canonical melee hit did not leave one HP");
            f.assertUnreviewedRewardsAbsent(
                f.initialInventory,"nonlethal melee");

            String sameTick=f.spell.settle(f.cast,f.outbound.writer);
            require("REJECTED_SHARED_PVM_ACTION_SAME_TICK".equals(
                    sameTick)&&f.outbound.bytes()==0&&
                f.player.bank().inventoryCount(556)==2&&
                f.world.npcLifecycle().get(f.target.id).hitpoints==1,
                "same-tick magic bypassed melee action gate");

            long boundary=f.player.canonicalNpcAttackNextAllowedTick();
            require(boundary>=2&&boundary<=32,
                "unexpected source basic recovery boundary "+boundary);
            while(f.world.clock().tick()+1<boundary){
                f.world.clock().advance();
                String denied=f.spell.settle(f.cast,f.outbound.writer);
                require("REJECTED_SHARED_PVM_ACTION_RECOVERING"
                        .equals(denied)&&
                    f.outbound.bytes()==0&&
                    f.world.npcLifecycle().get(f.target.id).hitpoints==1,
                    "magic bypassed prior melee recovery");
            }
            f.world.clock().advance();
            require(f.world.clock().tick()==boundary,
                "melee recovery boundary not reached");
            String death=f.spell.settle(f.cast,f.outbound.writer);
            LocalLabWindStrikeHitWitness proof=
                f.player.lastLocalLabWindStrikeAcceptedHit();
            require(death.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                death.contains("newlyDead=true")&&
                proof!=null&&proof.newlyDied&&
                proof.stillCurrentLethalRegistration()&&
                f.world.npcLifecycle().get(f.target.id).dead()&&
                f.outbound.bytes()>0,
                "source spell failed to finish real melee damage");
            f.assertOnePairSpent("lethal spell");
            String afterSpell=f.inventory();
            f.assertUnreviewedRewardsAbsent(
                afterSpell,"lethal spell");
            require(f.world.pvmRecords().receiptCount()==0,
                "spell lethal automatically created a kill record");

            PvmRecordService.Receipt granted=
                f.world.pvmRecords()
                    .recordSourceWindStrikeLethalIfCurrent(proof);
            require(granted.applied&&granted.kills==1&&
                f.world.pvmRecords().receiptCount()==1,
                "explicit genuine spell last hit refused");
            f.assertUnreviewedRewardsAbsent(
                afterSpell,"explicit spell last-hit record");
            require(f.outbound.bytes()==0,
                "explicit kill record emitted gameplay packet");
            PvmRecordService.Receipt replay=
                f.world.pvmRecords()
                    .recordSourceWindStrikeLethalIfCurrent(proof);
            require(replay.replayed&&!replay.applied&&
                f.world.pvmRecords().receiptCount()==1&&
                f.outbound.bytes()==0,
                "spell last-hit receipt replay mutated state");
            f.assertUnreviewedRewardsAbsent(
                afterSpell,"source spell record replay");
        }finally{f.close();}
    }

    private static void spellThenMeleeLethal()throws Exception{
        Fixture f=new Fixture("p538-spell-then-melee");
        try{
            String first=f.spell.settle(f.cast,f.outbound.writer);
            LocalLabWindStrikeHitWitness nonlethal=
                f.player.lastLocalLabWindStrikeAcceptedHit();
            require(first.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                first.contains("newlyDead=false")&&
                nonlethal!=null&&!nonlethal.newlyDied&&
                f.world.npcLifecycle().get(f.target.id).hitpoints==10&&
                f.outbound.bytes()>0,
                "first source spell did not leave ten HP");
            f.assertOnePairSpent("nonlethal spell");
            String afterSpell=f.inventory();
            f.assertUnreviewedRewardsAbsent(
                afterSpell,"nonlethal spell");
            PvmRecordService.Receipt invalid=
                f.world.pvmRecords()
                    .recordSourceWindStrikeLethalIfCurrent(nonlethal);
            require(!invalid.applied&&!invalid.replayed&&
                "NOT_COMMITTED_LETHAL_SOURCE_HIT".equals(invalid.reason),
                "nonlethal spell witness was granted a kill");

            for(int tick=0;tick<=2;tick++){
                if(tick>0)f.world.clock().advance();
                LocalCanonicalNpcAttackHandler.Result denied=
                    f.melee.handle(
                        f.attack,f.visible,f.outbound.writer);
                require(denied.status==
                        LocalCanonicalNpcAttackHandler.Status
                            .SHARED_PVM_BLOCKED&&
                    denied.acceptedHit==null&&
                    f.outbound.bytes()==0&&
                    f.world.npcLifecycle().get(f.target.id).hitpoints==10,
                    "melee bypassed source spell recovery tick "+tick);
                f.assertUnreviewedRewardsAbsent(
                    afterSpell,"blocked melee");
            }

            f.world.clock().advance();
            require(f.world.clock().tick()==3,
                "explicit three-tick spell cadence not reached");
            LocalCanonicalNpcAttackHandler.Result lethal=
                f.melee.handle(f.attack,f.visible,f.outbound.writer);
            require(lethal.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                lethal.appliedDamage==10&&lethal.newlyDied&&
                lethal.acceptedHit!=null&&
                lethal.acceptedHit.stillCurrentIn(f.world)&&
                f.world.npcLifecycle().get(f.target.id).dead()&&
                f.outbound.bytes()>0,
                "real melee did not finish nonlethal spell damage");

            require(f.player.lastLocalLabWindStrikeAcceptedHit()==nonlethal,
                "melee synthesized a new spell hit witness");
            require(f.world.pvmRecords().receiptCount()==0&&
                f.world.pvmRecords().snapshot(f.player).kills==0,
                "melee last hit minted source spell kill record");
            PvmRecordService.Receipt denied=
                f.world.pvmRecords()
                    .recordSourceWindStrikeLethalIfCurrent(nonlethal);
            require(!denied.applied&&!denied.replayed&&
                "NOT_COMMITTED_LETHAL_SOURCE_HIT".equals(denied.reason)&&
                f.world.pvmRecords().receiptCount()==0,
                "old nonlethal spell upgraded to melee death credit");
            f.assertUnreviewedRewardsAbsent(
                afterSpell,"lethal melee and refused source record");
            require(f.outbound.bytes()==0,
                "refused source spell record emitted gameplay packet");
        }finally{f.close();}
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        meleeThenSpellLethal();
        spellThenMeleeLethal();
        System.out.println(
            "P538_MIXED_LETHAL_REWARDS_PASS"+
            " realMeleeThenSpellDeath=true"+
            " realSpellThenMeleeDeath=true"+
            " sourceRecoveryPreserved=true"+
            " exactSpellLastHitOnlyRecordable=true"+
            " meleeLastHitCannotUpgradeSpellWitness=true"+
            " zeroPhantomXpInventoryGroundOrPackets=true"+
            " originalSpawnpkRewardsUnknown=true");
    }
}
