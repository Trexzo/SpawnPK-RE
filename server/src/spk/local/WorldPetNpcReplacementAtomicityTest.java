package spk.local;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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

        withForcedNextEntityId(
            mainId.value,
            ()->assertStateRejected(
                ()->pets.ensureMain(
                    owner,
                    12002,
                    30001,
                    3090,
                    3497,
                    0
                )
            )
        );

        assertUnchanged(
            "main id collision",
            registry,
            pets,
            owner,
            mainId,
            miniId,
            mainTile,
            miniTile
        );

        if(pets.main(owner)!=main)
            throw new AssertionError(
                "main id collision replaced exact canonical actor"
            );

        withForcedNextEntityId(
            miniId.value,
            ()->assertStateRejected(
                ()->pets.ensureMini(
                    owner,
                    12003,
                    30002,
                    3091,
                    3498,
                    0
                )
            )
        );

        assertUnchanged(
            "mini id collision",
            registry,
            pets,
            owner,
            mainId,
            miniId,
            mainTile,
            miniTile
        );

        if(pets.mini(owner)!=mini)
            throw new AssertionError(
                "mini id collision replaced exact canonical actor"
            );

        int collisionOwnersBefore=
            ownerMapSize(pets);

        EntityId collisionOwner=
            EntityId.next();

        withForcedNextEntityId(
            mainId.value,
            ()->assertStateRejected(
                ()->pets.ensureMain(
                    collisionOwner,
                    12004,
                    30003,
                    3092,
                    3499,
                    0
                )
            )
        );

        if(ownerMapSize(pets)!=
                collisionOwnersBefore)
            throw new AssertionError(
                "failed initial collision created empty owner slot"
            );

        if(pets.main(collisionOwner)!=null||
           pets.mini(collisionOwner)!=null)
            throw new AssertionError(
                "failed initial collision published owner actor"
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
            "duplicateMainCollisionPreserved=true "+
            "duplicateMiniCollisionPreserved=true "+
            "duplicateInitialCollisionNoResidue=true "+
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

    private static void assertStateRejected(
        ThrowingAction action
    )throws Exception{
        boolean rejected=false;

        try{
            action.run();
        }catch(IllegalStateException expected){
            rejected=
                expected.getMessage()!=null&&
                expected.getMessage().startsWith(
                    "duplicate world npc id "
                );
        }

        if(!rejected)
            throw new AssertionError(
                "canonical id collision replacement accepted"
            );
    }

    private static void withForcedNextEntityId(
        long value,
        ThrowingAction action
    )throws Exception{
        Field field=
            EntityId.class.getDeclaredField(
                "IDS"
            );
        field.setAccessible(true);

        AtomicLong ids=
            (AtomicLong)field.get(null);

        long restore=ids.get();

        try{
            ids.set(value);
            action.run();
        }finally{
            ids.updateAndGet(
                current->Math.max(
                    current,
                    restore
                )
            );
        }
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