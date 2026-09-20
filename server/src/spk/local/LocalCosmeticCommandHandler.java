package spk.local;

import java.io.IOException;

/**
 * Command adapter for the dedicated player cosmetic channel.
 *
 * Cosmetic inventory mutation remains in BankState and appearance publication
 * remains in PlayerPresentationService. This adapter only owns command routing
 * and reports the existing persistence side effect back to LocalSession.
 */
final class LocalCosmeticCommandHandler {
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerState playerState;
    private final PlayerPresentationService playerPresentation;

    LocalCosmeticCommandHandler(
        BankState bank,
        EquipmentState equipment,
        PlayerState playerState,
        PlayerPresentationService playerPresentation
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.playerPresentation=java.util.Objects.requireNonNull(playerPresentation,"playerPresentation");
    }

    Result handle(
        String[] p,
        String username,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(p==null||p.length==0||!p[0].equalsIgnoreCase("cosmetic"))return null;

        String sub=p.length>=2?p[1].toLowerCase(java.util.Locale.ROOT):"info";

        if(sub.equals("info")||sub.equals("status")){
            return new Result(
                "V5124_COSMETIC_INFO item="+playerState.cosmetic().itemId()+
                " nativeBs="+playerState.nativeIconItemId()+
                " ammo="+equipment.itemAt(EquipmentSlot.AMMO)+
                " authority=PLAYER_APPEARANCE_BS ui1688=NORMAL_EQUIPMENT cosmeticWidget="+
                BankState.COSMETIC_WIDGET+" cosmeticWidgetPublished=true",
                null
            );
        }

        if(sub.equals("off")||sub.equals("remove")){
            String r=bank.unequipCosmeticToInventory(playerState.cosmetic(),serverPackets);
            String saveReason=null;
            if(r.startsWith("COSMETIC_UNEQUIP_OK")){
                playerState.syncEquipmentPresentation(equipment);
                bank.sendCosmetic(serverPackets,playerState.cosmetic());
                playerPresentation.refresh(username,equipment,playerState,serverPackets);
                saveReason="COSMETIC_OFF";
            }
            return new Result(
                "V5124_"+r+
                " nativeBs="+playerState.nativeIconItemId()+
                " ammo="+equipment.itemAt(EquipmentSlot.AMMO)+
                " cosmeticWidget="+BankState.COSMETIC_WIDGET,
                saveReason
            );
        }

        return new Result(
            "V511_COSMETIC_HELP commands=info | off equip=normal_inventory_Wear/Wield_opcode41_on_native_icon_item",
            null
        );
    }

    static final class Result {
        final String logText;
        final String saveReason;

        Result(String logText,String saveReason){
            this.logText=logText;
            this.saveReason=saveReason;
        }
    }
}
