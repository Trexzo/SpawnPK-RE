package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * P5.36: a captured snapshot can restore the numeric kill counter,
 * but NOT its in-memory NPC-life source receipts. Retained negative
 * marker forbids reconstructed positive source credit after restart.
 * Explicit source-only fixture; no actual crash/FSYNC guarantee.
 */
public final class PlayableWindStrikeRestartReviewFenceTest {
    private static final int[] SEED={801,802,803,804};
    private static final String USER="p536-source-restart";

    private PlayableWindStrikeRestartReviewFenceTest(){}

    private static void require(boolean value,String why){
        if(!value)throw new AssertionError(
            "P536_SOURCE_RESTART_REVIEW "+why);
    }

    private static final class Capture {
        final OutboundPacketQueue queue=new OutboundPacketQueue(4096);
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));

        int bytes()throws IOException{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.size();
        }
    }

    private static final class Fixture {
        final World world=World.isolatedForTest(600L);
        final WorldPlayer owner=new WorldPlayer();
        final long generation;
        final NpcRegistry view;
        final ServerPacketWriter relay=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(new int[]{811,812,813,814}));
        final WorldNpc npc;
        final NpcEntity shown;
        final LocalLabWindStrikeNpcSettlement magic;
        final SpellTargetRequest request;

        Fixture(PlayerSnapshot snapshot)throws Exception{
            if(snapshot!=null)
                PlayerSnapshotCodec.applyValidated(snapshot,owner);
            generation=world.registerPlayer(owner,USER);
            view=new NpcRegistry(
                new DevAuthorityWorkbench(),
                world.petNpcs(),owner.id());
            SharedNpcWorldRelay.register(
                relay,world,owner,view,owner.movement());
            npc=world.npcs().spawn(100,3088,3495,0);
            world.npcLifecycle().register(
                npc,1,"CUSTOM_LOCALLAB_P536_SOURCE_ONE_HP");
            SharedNpcWorldRelay.trackCanonicalNpc(world,npc);
            SharedNpcWorldRelay.syncRemotePets(relay);
            shown=view.canonical(npc.id);
            require(shown!=null&&
                shown.canonicalId().equals(npc.id),
                "canonical source NPC is not projected");
            magic=new LocalLabWindStrikeNpcSettlement(
                world,owner,()->generation,view);
            request=new SpellTargetRequest(
                131,SpellTargetRequest.Kind.NPC,
                LocalLabWindStrikeRuneTransaction.WIND_STRIKE_WIDGET,
                shown.sceneIndex,-1,-1,-1,-1,-1);
        }

        void addRunes(int quantity)throws Exception{
            for(int id:new int[]{556,558}){
                String response=owner.bank().spawnItem(
                    id,quantity,new ServerPacketWriter(
                        new ByteArrayOutputStream(),
                        new IsaacCipher(new int[]{821,822,823,824})));
                require(response!=null&&response.contains("OK"),
                    "rune seed refused "+id);
            }
        }

        LocalLabWindStrikeHitWitness lethal()throws Exception{
            Capture wire=new Capture();
            String outcome=magic.settle(request,wire.writer);
            require(outcome.startsWith(
                    "LOCAL_LAB_WIND_STRIKE_NPC_SETTLED")&&
                outcome.contains("newlyDead=true")&&
                wire.bytes()>0,
                "actual one-HP source cast failed "+outcome);
            LocalLabWindStrikeHitWitness proof=
                owner.lastLocalLabWindStrikeAcceptedHit();
            require(proof!=null&&proof.newlyDied&&
                proof.stillCurrentLethalRegistration(),
                "source cast missing live exact death proof");
            return proof;
        }

        void nextNpcLife(){
            require(world.npcLifecycle().unregisterExact(npc),
                "source NPC old lifetime did not unregister");
            world.npcLifecycle().register(
                npc,1,"CUSTOM_LOCALLAB_P536_REBORN");
            world.clock().advance();
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

    private static void rejected(
        PvmRecordService.Receipt receipt,String expected
    ){
        require(receipt!=null&&!receipt.applied&&
            !receipt.replayed&&expected.equals(receipt.reason),
            "expected denial "+expected+
            " got "+(receipt==null?"null":receipt.reason));
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");

        PlayerSnapshot retained;
        Fixture original=new Fixture(null);
        try{
            original.addRunes(4);
            PvmRecordService records=original.world.pvmRecords();
            LocalLabWindStrikeHitWitness first=original.lethal();

            // P5.34 is never automatic; only this explicit source
            // entrypoint creates the kill counter and the marker.
            require(records.receiptCount()==0,
                "source spell created automatic kill record");
            PvmRecordService.Receipt applied=
                records.recordSourceWindStrikeLethalIfCurrent(first);
            require(applied.applied&&applied.kills==1&&
                records.receiptCount()==1,
                "first source record did not apply");
            SortedMap<String,String> firstNs=
                original.owner.snapshotExtensions().namespace(
                    PvmRecordService.NAMESPACE);
            require(PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(firstNs.get(
                        PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "negative review marker was not committed with kills");
            require(records.recordSourceWindStrikeLethalIfCurrent(
                    first).replayed&&
                records.snapshot(original.owner).kills==1,
                "same-source exact replay changed counter");

            // The same World/player session still retains original
            // in-memory companion identity; a distinct later NPC
            // death can earn ONE legitimate explicit source record.
            original.nextNpcLife();
            LocalLabWindStrikeHitWitness second=original.lethal();
            PvmRecordService.Receipt secondCredit=
                records.recordSourceWindStrikeLethalIfCurrent(second);
            require(secondCredit.applied&&secondCredit.kills==2&&
                records.receiptCount()==2,
                "current-session second lifetime incorrectly gated");

            // The legacy API must never silently remove a marker
            // once source history has entered the player snapshot.
            PvmRecordService.Receipt legacy=
                records.recordIfCurrent(
                    original.owner,original.generation,
                    original.npc.id,original.world.clock().tick()+1);
            require(legacy.applied&&legacy.kills==3&&
                records.snapshot(original.owner).kills==3,
                "existing legacy count transition changed");
            SortedMap<String,String> afterLegacy=
                original.owner.snapshotExtensions().namespace(
                    PvmRecordService.NAMESPACE);
            require(PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(afterLegacy.get(
                        PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "legacy record erased negative restart marker");

            // Capture immutable account snapshot exactly through
            // production codec. This is an IN-MEMORY restore test,
            // not a disk flush/rename or crash-recovery guarantee.
            retained=PlayerSnapshotCodec.capture(USER,original.owner);
            require(PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(retained.values().get(
                        "extension."+PvmRecordService.NAMESPACE+"."+
                        PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "captured snapshot omitted retained marker");
        }finally{
            original.close();
        }

        // A different World recreates a real one-HP source death
        // after the loaded snapshot was applied, but cannot invent
        // the missing JVM source death receipt ledger.
        Fixture resumed=new Fixture(retained);
        try{
            PvmRecordService freshRecords=resumed.world.pvmRecords();
            require(freshRecords.receiptCount()==0&&
                freshRecords.snapshot(resumed.owner).kills==3,
                "snapshot replay changed stored numeric kills");
            require(PvmRecordService.SOURCE_RESTART_REVIEW_VALUE
                    .equals(resumed.owner.snapshotExtensions()
                        .namespace(PvmRecordService.NAMESPACE)
                        .get(PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "fresh World did not carry source negative marker");
            // Remaining two source runes survive the in-memory
            // snapshot because these were real inventory mutations.
            LocalLabWindStrikeHitWitness restartDeath=resumed.lethal();
            rejected(
                freshRecords.recordSourceWindStrikeLethalIfCurrent(
                    restartDeath),
                "SOURCE_RESTART_REVIEW_REQUIRED");
            require(freshRecords.receiptCount()==0&&
                freshRecords.snapshot(resumed.owner).kills==3&&
                resumed.world.groundItems().snapshot().isEmpty(),
                "restarted source hit was granted a phantom kill/loot");

            // A corrupted or unknown marker must not be treated as
            // the safe source review predicate.
            SortedMap<String,String> ns=
                resumed.owner.snapshotExtensions().namespace(
                    PvmRecordService.NAMESPACE);
            TreeMap<String,String> invalid=new TreeMap<>(ns);
            invalid.put(
                PvmRecordService.SOURCE_RESTART_REVIEW_KEY,
                "invalid-proof");
            resumed.owner.snapshotExtensions().replaceNamespace(
                PvmRecordService.NAMESPACE,invalid);
            rejected(
                freshRecords.recordSourceWindStrikeLethalIfCurrent(
                    restartDeath),
                "INVALID_SOURCE_RESTART_MARKER");
            require(freshRecords.receiptCount()==0&&
                freshRecords.snapshot(resumed.owner).kills==3,
                "corrupt marker produced source positive settlement");

            // A legacy policy may still record its own kill after
            // the corrupt source marker was loaded. The historical
            // counter update must NEVER delete the malformed marker
            // and thus accidentally enable source grants again.
            PvmRecordService.Receipt oldPolicy=
                freshRecords.recordIfCurrent(
                    resumed.owner,resumed.generation,
                    resumed.npc.id,resumed.world.clock().tick()+1);
            require(oldPolicy.applied&&oldPolicy.kills==4&&
                "invalid-proof".equals(
                    resumed.owner.snapshotExtensions()
                        .namespace(PvmRecordService.NAMESPACE)
                        .get(PvmRecordService.SOURCE_RESTART_REVIEW_KEY)),
                "legacy writer erased corrupted negative source marker");
            rejected(
                freshRecords.recordSourceWindStrikeLethalIfCurrent(
                    restartDeath),
                "INVALID_SOURCE_RESTART_MARKER");
            require(freshRecords.receiptCount()==1&&
                freshRecords.snapshot(resumed.owner).kills==4,
                "legacy record changed source invalid-marker denial");
        }finally{
            resumed.close();
        }

        System.out.println(
            "P536_SOURCE_RESTART_REVIEW_PASS"+
            " atomicSnapshotMarkerAndKillCount=true"+
            " originalSessionFurtherKillStillAllowed=true"+
            " legacyRecordPreservesNegativeMarker=true"+
            " snapshotCodecRetainsSourceReviewMarker=true"+
            " newWorldLoadedCountWithoutProofDenied=true"+
            " corruptedMarkerDenied=true"+
            " legacyAfterCorruptMarkerPreservesDenial=true"+
            " zeroNewSourceReceiptsOnRestart=true"+
            " noXpLootOrOriginalServerPolicyClaim=true"+
            " trueCrashDurabilityNotProven=true");
    }
}
