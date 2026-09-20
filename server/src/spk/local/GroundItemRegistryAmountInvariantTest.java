package spk.local;

public final class GroundItemRegistryAmountInvariantTest {
    public static void main(String[] args){
        GroundItemRegistry registry=
            new GroundItemRegistry();

        Tile tile=
            new Tile(
                3087,
                3495,
                0
            );

        GroundItem stack=
            registry.add(
                995,
                10,
                tile,
                "opensrc",
                1L,
                false
            );

        GroundItem merged=
            registry.add(
                995,
                5,
                tile,
                "opensrc",
                2L,
                false
            );

        if(merged!=stack||
           stack.amount!=15||
           registry.size()!=1)
            throw new AssertionError(
                "positive merge changed stack identity/state"
            );

        assertRejectedWithoutMutation(
            registry,
            stack,
            0,
            15,
            1,
            tile
        );

        assertRejectedWithoutMutation(
            registry,
            stack,
            -1,
            15,
            1,
            tile
        );

        Tile other=
            new Tile(
                3088,
                3495,
                0
            );

        boolean negativeNewRejected=false;

        try{
            registry.add(
                995,
                -5,
                other,
                "opensrc",
                3L,
                false
            );
        }catch(IllegalArgumentException expected){
            negativeNewRejected=true;
        }

        if(!negativeNewRejected||
           registry.size()!=1)
            throw new AssertionError(
                "negative new stack accepted or mutated registry"
            );

        GroundItem max=
            registry.add(
                1337,
                Integer.MAX_VALUE,
                other,
                null,
                4L,
                true
            );

        boolean overflowRejected=false;

        try{
            registry.add(
                1337,
                1,
                other,
                null,
                5L,
                true
            );
        }catch(IllegalStateException expected){
            overflowRejected=
                expected.getMessage()
                    .contains(
                        "ground amount overflow"
                    );
        }

        if(!overflowRejected)
            throw new AssertionError(
                "ground amount overflow accepted"
            );

        if(max.amount!=Integer.MAX_VALUE||
           registry.size()!=2)
            throw new AssertionError(
                "overflow rejection mutated registry amount="+
                max.amount+
                " size="+
                registry.size()
            );

        boolean badItemRejected=false;

        try{
            registry.add(
                -1,
                1,
                tile,
                null,
                6L,
                false
            );
        }catch(IllegalArgumentException expected){
            badItemRejected=true;
        }

        if(!badItemRejected||
           registry.size()!=2)
            throw new AssertionError(
                "negative item id accepted"
            );

        boolean nullTileRejected=false;

        try{
            registry.add(
                995,
                1,
                null,
                null,
                7L,
                false
            );
        }catch(IllegalArgumentException expected){
            nullTileRejected=true;
        }

        if(!nullTileRejected||
           registry.size()!=2)
            throw new AssertionError(
                "null tile accepted"
            );

        System.out.println(
            "GROUND_ITEM_REGISTRY_AMOUNT_INVARIANT_PASS "+
            "positiveMerge=true "+
            "zeroRejected=true "+
            "negativeRejected=true "+
            "failureAtomic=true "+
            "overflowRejected=true "+
            "badItemRejected=true "+
            "nullTileRejected=true"
        );
    }

    private static void assertRejectedWithoutMutation(
        GroundItemRegistry registry,
        GroundItem stack,
        int amount,
        int expectedAmount,
        int expectedSize,
        Tile tile
    ){
        boolean rejected=false;

        try{
            registry.add(
                stack.itemId,
                amount,
                tile,
                stack.owner,
                99L,
                stack.devOwned
            );
        }catch(IllegalArgumentException expected){
            rejected=true;
        }

        if(!rejected)
            throw new AssertionError(
                "non-positive existing-stack add accepted amount="+
                amount
            );

        if(stack.amount!=expectedAmount||
           registry.size()!=expectedSize)
            throw new AssertionError(
                "rejected add mutated state amount="+
                stack.amount+
                " size="+
                registry.size()
            );
    }
}