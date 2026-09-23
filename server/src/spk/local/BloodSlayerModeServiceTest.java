package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class BloodSlayerModeServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_BLOOD_SLAYER";

    public static void main(String[] args){
        Map<String,ObjectiveProgressService>
            ledgers=
                new HashMap<>();

        ObjectiveProgressService ledger=
            new ObjectiveProgressService();

        for(String key:Arrays.asList(
                "blood:monster",
                "blood:boss",
                "blood:bounty",
                "blood:slaughter"))
            ledger.define(
                new ObjectiveDefinition(
                    key,
                    2L,
                    POLICY
                )
            );

        ledgers.put(
            "player:a",
            ledger
        );

        SlayerTaskService slayer=
            new SlayerTaskService(
                ledgers::get
            );

        final ArrayList<String>
            allocationCalls=
                new ArrayList<>();

        BloodSlayerModeService.TaskAllocator
            allocator=
                (player,mode)->{
                    allocationCalls.add(
                        player+"|"+mode
                    );

                    switch(mode){
                        case MONSTER_HUNTER_PVM:
                            return "blood:task:monster";
                        case BOSS_HUNTER_PVM:
                            return "blood:task:boss";
                        case BOUNTY_HUNTER_PK:
                            return "blood:task:bounty";
                        case SLAUGHTER_PK:
                            return "blood:task:slaughter";
                        default:
                            throw new AssertionError(
                                mode
                            );
                    }
                };

        BloodSlayerModeService service=
            new BloodSlayerModeService(
                slayer,
                allocator,
                POLICY
            );

        register(
            service,
            "blood:task:monster",
            "blood:monster",
            BloodSlayerModeService
                .Mode.MONSTER_HUNTER_PVM
        );
        register(
            service,
            "blood:task:boss",
            "blood:boss",
            BloodSlayerModeService
                .Mode.BOSS_HUNTER_PVM
        );
        register(
            service,
            "blood:task:bounty",
            "blood:bounty",
            BloodSlayerModeService
                .Mode.BOUNTY_HUNTER_PK
        );
        register(
            service,
            "blood:task:slaughter",
            "blood:slaughter",
            BloodSlayerModeService
                .Mode.SLAUGHTER_PK
        );

        require(
            BloodSlayerModeService.Mode
                .values().length==4&&
            service.registeredTaskCount()==4,
            "exact Blood Slayer mode count"
        );

        expect(
            IllegalStateException.class,
            ()->service.requestTask(
                "player:a",
                1L
            ),
            "task requested without selected mode"
        );

        BloodSlayerModeService.Snapshot
            selected=
                service.selectMode(
                    " Player:A ",
                    BloodSlayerModeService
                        .Mode.MONSTER_HUNTER_PVM
                );

        require(
            "player:a".equals(
                selected.playerRef
            )&&
            selected.selectedMode==
                BloodSlayerModeService
                    .Mode.MONSTER_HUNTER_PVM&&
            !selected.hasActiveTask()&&
            BloodSlayerModeService
                .PRESENTATION_AUTHORITY
                .equals(
                    selected
                        .presentationAuthority
                )&&
            POLICY.equals(
                selected.policyAuthority
            ),
            "Blood Slayer selection"
        );

        BloodSlayerModeService.AssignmentResult
            monster=
                service.requestTask(
                    "PLAYER:A",
                    10L
                );

        require(
            monster.mode==
                BloodSlayerModeService
                    .Mode.MONSTER_HUNTER_PVM&&
            "blood:task:monster".equals(
                monster.task
                    .definition
                    .taskKey
            )&&
            monster.player.hasActiveTask()&&
            allocationCalls.equals(
                Collections.singletonList(
                    "player:a|MONSTER_HUNTER_PVM"
                )
            ),
            "Blood Slayer allocator/delegate assignment"
        );

        expect(
            IllegalStateException.class,
            ()->service.selectMode(
                "player:a",
                BloodSlayerModeService
                    .Mode.BOSS_HUNTER_PVM
            ),
            "mode switched with active task"
        );

        expect(
            IllegalStateException.class,
            ()->service.clearMode(
                "player:a"
            ),
            "mode cleared with active task"
        );

        // Idempotent same-mode selection is harmless while active.
        require(
            service.selectMode(
                "player:a",
                BloodSlayerModeService
                    .Mode.MONSTER_HUNTER_PVM
            ).hasActiveTask(),
            "same Blood Slayer mode reselect"
        );

        slayer.cancel(
            monster.task.taskId,
            11L
        );

        require(
            service.selectMode(
                "player:a",
                BloodSlayerModeService
                    .Mode.BOSS_HUNTER_PVM
            ).selectedMode==
                BloodSlayerModeService
                    .Mode.BOSS_HUNTER_PVM,
            "mode switch after terminal task"
        );

        BloodSlayerModeService.AssignmentResult
            boss=
                service.requestTask(
                    "player:a",
                    12L
                );

        require(
            "blood:task:boss".equals(
                boss.task
                    .definition
                    .taskKey
            )&&
            boss.mode.pvm()&&
            !boss.mode.pvp(),
            "Boss hunter allocation"
        );

        slayer.skip(
            boss.task.taskId,
            13L
        );

        service.selectMode(
            "player:a",
            BloodSlayerModeService
                .Mode.BOUNTY_HUNTER_PK
        );

        BloodSlayerModeService.AssignmentResult
            bounty=
                service.requestTask(
                    "player:a",
                    14L
                );

        require(
            "blood:task:bounty".equals(
                bounty.task
                    .definition
                    .taskKey
            )&&
            bounty.mode.pvp(),
            "Bounty hunter allocation"
        );

        slayer.cancel(
            bounty.task.taskId,
            15L
        );

        service.selectMode(
            "player:a",
            BloodSlayerModeService
                .Mode.SLAUGHTER_PK
        );

        BloodSlayerModeService.AssignmentResult
            slaughter=
                service.requestTask(
                    "player:a",
                    16L
                );

        require(
            "blood:task:slaughter".equals(
                slaughter.task
                    .definition
                    .taskKey
            )&&
            slaughter.mode.pvp(),
            "Slaughter allocation"
        );

        slayer.cancel(
            slaughter.task.taskId,
            17L
        );

        BloodSlayerModeService.Snapshot cleared=
            service.clearMode(
                "player:a"
            );

        require(
            !cleared.hasSelection()&&
            !cleared.hasActiveTask(),
            "Blood Slayer clear after terminal task"
        );

        authorityAndEligibilityGuards(
            ledgers
        );
        immutableSnapshot(service);
        protocolBoundary();
        allocatorRunsOutsideServiceMonitor();

        System.out.println(
            "BLOOD_SLAYER_MODE_SERVICE_PASS "+
            "exactModes4=true "+
            "normalizedPlayerIdentity=true "+
            "modeSelection=true "+
            "allocatorReceivesMode=true "+
            "slayerTaskDelegation=true "+
            "taskAuthorityPolicy=true "+
            "modeEligibility=true "+
            "activeTaskBlocksSwitch=true "+
            "terminalAllowsSwitch=true "+
            "missingSelectionRejected=true "+
            "pointsMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void authorityAndEligibilityGuards(
        Map<String,ObjectiveProgressService> ledgers
    ){
        ObjectiveProgressService ledger=
            new ObjectiveProgressService();

        ledger.define(
            new ObjectiveDefinition(
                "blood:guard",
                1L,
                POLICY
            )
        );

        ledgers.put(
            "player:guard",
            ledger
        );

        SlayerTaskService slayer=
            new SlayerTaskService(
                ledgers::get
            );

        BloodSlayerModeService badAllocator=
            new BloodSlayerModeService(
                slayer,
                (player,mode)->
                    "blood:unregistered",
                POLICY
            );

        badAllocator.selectMode(
            "player:guard",
            BloodSlayerModeService
                .Mode.MONSTER_HUNTER_PVM
        );

        expect(
            IllegalArgumentException.class,
            ()->badAllocator.requestTask(
                "player:guard",
                20L
            ),
            "allocator returned unregistered task"
        );

        require(
            slayer.active(
                "player:guard"
            )==null,
            "unregistered allocator mutated Slayer"
        );

        expect(
            IllegalArgumentException.class,
            ()->badAllocator
                .registerTaskDefinition(
                    new SlayerTaskService.Definition(
                        "blood:wrong-authority",
                        "family:blood",
                        "target:test",
                        "blood:guard",
                        "EXACT_CURRENT_CLIENT"
                    ),
                    Collections.singleton(
                        BloodSlayerModeService
                            .Mode.MONSTER_HUNTER_PVM
                    )
                ),
            "Blood Slayer task authority mismatch"
        );

        require(
            slayer.definitionCount()==0,
            "wrong-authority definition reached Slayer"
        );

        BloodSlayerModeService eligibility=
            new BloodSlayerModeService(
                slayer,
                (player,mode)->
                    "blood:monster-only",
                POLICY
            );

        eligibility.registerTaskDefinition(
            new SlayerTaskService.Definition(
                "blood:monster-only",
                "family:blood",
                "target:test",
                "blood:guard",
                POLICY
            ),
            Collections.singleton(
                BloodSlayerModeService
                    .Mode.MONSTER_HUNTER_PVM
            )
        );

        eligibility.selectMode(
            "player:guard",
            BloodSlayerModeService
                .Mode.BOSS_HUNTER_PVM
        );

        expect(
            IllegalArgumentException.class,
            ()->eligibility.requestTask(
                "player:guard",
                21L
            ),
            "mode-ineligible task assigned"
        );

        require(
            slayer.active(
                "player:guard"
            )==null,
            "mode-ineligible task mutated Slayer"
        );
    }

    private static void register(
        BloodSlayerModeService service,
        String taskKey,
        String objectiveKey,
        BloodSlayerModeService.Mode mode
    ){
        service.registerTaskDefinition(
            new SlayerTaskService.Definition(
                taskKey,
                "family:blood",
                "target:"+taskKey,
                objectiveKey,
                POLICY
            ),
            Collections.singleton(
                mode
            )
        );
    }

    private static void immutableSnapshot(
        BloodSlayerModeService service
    ){
        boolean immutable=false;

        try{
            service.snapshot()
                .clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Blood Slayer snapshot mutable"
        );
    }

    private static void allocatorRunsOutsideServiceMonitor(){
        Map<String,ObjectiveProgressService> ledgers=
            new HashMap<>();

        ObjectiveProgressService ledger=
            new ObjectiveProgressService();

        ledger.define(
            new ObjectiveDefinition(
                "blood:reentrant",
                1L,
                POLICY
            )
        );

        ledgers.put(
            "player:reentrant",
            ledger
        );

        SlayerTaskService slayer=
            new SlayerTaskService(
                ledgers::get
            );

        final BloodSlayerModeService[] holder=
            new BloodSlayerModeService[1];

        BloodSlayerModeService service=
            new BloodSlayerModeService(
                slayer,
                (player,mode)->{
                    holder[0]
                        .registeredTaskCount();
                    holder[0]
                        .get(player);
                    return "blood:task:reentrant";
                },
                POLICY
            );

        holder[0]=service;

        register(
            service,
            "blood:task:reentrant",
            "blood:reentrant",
            BloodSlayerModeService
                .Mode.MONSTER_HUNTER_PVM
        );

        service.selectMode(
            "player:reentrant",
            BloodSlayerModeService
                .Mode.MONSTER_HUNTER_PVM
        );

        BloodSlayerModeService.AssignmentResult
            result=
                service.requestTask(
                    "player:reentrant",
                    70L
                );

        require(
            result.task!=null&&
            result.player.hasActiveTask(),
            "Blood Slayer allocator re-entry"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                BloodSlayerModeService.class,
                BloodSlayerModeService.Snapshot.class,
                BloodSlayerModeService.AssignmentResult.class
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
                   name.contains("point")||
                   name.contains("reward"))
                    throw new AssertionError(
                        "protocol/points/reward state leaked into Blood Slayer "+
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

    private BloodSlayerModeServiceTest(){}
}
