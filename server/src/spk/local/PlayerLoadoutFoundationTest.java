package spk.local;

import java.lang.reflect.Field;
import java.util.*;

/** Deterministic regressions for Issue #171 semantic PlayerLoadout foundation. */
public final class PlayerLoadoutFoundationTest {
    public static void main(String[] args){
        PlayerLoadout first=
            fixture(
                1L,
                "item:fixture-sword",
                "pet:fixture-a"
            );

        expect(
            UnsupportedOperationException.class,
            ()->first.inventory.clear(),
            "inventory immutability"
        );

        expect(
            UnsupportedOperationException.class,
            ()->first.equipment.add(
                new PlayerLoadout.EquipmentEntry(
                    "slot:illegal",
                    "item:illegal",
                    1L
                )
            ),
            "equipment immutability"
        );

        expect(
            UnsupportedOperationException.class,
            ()->first.skillProfile.values.clear(),
            "skill profile immutability"
        );

        LoadoutService service=
            new LoadoutService();

        LoadoutService.Snapshot created=
            service.create(first);

        eq(
            PlayerLoadoutVersion.of(1L),
            created.loadout.version,
            "created version"
        );
        check(
            !created.hasAppliedVersion(),
            "created loadout not applied"
        );

        expect(
            IllegalStateException.class,
            ()->service.create(first),
            "duplicate loadout create"
        );

        LoadoutService.ValidationResult validation=
            service.validate(
                first.ownerRef,
                first.id,
                loadout->
                    loadout.inventory.size()==1&&
                    loadout.equipment.size()==1
                        ?LoadoutService.ValidationResult.valid()
                        :LoadoutService.ValidationResult.invalid(
                            "CUSTOM_LOCALLAB invalid fixture"
                        )
            );

        check(validation.valid,"external validator accepted");

        LoadoutService.LoadoutApplyPlan v1Plan=
            service.planApply(
                first.ownerRef,
                first.id,
                loadout->
                    LoadoutService.ValidationResult.valid()
            );

        eq(
            PlayerLoadoutVersion.of(1L),
            v1Plan.loadout.version,
            "plan version"
        );

        check(
            service.acknowledgeApplied(v1Plan),
            "first apply acknowledgement"
        );
        check(
            !service.acknowledgeApplied(v1Plan),
            "same version apply idempotent"
        );

        PlayerLoadout second=
            fixture(
                2L,
                "item:fixture-axe",
                "pet:fixture-b"
            );

        expect(
            IllegalStateException.class,
            ()->service.replace(
                second,
                PlayerLoadoutVersion.of(2L)
            ),
            "expected-current conflict"
        );

        LoadoutService.Snapshot replaced=
            service.replace(
                second,
                PlayerLoadoutVersion.of(1L)
            );

        eq(
            PlayerLoadoutVersion.of(2L),
            replaced.loadout.version,
            "replacement version"
        );
        eq(
            PlayerLoadoutVersion.of(1L),
            replaced.lastAppliedVersion,
            "last applied revision preserved"
        );

        expect(
            IllegalStateException.class,
            ()->service.acknowledgeApplied(v1Plan),
            "stale apply plan fails closed"
        );

        PlayerLoadout skipped=
            fixture(
                4L,
                "item:fixture-skip",
                "pet:fixture-c"
            );

        expect(
            IllegalStateException.class,
            ()->service.replace(
                skipped,
                PlayerLoadoutVersion.of(2L)
            ),
            "version skip fails closed"
        );

        eq(
            PlayerLoadoutVersion.of(2L),
            service.get(
                first.ownerRef,
                first.id
            ).loadout.version,
            "failed replacement unchanged"
        );

        expect(
            IllegalStateException.class,
            ()->service.planApply(
                first.ownerRef,
                first.id,
                loadout->
                    LoadoutService.ValidationResult.invalid(
                        Arrays.asList(
                            "CUSTOM_LOCALLAB missing item",
                            "CUSTOM_LOCALLAB pet unavailable"
                        )
                    )
            ),
            "invalid plan fails closed"
        );

        LoadoutService.LoadoutApplyPlan v2Plan=
            service.planApply(
                first.ownerRef,
                first.id,
                loadout->
                    LoadoutService.ValidationResult.valid()
            );

        check(
            service.acknowledgeApplied(v2Plan),
            "new revision acknowledgement"
        );

        eq(
            PlayerLoadoutVersion.of(2L),
            service.get(
                first.ownerRef,
                first.id
            ).lastAppliedVersion,
            "new revision recorded"
        );

        expect(
            UnsupportedOperationException.class,
            ()->service.snapshot().clear(),
            "service snapshot immutability"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerLoadout(
                PlayerLoadoutId.of("custom:duplicate-slot"),
                PlayerLoadoutVersion.of(1L),
                "player:test",
                Collections.emptyList(),
                Arrays.asList(
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon",
                        "item:a",
                        1L
                    ),
                    new PlayerLoadout.EquipmentEntry(
                        "slot:weapon",
                        "item:b",
                        1L
                    )
                ),
                null,
                null,
                "CUSTOM_LOCALLAB"
            ),
            "duplicate semantic equipment slot"
        );

