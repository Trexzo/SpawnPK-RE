package spk.local;

import java.lang.reflect.Field;
import java.util.Map;

public final class WorldPetNpcReplacementAtomicityTest {
    public static void main(String[] args)throws Exception{
        WorldNpcRegistry registry=
            new WorldNpcRegistry();

        WorldPetNpcService pets=
            new WorldPetNpcService(
                registry
            );

        EntityId owner=
            EntityId.next();

        WorldNpc main=
            pets.ensureMain(
                owner,
                12000,
                29999,
                3087,
                3495,
                0
            );

        WorldNpc mini=
            pets.ensureMini(
                owner,
                12001,
                30000,
                3088,
                3495,
                0
            );

        EntityId mainId=main.id;
        EntityId miniId=mini.id;
        Tile mainTile=main.tile();
        Tile miniTile=mini.tile();

        if(registry.size()!=2||
           pets.ownerActorCount(owner)!=2)
            throw new AssertionError(
                "fixture ownership missing"
            );

        assertRejected(
            ()->pets.ensureMain(
                owner,
                16384,
                29999,
                3200,
                3200,
                0
            )
        );

        assertUnchanged(
            "main definition",
            registry,
            pets,
            owner,
            mainId,
            miniId,
            mainTile,
            miniTile
        );

        assertRejected(
            ()->pets.ensureMain(
                owner,
                12000,
                29999,
                3200,
                3200,
                4
            )
        );

        assertUnchanged(
            "main plane",
            registry,
            pets,
            owner,
            mainId,
            miniId,
            mainTile,
            miniTile
        );

        assertRejected(
            ()->pets.ensureMini(
                owner,
                12001,
                -2,
                3200,
                3200,
                0
            )
        );

        assertUnchanged(
            "mini source item",
            registry,
            pets,
            owner,
            mainId,
            miniId,
            mainTile,
            miniTile
        );

        int ownersBefore=
            ownerMapSize(pets);

        EntityId unseenOwner=
            EntityId.next();

        assertRejected(
            ()->pets.ensureMini(
                unseenOwner,
                -1,
                30000,
                3000,
                3000,
                0
            )
        );

        if(ownerMapSize(pets)!=ownersBefore)
            throw new AssertionError(
                "rejected unseen owner created empty actor slot"
            );

        WorldNpc replacement=
            pets.ensureMain(
                owner,
                12002,
                30001,
                3090,
                3497,
                0
            );

        if(replacement.id.equals(mainId))
            throw new AssertionError(
                "valid replacement retained old identity"
            );

        if(registry.byId(mainId)!=null)
            throw new AssertionError(
                "valid replacement left old main registered"
            );

        if(registry.byId(replacement.id)!=
                replacement)
            throw new AssertionError(
                "valid replacement missing from registry"
            );

        if(!replacement.id.equals(
                pets.main(owner).id))
            throw new AssertionError(
                "owner main mapping not updated"
            );

        if(!miniId.equals(
                pets.mini(owner).id))
            throw new AssertionError(
                "valid main replacement disturbed mini"
            );

        if(registry.size()!=2||
           pets.ownerActorCount(owner)!=2)
            throw new AssertionError(
                "valid replacement changed actor cardinality"
            );

        System.out.println(
            "WORLD_PET_NPC_REPLACEMENT_ATOMICITY_PASS "+
            "invalidMainDefinitionPreserved=true "+
            "invalidMainPlanePreserved=true "+
            "invalidMiniSourcePreserved=true "+
            "unseenOwnerNoResidue=true "+
            "validReplacement=true"
        );
    }

    private static void assertUnchanged(
        String label,
        WorldNpcRegistry registry,
        WorldPetNpcService pets,
        EntityId owner,
        EntityId expectedMainId,
        EntityId expectedMiniId,
        Tile expectedMainTile,
        Tile expectedMiniTile
    ){
        WorldNpc main=pets.main(owner);
        WorldNpc mini=pets.mini(owner);

        if(main==null||
           !expectedMainId.equals(main.id)||
           registry.byId(expectedMainId)!=main||
           !expectedMainTile.equals(main.tile()))
            throw new AssertionError(
                label+" changed canonical main"
            );

        if(mini==null||
           !expectedMiniId.equals(mini.id)||
           registry.byId(expectedMiniId)!=mini||
           !expectedMiniTile.equals(mini.tile()))
            throw new AssertionError(
                label+" changed canonical mini"
            );

        if(registry.size()!=2||
           pets.ownerActorCount(owner)!=2)
            throw new AssertionError(
                label+" changed registry cardinality"
            );
    }

    private static void assertRejected(
        ThrowingAction action
    )throws Exception{
        boolean rejected=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "invalid replacement accepted"
            );
    }

    @SuppressWarnings("unchecked")
    private static int ownerMapSize(
        WorldPetNpcService pets
    )throws Exception{
        Field field=
            WorldPetNpcService.class
                .getDeclaredField(
                    "byOwner"
                );

        field.setAccessible(true);

        return ((Map<EntityId,?>)
            field.get(pets)).size();
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run()throws Exception;
    }
}