package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class CollectionLogApplicationServiceTest {
    private static final CollectionLogEvidenceAuthority
        POLICY=
            CollectionLogEvidenceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        CollectionLogDefinition.EntryId alphaA=
            new CollectionLogDefinition
                .EntryId(
                    "entry:alpha:a"
                );
        CollectionLogDefinition.EntryId alphaB=
            new CollectionLogDefinition
                .EntryId(
                    "entry:alpha:b"
                );
        CollectionLogDefinition.EntryId boxA=
            new CollectionLogDefinition
                .EntryId(
                    "entry:box:a"
                );

        CollectionLogDefinition alpha=
            definition(
                "collection:alpha",
                "bosses",
                "Alpha Boss",
                Arrays.asList(
                    alphaA,
                    alphaB
                ),
                true
            );

        CollectionLogDefinition box=
            definition(
                "collection:box",
                "boxes",
                "Example Box",
                Collections.singletonList(
                    boxA
                ),
                false
            );

        CollectionLogApplicationService service=
            new CollectionLogApplicationService(
                Arrays.asList(
                    application(
                        alpha,
                        Arrays.asList(
                            alphaA,
                            alphaB
                        ),
                        "Alpha completion",
                        "Externally-settled reward."
                    ),
                    application(
                        box,
                        Collections.singletonList(
                            boxA
                        ),
                        "Box completion",
                        "Externally-settled reward."
                    )
                )
            );

        CollectionLogApplicationService.PlayerSnapshot
            selected=
                service.selectCategory(
                    " Player:Alice ",
                    new CollectionLogDefinition
                        .CategoryId(
                            "bosses"
                        )
                );

        selected=
            service.selectCollection(
                "player:alice",
                alpha.id()
            );

        service.selectCategory(
            "player:bob",
            new CollectionLogDefinition
                .CategoryId(
                    "bosses"
                )
        );
        service.selectCollection(
            "player:bob",
            alpha.id()
        );

        require(
            "player:alice".equals(
                selected.playerRef
            )&&
            selected.selectedCollection()
                .collectionId
                .equals(
                    alpha.id()
                )&&
            selected.selectedCollection()
                .resultEntries
                .size()==2&&
            CollectionLogApplicationService
                .PRESENTATION_AUTHORITY
                .equals(
                    selected.selectedCollection()
                        .presentationAuthority
                ),
            "Collection Log selection/application projection"
        );

        expect(
            IllegalStateException.class,
            ()->service.selectCollection(
                "player:alice",
                box.id()
            ),
            "cross-category Collection Log selection"
        );

        CollectionLogApplicationService.DiscoveryResult
            first=
                service.recordValidatedDiscovery(
                    "player:alice",
                    alpha.id(),
                    alphaA
                );

        require(
            first.result==
                CollectionLogService
                    .DiscoveryResult
                    .FIRST_DISCOVERY&&
            first.collection.obtainedCount==1&&
            service.getPlayer(
                "player:bob"
            ).collection(
                alpha.id()
            ).obtainedCount==0,
            "Collection Log player-isolated discovery"
        );

        CollectionLogApplicationService.DiscoveryResult
            duplicate=
                service.recordValidatedDiscovery(
                    "player:alice",
                    alpha.id(),
                    alphaA
                );

        require(
            duplicate.result==
                CollectionLogService
                    .DiscoveryResult
                    .ALREADY_OBTAINED&&
            duplicate.collection
                .obtainedCount==1,
            "Collection Log duplicate discovery"
        );

        CollectionLogApplicationService.CollectionSnapshot
            kill=
                service.recordAuthoritativeKillCount(
                    "player:alice",
                    alpha.id(),
                    42L
                );

        require(
            kill.supportsKillCount()&&
            kill.killCount.longValue()==42L,
            "Collection Log authoritative kill count"
        );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmRewardSettledAndMarkClaimed(
                    "player:alice",
                    alpha.id()
                ),
            "incomplete Collection Log reward claim"
        );

        CollectionLogApplicationService.DiscoveryResult
            completed=
                service.recordValidatedDiscovery(
                    "player:alice",
                    alpha.id(),
                    alphaB
                );

        require(
            completed.result==
                CollectionLogService
                    .DiscoveryResult
                    .FIRST_DISCOVERY_COMPLETED&&
            completed.collection.complete&&
            completed.collection
                .rewardClaimable(),
            "Collection Log completion"
        );

        CollectionLogApplicationService.ClaimResult
            claimed=
                service
                    .confirmRewardSettledAndMarkClaimed(
                        "player:alice",
                        alpha.id()
                    );

        require(
            claimed.changed&&
            claimed.collection
                .completionRewardClaimed&&
            !claimed.collection
                .rewardClaimable()&&
            !service
                .confirmRewardSettledAndMarkClaimed(
                    "player:alice",
                    alpha.id()
                ).changed,
            "Collection Log post-settlement claim"
        );

        CollectionLogApplicationService.PlayerSnapshot
            switched=
                service.selectCategory(
                    "player:alice",
                    box.categoryId()
                );

        require(
            switched.selectedCollectionId==null&&
            switched.selectedCategoryId
                .equals(
                    box.categoryId()
                ),
            "Collection Log category change selection clear"
        );

        definitionGuards(alpha,alphaA);
        immutableCatalog(service);
        protocolBoundary();

        System.out.println(
            "COLLECTION_LOG_APPLICATION_PASS "+
            "playerIsolation=true "+
            "sharedCatalog=true "+
            "normalizedPlayerIdentity=true "+
            "categorySelection=true "+
            "collectionSelection=true "+
            "crossCategoryRejected=true "+
            "categoryChangeClearsSelection=true "+
            "maxResultSlots120=true "+
            "resultMappingValidated=true "+
            "duplicateDiscoveryIdempotent=true "+
            "killCountDelegated=true "+
            "incompleteRewardClaimRejected=true "+
            "externalRewardSettlementRequired=true "+
            "claimIdempotent=true "+
            "rewardPayloadAbsent=true "+
            "dropRatesAbsent=true "+
            "protocolIndependent=true"
        );
    }

    private static void definitionGuards(
        CollectionLogDefinition alpha,
        CollectionLogDefinition.EntryId
            alphaA
    ){
        ArrayList<
            CollectionLogDefinition.EntryId
        > tooMany=
            new ArrayList<>();

        LinkedHashSet<
            CollectionLogDefinition.EntryId
        > required=
            new LinkedHashSet<>();

        for(int i=0;i<121;i++){
            CollectionLogDefinition.EntryId id=
                new CollectionLogDefinition
                    .EntryId(
                        "entry:many:"+i
                    );
            tooMany.add(id);
            required.add(id);
        }

        CollectionLogDefinition many=
            new CollectionLogDefinition(
                new CollectionLogDefinition
                    .CollectionId(
                        "collection:many"
                    ),
                new CollectionLogDefinition
                    .CategoryId(
                        "other"
                    ),
                "Many",
                required,
                false,
                POLICY
            );

        expect(
            IllegalArgumentException.class,
            ()->application(
                many,
                tooMany,
                "Many reward",
                "Many reward description"
            ),
            "Collection Log >120 result slots"
        );

        expect(
            IllegalArgumentException.class,
            ()->application(
                alpha,
                Arrays.asList(
                    alphaA,
                    alphaA
                ),
                "Duplicate",
                "Duplicate description"
            ),
            "duplicate Collection Log result entry"
        );

        expect(
            IllegalArgumentException.class,
            ()->application(
                alpha,
                Collections.singletonList(
                    new CollectionLogDefinition
                        .EntryId(
                            "entry:not-required"
                        )
                ),
                "Unknown",
                "Unknown description"
            ),
            "Collection Log result not required"
        );

        CollectionLogDefinition wrong=
            new CollectionLogDefinition(
                new CollectionLogDefinition
                    .CollectionId(
                        "collection:wrong-authority"
                    ),
                new CollectionLogDefinition
                    .CategoryId(
                        "other"
                    ),
                "Wrong",
                new LinkedHashSet<
                    CollectionLogDefinition.EntryId
                >(
                    Collections.singletonList(
                        new CollectionLogDefinition
                            .EntryId(
                                "entry:wrong"
                            )
                    )
                ),
                false,
                CollectionLogEvidenceAuthority
                    .EXACT_CURRENT_CLIENT
            );

        expect(
            IllegalArgumentException.class,
            ()->new CollectionLogApplicationService
                .ApplicationDefinition(
                    wrong,
                    Collections.emptyList(),
                    "Wrong",
                    "Wrong",
                    POLICY
                ),
            "Collection Log application authority mismatch"
        );
    }

    private static CollectionLogDefinition definition(
        String collectionId,
        String categoryId,
        String name,
        Collection<
            CollectionLogDefinition.EntryId
        > entries,
        boolean supportsKillCount
    ){
        return new CollectionLogDefinition(
            new CollectionLogDefinition
                .CollectionId(
                    collectionId
                ),
            new CollectionLogDefinition
                .CategoryId(
                    categoryId
                ),
            name,
            new LinkedHashSet<
                CollectionLogDefinition.EntryId
            >(entries),
            supportsKillCount,
            POLICY
        );
    }

    private static CollectionLogApplicationService
        .ApplicationDefinition application(
            CollectionLogDefinition definition,
            Collection<
                CollectionLogDefinition.EntryId
            > resultEntries,
            String heading,
            String description
        ){
        return new CollectionLogApplicationService
            .ApplicationDefinition(
                definition,
                resultEntries,
                heading,
                description,
                POLICY
            );
    }

    private static void immutableCatalog(
        CollectionLogApplicationService service
    ){
        boolean immutable=false;

        try{
            service.catalog().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Collection Log catalog mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                CollectionLogApplicationService.class,
                CollectionLogApplicationService.ApplicationDefinition.class,
                CollectionLogApplicationService.CollectionSnapshot.class,
                CollectionLogApplicationService.PlayerSnapshot.class
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
                   name.contains("containerid")||
                   name.contains("rewarditem")||
                   name.contains("rewardamount")||
                   name.contains("droprate"))
                    throw new AssertionError(
                        "protocol/reward/drop-rate identity leaked into Collection Log "+
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

    private CollectionLogApplicationServiceTest(){}
}
