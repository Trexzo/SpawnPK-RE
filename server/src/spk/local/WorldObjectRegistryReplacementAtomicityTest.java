package spk.local;

public final class WorldObjectRegistryReplacementAtomicityTest {
    public static void main(String[] args){
        WorldObjectRegistry registry=
            new WorldObjectRegistry();

        Tile tile=
            new Tile(
                3087,
                3495,
                0
            );

        WorldObject original=
            registry.put(
                100,
                tile,
                10,
                0,
                false
            );

        if(registry.size()!=1||
           registry.findAt(tile,10)!=original)
            throw new AssertionError(
                "initial object registration failed"
            );

        assertRejectedKeepsOriginal(
            registry,
            original,
            -1,
            tile,
            10,
            0
        );

        assertRejectedKeepsOriginal(
            registry,
            original,
            101,
            tile,
            10,
            4
        );

        assertRejectedKeepsOriginal(
            registry,
            original,
            101,
            tile,
            23,
            0
        );

        boolean nullTileRejected=false;

        try{
            registry.put(
                101,
                null,
                10,
                0,
                false
            );
        }catch(IllegalArgumentException expected){
            nullTileRejected=true;
        }

        if(!nullTileRejected||
           registry.size()!=1||
           registry.findAt(tile,10)!=original)
            throw new AssertionError(
                "null-tile rejection mutated registry"
            );

        WorldObject replacement=
            registry.put(
                200,
                tile,
                10,
                2,
                true
            );

        if(registry.size()!=1||
           registry.findAt(tile,10)!=replacement||
           replacement==original)
            throw new AssertionError(
                "valid replacement failed"
            );

        if(replacement.id!=original.id+1)
            throw new AssertionError(
                "invalid replacements consumed object ids original="+
                original.id+
                " replacement="+
                replacement.id
            );

        if(replacement.objectId!=200||
           replacement.rotation!=2||
           !replacement.devOwned)
            throw new AssertionError(
                "replacement fields changed"
            );

        System.out.println(
            "WORLD_OBJECT_REGISTRY_REPLACEMENT_ATOMICITY_PASS "+
            "invalidObjectRejected=true "+
            "invalidRotationRejected=true "+
            "invalidShapeRejected=true "+
            "nullTileRejected=true "+
            "existingPreserved=true "+
            "idNotConsumed=true "+
            "validReplacement=true"
        );
    }

    private static void assertRejectedKeepsOriginal(
        WorldObjectRegistry registry,
        WorldObject original,
        int objectId,
        Tile tile,
        int shape,
        int rotation
    ){
        boolean rejected=false;

        try{
            registry.put(
                objectId,
                tile,
                shape,
                rotation,
                false
            );
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "invalid replacement accepted objectId="+
                objectId+
                " shape="+shape+
                " rotation="+rotation
            );

        if(registry.size()!=1||
           registry.findAt(
               original.tile,
               original.shape
           )!=original)
            throw new AssertionError(
                "invalid replacement removed original object"
            );
    }
}