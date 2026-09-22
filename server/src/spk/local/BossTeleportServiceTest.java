package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class BossTeleportServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_BOSS_TELEPORT";

    public static void main(String[] args){
        ArrayList<String> executions=
            new ArrayList<>();

        BossTeleportService service=
            new BossTeleportService(
                (player,entry)->
                    "player:denied".equals(
                        player)
                        ?BossTeleportService
                            .EligibilityDecision
                            .deny(
                                "caller denied"
                            )
                        :BossTeleportService
                            .EligibilityDecision
                            .allow(),
                (player,target,authority)->{
                    executions.add(
                        player+"|"+
                        target+"|"+
                        authority
                    );

                    if("target:executor-fail"
                            .equals(target))
                        return BossTeleportService
                            .ExecutionResult
                            .failure(
                                "runtime refused"
                            );

                    return BossTeleportService
                        .ExecutionResult
                        .success();
                }
            );

        BossTeleportService.Snapshot catalog=
            service.replaceCatalog(
                Arrays.asList(
                    boss(
                        "boss:alpha",
                        "Alpha",
                        "target:alpha",
                        "drops:alpha"
                    ),
                    boss(
                        "boss:beta",
                        "Beta",
                        "target:executor-fail",
                        null
                    )
                )
            );

        require(
            catalog.entries.size()==2&&
            "drops:alpha".equals(
                catalog.boss(
                    "boss:alpha"
                ).fullDropTableKey
            ),
            "Boss Teleport catalog"
        );

        BossTeleportService.PlayerSnapshot
            alice=
                service.selectBoss(
                    " Player:Alice ",
                    "boss:alpha"
                );

        service.selectBoss(
            "player:bob",
            "boss:beta"
        );

        require(
            "player:alice".equals(
                alice.playerRef
            )&&
            "boss:alpha".equals(
                alice.selectedBossKey
            )&&
            "target:alpha".equals(
                alice.selectedTeleportTargetKey
            )&&
            "drops:alpha".equals(
                alice.selectedFullDropTableKey
            )&&
            "boss:beta".equals(
                service.getPlayer(
                    "player:bob"
                ).selectedBossKey
            ),
            "Boss Teleport player selection"
        );

        expect(
            IllegalStateException.class,
            ()->service.requestTeleport(
                "player:no-selection"
            ),
            "Boss Teleport without selection"
        );

        service.selectBoss(
            "player:denied",
            "boss:alpha"
        );

        int callsBefore=
            executions.size();

        BossTeleportService.TeleportRequestResult
            denied=
                service.requestTeleport(
                    "player:denied"
                );

        require(
            !denied.eligibilityAllowed&&
            !denied.executedSuccessfully&&
            executions.size()==callsBefore&&
            service.getPlayer(
                "player:denied"
            ).successfulTeleports==0L,
            "Boss Teleport eligibility denial"
        );

        BossTeleportService.TeleportRequestResult
            failed=
                service.requestTeleport(
                    "player:bob"
                );

        require(
            failed.eligibilityAllowed&&
            !failed.executedSuccessfully&&
            executions.get(
                executions.size()-1
            ).equals(
                "player:bob|target:executor-fail|"+
                POLICY
            )&&
            service.getPlayer(
                "player:bob"
            ).successfulTeleports==0L,
            "Boss Teleport executor failure"
        );

        BossTeleportService.TeleportRequestResult
            success=
                service.requestTeleport(
                    "player:alice"
                );

        require(
            success.eligibilityAllowed&&
            success.executedSuccessfully&&
            success.player
                .successfulTeleports==1L&&
            executions.get(
                executions.size()-1
            ).equals(
                "player:alice|target:alpha|"+
                POLICY
            ),
            "Boss Teleport success"
        );

        service.requestTeleport(
            "player:alice"
        );

        require(
            service.getPlayer(
                "player:alice"
            ).successfulTeleports==2L,
            "Boss Teleport success count"
        );

        catalogGuards(service);
        immutableSnapshot(service);
        protocolBoundary();

        System.out.println(
            "BOSS_TELEPORT_SERVICE_PASS "+
            "maxRows13=true "+
            "catalogReplaceAtomic=true "+
            "playerSelectionIsolation=true "+
            "selectedRowPreserved=true "+
            "missingSelectionRejected=true "+
            "eligibilityDelegated=true "+
            "movementExecutorInjected=true "+
            "executorFailureAtomic=true "+
            "successfulRequestCount=true "+
            "semanticTargetOnly=true "+
            "coordinatesOwned=false "+
            "dropContentsOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void catalogGuards(
        BossTeleportService service
    ){
        ArrayList<BossTeleportService.Entry>
            tooMany=
                new ArrayList<>();

        for(int i=0;i<14;i++)
            tooMany.add(
                boss(
                    "boss:many:"+i,
                    "Many "+i,
                    "target:many:"+i,
                    null
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceCatalog(
                tooMany
            ),
            "Boss Teleport accepted >13 rows"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceCatalog(
                Arrays.asList(
                    boss(
                        "boss:duplicate",
                        "One",
                        "target:one",
                        null
                    ),
                    boss(
                        "BOSS:DUPLICATE",
                        "Two",
                        "target:two",
                        null
                    )
                )
            ),
            "Boss Teleport duplicate bossKey"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceCatalog(
                Arrays.asList(
                    boss(
                        "boss:one",
                        "One",
                        "target:duplicate",
                        null
                    ),
                    boss(
                        "boss:two",
                        "Two",
                        "TARGET:DUPLICATE",
                        null
                    )
                )
            ),
            "Boss Teleport duplicate target"
        );

        require(
            service.size()==2,
            "failed Boss Teleport replacement mutated catalog"
        );

        expect(
            IllegalStateException.class,
            ()->service.replaceCatalog(
                Collections.singletonList(
                    boss(
                        "boss:alpha",
                        "Alpha",
                        "target:alpha",
                        "drops:alpha"
                    )
                )
            ),
            "catalog removed selected boss"
        );

        require(
            service.size()==2&&
            "boss:beta".equals(
                service.getPlayer(
                    "player:bob"
                ).selectedBossKey
            ),
            "selected-row preservation guard mutated state"
        );
    }

    private static BossTeleportService.Entry boss(
        String bossKey,
        String name,
        String target,
        String dropTable
    ){
        return new BossTeleportService.Entry(
            bossKey,
            name,
            "Caller-defined description for "+
                name,
            target,
            dropTable,
            Arrays.asList(
                "Presentation detail A",
                "Presentation detail B"
            ),
            POLICY
        );
    }

    private static void immutableSnapshot(
        BossTeleportService service
    ){
        boolean immutable=false;

        try{
            service.snapshot()
                .entries.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Boss Teleport catalog snapshot mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                BossTeleportService.class,
                BossTeleportService.Entry.class,
                BossTeleportService.PlayerSnapshot.class,
                BossTeleportService.TeleportRequestResult.class
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
                   name.equals("x")||
                   name.equals("y")||
                   name.contains("plane")||
                   name.contains("coordinate")||
                   name.contains("dropitem")||
                   name.contains("droprate"))
                    throw new AssertionError(
                        "runtime/protocol/drop identity leaked into Boss Teleport "+
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

    private BossTeleportServiceTest(){}
}
