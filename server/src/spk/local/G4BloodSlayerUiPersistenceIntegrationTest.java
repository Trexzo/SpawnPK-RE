package spk.local;

import java.io.*;
import java.util.*;

public final class G4BloodSlayerUiPersistenceIntegrationTest {
    private static final String OWNER="g44-ui-persist";
    private static final int[] SEED={91,92,93,94};

    public static void main(String[] args)throws Exception{
        MemoryPlayers repository=new MemoryPlayers();

        boolean persistenceHydrated=false;
        boolean nativeAssignmentPersisted=false;
        boolean freshWorldActiveUi=false;
        boolean freshWorldCompletedUi=false;
        boolean malformedNativeFailClosed=false;
        boolean unsupportedModesFailClosed=false;
        boolean persistenceBeforeFeedback=false;

        World source=World.isolatedForTest(
            60_000L,
            repository
        );
        WorldPlayer sourcePlayer=new WorldPlayer();
        source.registerPlayer(sourcePlayer,OWNER);

        try{
            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    sourcePlayer,
                    source.localLabSlayer()
                );

            ByteArrayOutputStream rootBytes=
                new ByteArrayOutputStream();
            BloodSlayerPresentation.open(
                writer(rootBytes)
            );
            requireExactRoot(
                rootBytes.toByteArray()
            );

            LocalBloodSlayerUiHandler.Result unsupported=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .SLAUGHTER_WIDGET
                    ),
                    source.clock().tick()
                );

            unsupportedModesFailClosed=
                unsupported.status==
                    LocalBloodSlayerUiHandler.Status
                        .UNSUPPORTED_MODE&&
                source.localLabSlayer()
                    .status(OWNER)
                    .selectedMode==null&&
                source.localLabSlayer()
                    .status(OWNER)
                    .task==null;

            require(
                unsupportedModesFailClosed,
                "unsupported native mode mutated state"
            );

            LocalBloodSlayerUiHandler.Result selected=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .MONSTER_HUNTER_WIDGET
                    ),
                    source.clock().tick()
                );

            require(
                selected.status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED&&
                selected.taskStatus.persistenceValid()&&
                selected.taskStatus.selectedMode==
                    BloodSlayerModeService.Mode
                        .MONSTER_HUNTER_PVM,
                "native Monster Hunter selection failed"
            );

            ByteArrayOutputStream feedback=
                new ByteArrayOutputStream();
            ServerPacketWriter feedbackWriter=
                writer(feedback);
            final boolean[] savedBeforeFeedback={false};

            LocalBloodSlayerUiHandler.Result assigned=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    source.clock().tick()
                );

            LocalBloodSlayerUiSettlement.settle(
                assigned,
                reason->{
                    require(
                        feedback.size()==0,
                        "native feedback preceded persistence"
                    );
                    require(
                        LocalBloodSlayerUiHandler
                            .SAVE_TASK
                            .equals(reason),
                        "native save reason drift "+
                        reason
                    );
                    repository.saveUnchecked(
                        PlayerSnapshotCodec.capture(
                            OWNER,
                            sourcePlayer
                        )
                    );
                    savedBeforeFeedback[0]=true;
                },
                feedbackWriter,
                "[g4.4] "
            );

            persistenceBeforeFeedback=
                savedBeforeFeedback[0]&&
                feedback.size()>0;

            SortedMap<String,String> extension=
                sourcePlayer.snapshotExtensions()
                    .namespace(
                        LocalLabSlayerPersistence
                            .NAMESPACE
                    );

            nativeAssignmentPersisted=
                assigned.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                assigned.saveReason!=null&&
                assigned.taskStatus.active()&&
                "ACTIVE".equals(
                    extension.get("state")
                )&&
                "0".equals(
                    extension.get("progress")
                )&&
                LocalLabSlayerRuntime.AUTHORITY.equals(
                    extension.get("authority")
                );

            require(
                nativeAssignmentPersisted&&
                persistenceBeforeFeedback,
                "native task assignment persistence mismatch"
            );
        }finally{
            source.close();
        }

        World activeWorld=World.isolatedForTest(
            60_000L,
            repository
        );
        WorldPlayer activePlayer=
            loadAndRegister(
                activeWorld,
                repository,
                OWNER
            );

        try{
            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    activePlayer,
                    activeWorld.localLabSlayer()
                );

            LocalLabSlayerRuntime.StatusSnapshot hydrated=
                activeWorld.localLabSlayer()
                    .status(OWNER);

            persistenceHydrated=
                hydrated.persistenceValid()&&
                hydrated.active()&&
                hydrated.task.objective.progress==0L;

            LocalBloodSlayerUiHandler.Result selected=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .MONSTER_HUNTER_WIDGET
                    ),
                    activeWorld.clock().tick()
                );

            LocalBloodSlayerUiHandler.Result existing=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    activeWorld.clock().tick()
                );

            freshWorldActiveUi=
                persistenceHydrated&&
                selected.status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED&&
                existing.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_EXISTING&&
                existing.saveReason==null&&
                existing.taskStatus.active()&&
                existing.taskStatus.task.objective
                    .progress==0L;

            require(
                freshWorldActiveUi,
                "fresh World ACTIVE task not usable through native UI"
            );

            LocalLabSlayerRuntime.KillCreditResult kill=
                activeWorld.localLabSlayer()
                    .recordMonsterSpawnerKill(
                        OWNER,
                        LocalLabSlayerRuntime
                            .TARGET_DEFINITION_ID,
                        activeWorld.clock().tick()
                    );

            require(
                kill.completedNow&&
                kill.status.complete(),
                "fresh World active task did not complete canonically"
            );

            repository.save(
                PlayerSnapshotCodec.capture(
                    OWNER,
                    activePlayer
                )
            );
        }finally{
            activeWorld.close();
        }

        World completedWorld=World.isolatedForTest(
            60_000L,
            repository
        );
        WorldPlayer completedPlayer=
            loadAndRegister(
                completedWorld,
                repository,
                OWNER
            );
        PlayerSnapshot completedSnapshot;

        try{
            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    completedPlayer,
                    completedWorld.localLabSlayer()
                );

            LocalBloodSlayerUiHandler.Result selected=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .MONSTER_HUNTER_WIDGET
                    ),
                    completedWorld.clock().tick()
                );

            LocalBloodSlayerUiHandler.Result existing=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .GET_TASK_WIDGET
                    ),
                    completedWorld.clock().tick()
                );

            freshWorldCompletedUi=
                selected.status==
                    LocalBloodSlayerUiHandler.Status
                        .MODE_SELECTED&&
                existing.status==
                    LocalBloodSlayerUiHandler.Status
                        .TASK_ASSIGNED&&
                LocalBloodSlayerUiHandler.SAVE_TASK
                    .equals(
                        existing.saveReason
                    )&&
                existing.taskStatus.active()&&
                existing.taskStatus.task.objective
                    .progress==0L;

            require(
                freshWorldCompletedUi,
                "fresh World COMPLETED task did not renew through native UI"
            );

            completedSnapshot=
                PlayerSnapshotCodec.capture(
                    OWNER,
                    completedPlayer
                );
        }finally{
            completedWorld.close();
        }

        TreeMap<String,String> malformedValues=
            new TreeMap<>(
                completedSnapshot.values()
            );
        malformedValues.put(
            "extension."+
            LocalLabSlayerPersistence.NAMESPACE+
            ".authority",
            "WRONG_AUTHORITY"
        );

        PlayerSnapshot malformed=
            new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "g44-broken",
                malformedValues
            );
        repository.save(malformed);

        World malformedWorld=World.isolatedForTest(
            60_000L,
            repository
        );
        WorldPlayer malformedPlayer=
            loadAndRegister(
                malformedWorld,
                repository,
                "g44-broken"
            );

        try{
            LocalBloodSlayerUiHandler ui=
                new LocalBloodSlayerUiHandler(
                    malformedPlayer,
                    malformedWorld.localLabSlayer()
                );

            SortedMap<String,String> beforeMalformed=
                malformedPlayer.snapshotExtensions()
                    .namespace(
                        LocalLabSlayerPersistence
                            .NAMESPACE
                    );

            LocalBloodSlayerUiHandler.Result rejected=
                ui.handle(
                    BloodSlayerPresentation.resolveWidget(
                        BloodSlayerPresentation
                            .MONSTER_HUNTER_WIDGET
                    ),
                    malformedWorld.clock().tick()
                );

            SortedMap<String,String> afterMalformed=
                malformedPlayer.snapshotExtensions()
                    .namespace(
                        LocalLabSlayerPersistence
                            .NAMESPACE
                    );

            malformedNativeFailClosed=
                rejected.status==
                    LocalBloodSlayerUiHandler.Status
                        .PERSISTENCE_INVALID&&
                rejected.saveReason==null&&
                rejected.taskStatus!=null&&
                !rejected.taskStatus
                    .persistenceValid()&&
                beforeMalformed.equals(
                    afterMalformed
                )&&
                malformedWorld.localLabSlayer()
                    .slayer()
                    .taskCount()==0;

            require(
                malformedNativeFailClosed,
                "malformed native path did not fail closed"
            );
        }finally{
            malformedWorld.close();
        }

        require(
            BloodSlayerPresentation.WIDGET_ACTION_OPCODE==185,
            "Blood Slayer widget opcode drift"
        );

        System.out.println(
            "G4_BLOOD_SLAYER_UI_PERSISTENCE_INTEGRATION_PASS"+
            " root54100=true"+
            " monsterHunter54109=true"+
            " getTask54113=true"+
            " c2s185=true"+
            " persistenceHydrated="+persistenceHydrated+
            " nativeAssignmentPersisted="+nativeAssignmentPersisted+
            " freshWorldActiveUi="+freshWorldActiveUi+
            " freshWorldCompletedUi="+freshWorldCompletedUi+
            " malformedNativeFailClosed="+malformedNativeFailClosed+
            " unsupportedModesFailClosed="+unsupportedModesFailClosed+
            " persistenceBeforeFeedback="+persistenceBeforeFeedback+
            " pointsClaim=false"+
            " rewardClaim=false"+
            " originalSpawnpkTaskPolicyClaim=false"
        );
    }

    private static WorldPlayer loadAndRegister(
        World world,
        MemoryPlayers repository,
        String username
    )throws Exception{
        Optional<PlayerSnapshot> loaded=
            repository.load(username);

        require(
            loaded.isPresent(),
            "missing persisted snapshot "+
            username
        );

        WorldPlayer player=new WorldPlayer();
        PlayerSnapshotCodec.applyValidated(
            loaded.get(),
            player
        );
        world.registerPlayer(
            player,
            username
        );
        return player;
    }

    private static void requireExactRoot(
        byte[] root
    ){
        require(
            root.length==3,
            "Blood Slayer root frame length"
        );

        IsaacCipher cipher=
            new IsaacCipher(
                SEED.clone()
            );
        int opcode=
            ((root[0]&255)-
                cipher.nextInt())&
                255;

        require(
            opcode==97&&
            (root[1]&255)==0xd3&&
            (root[2]&255)==0x54,
            "exact Blood Slayer root 54100"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                SEED.clone()
            )
        );
    }

    private static final class MemoryPlayers
        implements PlayerRepository {
        private final HashMap<String,PlayerSnapshot>
            values=
                new HashMap<>();

        @Override public synchronized
            Optional<PlayerSnapshot> load(
                String username
            ){
            return Optional.ofNullable(
                values.get(
                    normalize(username)
                )
            );
        }

        @Override public synchronized void save(
            PlayerSnapshot snapshot
        ){
            values.put(
                normalize(
                    snapshot.username()
                ),
                snapshot
            );
        }

        synchronized void saveUnchecked(
            PlayerSnapshot snapshot
        ){
            save(snapshot);
        }

        private static String normalize(
            String value
        ){
            return value==null
                ?""
                :value.trim()
                    .toLowerCase(
                        Locale.ROOT
                    );
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G4BloodSlayerUiPersistenceIntegrationTest(){}
}
