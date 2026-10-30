package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * P5.37 source-only negative reward boundary.
 *
 * A canonical one-HP Wind Strike spends precisely its reviewed runes,
 * and an explicit P5.34/P5.36 PvM kill-record transition changes ONLY
 * the account's local kill-record extension. Neither operation may
 * implicitly grant combat XP, inventory loot or a ground-item drop.
 *
 * This deliberately DOES NOT implement or infer original SpawnPK reward
 * rules, or claim durable restart and original-client GUI acceptance.
 */
public final class PlayableSourceKillRewardSeparationTest {
    private static final String USER="p537-reward-separation";
    private PlayableSourceKillRewardSeparationTest(){}

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError("P537_SOURCE_REWARDS "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(new int[]{901,902,903,904}));
        int drainBytes()throws IOException{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            queue.drainTo(bytes,1<<20);
            return bytes.size();
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer owner=new WorldPlayer();
        final long generation;
        final NpcRegistry scene;
        final ServerPacketWriter relay=new ServerPacketWriter(
            new ByteArrayOutputStream(),
            new IsaacCipher(new int[]{911,912,913,914}));
        final WorldNpc npc;
        final LocalLabWindStrikeNpcSettlement spell;
        final SpellTargetRequest request;

        Fixture(PlayerSnapshot restored)throws Exception{
            if(restored!=null)
                PlayerSnapshotCodec.applyValidated(restored,owner);
            generation=world.registerPlayer(owner,USER);
            scene=new NpcRegistry(
                new DevAuthorityWorkbench(),world.petNpcs(),owner.id());
            SharedNpcWorldRelay.register(
                relay,world,owner,scene,owner.movement());
            npc=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                npc,1,"CUSTOM_LOCALLAB_P537_ONE_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relay);
            NpcEntity visible=scene.canonical(npc.id);
            require(visible!=null&&
                npc.id.equals(visible.canonicalId()),
                "real canonical target not projected");
            spell=new LocalLabWindStrikeNpcSettlement(
                world,owner,()->generation,scene);
            request=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                visible.sceneIndex,-1,-1,-1,-1,-1);
        }

        void seedTwoRunes()throws Exception{
            for(int id:new int[]{556,558}){
                String response=owner.bank().spawnItem(
                    id,2,new ServerPacketWriter(
                        new ByteArrayOutputStream(),
                        new IsaacCipher(new int[]{921,922,923,924})));
                require(response!=null&&response.contains("OK"),
                    "source rune seed rejected "+id);
            }
        }

        LocalLabWindStrikeHitWitness lethal(Capture capture)
            throws Exception{
            String result=spell.settle(request,capture.writer);
            require(result.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                result.contains("newlyDead=true")&&
                capture.drainBytes()>0,
                "canonical source spell failed "+result);
            LocalLabWindStrikeHitWitness witness=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(witness!=null&&witness.newlyDied&&
                witness.stillCurrentLethalRegistration(),
                "real lethal hit proof unavailable");
            return witness;
        }

        String inventory(){
            StringBuilder out=new StringBuilder();
            for(int slot=0;slot<BankState.INVENTORY_CAPACITY;slot++){
                BankState.Stack item=owner.bank().inventoryAt(slot);
                if(item==null)out.append("-");
                else out.append(item.itemId).append(':').append(item.qty);
                out.append('|');
            }
            return out.toString();
        }

        int[] xp(){
            int[] values=new int[PlayerState.COMBAT_SKILL_COUNT];
            for(int i=0;i<values.length;i++)
                values[i]=owner.playerState().xp(i);
            return values;
        }

        void requireNoReward(int[] xp,String inventory,String phase){
            require(Arrays.equals(xp,xp()),
                phase+" created unreviewed combat XP");
            require(inventory.equals(inventory()),
                phase+" changed items beyond the reviewed spell cost");
            require(world.groundItems().snapshot().isEmpty(),
                phase+" created unreviewed ground loot");
        }

