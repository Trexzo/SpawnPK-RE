package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.39: independent, exact WorldPlayer ownership for source-only
 * mixed PvM lethal hit receipts. Two real players attack the SAME
 * canonical NPC on the SAME World tick, in both possible style orders.
 *
 * A source Wind Strike last-hit may explicitly record only its own
 * verified owner. A previous nonlethal spell cannot receive credit
 * when a different player's melee attack finishes the NPC.
 *
 * No original-game XP, drops, last-hit policy or GUI is asserted.
 */
public final class PlayableMultiPlayerLethalSourceOwnershipTest {
    private PlayableMultiPlayerLethalSourceOwnershipTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P539_MULTIPLAYER_LETHAL "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1101,1102,1103,1104}));
        int bytes()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static final class Actor {
        final WorldPlayer player=new WorldPlayer();
        final long generation;
        final NpcRegistry scene;
        final NpcEntity visible;
        final LocalCanonicalNpcAttackHandler melee;
        final LocalLabWindStrikeNpcSettlement magic;
        final NpcAction basic;
        final SpellTargetRequest cast;
        final Capture packets=new Capture();

        Actor(World world,WorldNpc npc,String name)throws Exception{
            generation=world.registerPlayer(player,name);
            player.movement().restoreAccountState(
                false,100,3087,3495,0);
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),player.id());
            visible=scene.spawnMirroredNpc(
                npc.definitionId,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(visible!=null,"player NPC projection missing "+name);
            visible.bindCanonicalId(npc.id);
            for(int id:new int[]{556,558}){
                String seeded=player.bank().spawnItem(
                    id,2,G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(new ByteArrayOutputStream()));
                check(seeded!=null&&seeded.contains("OK"),
                    "rune fixture failed "+name+" id="+id);
            }
            check(player.installLocalLabSharedPvmAdmission(
                    world,generation),
                "optional source shared PvM admission failed "+name);
            check(player.installLocalLabWindStrikeRecovery(
                    world,generation,3),
                "optional source spell cadence failed "+name);
            melee=new LocalCanonicalNpcAttackHandler(
                world,player,()->generation,
                player.equipment(),player.combatStyles(),scene);
            magic=new LocalLabWindStrikeNpcSettlement(
                world,player,()->generation,scene);
            basic=new NpcAction(72,visible.sceneIndex);
            cast=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                visible.sceneIndex,-1,-1,-1,-1,-1);
        }

        int[] xp(){
            int[] xp=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<xp.length;i++)
                xp[i]=player.playerState().xp(i);
            return xp;
        }

        void close(World world){
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldNpc npc;
        final Actor basicOwner;
        final Actor spellOwner;
        final int[] basicXp;
        final int[] spellXp;

        Fixture(String phase)throws Exception{
            int definition=
                G1903DecodedOpcode72AcceptedHitWitnessTest
                    .attackDefinition();
            npc=world.npcs().spawn(definition,3088,3495,0);
            world.npcLifecycle().register(
                npc,11,"CUSTOM_LOCALLAB_P539_ELEVEN_HP");
            basicOwner=new Actor(world,npc,phase+"-basic");
            spellOwner=new Actor(world,npc,phase+"-spell");
            basicXp=basicOwner.xp();
            spellXp=spellOwner.xp();
            check(!basicOwner.player.id().equals(spellOwner.player.id())&&
                world.pvmRecords().receiptCount()==0,
                "distinct actors shared identity or preexisting receipts");
        }

        void rewardInvariant(String phase){
            check(Arrays.equals(basicXp,basicOwner.xp())&&
                Arrays.equals(spellXp,spellOwner.xp()),
                phase+" granted unreviewed combat XP");
            check(world.groundItems().snapshot().isEmpty(),
                phase+" added unreviewed ground loot");
        }

        void oneSpellCost(){
            check(spellOwner.player.bank().inventoryCount(556)==1&&
                spellOwner.player.bank().inventoryCount(558)==1&&
                basicOwner.player.bank().inventoryCount(556)==2&&
                basicOwner.player.bank().inventoryCount(558)==2,
                "spell cost charged wrong player or amount");
        }

        void close(){
            basicOwner.close(world);
            spellOwner.close(world);
            world.npcLifecycle().unregisterExact(npc);
            world.npcs().remove(npc.id);
            if(!world.closed())world.close();
        }
    }

    private static void differentPlayerSpellFinishesMelee()throws Exception{
        Fixture f=new Fixture("p539-spell-finishes");
        try{
            LocalCanonicalNpcAttackHandler.Result first=
                f.basicOwner.melee.handle(
                    f.basicOwner.basic,f.basicOwner.visible,
                    f.basicOwner.packets.writer);
            check(first.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                first.appliedDamage==10&&!first.newlyDied&&
                first.acceptedHit!=null&&
                first.acceptedHit.attacker==f.basicOwner.player&&
                f.world.npcLifecycle().get(f.npc.id).hitpoints==1&&
                f.basicOwner.packets.bytes()>0,
                "first real melee hit did not leave exact one HP");

            // The shared P5.30 gate is PER PLAYER: the spell caster
            // did not attack on this tick, so the spell is admissible
            // even though another player has an active melee cadence.
            check(f.world.clock().tick()==0,
                "two-player test unexpectedly advanced World clock");
            String finish=f.spellOwner.magic.settle(
                f.spellOwner.cast,f.spellOwner.packets.writer);
            LocalLabWindStrikeHitWitness proof=
                f.spellOwner.player.lastLocalLabWindStrikeAcceptedHit();
            check(finish.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                finish.contains("newlyDead=true")&&
                proof!=null&&proof.newlyDied&&
                proof.attacker==f.spellOwner.player&&
                proof.attackerId.equals(f.spellOwner.player.id())&&
                proof.npcId.equals(f.npc.id)&&
                proof.stillCurrentLethalRegistration()&&
                f.world.npcLifecycle().get(f.npc.id).dead()&&
                f.spellOwner.packets.bytes()>0,
                "separate player's lethal source spell had wrong owner");
            f.oneSpellCost();
            f.rewardInvariant("before explicit record");
            PvmRecordService ledger=f.world.pvmRecords();
            check(ledger.receiptCount()==0&&
                ledger.snapshot(f.basicOwner.player).kills==0&&
                ledger.snapshot(f.spellOwner.player).kills==0,
                "cross-player hit implicitly minted a kill");
            PvmRecordService.Receipt grant=
                ledger.recordSourceWindStrikeLethalIfCurrent(proof);
            check(grant.applied&&grant.kills==1&&
                ledger.receiptCount()==1&&
                ledger.snapshot(f.spellOwner.player).kills==1&&
                ledger.snapshot(f.basicOwner.player).kills==0,
                "source kill record credited wrong player");
            check(ledger.recordSourceWindStrikeLethalIfCurrent(proof)
                    .replayed&&
                ledger.receiptCount()==1&&
                f.spellOwner.packets.bytes()==0,
                "cross-player source receipt replay duplicated effect");
            f.rewardInvariant("after explicit spell receipt/replay");
        }finally{f.close();}
    }

    private static void differentPlayerMeleeFinishesSpell()throws Exception{
        Fixture f=new Fixture("p539-melee-finishes");
        try{
            String first=f.spellOwner.magic.settle(
                f.spellOwner.cast,f.spellOwner.packets.writer);
            LocalLabWindStrikeHitWitness prior=
                f.spellOwner.player.lastLocalLabWindStrikeAcceptedHit();
            check(first.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                first.contains("newlyDead=false")&&
                prior!=null&&!prior.newlyDied&&
                prior.attacker==f.spellOwner.player&&
                f.world.npcLifecycle().get(f.npc.id).hitpoints==10&&
                f.spellOwner.packets.bytes()>0,
                "first nonlethal source spell did not leave ten HP");
            f.oneSpellCost();

            // Independent player gates must not accidentally serialize
            // all players behind the caster's three-tick spell policy.
            check(f.world.clock().tick()==0,
                "two-player test unexpectedly advanced World clock");
            LocalCanonicalNpcAttackHandler.Result last=
                f.basicOwner.melee.handle(
                    f.basicOwner.basic,f.basicOwner.visible,
                    f.basicOwner.packets.writer);
            check(last.status==
                    LocalCanonicalNpcAttackHandler.Status.HIT&&
                last.newlyDied&&last.appliedDamage==10&&
                last.acceptedHit!=null&&
                last.acceptedHit.attacker==f.basicOwner.player&&
                last.acceptedHit.attackerId.equals(
                    f.basicOwner.player.id())&&
                f.world.npcLifecycle().get(f.npc.id).dead()&&
                f.basicOwner.packets.bytes()>0,
                "different player's actual melee last hit denied");
            check(f.spellOwner.player.lastLocalLabWindStrikeAcceptedHit()
                    ==prior,
                "melee rewrote the previous player's nonlethal spell proof");
            PvmRecordService ledger=f.world.pvmRecords();
            PvmRecordService.Receipt denied=
                ledger.recordSourceWindStrikeLethalIfCurrent(prior);
            check(!denied.applied&&!denied.replayed&&
                "NOT_COMMITTED_LETHAL_SOURCE_HIT".equals(denied.reason)&&
                ledger.receiptCount()==0&&
                ledger.snapshot(f.basicOwner.player).kills==0&&
                ledger.snapshot(f.spellOwner.player).kills==0&&
                f.spellOwner.packets.bytes()==0,
                "old nonlethal spell was upgraded by other player's kill");
            f.rewardInvariant("after foreign melee death and denial");
        }finally{f.close();}
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        differentPlayerSpellFinishesMelee();
        differentPlayerMeleeFinishesSpell();
        System.out.println(
            "P539_MULTIPLAYER_LETHAL_OWNER_PASS"+
            " twoActualWorldPlayersAndOneCanonicalNpc=true"+
            " perPlayerCrossStyleAdmissionIndependent=true"+
            " realSpellLastHitCreditedToExactOwnerOnly=true"+
            " foreignMeleeCannotUpgradeNonlethalSpellWitness=true"+
            " noPhantomXpGroundLootOrUnsolicitedPackets=true"+
            " originalSpawnpkKillPolicyUnverified=true");
    }
}
