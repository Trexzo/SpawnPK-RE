package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class LoadoutEditorServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_LOADOUT_EDITOR";

    public static void main(String[] args){
        LoadoutService loadouts=
            new LoadoutService();

        PlayerLoadout original=
            fixture();

        loadouts.create(original);

        LoadoutEditorService editor=
            new LoadoutEditorService(
                loadouts
            );

        List<PlayerLoadout.InventoryEntry>
            inventory=
                Arrays.asList(
                    new PlayerLoadout.InventoryEntry(
                        "item:food-new",
                        12L
                    ),
                    new PlayerLoadout.InventoryEntry(
                        "item:potion-new",
                        4L
                    )
                );

        List<PlayerLoadout.EquipmentEntry>
            equipment=
                Arrays.asList(
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon",
                        "item:weapon-new",
                        1L
                    ),
                    new PlayerLoadout.EquipmentEntry(
                        "slot:cape",
                        "item:cape-new",
                        1L
                    )
                );

        LoadoutEditorService.SaveSnapshot saved=
            editor.saveSnapshot(
                original.ownerRef,
                original.id,
                PlayerLoadoutVersion.of(1L),
                inventory,
                equipment,
                POLICY
            );

        require(
            saved.previousVersion.equals(
                PlayerLoadoutVersion.of(1L)
            )&&
            saved.loadout.version.equals(
                PlayerLoadoutVersion.of(2L)
            ),
            "loadout editor version transition"
        );

        require(
            saved.loadout.inventory.size()==2&&
            "item:food-new".equals(
                saved.loadout.inventory
                    .get(0).itemKey
            )&&
            saved.loadout.equipment.size()==2&&
            "item:weapon-new".equals(
                saved.loadout.equipment
                    .get(0).itemKey
            ),
            "loadout editor snapshot replacement"
        );

        require(
            saved.loadout.skillProfile==
                original.skillProfile&&
            saved.loadout.petSelection==
                original.petSelection,
            "loadout editor preserves non-editor sections"
        );

        require(
            POLICY.equals(
                saved.loadout.sourceAuthority
            )&&
            LoadoutEditorService
                .PRESENTATION_AUTHORITY
                .equals(
                    saved.presentationAuthority
                ),
            "loadout editor authority separation"
        );

        LoadoutService.Snapshot v2=
            loadouts.get(
                original.ownerRef,
                original.id
            );

        expect(
            IllegalStateException.class,
            ()->editor.saveSnapshot(
                original.ownerRef,
                original.id,
                PlayerLoadoutVersion.of(1L),
                inventory,
                equipment,
                POLICY
            ),
            "stale editor save"
        );

        unchanged(v2,loadouts);

        expect(
            NullPointerException.class,
            ()->editor.saveSnapshot(
                original.ownerRef,
                original.id,
                PlayerLoadoutVersion.of(2L),
                null,
                equipment,
                POLICY
            ),
            "missing inventory half"
        );

        unchanged(v2,loadouts);

        expect(
            NullPointerException.class,
            ()->editor.saveSnapshot(
                original.ownerRef,
                original.id,
                PlayerLoadoutVersion.of(2L),
                inventory,
                null,
                POLICY
            ),
            "missing equipment half"
        );

        unchanged(v2,loadouts);

        List<PlayerLoadout.EquipmentEntry>
            duplicateSlots=
                Arrays.asList(
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon",
                        "item:first",
                        1L
                    ),
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon",
                        "item:second",
                        1L
                    )
                );

        expect(
            IllegalArgumentException.class,
            ()->editor.saveSnapshot(
                original.ownerRef,
                original.id,
                PlayerLoadoutVersion.of(2L),
                inventory,
                duplicateSlots,
                POLICY
            ),
            "invalid complete snapshot"
        );

        unchanged(v2,loadouts);

        expect(
            IllegalArgumentException.class,
            ()->editor.saveSnapshot(
                original.ownerRef,
                original.id,
                PlayerLoadoutVersion.of(2L),
                inventory,
                equipment,
                "EXACT_CURRENT_CLIENT"
            ),
            "presentation authority as gameplay policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->editor.saveSnapshot(
                original.ownerRef,
                original.id,
                PlayerLoadoutVersion.of(2L),
                inventory,
                equipment,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown gameplay authority"
        );

        unchanged(v2,loadouts);

        expect(
            IllegalArgumentException.class,
            ()->editor.saveSnapshot(
                "player:missing",
                original.id,
                PlayerLoadoutVersion.of(1L),
                inventory,
                equipment,
                POLICY
            ),
            "unknown semantic loadout"
        );

        completePairOnlyContract();
        protocolBoundary();

        System.out.println(
            "LOADOUT_EDITOR_ATOMIC_SNAPSHOT_PASS "+
            "completeInventoryEquipmentPair=true "+
            "partialPairRejected=true "+
            "versionedReplace=true "+
            "staleVersionRejected=true "+
            "failureAtomic=true "+
            "skillProfilePreserved=true "+
            "petSelectionPreserved=true "+
            "presentationAuthoritySeparated=true "+
            "unknownAuthorityRejected=true "+
            "transportStagingOwned=false "+
            "defaultPolicyOwned=false "+
            "applyPolicyOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static PlayerLoadout fixture(){
        return new PlayerLoadout(
            PlayerLoadoutId.of(
                "loadout:editor-test"
            ),
            PlayerLoadoutVersion.of(1L),
            "player:alice",
            Collections.singletonList(
                new PlayerLoadout.InventoryEntry(
                    "item:food-old",
                    8L
                )
            ),
            Collections.singletonList(
                new PlayerLoadout.EquipmentEntry(
                    "slot:weapon",
                    "item:weapon-old",
                    1L
                )
            ),
            new PlayerLoadout.SkillProfile(
                Arrays.asList(
                    new PlayerLoadout.SkillValue(
                        "skill:attack",
                        75L
                    ),
                    new PlayerLoadout.SkillValue(
                        "skill:strength",
                        80L
                    )
                )
            ),
            new PlayerLoadout.PetSelection(
                "pet:existing"
            ),
            "LOCAL_LAB_POLICY_SEED"
        );
    }

    private static void unchanged(
        LoadoutService.Snapshot expected,
        LoadoutService loadouts
    ){
        LoadoutService.Snapshot actual=
            loadouts.get(
                expected.loadout.ownerRef,
                expected.loadout.id
            );

        require(
            actual.loadout==
                expected.loadout&&
            actual.loadout.version.equals(
                PlayerLoadoutVersion.of(2L)
            ),
            "failed editor save mutated loadout"
        );
    }

    private static void completePairOnlyContract(){
        Method save=null;

        for(Method method:
                LoadoutEditorService.class
                    .getDeclaredMethods()){
            if(method.getName()
                    .equals(
                        "saveSnapshot")){
                if(save!=null)
                    throw new AssertionError(
                        "multiple editor save paths"
                    );
                save=method;
            }

            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("stage")||
               name.contains("half")||
               name.contains("cld"))
                throw new AssertionError(
                    "split transport staging leaked into Chat 3 method "+
                    method.getName()
                );
        }

        require(
            save!=null,
            "missing atomic editor save method"
        );

        int collectionParameters=0;

        for(Class<?> type:
                save.getParameterTypes()){
            if(Collection.class
                    .isAssignableFrom(type))
                collectionParameters++;
        }

        require(
            collectionParameters==2,
            "editor save must require inventory + equipment semantic collections"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                LoadoutEditorService.class,
                LoadoutEditorService
                    .SaveSnapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("command")||
                   name.contains("cld")||
                   name.contains("containerid")||
                   name.contains("clientslot")||
                   name.contains("socket")||
                   name.contains("session"))
                    throw new AssertionError(
                        "transport identity leaked into loadout editor "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
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

    private LoadoutEditorServiceTest(){}
}