        void close(){
            SharedNpcWorldRelay.untrackCanonicalNpc(world,npc.id);
            SharedNpcWorldRelay.unregister(relay);
            world.npcLifecycle().unregisterExact(npc);
            world.npcs().remove(npc.id);
            if(world.players().owns(owner,generation))
                world.unregisterPlayer(owner,generation);
            if(!world.closed())world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");

        PlayerSnapshot saved;
        Fixture original=new Fixture(null);
        try{
            original.seedTwoRunes();
            int[] beforeXp=original.xp();
            require(original.world.groundItems().snapshot().isEmpty(),
                "unexpected ground fixture preimage");
            Capture packets=new Capture();
            LocalLabWindStrikeHitWitness lethal=original.lethal(packets);
            require(original.owner.bank().inventoryCount(556)==1&&
                original.owner.bank().inventoryCount(558)==1,
                "real spell did not spend exactly one air+mind pair");
            String afterSpellInventory=original.inventory();
            original.requireNoReward(
                beforeXp,afterSpellInventory,"after source spell");
            PvmRecordService ledger=original.world.pvmRecords();
            require(ledger.receiptCount()==0,
                "source spell automatically granted a kill record");

            PvmRecordService.Receipt first=
                ledger.recordSourceWindStrikeLethalIfCurrent(lethal);
            require(first.applied&&first.kills==1&&
                ledger.receiptCount()==1,
                "explicit source kill receipt was not committed");
            original.requireNoReward(
                beforeXp,afterSpellInventory,"after explicit receipt");
            require(packets.drainBytes()==0,
                "kill-record-only transition emitted gameplay packets");

            PvmRecordService.Receipt replay=
                ledger.recordSourceWindStrikeLethalIfCurrent(lethal);
            require(replay.replayed&&!replay.applied&&
                ledger.receiptCount()==1,
                "source receipt replay was not idempotent");
            original.requireNoReward(
                beforeXp,afterSpellInventory,"after receipt replay");
            saved=PlayerSnapshotCodec.capture(USER,original.owner);
        }finally{
            original.close();
        }

        // A new World has no recovered source receipt ledger. A
        // legitimate subsequent NPC hit still spends its own runes,
        // but new source kill credit MUST be denied after restoration.
        Fixture resumed=new Fixture(saved);
        try{
            require(resumed.world.pvmRecords().snapshot(resumed.owner)
                    .kills==1,
                "saved source kill count did not restore");
            int[] restoredXp=resumed.xp();
            String preCastInventory=resumed.inventory();
            Capture packets=new Capture();
            LocalLabWindStrikeHitWitness fresh=resumed.lethal(packets);
            require(resumed.owner.bank().inventoryCount(556)==0&&
                resumed.owner.bank().inventoryCount(558)==0&&
                !preCastInventory.equals(resumed.inventory()),
                "restored runes were not spent by actual second cast");
            String postCastInventory=resumed.inventory();
            resumed.requireNoReward(
                restoredXp,postCastInventory,"after restored cast");

            PvmRecordService.Receipt denied=
                resumed.world.pvmRecords()
                    .recordSourceWindStrikeLethalIfCurrent(fresh);
            require(!denied.applied&&!denied.replayed&&
                "SOURCE_RESTART_REVIEW_REQUIRED".equals(denied.reason)&&
                resumed.world.pvmRecords().snapshot(resumed.owner)
                    .kills==1&&
                resumed.world.pvmRecords().receiptCount()==0,
                "restored source hit bypassed review-only kill gate");
            resumed.requireNoReward(
                restoredXp,postCastInventory,"after restart denial");
            require(packets.drainBytes()==0,
                "refused source kill record emitted gameplay packets");
        }finally{
            resumed.close();
        }

        System.out.println(
            "P537_SOURCE_KILL_REWARD_SEPARATION_PASS"+
            " exactRuneCostOnly=true"+
            " explicitRecordNoInventoryOrXpOrGroundMutation=true"+
            " replayNoRewardSideEffects=true"+
            " restoredHitNeedsReviewBeforeRecord=true"+
            " normalLocalSessionUnchanged=true"+
            " originalSpawnpkXpLootUnverified=true"+
            " crashDurabilityUnverified=true");
    }
}
