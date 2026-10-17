package spk.local;

import java.util.*;

public final class GroundItemBatchSettlementTest {
    public static void main(String[] args){
        distinctAndMergedBatch();
        duplicateRowsCanonicalize();
        overflowRollsBackWholeBatch();
        invalidLaterRowRollsBackWholeBatch();

        System.out.println(
            "GROUND_ITEM_BATCH_SETTLEMENT_PASS "+
            "atomicBatch=true "+
            "mergePreflight=true "+
            "overflowRollback=true "+
            "invalidRollback=true "+
            "deterministicOrder=true "+
            "policyOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void distinctAndMergedBatch(){
        GroundItemRegistry registry=
            new GroundItemRegistry();

        Tile tileA=
            new Tile(
                3200,
                3200,
                0
            );
        Tile tileB=
            new Tile(
                3201,
                3200,
                0
            );

        GroundItem existing=
            registry.add(
                995,
                100,
                tileA,
                "player:a",
                3L,
                false
            );

        List<GroundItem> result=
            registry.addBatch(
                Arrays.asList(
                    new GroundItemRegistry.AddRequest(
                        995,
                        50,
                        tileA,
                        "player:a",
                        10L,
                        false
                    ),
                    new GroundItemRegistry.AddRequest(
                        4151,
                        1,
                        tileB,
                        "player:a",
                        10L,
                        false
                    ),
                    new GroundItemRegistry.AddRequest(
                        995,
                        25,
                        tileA,
                        "player:a",
                        11L,
                        false
                    )
                )
            );

        require(
            result.size()==2&&
            result.get(0)==existing&&
            result.get(0).amount==175&&
            result.get(0).spawnedTick==3L&&
            result.get(1).itemId==4151&&
            result.get(1).amount==1&&
            registry.size()==2,
            "distinct + existing merge"
        );

        expect(
            UnsupportedOperationException.class,
            ()->result.add(existing),
            "result list mutable"
        );
    }

    private static void duplicateRowsCanonicalize(){
        GroundItemRegistry registry=
            new GroundItemRegistry();
        Tile tile=
            new Tile(
                3210,
                3210,
                0
            );

        List<GroundItem> result=
            registry.addBatch(
                Arrays.asList(
                    new GroundItemRegistry.AddRequest(
                        1337,
                        2,
                        tile,
                        null,
                        7L,
                        false
                    ),
                    new GroundItemRegistry.AddRequest(
                        1337,
                        3,
                        tile,
                        null,
                        9L,
                        false
                    )
                )
            );

        require(
            result.size()==1&&
            result.get(0).amount==5&&
            result.get(0).spawnedTick==7L&&
            registry.size()==1,
            "duplicate rows not canonicalized"
        );
    }

    private static void overflowRollsBackWholeBatch(){
        GroundItemRegistry registry=
            new GroundItemRegistry();
        Tile tile=
            new Tile(
                3220,
                3220,
                0
            );
        Tile other=
            new Tile(
                3221,
                3220,
                0
            );

        GroundItem existing=
            registry.add(
                995,
                Integer.MAX_VALUE-5,
                tile,
                "player:b",
                1L,
                false
            );

        List<GroundItem> before=
            registry.snapshot();

        expect(
            IllegalStateException.class,
            ()->registry.addBatch(
                Arrays.asList(
                    new GroundItemRegistry.AddRequest(
                        4151,
                        1,
                        other,
                        "player:b",
                        2L,
                        false
                    ),
                    new GroundItemRegistry.AddRequest(
                        995,
                        10,
                        tile,
                        "player:b",
                        2L,
                        false
                    )
                )
            ),
            "later merge overflow"
        );

        List<GroundItem> after=
            registry.snapshot();

        require(
            after.size()==1&&
            after.get(0)==existing&&
            after.get(0).amount==
                Integer.MAX_VALUE-5&&
            registry.find(
                4151,
                other.x,
                other.y,
                other.plane
            )==null&&
            before.size()==after.size(),
            "overflow partially mutated batch"
        );
    }

    private static void invalidLaterRowRollsBackWholeBatch(){
        GroundItemRegistry registry=
            new GroundItemRegistry();
        Tile tile=
            new Tile(
                3230,
                3230,
                0
            );

        GroundItemRegistry.AddRequest valid=
            new GroundItemRegistry.AddRequest(
                995,
                100,
                tile,
                null,
                -1L,
                false
            );

        expect(
            NullPointerException.class,
            ()->registry.addBatch(
                Arrays.asList(
                    valid,
                    null
                )
            ),
            "null later row"
        );

        require(
            registry.size()==0&&
            registry.snapshot().isEmpty(),
            "invalid later row partially mutated batch"
        );

        GroundItem legacy=
            registry.add(
                995,
                1,
                tile,
                null,
                -1L,
                false
            );

        require(
            legacy.spawnedTick==-1L,
            "legacy negative tick compatibility changed"
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        ThrowingRunnable action,
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private GroundItemBatchSettlementTest(){}
}