        protocolBoundaryGuard();

        System.out.println(
            "ISSUE171_PLAYER_LOADOUT_PASS "+
            "versioned=true "+
            "immutable=true "+
            "validationSeparated=true "+
            "applyPlanOnly=true "+
            "stalePlanGuard=true "+
            "terminalAckIdempotent=true "+
            "liveMutation=false "+
            "protocolIndependent=true "+
            "authorityPreserved=true "+
            "loadouts="+service.size()
        );
    }

    private static PlayerLoadout fixture(
        long version,
        String weapon,
        String pet
    ){
        return new PlayerLoadout(
            PlayerLoadoutId.of("custom:pvp-primary"),
            PlayerLoadoutVersion.of(version),
            "player:alice",
            Collections.singletonList(
                new PlayerLoadout.InventoryEntry(
                    "item:fixture-food",
                    10L
                )
            ),
            Collections.singletonList(
                new PlayerLoadout.EquipmentEntry(
                    "slot:weapon",
                    weapon,
                    1L
                )
            ),
            new PlayerLoadout.SkillProfile(
                Arrays.asList(
                    new PlayerLoadout.SkillValue(
                        "skill:attack",
                        60L
                    ),
                    new PlayerLoadout.SkillValue(
                        "skill:strength",
                        70L
                    )
                )
            ),
            new PlayerLoadout.PetSelection(pet),
            "CUSTOM_LOCALLAB"
        );
    }

    private static void protocolBoundaryGuard(){
        Class<?>[] classes={
            PlayerLoadoutId.class,
            PlayerLoadoutVersion.class,
            PlayerLoadout.class,
            PlayerLoadout.InventoryEntry.class,
            PlayerLoadout.EquipmentEntry.class,
            PlayerLoadout.SkillValue.class,
            PlayerLoadout.SkillProfile.class,
            PlayerLoadout.PetSelection.class,
            LoadoutService.class,
            LoadoutService.Snapshot.class,
            LoadoutService.LoadoutApplyPlan.class
        };

        String[] banned={
            "widget",
            "opcode",
            "subtype",
            "packet",
            "clientclass",
            "socket",
            "session",
            "commands",
            "commandstring",
            "clientslot"
        };

        for(Class<?> type:classes){
            for(Field field:type.getDeclaredFields()){
                String name=
                    field.getName().toLowerCase(
                        Locale.ROOT
                    );

                for(String token:banned){
                    if(name.contains(token))
                        fail(
                            "protocol identity leaked "+
                            type.getName()+"."+
                            field.getName()
                        );
                }

                String fieldType=
                    field.getType().getName();

                if(fieldType.equals(
                        "spk.local.Inventory")||
                   fieldType.equals(
                        "spk.local.EquipmentState")||
                   fieldType.contains(
                        "PetRuntime")||
                   fieldType.contains(
                        "SkillRuntime"))
                    fail(
                        "live mutation dependency "+
                        type.getName()+"."+
                        field.getName()
                    );
            }
        }
    }

    private static void check(
        boolean condition,
        String label
    ){
        if(!condition)
            fail(label);
    }

    private static void eq(
        Object expected,
        Object actual,
        String label
    ){
        if(!Objects.equals(expected,actual))
            fail(
                label+
                " expected="+expected+
                " actual="+actual
            );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Throwing action,
        String label
    ){
        try{
            action.run();
            fail(
                label+
                " did not throw "+
                type.getSimpleName()
            );
        }catch(Throwable error){
            if(!type.isInstance(error))
                fail(
                    label+
                    " threw "+
                    error
                );
        }
    }

    private static void fail(String message){
        throw new AssertionError(message);
    }

    private interface Throwing {
        void run() throws Exception;
    }
}
