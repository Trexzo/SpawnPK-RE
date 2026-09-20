package spk.local;

import java.io.ByteArrayOutputStream;

public final class LocalSpellTargetHandlerTest {
    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        NpcRegistry npcs=new NpcRegistry();
        CombatEngine combat=new CombatEngine();

        LocalSpellTargetHandler h=new LocalSpellTargetHandler(
            player.magic(),
            player.bank(),
            player.equipment(),
            player.playerState(),
            npcs,
            combat
        );

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        SpellTargetRequest unknown=new SpellTargetRequest(
            249,
            SpellTargetRequest.Kind.PLAYER,
            999999,
            7,
            -1,
            -1,
            -1,
            -1,
            -1
        );
        String unknownResult=h.handle(unknown,w);
        if(!unknownResult.contains("REJECTED_UNKNOWN_TARGETED_SPELL"))
            throw new AssertionError("unknown spell did not fail closed: "+unknownResult);

        // Exact R25 Air surge row has no visible resource requirements and supports
        // PLAYER/NPC targets. The server effect remains deliberately unresolved.
        SpellTargetRequest playerTarget=new SpellTargetRequest(
            249,
            SpellTargetRequest.Kind.PLAYER,
            19100,
            7,
            -1,
            -1,
            -1,
            -1,
            -1
        );
        String playerResult=h.handle(playerTarget,w);
        if(!playerResult.contains("ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Air surge"))
            throw new AssertionError("accepted route validation missing: "+playerResult);
        if(!playerResult.contains(
            "TARGET_PLAYER_ROUTED index=7 effect=UNIMPLEMENTED_SERVER_AUTHORITY"))
            throw new AssertionError("player effect must remain unresolved: "+playerResult);

        // Enchant Lvl-1 Jewelry supports INVENTORY_ITEM. Supply only the visible
        // rune requirements, then prove the typed target-slot guard still rejects
        // an item id that is not actually present at the requested slot.
        player.bank().spawnItem(555,1,w);
        player.bank().spawnItem(564,1,w);
        SpellTargetRequest inventoryMismatch=new SpellTargetRequest(
            237,
            SpellTargetRequest.Kind.INVENTORY_ITEM,
            1155,
            -1,
            1234,
            BankState.NORMAL_INVENTORY_CONTAINER,
            20,
            -1,
            -1
        );
        String inventoryResult=h.handle(inventoryMismatch,w);
        if(!inventoryResult.contains("REJECTED_TARGET_INVENTORY_MISMATCH"))
            throw new AssertionError("inventory mismatch guard lost: "+inventoryResult);

        System.out.println(
            "LOCAL_SPELL_TARGET_HANDLER_PASS unknownFailClosed=true playerUnimplementedAuthority=true inventoryMismatch=true");
    }
}
