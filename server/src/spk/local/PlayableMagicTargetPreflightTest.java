package spk.local;

import java.io.ByteArrayOutputStream;

/**
 * P5.11: NPC and item targets must be authoritative/visible before a
 * targeted spell can update MagicState.last, claim validation, or
 * publish a combat dummy packet.
 *
 * This covers preflight admission, not target visibility revocation
 * races, runnable PvM rune consumption or original spell effects.
 */
public final class PlayableMagicTargetPreflightTest {
    private static final int SPELL_AIR_SURGE=19100;
    private static final int SPELL_ENCHANT=1155;
    private static final int HP=PlayerState.HITPOINTS;
    private static final int ITEM_WIDGET=BankState.NORMAL_INVENTORY_CONTAINER;

    private PlayableMagicTargetPreflightTest(){}

    private static void require(boolean ok,String explanation){
        if(!ok)throw new AssertionError(
            "P511_MAGIC_TARGET_PREFLIGHT "+explanation);
    }

    private static SpellTargetRequest player(){
        return new SpellTargetRequest(
            249,SpellTargetRequest.Kind.PLAYER,
            SPELL_AIR_SURGE,7,-1,-1,-1,-1,-1);
    }

    private static SpellTargetRequest npc(int scene){
        return new SpellTargetRequest(
            131,SpellTargetRequest.Kind.NPC,
            SPELL_AIR_SURGE,scene,-1,-1,-1,-1,-1);
    }

    private static SpellTargetRequest item(int widget,int slot,int id){
        return new SpellTargetRequest(
            237,SpellTargetRequest.Kind.INVENTORY_ITEM,
            SPELL_ENCHANT,-1,id,widget,slot,-1,-1);
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected arguments");
        WorldPlayer owner=new WorldPlayer();
        NpcRegistry registry=new NpcRegistry();
        CombatEngine combat=new CombatEngine();
        LocalSpellTargetHandler live=new LocalSpellTargetHandler(
            owner,registry,combat);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            bytes,new IsaacCipher(new int[]{211,212,213,214}));

        require(owner.playerState().currentLevel(HP)==99,
            "starting HP");
        String before=owner.magic().last();
        require("NONE".equals(before),"start magic state");

        String missing=live.handle(npc(65534),writer);
        require(missing.contains("REJECTED_TARGET_NPC_NOT_VISIBLE")&&
            owner.magic().last().equals(before)&&
            bytes.size()==0,
            "invisible NPC generated accepted spell/packets");

        // Inventory validity has priority over spell requirements:
        // invalid container or stale slot must not be treated as cast.
        String wrongWidget=live.handle(item(9999,4,555),writer);
        require(wrongWidget.contains(
                "REJECTED_TARGET_UNVERIFIED_INVENTORY_WIDGET")&&
            owner.magic().last().equals(before)&&
            bytes.size()==0,
            "unverified item container admitted magic");

        String missingItem=live.handle(item(ITEM_WIDGET,27,987654),writer);
        require(missingItem.contains(
                "REJECTED_TARGET_INVENTORY_MISMATCH")&&
            owner.magic().last().equals(before)&&
            bytes.size()==0,
            "stale inventory slot changed magic state");

        // Even after the client-visible spell requirements are
        // provided, mismatched target slot must still be rejected.
        owner.bank().spawnItem(555,1,writer);
        owner.bank().spawnItem(564,1,writer);
        int afterSetup=bytes.size();
        String enchantBefore=owner.magic().last();
        String mismatched=live.handle(item(ITEM_WIDGET,27,123456),writer);
        require(mismatched.contains(
                "REJECTED_TARGET_INVENTORY_MISMATCH")&&
            owner.magic().last().equals(enchantBefore)&&
            bytes.size()==afterSetup,
            "stale target accepted after resource availability");

        // A valid player-target route retains the previous explicit
        // server-authority boundary: no guessed player damage.
        String playerSpell=live.handle(player(),writer);
        require(playerSpell.contains(
                "ACCEPTED_CLIENT_VISIBLE_REQUIREMENTS spell=Air surge")&&
            playerSpell.contains(
                "TARGET_PLAYER_ROUTED index=7")&&
            playerSpell.contains("UNIMPLEMENTED_SERVER_AUTHORITY")&&
            owner.magic().last().startsWith("TARGET_ACCEPT")&&
            bytes.size()==afterSetup,
            "valid existing player spell route changed");

        String acceptedLast=owner.magic().last();
        String badAgain=live.handle(npc(65534),writer);
        require(badAgain.contains("REJECTED_TARGET_NPC_NOT_VISIBLE")&&
            owner.magic().last().equals(acceptedLast)&&
            bytes.size()==afterSetup,
            "stale NPC erased valid spell state");

        PlayerLifecycleService life=
            new PlayerLifecycleService(owner);
        require(life.applyDamage(500,20L,"P511_DEATH").died,
            "lethal fixture");
        String dead=live.handle(npc(65534),writer);
        require(dead.contains("REJECTED_CASTER_DEAD")&&
            owner.magic().last().equals(acceptedLast)&&
            bytes.size()==afterSetup,
            "dead-caster guard lost priority over target preflight");

        System.out.println(
            "P511_MAGIC_TARGET_PREFLIGHT_PASS"+
            " invisibleNpcRejectedBeforeSpell=true"+
            " invalidItemWidgetDenied=true"+
            " staleInventorySlotRejectedBeforeSpell=true"+
            " resourceAvailableStillRequiresValidTarget=true"+
            " existingPlayerRouteRemainsUnimplemented=true"+
            " noPhantomMagicLastMutation=true"+
            " noPhantomPackets=true"+
            " deadGuardHasPriority=true"+
            " originalGuiRuneConsumptionAndDamageNotClaimed=true");
    }
}
