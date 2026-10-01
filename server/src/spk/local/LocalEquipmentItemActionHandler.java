package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed inventory/equipment/cosmetic item-action coordinator.
 *
 * Raw ItemContainerAction decoding remains in ClientPacketProbe/LocalSession.
 * This class owns only the coherent equipment/cosmetic semantic family and
 * returns ordered log/save effects so account persistence remains session-owned.
 */
final class LocalEquipmentItemActionHandler {
    private final BankState bank;
    private final EquipmentState equipment;
    private final PlayerState playerState;
    private final PlayerPresentationService playerPresentation;
    private final CombatStyleState combatStyles;

    LocalEquipmentItemActionHandler(
        BankState bank,
        EquipmentState equipment,
        PlayerState playerState,
        PlayerPresentationService playerPresentation,
        CombatStyleState combatStyles
    ){
        this.bank=java.util.Objects.requireNonNull(bank,"bank");
        this.equipment=java.util.Objects.requireNonNull(equipment,"equipment");
        this.playerState=java.util.Objects.requireNonNull(playerState,"playerState");
        this.playerPresentation=java.util.Objects.requireNonNull(playerPresentation,"playerPresentation");
        this.combatStyles=java.util.Objects.requireNonNull(combatStyles,"combatStyles");
    }

    Result handle(
        ItemContainerAction action,
        String username,
        ServerPacketWriter serverPackets
    )throws IOException{
        if(action==null)return null;

        if(action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER){
            InventoryActionRouter.Resolution route=
                InventoryActionRouter.resolve(action);

            if(route.is("Override")){
                BankState.Stack stack=bank.inventoryAt(action.slot);
                if(stack==null||stack.itemId!=action.itemId||stack.qty<=0){
                    return Result.post(
                        "V51842_OVERRIDE "+action+
                        " route="+route+
                        " result=REJECTED_INVENTORY_MISMATCH"
                    );
                }

                String result=CosmeticOverrideService.apply(
                    bank,
                    action.slot,
                    action.itemId,
                    playerState.cosmetic(),
                    serverPackets
                );

                boolean changed=result.startsWith("COSMETIC_OVERRIDE_OK");
                if(changed){
                    playerState.syncEquipmentPresentation(equipment);
                    bank.sendCosmetic(serverPackets,playerState.cosmetic());
                    playerPresentation.refresh(
                        username,equipment,playerState,serverPackets);
                }

                return new Result(
                    Collections.<String>emptyList(),
                    changed?"COSMETIC_OVERRIDE":null,
                    Collections.singletonList(
                        "V51842_OVERRIDE "+action+
                        " route="+route+
                        " result="+result+
                        " bs="+playerState.cosmetic().itemId()+
                        " underlyingEquipmentUnchanged=true ammo="+
                        equipment.itemAt(EquipmentSlot.AMMO)+
                        " appearanceRefresh="+changed+
                        " authority=EXACT_CLIENT_ACTION_PLUS_EXISTING_BS_CHANNEL"
                    )
                );
            }

            if(route.is("Defuse")){
                BankState.Stack stack=bank.inventoryAt(action.slot);
                if(stack==null||stack.itemId!=action.itemId||stack.qty<=0){
                    return Result.post(
                        "V51842_DEFUSE "+action+
                        " route="+route+
                        " result=REJECTED_INVENTORY_MISMATCH"
                    );
                }

                return Result.post(
                    "V51842_DEFUSE "+action+
                    " route="+route+
                    " result=FAIL_CLOSED_NO_MUTATION authority=UNKNOWN_SERVER_AUTHORITY"
                );
            }
        }

        if(action.opcode==41 &&
           action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER &&
           ItemCatalog.isNativePlayerIcon(action.itemId)){
            String result=bank.equipCosmeticFromInventory(
                action.slot,
                action.itemId,
                playerState.cosmetic(),
                serverPackets
            );

            boolean changed=result.startsWith("COSMETIC_EQUIP_OK");
            if(changed){
                playerState.syncEquipmentPresentation(equipment);
                playerPresentation.refresh(
                    username,equipment,playerState,serverPackets);
            }

            return new Result(
                Collections.<String>emptyList(),
                changed?"COSMETIC_EQUIP":null,
                Collections.singletonList(
                    "V511_COSMETIC_EQUIP "+action+
                    " result="+result+
                    " nativeBs="+playerState.nativeIconItemId()+
                    " ammo="+equipment.itemAt(EquipmentSlot.AMMO)+
                    " appearanceRefresh="+changed
                )
            );
        }

        if(action.opcode==41 &&
           action.widgetId==BankState.NORMAL_INVENTORY_CONTAINER){
            String result=bank.equipFromInventory(
                action.slot,
                action.itemId,
                equipment,
                serverPackets
            );

            boolean changed=result.startsWith("EQUIP_OK");
            ArrayList<String> beforeSave=new ArrayList<>();

            if(changed){
                playerState.syncEquipmentPresentation(equipment);
                playerPresentation.refresh(
                    username,equipment,playerState,serverPackets);

                int root=CombatInterfaceRepository.forWeapon(
                    equipment.weapon());

                serverPackets.fixed(
                    71,
                    BootstrapPackets.sidebar71(
                        root,
                        CombatInterfaceRepository.TAB_INDEX
                    )
                );

                beforeSave.add(
                    "V510_STYLE_EQUIP_RECONCILE "+
                    combatStyles.reconcileRoot(root,serverPackets)
                );
            }

            return new Result(
                beforeSave,
                "EQUIP_FROM_INVENTORY",
                Collections.singletonList(
                    "V522_EQUIPMENT_ITEM_ACTION "+action+
                    " result="+result+
                    " weapon="+equipment.weapon()+
                    " appearanceRefresh="+changed+
                    " decoderAligned=true"
                )
            );
        }

        if(action.opcode==145 &&
           action.widgetId==BankState.COSMETIC_WIDGET){
            int active=playerState.cosmetic().itemId();

            if(action.slot!=0||active<0||action.itemId!=active){
                return Result.post(
                    "V5124_COSMETIC_WIDGET_REMOVE "+action+
                    " result=REJECTED_EXPECTED slot0_item="+active
                );
            }

            String result=bank.unequipCosmeticToInventory(
                playerState.cosmetic(),
                serverPackets
            );

            boolean changed=result.startsWith("COSMETIC_UNEQUIP_OK");
            if(changed){
                playerState.syncEquipmentPresentation(equipment);
                playerPresentation.refresh(
                    username,equipment,playerState,serverPackets);
            }

            return new Result(
                Collections.<String>emptyList(),
                changed?"COSMETIC_WIDGET_REMOVE":null,
                Collections.singletonList(
                    "V5124_COSMETIC_WIDGET_REMOVE "+action+
                    " result="+result+
                    " nativeBs="+playerState.nativeIconItemId()+
                    " ammo="+equipment.itemAt(EquipmentSlot.AMMO)
                )
            );
        }

        if(action.opcode==145 &&
           action.widgetId==EquipmentState.EQUIPMENT_WIDGET){
            String result=bank.unequipToInventory(
                action.slot,
                action.itemId,
                equipment,
                serverPackets
            );

            boolean changed=result.startsWith("UNEQUIP_OK");
            ArrayList<String> beforeSave=new ArrayList<>();

            if(changed){
                playerState.syncEquipmentPresentation(equipment);
                playerPresentation.refresh(
                    username,equipment,playerState,serverPackets);

                int root=CombatInterfaceRepository.forWeapon(
                    equipment.weapon());

                serverPackets.fixed(
                    71,
                    BootstrapPackets.sidebar71(
                        root,
                        CombatInterfaceRepository.TAB_INDEX
                    )
                );

                beforeSave.add(
                    "V510_STYLE_UNEQUIP_RECONCILE "+
                    combatStyles.reconcileRoot(root,serverPackets)
                );
            }

            return new Result(
                beforeSave,
                "UNEQUIP_TO_INVENTORY",
                Collections.singletonList(
                    "V522_UNEQUIP_ITEM_ACTION "+action+
                    " result="+result+
                    " appearanceRefresh="+changed+
                    " decoderAligned=true"
                )
            );
        }

        return null;
    }

    static final class Result {
        final List<String> beforeSaveLogs;
        final String saveReason;
        final List<String> afterSaveLogs;

        Result(
            List<String> beforeSaveLogs,
            String saveReason,
            List<String> afterSaveLogs
        ){
            this.beforeSaveLogs=
                beforeSaveLogs==null
                    ?Collections.<String>emptyList()
                    :Collections.unmodifiableList(
                        new ArrayList<String>(beforeSaveLogs));
            this.saveReason=saveReason;
            this.afterSaveLogs=
                afterSaveLogs==null
                    ?Collections.<String>emptyList()
                    :Collections.unmodifiableList(
                        new ArrayList<String>(afterSaveLogs));
        }

        static Result post(String log){
            return new Result(
                Collections.<String>emptyList(),
                null,
                Collections.singletonList(log)
            );
        }
    }
}
