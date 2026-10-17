package spk.local;

import java.util.*;

public final class LoadoutEditorWireSemanticAdapterTest {
    public static void main(String[] args){
        exactSemanticProjection();
        malformedInventoryEmptyRejected();
        malformedEquipmentPlaceholderRejected();
        wrongEquipmentSlotRejected();
        unknownItemRejected();
        targetIdentityRemainsUnowned();

        System.out.println(
            "PLAYABILITY_LOADOUT_EDITOR_WIRE_SEMANTIC_PASS "+
            "inventory28=true "+
            "equipment14=true "+
            "duplicateInventoryAggregated=true "+
            "equipmentOutputIndexSemantic=true "+
            "placeholder6811=true "+
            "malformedEmptyFailClosed=true "+
            "catalogGuard=true "+
            "equipmentMetadataGuard=true "+
            "targetSelectionOwned=false "+
            "transportAuthority=EXACT_CURRENT_CLIENT "+
            "policyAuthority=CUSTOM_LOCALLAB_PLAYABILITY"
        );
    }

    private static void exactSemanticProjection(){
        List<LoadoutEditorSaveClientRequest.WireEntry>
            inventory=emptyInventory();

        inventory.set(
            0,
            wire(995,500)
        );
        inventory.set(
            1,
            wire(15272,1)
        );
        inventory.set(
            9,
            wire(995,500)
        );
        inventory.set(
            27,
            wire(15272,1)
        );

        List<LoadoutEditorSaveClientRequest.WireEntry>
            equipment=emptyEquipment();

        equipment.set(
            EquipmentSlot.WEAPON.equipmentIndex,
            wire(4151,1)
        );

        LoadoutEditorWireSemanticAdapter.Snapshot
            semantic=
                LoadoutEditorWireSemanticAdapter
                    .adapt(
                        request(
                            inventory,
                            equipment
                        )
                    );

        require(
            semantic.inventory.size()==2,
            "inventory semantic count"
        );

        require(
            "item:995".equals(
                semantic.inventory
                    .get(0)
                    .itemKey
            )&&
            semantic.inventory
                .get(0)
                .quantity==1000L,
            "coin duplicate aggregation"
        );

        require(
            "item:15272".equals(
                semantic.inventory
                    .get(1)
                    .itemKey
            )&&
            semantic.inventory
                .get(1)
                .quantity==2L,
            "food duplicate aggregation"
        );

        require(
            semantic.equipment.size()==1,
            "equipment semantic count"
        );

        PlayerLoadout.EquipmentEntry weapon=
            semantic.equipment.get(0);

        require(
            "slot:weapon".equals(
                weapon.slotKey
            )&&
            "item:4151".equals(
                weapon.itemKey
            )&&
            weapon.quantity==1L,
            "weapon output-index mapping"
        );

        require(
            LoadoutEditorWireSemanticAdapter
                .TRANSPORT_AUTHORITY
                .equals(
                    semantic.transportAuthority
                )&&
            LoadoutEditorWireSemanticAdapter
                .POLICY_AUTHORITY
                .equals(
                    semantic.policyAuthority
                ),
            "authority separation"
        );
    }

    private static void malformedInventoryEmptyRejected(){
        List<LoadoutEditorSaveClientRequest.WireEntry>
            inventory=emptyInventory();

        inventory.set(
            5,
            wire(-1,1)
        );

        expect(
            IllegalArgumentException.class,
            ()->LoadoutEditorWireSemanticAdapter
                .adapt(
                    request(
                        inventory,
                        emptyEquipment()
                    )
                ),
            "malformed inventory empty"
        );
    }

    private static void malformedEquipmentPlaceholderRejected(){
        List<LoadoutEditorSaveClientRequest.WireEntry>
            equipment=emptyEquipment();

        equipment.set(
            6,
            wire(4151,1)
        );

        expect(
            IllegalArgumentException.class,
            ()->LoadoutEditorWireSemanticAdapter
                .adapt(
                    request(
                        emptyInventory(),
                        equipment
                    )
                ),
            "equipment placeholder 6"
        );
    }

    private static void wrongEquipmentSlotRejected(){
        List<LoadoutEditorSaveClientRequest.WireEntry>
            equipment=emptyEquipment();

        equipment.set(
            EquipmentSlot.HEAD.equipmentIndex,
            wire(4151,1)
        );

        expect(
            IllegalArgumentException.class,
            ()->LoadoutEditorWireSemanticAdapter
                .adapt(
                    request(
                        emptyInventory(),
                        equipment
                    )
                ),
            "weapon in head output"
        );
    }

    private static void unknownItemRejected(){
        List<LoadoutEditorSaveClientRequest.WireEntry>
            inventory=emptyInventory();

        inventory.set(
            0,
            wire(
                Integer.MAX_VALUE,
                1
            )
        );

        expect(
            IllegalArgumentException.class,
            ()->LoadoutEditorWireSemanticAdapter
                .adapt(
                    request(
                        inventory,
                        emptyEquipment()
                    )
                ),
            "unknown inventory item"
        );
    }

    private static void targetIdentityRemainsUnowned(){
        for(java.lang.reflect.Field field:
                LoadoutEditorWireSemanticAdapter
                    .Snapshot.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("loadoutid")||
               name.contains("version")||
               name.contains("target")||
               name.contains("default"))
                throw new AssertionError(
                    "unproven editor target leaked field="+
                    field.getName()
                );
        }
    }

    private static LoadoutEditorSaveClientRequest request(
        List<LoadoutEditorSaveClientRequest.WireEntry> inventory,
        List<LoadoutEditorSaveClientRequest.WireEntry> equipment
    ){
        return new LoadoutEditorSaveClientRequest(
            inventory,
            equipment,
            10L,
            11L
        );
    }

    private static ArrayList<LoadoutEditorSaveClientRequest.WireEntry>
        emptyInventory(){
        ArrayList<LoadoutEditorSaveClientRequest.WireEntry> out=
            new ArrayList<>();

        for(int i=0;
            i<LoadoutEditorCompatibilityPairer
                .INVENTORY_ENTRY_COUNT;
            i++)
            out.add(
                wire(-1,0)
            );

        return out;
    }

    private static ArrayList<LoadoutEditorSaveClientRequest.WireEntry>
        emptyEquipment(){
        ArrayList<LoadoutEditorSaveClientRequest.WireEntry> out=
            new ArrayList<>();

        for(int i=0;
            i<LoadoutEditorCompatibilityPairer
                .EQUIPMENT_ENTRY_COUNT;
            i++){
            EquipmentSlot slot=
                EquipmentSlot
                    .fromEquipmentIndex(i);

            out.add(
                slot==null
                    ?wire(0,0)
                    :wire(-1,0)
            );
        }

        return out;
    }

    private static LoadoutEditorSaveClientRequest.WireEntry
        wire(
            int itemId,
            int amount
        ){
        return new LoadoutEditorSaveClientRequest
            .WireEntry(
                itemId,
                amount
            );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LoadoutEditorWireSemanticAdapterTest(){}
}
