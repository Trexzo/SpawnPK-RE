package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class MonsterSpawnerServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_MONSTER_SPAWNER";

    public static void main(String[] args)throws Exception{
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                registry
            );

        MonsterSpawnerService.CatalogSnapshot catalog=
            service.replaceCatalog(
                Arrays.asList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "npc:test:one",
                        100
                    ),
                    new MonsterSpawnerService.CatalogEntry(
                        21,
                        "npc:test:last",
                        200
                    )
                ),
                "CALLER_DEFINED_TEST_CATALOG"
            );

        require(
            catalog.entries.size()==2&&
            catalog.row(0).definitionId==100&&
            catalog.row(21).definitionId==200,
            "Monster Spawner catalog"
        );

        expect(
            IllegalArgumentException.class,
            ()->new MonsterSpawnerService.CatalogEntry(
                22,
                "npc:invalid:row",
                300
            ),
            "row 22 accepted"
        );

        List<MonsterSpawnerService.CatalogEntry>
            duplicateRows=
                Arrays.asList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "npc:a",
                        101
                    ),
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "npc:b",
                        102
                    )
                );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceCatalog(
                duplicateRows,
                "BAD"
            ),
            "duplicate catalog row"
        );

        require(
            service.catalog().entries.size()==2&&
            service.catalog().row(21)!=null,
            "failed catalog replacement mutated live catalog"
        );

        service.openSession(
            "player:a",
            POLICY
        );
        service.openSession(
            "player:b",
            POLICY
        );

        expect(
            IllegalStateException.class,
            ()->service.activate(
                "player:a",
                2
            ),
            "activation without selection"
        );

        MonsterSpawnerService.SessionSnapshot selected=
            service.selectRow(
                "player:a",
                0
            );

        require(
            selected.hasSelection()&&
            "npc:test:one".equals(
                selected.selectedSemanticKey
            )&&
            selected.selectedDefinitionId==100,
            "Monster Spawner selection"
        );

        MonsterSpawnerService.SessionSnapshot active=
            service.activate(
                "player:a",
                2
            );

        // Synthetic budget 2 intentionally proves visible client x5 is not
        // hardcoded as server authority.
        require(
            active.active&&
            active.remainingSpawnBudget==2,
            "caller-defined Monster Spawner budget"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.spawnSelected(
                "player:a",
                3200,
                3200,
                4
            ),
            "invalid plane spawn"
        );

        MonsterSpawnerService.SessionSnapshot
            afterInvalid=
                service.getSession(
                    "player:a"
                );

        require(
            afterInvalid.active&&
            afterInvalid.remainingSpawnBudget==2&&
            afterInvalid.spawnedNpcIds.isEmpty()&&
            registry.size()==0,
            "failed spawn consumed Monster Spawner state"
        );

        MonsterSpawnerService.SpawnResult first=
            service.spawnSelected(
                "player:a",
                3200,
                3200,
                0
            );

        require(
            first.npc.definitionId==100&&
            !first.npc.owned()&&
            first.npc.ownerId==null&&
            first.npc.sourceItemId==-1&&
            registry.byId(
                first.npc.id
            )==first.npc&&
            first.session.active&&
            first.session.remainingSpawnBudget==1&&
            first.session.tracks(
                first.npc.id
            ),
            "first canonical Monster Spawner spawn"
        );

        MonsterSpawnerService.SpawnResult second=
            service.spawnSelected(
                "player:a",
                3201,
                3200,
                0
            );

        require(
            !second.session.active&&
            second.session.remainingSpawnBudget==0&&
            second.session.spawnedNpcIds.size()==2&&
            registry.size()==2,
            "Monster Spawner budget exhaustion"
        );

        expect(
            IllegalStateException.class,
            ()->service.spawnSelected(
                "player:a",
                3202,
                3200,
                0
            ),
            "spawn after budget exhaustion"
        );

        service.selectRow(
            "player:b",
            21
        );
        service.activate(
            "player:b",
            1
        );

        MonsterSpawnerService.SpawnResult bSpawn=
            service.spawnSelected(
                "player:b",
                3300,
                3300,
                1
            );

        require(
            bSpawn.npc.definitionId==200&&
            service.getSession(
                "player:a"
            ).spawnedNpcIds.size()==2&&
            service.getSession(
                "player:b"
            ).spawnedNpcIds.size()==1,
            "Monster Spawner owner/session isolation"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.despawnTracked(
                "player:a",
                bSpawn.npc.id
            ),
            "foreign Monster Spawner despawn"
        );

        require(
            registry.remove(
                bSpawn.npc.id
            ),
            "remove tracked player:b NPC externally"
        );

        expect(
            IllegalStateException.class,
            ()->service.despawnTracked(
                "player:b",
                bSpawn.npc.id
            ),
            "missing canonical tracked NPC despawn"
        );

        require(
            service.getSession(
                "player:b"
            ).tracks(
                bSpawn.npc.id
            ),
            "failed exact-object despawn consumed tracking"
        );

        MonsterSpawnerService.SessionSnapshot
            afterDespawn=
                service.despawnTracked(
                    "player:a",
                    first.npc.id
                );

        require(
            !afterDespawn.tracks(
                first.npc.id
            )&&
            registry.byId(
                first.npc.id
            )==null&&
            registry.byId(
                second.npc.id
            )==second.npc,
            "tracked Monster Spawner despawn"
        );

        expect(
            IllegalStateException.class,
            ()->service.replaceCatalog(
                Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        21,
                        "npc:only:last",
                        201
                    )
                ),
                "REMOVES_SELECTED_ROW"
            ),
            "catalog replacement removed selected row"
        );

        require(
            service.catalog().row(0)!=null&&
            service.catalog().row(21)!=null,
            "selected-row catalog rejection not atomic"
        );

        boolean immutable=false;

        try{
            service.getSession(
                "player:a"
            ).spawnedNpcIds.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Monster Spawner snapshot mutable"
        );

        selectedRowIdentityStable();
        sessionRetirement();
        exactSessionRetirement();
        sessionPresentationOwnership();
        catalogPresentationOwnership();
        protocolBoundary();

        System.out.println(
            "MONSTER_SPAWNER_SERVICE_PASS "+
            "clientRows0to21=true "+
            "catalogCallerDefined=true "+
            "catalogReplaceAtomic=true "+
            "selectedRowIdentityStable=true "+
            "ownerSessionIsolation=true "+
            "selectionRequired=true "+
            "callerBudget=true "+
            "x5Hardcoded=false "+
            "canonicalWorldNpcSpawn=true "+
            "spawnTrackingLinearized=true "+
            "failedSpawnAtomic=true "+
            "budgetExhaustionDeactivates=true "+
            "trackedDespawn=true "+
            "exactTrackedNpcDespawn=true "+
            "missingCanonicalDespawnRetainsTracking=true "+
            "foreignDespawnRejected=true "+
            "idleSessionRetirement=true "+
            "trackedSessionRetained=true "+
            "sameOwnerReopen=true "+
            "exactSessionRetirement=true "+
            "changedSessionRetirementRejected=true "+
            "sessionPresentationAtomic=true "+
            "stalePresentationRejected=true "+
            "staleCatalogPresentationRejected=true "+
            "ownerIdAbuse=false "+
            "sourceItemIdAbuse=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void selectedRowIdentityStable(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                registry
            );

        service.replaceCatalog(
            Arrays.asList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "npc:selected",
                    100
                ),
                new MonsterSpawnerService.CatalogEntry(
                    1,
                    "npc:unselected",
                    101
                )
            ),
            "IDENTITY_BASE"
        );

        service.openSession(
            "player:identity",
            POLICY
        );
        service.selectRow(
            "player:identity",
            0
        );
        service.activate(
            "player:identity",
            1
        );

        expect(
            IllegalStateException.class,
            ()->service.replaceCatalog(
                Arrays.asList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "npc:retargeted",
                        100
                    ),
                    new MonsterSpawnerService.CatalogEntry(
                        1,
                        "npc:unselected",
                        101
                    )
                ),
                "IDENTITY_SEMANTIC_DRIFT"
            ),
            "selected semantic key changed"
        );

        expect(
            IllegalStateException.class,
            ()->service.replaceCatalog(
                Arrays.asList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "npc:selected",
                        200
                    ),
                    new MonsterSpawnerService.CatalogEntry(
                        1,
                        "npc:unselected",
                        101
                    )
                ),
                "IDENTITY_DEFINITION_DRIFT"
            ),
            "selected definition changed"
        );

        MonsterSpawnerService.CatalogSnapshot
            afterRejected=
                service.catalog();

        require(
            "npc:selected".equals(
                afterRejected.row(0).semanticKey
            )&&
            afterRejected.row(0).definitionId==100&&
            service.getSession(
                "player:identity"
            ).selectedDefinitionId==100,
            "failed identity replacement mutated selection"
        );

        MonsterSpawnerService.CatalogSnapshot
            accepted=
                service.replaceCatalog(
                    Arrays.asList(
                        new MonsterSpawnerService.CatalogEntry(
                            0,
                            "npc:selected",
                            100
                        ),
                        new MonsterSpawnerService.CatalogEntry(
                            1,
                            "npc:unselected:new",
                            202
                        )
                    ),
                    "IDENTITY_UNSELECTED_REPLACEMENT"
                );

        require(
            accepted.row(0).definitionId==100&&
            "npc:unselected:new".equals(
                accepted.row(1).semanticKey
            )&&
            accepted.row(1).definitionId==202,
            "unselected catalog row replacement"
        );

        MonsterSpawnerService.SpawnResult spawn=
            service.spawnSelected(
                "player:identity",
                3400,
                3400,
                0
            );

        require(
            spawn.npc.definitionId==100&&
            "npc:selected".equals(
                spawn.session.selectedSemanticKey
            )&&
            spawn.session.selectedDefinitionId==100,
            "selected identity drifted after accepted catalog replacement"
        );
    }

    private static void sessionRetirement(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                registry
            );

        service.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "npc:retirement",
                    310
                )
            ),
            "RETIREMENT_CATALOG"
        );

        service.openSession(
            "player:retire",
            POLICY
        );
        service.selectRow(
            "player:retire",
            0
        );
        service.activate(
            "player:retire",
            2
        );

        require(
            service.retireSessionIfNoTrackedNpcs(
                "player:retire"
            )&&
            service.getSession(
                "player:retire"
            )==null&&
            service.sessionCount()==0,
            "idle active session retirement"
        );

        service.openSession(
            "player:retire",
            POLICY
        );
        service.selectRow(
            "player:retire",
            0
        );
        service.activate(
            "player:retire",
            1
        );

        MonsterSpawnerService.SpawnResult spawn=
            service.spawnSelected(
                "player:retire",
                3200,
                3200,
                0
            );

        require(
            !service.retireSessionIfNoTrackedNpcs(
                "player:retire"
            )&&
            service.getSession(
                "player:retire"
            ).tracks(
                spawn.npc.id
            ),
            "tracked session retired"
        );

        service.despawnTracked(
            "player:retire",
            spawn.npc.id
        );

        require(
            service.retireSessionIfNoTrackedNpcs(
                "player:retire"
            )&&
            service.getSession(
                "player:retire"
            )==null,
            "retirement after tracked NPC release"
        );

        service.openSession(
            "player:retire",
            POLICY
        );

        require(
            service.getSession(
                "player:retire"
            )!=null&&
            !service.retireSessionIfNoTrackedNpcs(
                "missing-owner"
            ),
            "same owner reopen / absent retirement"
        );
    }

    private static void exactSessionRetirement(){
        WorldNpcRegistry registry=
            new WorldNpcRegistry();
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                registry
            );

        service.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "npc:exact-retirement",
                    311
                )
            ),
            "EXACT_RETIREMENT_CATALOG"
        );

        MonsterSpawnerService.SessionSnapshot opened=
            service.openSession(
                "player:exact-retire",
                POLICY
            );

        require(
            service.retireSessionIfCurrentAndNoTrackedNpcs(
                "player:exact-retire",
                opened
            )&&
            service.getSession(
                "player:exact-retire"
            )==null,
            "exact unchanged session retirement"
        );

        MonsterSpawnerService.SessionSnapshot staleOpening=
            service.openSession(
                "player:exact-retire",
                POLICY
            );

        service.selectRow(
            "player:exact-retire",
            0
        );

        require(
            !service.retireSessionIfCurrentAndNoTrackedNpcs(
                "player:exact-retire",
                staleOpening
            )&&
            service.getSession(
                "player:exact-retire"
            )!=null&&
            service.getSession(
                "player:exact-retire"
            ).selectedRowIndex!=null,
            "changed session was retired by stale exact snapshot"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.retireSessionIfCurrentAndNoTrackedNpcs(
                "different-owner",
                staleOpening
            ),
            "exact retirement owner mismatch"
        );
    }

    private static void sessionPresentationOwnership()
        throws Exception{
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                new WorldNpcRegistry()
            );

        service.replaceCatalog(
            Arrays.asList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "npc:presentation-zero",
                    410
                ),
                new MonsterSpawnerService.CatalogEntry(
                    1,
                    "npc:presentation-one",
                    411
                )
            ),
            "PRESENTATION_ATOMIC_CATALOG"
        );
        service.openSession(
            "player:presentation",
            POLICY
        );

        MonsterSpawnerService.SessionSnapshot expected=
            service.selectRow(
                "player:presentation",
                0
            );

        final Thread[] mutator={null};
        final Throwable[] mutationFailure={null};

        service.presentSessionIfCurrent(
            "player:presentation",
            expected,
            current->{
                require(
                    current.selectedRowIndex!=null&&
                    current.selectedRowIndex.intValue()==0,
                    "presentation action current snapshot"
                );

                mutator[0]=
                    new Thread(
                        ()->{
                            try{
                                service.selectRow(
                                    "player:presentation",
                                    1
                                );
                            }catch(Throwable failure){
                                mutationFailure[0]=failure;
                            }
                        },
                        "monster-spawner-presentation-mutator"
                    );
                mutator[0].start();

                long deadline=
                    System.nanoTime()+
                    2_000_000_000L;

                while(mutator[0].isAlive()&&
                      mutator[0].getState()!=
                        Thread.State.BLOCKED&&
                      System.nanoTime()<deadline)
                    Thread.yield();

                require(
                    mutator[0].getState()==
                        Thread.State.BLOCKED,
                    "session mutation crossed presentation ownership"
                );
            }
        );

        mutator[0].join(
            2_000L
        );

        require(
            !mutator[0].isAlive()&&
            mutationFailure[0]==null&&
            service.getSession(
                "player:presentation"
            ).selectedRowIndex.intValue()==1,
            "presentation ownership did not release cleanly"
        );

        final int[] staleCalls={0};

        expect(
            IllegalStateException.class,
            ()->service.presentSessionIfCurrent(
                "player:presentation",
                expected,
                current->staleCalls[0]++
            ),
            "stale presentation snapshot"
        );

        require(
            staleCalls[0]==0,
            "stale presentation action executed"
        );
    }

    private static void catalogPresentationOwnership(){
        MonsterSpawnerService service=
            new MonsterSpawnerService(
                new WorldNpcRegistry()
            );

        MonsterSpawnerService.CatalogSnapshot expectedCatalog=
            service.replaceCatalog(
                Collections.singletonList(
                    new MonsterSpawnerService.CatalogEntry(
                        0,
                        "npc:catalog-presentation-zero",
                        420
                    )
                ),
                "PRESENTATION_CATALOG_A"
            );
        MonsterSpawnerService.SessionSnapshot expectedSession=
            service.openSession(
                "player:catalog-presentation",
                POLICY
            );

        final int[] calls={0};

        service.presentSessionCatalogIfCurrent(
            "player:catalog-presentation",
            expectedSession,
            expectedCatalog,
            current->calls[0]++
        );

        require(
            calls[0]==1,
            "current catalog presentation did not execute"
        );

        service.replaceCatalog(
            Collections.singletonList(
                new MonsterSpawnerService.CatalogEntry(
                    0,
                    "npc:catalog-presentation-replaced",
                    421
                )
            ),
            "PRESENTATION_CATALOG_B"
        );

        expect(
            IllegalStateException.class,
            ()->service.presentSessionCatalogIfCurrent(
                "player:catalog-presentation",
                expectedSession,
                expectedCatalog,
                current->calls[0]++
            ),
            "stale catalog presentation"
        );

        require(
            calls[0]==1,
            "stale catalog presentation action executed"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                MonsterSpawnerService.class,
                MonsterSpawnerService.CatalogEntry.class,
                MonsterSpawnerService.SessionSnapshot.class
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
                   name.contains("interface")||
                   name.contains("reward"))
                    throw new AssertionError(
                        "protocol/reward state leaked into Monster Spawner "+
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

    private MonsterSpawnerServiceTest(){}
}
