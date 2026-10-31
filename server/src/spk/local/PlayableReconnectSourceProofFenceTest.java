package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.SortedMap;

/**
 * P5.43: same-World, same-account-name snapshot handoff to a NEW
 * WorldPlayer instance may retain the numeric source kill history,
 * but cannot inherit a previous owner's in-JVM receipt provenance.
 *
 * Re-register the EXACT same NPC ID/life across the handoff. On the
 * same tick, the new owner's real lethal source hit collides with
 * the original source death and must not be mistaken for replay.
 * On a later World tick, the new owner's real lethal hit must still
 * require source restart review, not borrow the old owner's proof.
 *
 * No network login, disk save/FSYNC, original server reward rules,
 * or cross-JVM account ownership is certified by this LocalLab test.
 */
public final class PlayableReconnectSourceProofFenceTest {
    private PlayableReconnectSourceProofFenceTest(){}

    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError(
            "P543_RECONNECT_SOURCE_PROOF "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(8192);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{1501,1502,1503,1504}));
        int bytes()throws IOException{
            ByteArrayOutputStream result=new ByteArrayOutputStream();
            queue.drainTo(result,1<<20);
            return result.size();
        }
    }

    private static final class Actor {
        final WorldPlayer player=new WorldPlayer();
        final long generation;
        final NpcRegistry scene;
        final LocalLabWindStrikeNpcSettlement spell;
        final SpellTargetRequest cast;
        final Capture packets=new Capture();

        Actor(World world,WorldNpc npc,String username,
              PlayerSnapshot restored)throws Exception{
            if(restored!=null)
                PlayerSnapshotCodec.applyValidated(restored,player);
            generation=world.registerPlayer(player,username);
            if(restored==null)
                player.movement().restoreAccountState(
                    false,100,3087,3495,0);
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),player.id());
            NpcEntity shown=scene.spawnMirroredNpc(
                npc.definitionId,3088,3495,null,player.movement(),
                G1903DecodedOpcode72AcceptedHitWitnessTest.writer(
                    new ByteArrayOutputStream()));
            check(shown!=null&&
                player.movement().x()==3087&&
                player.movement().y()==3495,
                "snapshot owner scene or movement changed");
            shown.bindCanonicalId(npc.id);
            spell=new LocalLabWindStrikeNpcSettlement(
                world,player,()->generation,scene);
            cast=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                shown.sceneIndex,-1,-1,-1,-1,-1);
        }

        void seedRunes(int quantity)throws Exception{
            for(int id:new int[]{556,558}){
                String result=player.bank().spawnItem(
                    id,quantity,
                    G1903DecodedOpcode72AcceptedHitWitnessTest
                        .writer(new ByteArrayOutputStream()));
                check(result!=null&&result.contains("OK"),
                    "source rune seed failed "+id);
            }
        }

        LocalLabWindStrikeHitWitness lethal(
            World world,WorldNpc npc,Object life
        )throws Exception{
            String result=spell.settle(cast,packets.writer);
            LocalLabWindStrikeHitWitness hit=
                player.lastLocalLabWindStrikeAcceptedHit();
            check(result.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                result.contains("newlyDead=true")&&
                packets.bytes()>0&&
                hit!=null&&hit.newlyDied&&
                hit.world==world&&
                hit.attacker==player&&
                hit.generation==generation&&
                hit.npcId.equals(npc.id)&&
                hit.npcRegistrationIdentity==life&&
                hit.stillCurrentLethalRegistration(),
                "actual current owner source death not proven "+result);
            return hit;
        }

        int[] xp(){
            int[] result=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<result.length;i++)
                result[i]=player.playerState().xp(i);
            return result;
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

        void close(World world){
            if(world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
        }
    }

    private static void denied(
        PvmRecordService.Receipt result,String reason
    ){
        check(result!=null&&!result.applied&&!result.replayed&&
            reason.equals(result.reason),
            "expected fail-closed "+reason+" but received "+
            (result==null?"null":result.reason));
    }

    private static void run(int definition,int round)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldNpc npc=null;
        Actor original=null;
        Actor reconnected=null;
        String username="p543-same-account-"+round;
        try{
            npc=world.npcs().spawn(definition,3088,3495,0);
            NpcLifecycleService.Snapshot life1=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P543_FIRST_LIFE");
            original=new Actor(world,npc,username,null);
            original.seedRunes(3);
            int[] startXp=original.xp();
            LocalLabWindStrikeHitWitness first=
                original.lethal(
                    world,npc,life1.registrationIdentity);
            PvmRecordService records=world.pvmRecords();
            PvmRecordService.Receipt firstGrant=
                records.recordSourceWindStrikeLethalIfCurrent(
                    first);
            check(firstGrant.applied&&firstGrant.kills==1&&
                records.receiptCount()==1&&
                records.snapshot(original.player).kills==1&&
                original.player.bank().inventoryCount(556)==2&&
                original.player.bank().inventoryCount(558)==2,
                "original exact source owner not credited once");
            SortedMap<String,String> before=
                original.player.snapshotExtensions()
                    .namespace(PvmRecordService.NAMESPACE);
            check(PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(before.get(
                        PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "original source receipt did not set negative marker");
            check(records.recordSourceWindStrikeLethalIfCurrent(
                first).replayed,
                "first exact witness replay not idempotent");

            PlayerSnapshot saved=
                PlayerSnapshotCodec.capture(
                    username,original.player);
            check(PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(saved.values().get(
                        "extension."+PvmRecordService.NAMESPACE+"."+
                        PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "in-memory account handoff lost source review marker");
            check(world.unregisterPlayer(
                    original.player,original.generation),
                "original exact player generation did not retire");
            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    first),
                "STALE_PLAYER_GENERATION");

            // The SAME account username is deliberately restored
            // into a NEW WorldPlayer. A stale receipt from this World
            // cannot prove this new player's generation/identity.
            reconnected=new Actor(world,npc,username,saved);
            check(reconnected.player!=original.player&&
                !reconnected.player.id().equals(original.player.id())&&
                reconnected.player.lastLocalLabWindStrikeAcceptedHit()
                    ==null&&
                records.snapshot(reconnected.player).kills==1&&
                reconnected.player.bank().inventoryCount(556)==2&&
                reconnected.player.bank().inventoryCount(558)==2&&
                Arrays.equals(startXp,reconnected.xp()),
                "snapshot handoff invented owner proof or rewards");

            check(world.npcLifecycle().unregisterExact(npc),
                "old NPC registration not removable after reconnect");
            NpcLifecycleService.Snapshot life2=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P543_SECOND_LIFE");
            check(life2.registrationIdentity!=
                    life1.registrationIdentity&&
                world.clock().tick()==first.deathTick,
                "same NPC/tick collision not constructed");
            LocalLabWindStrikeHitWitness second=
                reconnected.lethal(
                    world,npc,life2.registrationIdentity);
            check(second.attacker==reconnected.player&&
                second.attacker!=first.attacker&&
                second.npcId.equals(first.npcId)&&
                second.deathTick==first.deathTick,
                "new owner/life proof incorrectly reused old identity");
            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    second),
                "SOURCE_DEATH_LIFETIME_COLLISION");
            check(records.receiptCount()==1&&
                records.snapshot(reconnected.player).kills==1&&
                reconnected.player.bank().inventoryCount(556)==1&&
                reconnected.player.bank().inventoryCount(558)==1,
                "same-tick new owner collision mutated kill count");

            // Different death tick removes the old (ID,tick) receipt
            // collision, but NOT the prior account's negative marker.
            // The in-JVM source proof is still bound to the old
            // WorldPlayer object and may not bless the new login.
            world.clock().advance();
            check(world.npcLifecycle().unregisterExact(npc),
                "second NPC registration not retired");
            NpcLifecycleService.Snapshot life3=
                world.npcLifecycle().register(
                    npc,1,"CUSTOM_LOCALLAB_P543_THIRD_LIFE");
            LocalLabWindStrikeHitWitness third=
                reconnected.lethal(
                    world,npc,life3.registrationIdentity);
            check(third.deathTick!=first.deathTick&&
                third.attacker==reconnected.player,
                "third NPC lifetime proof reused historical death");
            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    second),
                "NOT_CURRENT_SOURCE_HIT");
            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    third),
                "SOURCE_RESTART_REVIEW_REQUIRED");
            denied(
                records.recordSourceWindStrikeLethalIfCurrent(
                    first),
                "STALE_PLAYER_GENERATION");

            check(records.receiptCount()==1&&
                records.snapshot(reconnected.player).kills==1&&
                reconnected.player.bank().inventoryCount(556)==0&&
                reconnected.player.bank().inventoryCount(558)==0&&
                Arrays.equals(startXp,reconnected.xp())&&
                Arrays.equals(startXp,original.xp())&&
                world.groundItems().snapshot().isEmpty()&&
                PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(reconnected.player.snapshotExtensions()
                        .namespace(PvmRecordService.NAMESPACE)
                        .get(PvmRecordService.SOURCE_RESTART_REVIEW_KEY))&&
                reconnected.packets.bytes()==0&&
                original.packets.bytes()==0,
                "reconnect source record produced phantom grant/reward");

        }finally{
            if(original!=null)original.close(world);
            if(reconnected!=null)reconnected.close(world);
            if(npc!=null){
                world.npcLifecycle().unregisterExact(npc);
                world.npcs().remove(npc.id);
            }
            if(!world.closed())world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        int definition=
            G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
        for(int i=0;i<6;i++)
            run(definition,i);
        System.out.println(
            "P543_RECONNECT_SOURCE_PROOF_FENCE_PASS"+
            " sixFreshWorldSameNameAccountHandoffs=true"+
            " restoredKillsAndNegativeMarkerPreserved=true"+
            " oldSourceOwnerWitnessDeniedAfterUnregister=true"+
            " sameTickNewNpcLifeNotCountedAsReplay=true"+
            " newTickSourceKillStillNeedsRestartReview=true"+
            " oldWorldCompanionNotLoanedToNewWorldPlayer=true"+
            " noPhantomXpLootOrPackets=true"+
            " actualNetworkLoginAndCrashDurabilityUnverified=true");
    }
}
