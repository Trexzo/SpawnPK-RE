package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Objects;

public final class G95TournamentPvpDeathIntegrationTest {
    private static final String DIRECT_A="g95-direct-a";
    private static final String DIRECT_B="g95-direct-b";
    private static final String LIVE_A="g95-live-a";
    private static final String LIVE_B="g95-live-b";
    private static final String STALE_A="g95-stale-a";
    private static final String STALE_B="g95-stale-b";
    private static final String OUT_A="g95-out-a";
    private static final String OUT_B="g95-out-b";

    public static void main(String[] args)throws Exception{
        boolean activeMatch=false;
        boolean canonicalDeathCompletes=false;
        boolean attackerWins=false;
        boolean victimEliminated=false;
        boolean sessionCompleted=false;
        boolean instanceClosed=false;
        boolean precommitReplayIdempotent=false;
        boolean terminalDedupeRetired=false;
        boolean nonTournamentIsolation=false;
        boolean staleAttackerCapturedIdentity=false;

        World directWorld=
            World.isolatedForTest(
                60_000L
            );

        try{
            LocalLabTournamentRuntime runtime=
                directWorld.localTournament();

            runtime.register(DIRECT_A);
            runtime.register(DIRECT_B);

            LocalLabTournamentRuntime.MatchStartResult
                started=
                    runtime.activateAndStartMatch(
                        DIRECT_A,
                        DIRECT_B,
                        directWorld.clock().tick()
                    );

            activeMatch=
                started.snapshot.match(
                    started.matchId
                ).state==
                    TournamentService
                        .TournamentMatchState.ACTIVE;

            LocalLabTournamentRuntime.PvpDeathResult first=
                runtime.recordCanonicalPvpDeath(
                    DIRECT_A,
                    DIRECT_B,
                    1L
                );
            LocalLabTournamentRuntime.PvpDeathResult replay=
                runtime.recordCanonicalPvpDeath(
                    DIRECT_A,
                    DIRECT_B,
                    1L
                );

            precommitReplayIdempotent=
                first.tournamentMatch&&
                first.completedNow&&
                replay.tournamentMatch&&
                replay.completedNow&&
                first.matchId.equals(
                    replay.matchId
                )&&
                runtime.pvpDeathDedupeCount()==1&&
                runtime.snapshot().matches.size()==1&&
                runtime.snapshot()
                    .match(started.matchId)
                    .state==
                    TournamentService
                        .TournamentMatchState.COMPLETED;

            require(
                activeMatch&&
                precommitReplayIdempotent,
                "pre-commit Tournament death memoization failed"
            );

            runtime.retireCanonicalPvpDeath(
                DIRECT_A,
                DIRECT_B,
                1L
            );

            LocalLabTournamentRuntime.PvpDeathResult outside=
                runtime.recordCanonicalPvpDeath(
                    "not-in-tournament-a",
                    "not-in-tournament-b",
                    2L
                );

            nonTournamentIsolation=
                !outside.tournamentMatch&&
                !outside.completedNow&&
                outside.matchId==null&&
                runtime.pvpDeathDedupeCount()==0&&
                runtime.snapshot().matches.size()==1;

            require(
                nonTournamentIsolation,
                "non-Tournament runtime death mutated Tournament state"
            );
        }finally{
            directWorld.close();
        }

        World liveWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer liveAttacker=
            new WorldPlayer();
        WorldPlayer liveVictim=
            new WorldPlayer();
        long liveAttackerGeneration=
            liveWorld.registerPlayer(
                liveAttacker,
                LIVE_A
            );
        long liveVictimGeneration=
            liveWorld.registerPlayer(
                liveVictim,
                LIVE_B
            );

        try{
            LocalLabTournamentRuntime runtime=
                liveWorld.localTournament();

            runtime.register(LIVE_A);
            runtime.register(LIVE_B);
            LocalLabTournamentRuntime.MatchStartResult started=
                runtime.activateAndStartMatch(
                    LIVE_A,
                    LIVE_B,
                    liveWorld.clock().tick()
                );

            lethal(
                liveWorld,
                liveAttacker,
                liveAttackerGeneration,
                liveVictim,
                liveVictimGeneration,
                10L
            );

            SettlementHarness settlement=
                new SettlementHarness(
                    liveWorld,
                    liveVictim
                );

            settlement.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g95-live] "
                );

            TournamentService.Snapshot after=
                runtime.snapshot();
            TournamentService.MatchSnapshot match=
                after.match(
                    started.matchId
                );
            MatchSession session=
                runtime.matches().get(
                    started.matchId
                );
            WorldInstanceService.Snapshot instance=
                runtime.instances().get(
                    started.instanceId
                );

            canonicalDeathCompletes=
                match!=null&&
                match.state==
                    TournamentService
                        .TournamentMatchState.COMPLETED;

            attackerWins=
                canonicalDeathCompletes&&
                LIVE_A.equals(
                    match.winnerRef
                )&&
                after.entrant(LIVE_A).state==
                    TournamentService
                        .EntrantState.REGISTERED;

            victimEliminated=
                after.entrant(LIVE_B).state==
                    TournamentService
                        .EntrantState.ELIMINATED;

            sessionCompleted=
                session!=null&&
                session.state==
                    MatchSession.State.COMPLETED&&
                session.result!=null&&
                "canonical-pvp-death".equals(
                    session.result.outcomeKey
                )&&
                LocalLabTournamentRuntime
                    .PVP_DEATH_AUTHORITY
                    .equals(
                        session.result
                            .decisionAuthority
                    );

            instanceClosed=
                instance!=null&&
                instance.lifecycle==
                    WorldInstanceService
                        .Lifecycle.CLOSED;

            terminalDedupeRetired=
                runtime.pvpDeathDedupeCount()==0;

            require(
                canonicalDeathCompletes&&
                attackerWins&&
                victimEliminated&&
                sessionCompleted&&
                instanceClosed&&
                terminalDedupeRetired,
                "canonical Tournament PvP settlement postimage failed"
            );

            settlement.coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g95-live-replay] "
                );

            terminalDedupeRetired=
                terminalDedupeRetired&&
                runtime.pvpDeathDedupeCount()==0&&
                runtime.snapshot().matches.size()==1&&
                runtime.snapshot()
                    .match(started.matchId)
                    .state==
                    TournamentService
                        .TournamentMatchState.COMPLETED;

            require(
                terminalDedupeRetired,
                "terminal death replay recreated Tournament dedupe"
            );
        }finally{
            liveWorld.close();
        }

        World staleWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer staleAttacker=
            new WorldPlayer();
        WorldPlayer staleVictim=
            new WorldPlayer();
        long staleAttackerGeneration=
            staleWorld.registerPlayer(
                staleAttacker,
                STALE_A
            );
        long staleVictimGeneration=
            staleWorld.registerPlayer(
                staleVictim,
                STALE_B
            );

        try{
            LocalLabTournamentRuntime runtime=
                staleWorld.localTournament();

            runtime.register(STALE_A);
            runtime.register(STALE_B);
            LocalLabTournamentRuntime.MatchStartResult started=
                runtime.activateAndStartMatch(
                    STALE_A,
                    STALE_B,
                    staleWorld.clock().tick()
                );

            lethal(
                staleWorld,
                staleAttacker,
                staleAttackerGeneration,
                staleVictim,
                staleVictimGeneration,
                20L
            );

            require(
                staleWorld.unregisterPlayer(
                    staleAttacker,
                    staleAttackerGeneration
                ),
                "stale attacker unregister fixture"
            );

            new SettlementHarness(
                staleWorld,
                staleVictim
            ).coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g95-stale] "
                );

            TournamentService.MatchSnapshot staleMatch=
                runtime.snapshot().match(
                    started.matchId
                );

            staleAttackerCapturedIdentity=
                staleMatch!=null&&
                staleMatch.state==
                    TournamentService
                        .TournamentMatchState.COMPLETED&&
                STALE_A.equals(
                    staleMatch.winnerRef
                )&&
                runtime.snapshot()
                    .entrant(STALE_B)
                    .state==
                    TournamentService
                        .EntrantState.ELIMINATED&&
                runtime.pvpDeathDedupeCount()==0;

            require(
                staleAttackerCapturedIdentity,
                "captured stale attacker identity did not settle Tournament"
            );
        }finally{
            staleWorld.close();
        }

        World outsideWorld=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer outsideAttacker=
            new WorldPlayer();
        WorldPlayer outsideVictim=
            new WorldPlayer();
        long outsideAttackerGeneration=
            outsideWorld.registerPlayer(
                outsideAttacker,
                OUT_A
            );
        long outsideVictimGeneration=
            outsideWorld.registerPlayer(
                outsideVictim,
                OUT_B
            );

        try{
            LocalLabTournamentRuntime runtime=
                outsideWorld.localTournament();

            lethal(
                outsideWorld,
                outsideAttacker,
                outsideAttackerGeneration,
                outsideVictim,
                outsideVictimGeneration,
                30L
            );

            new SettlementHarness(
                outsideWorld,
                outsideVictim
            ).coordinator
                .settleCurrentDeathForSessionTeardown(
                    "[g95-outside] "
                );

            TournamentService.Snapshot snapshot=
                runtime.snapshot();

            nonTournamentIsolation=
                nonTournamentIsolation&&
                snapshot.eventLifecycle==
                    GlobalEventService
                        .Lifecycle.SCHEDULED&&
                snapshot.entrants.isEmpty()&&
                snapshot.matches.isEmpty()&&
                runtime.pvpDeathDedupeCount()==0;

            require(
                nonTournamentIsolation,
                "canonical non-Tournament PvP death mutated Tournament runtime"
            );
        }finally{
            outsideWorld.close();
        }

        System.out.println(
            "G95_TOURNAMENT_PVP_DEATH_PASS"+
            " activeMatch="+activeMatch+
            " canonicalDeathCompletes="+
                canonicalDeathCompletes+
            " attackerWins="+attackerWins+
            " victimEliminated="+victimEliminated+
            " sessionCompleted="+sessionCompleted+
            " instanceClosed="+instanceClosed+
            " precommitReplayIdempotent="+
                precommitReplayIdempotent+
            " terminalDedupeRetired="+
                terminalDedupeRetired+
            " nonTournamentIsolation="+
                nonTournamentIsolation+
            " staleAttackerCapturedIdentity="+
                staleAttackerCapturedIdentity+
            " nextPairClaim=false"+
            " championClaim=false"+
            " rewardClaim=false"+
            " persistenceClaim=false"+
            " originalSpawnpkPolicyClaim=false"
        );
    }

    static void lethal(
        World world,
        WorldPlayer attacker,
        long attackerGeneration,
        WorldPlayer victim,
        long victimGeneration,
        long worldTick
    )throws Exception{
        require(
            victim.playerState()
                .setCurrentLevel(
                    PlayerState.HITPOINTS,
                    9
                ),
            "victim HP fixture"
        );

        CombatStyleRepository.Style style=
            CombatStyleRepository
                .defaultForRoot(12290);

        require(
            style!=null,
            "missing melee style fixture"
        );

        PlayerCombatResolutionService combat=
            new PlayerCombatResolutionService(
                attacker,
                CombatDamageRules.localLabFallback(),
                CombatAttackTimingRules
                    .recoveredCompatibility(),
                CombatSystemHooks.forPlayer(
                    attacker
                )
            );

        PlayerCombatResolutionService.Result result=
            combat.resolveImmediateOwned(
                world,
                attackerGeneration,
                victim,
                victimGeneration,
                4151,
                style,
                worldTick
            );

        require(
            result.lifecycle.died&&
            victim.lifecycle().dead()&&
            victim.lifecycle()
                .deathAttribution()!=null&&
            attacker.id().equals(
                victim.lifecycle()
                    .deathAttribution()
                    .attackerId
            )&&
            victim.lifecycle()
                .deathAttribution()
                .attackerGeneration==
                    attackerGeneration&&
            attacker.username().equalsIgnoreCase(
                victim.lifecycle()
                    .deathAttribution()
                    .attackerUsername
            )&&
            "PLAYER_PVP".equals(
                victim.lifecycle()
                    .deathAttribution()
                    .context
            ),
            "canonical PvP attribution fixture"
        );
    }

    static final class SettlementHarness {
        final World world;
        final WorldPlayer player;
        final MovementState movement;
        final BankState bank;
        final EquipmentState equipment;
        final PetState petState;
        final PetEffectState petEffects;
        final CombatStyleState combatStyles;
        final DevAuthorityWorkbench dev=
            new DevAuthorityWorkbench();
        final NpcRegistry npcs=
            new NpcRegistry(dev);
        final HomeWorldRuntimePlan homeWorld=
            new HomeWorldRuntimePlan();
        final CombatEngine combat=
            new CombatEngine(dev);
        final ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        final ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    new int[]{41,42,43,44}
                )
            );
        final SceneUpdatePublisher publisher;
        final LocalPlayerInteractionHandler
            playerInteractions;
        final LocalBankObjectInteractionHandler
            bankObjects;
        final LocalRoutedNpcInteractionHandler
            routedNpcs;
        final LocalGroundItemInteractionHandler
            groundItems;
        final LocalPetDropPickupHandler
            petDropPickup;
        final LocalPetRuntimeCommandHandler
            petRuntime;
        final RegionBridge regionBridge=
            new RegionBridge();
        final TickBridge tickBridge=
            new TickBridge();
        final LocalWorldTickCoordinator coordinator;

        SettlementHarness(
            World world,
            WorldPlayer player
        )throws Exception{
            this.world=Objects.requireNonNull(
                world,
                "world"
            );
            this.player=Objects.requireNonNull(
                player,
                "player"
            );
            this.movement=player.movement();
            this.bank=player.bank();
            this.equipment=player.equipment();
            this.petState=player.petState();
            this.petEffects=player.petEffects();
            this.combatStyles=
                player.combatStyles();

            publisher=
                new SceneUpdatePublisher(
                    writer,
                    new SceneCoordinateContext(
                        MovementState.REGION_BASE_X,
                        MovementState.REGION_BASE_Y,
                        0
                    )
                );

            playerInteractions=
                new LocalPlayerInteractionHandler(
                    world,
                    player,
                    movement,
                    equipment
                );

            bankObjects=
                new LocalBankObjectInteractionHandler(
                    bank,
                    movement
                );

            routedNpcs=
                new LocalRoutedNpcInteractionHandler(
                    npcs,
                    bank,
                    movement,
                    null,
                    player,
                    equipment
                );

            groundItems=
                new LocalGroundItemInteractionHandler(
                    world,
                    bank,
                    movement
                );

            PetBridge petBridge=
                new PetBridge(
                    player.username(),
                    publisher
                );

            petDropPickup=
                new LocalPetDropPickupHandler(
                    world,
                    bank,
                    movement,
                    petState,
                    petEffects,
                    player.miniPets(),
                    npcs,
                    new VoidglassPetState(),
                    new PetAccessoryState(),
                    dev,
                    petBridge
                );

            petRuntime=
                new LocalPetRuntimeCommandHandler(
                    petState,
                    petEffects,
                    npcs,
                    movement
                );

            regionBridge.publisher=publisher;
            regionBridge.username=
                player.username();

            LocalRegionStreamHandler regionStreams=
                new LocalRegionStreamHandler(
                    false,
                    world,
                    player,
                    movement,
                    homeWorld,
                    npcs,
                    playerInteractions,
                    combat,
                    regionBridge
                );

            tickBridge.publisher=publisher;

            coordinator=
                new LocalWorldTickCoordinator(
                    false,
                    world,
                    player,
                    movement,
                    equipment,
                    combatStyles,
                    petEffects,
                    npcs,
                    homeWorld,
                    combat,
                    regionStreams,
                    playerInteractions,
                    bankObjects,
                    routedNpcs,
                    groundItems,
                    petDropPickup,
                    petRuntime,
                    tickBridge
                );
        }
    }

    private static final class PetBridge
        implements LocalPetDropPickupHandler.SessionBridge {
        private final String username;
        private final SceneUpdatePublisher publisher;

        PetBridge(
            String username,
            SceneUpdatePublisher publisher
        ){
            this.username=username;
            this.publisher=publisher;
        }

        @Override public String username(){
            return username;
        }
        @Override public boolean persistentAccount(){
            return true;
        }
        @Override public long sessionWorldTick(){
            return 1L;
        }
        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }
        @Override public void saveAccount(
            String tag,
            String reason
        ){}
        @Override public int syncScopesightPassive(
            ServerPacketWriter serverPackets
        ){
            return 0;
        }
        @Override public void resetPetFollowDeadline(){}
        @Override public void ensurePetFollowScheduled(long now){}
    }

    private static final class RegionBridge
        implements LocalRegionStreamHandler.SessionBridge {
        SceneUpdatePublisher publisher;
        String username;

        @Override public String username(){
            return username;
        }
        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }
        @Override public void replaceScenePublisher(
            SceneUpdatePublisher replacement
        ){
            publisher=replacement;
        }
        @Override public void resetPetFollowRuntime(){}
    }

    private static final class TickBridge
        implements LocalWorldTickCoordinator.SessionBridge {
        SceneUpdatePublisher publisher;
        long petDeadline=Long.MAX_VALUE;

        @Override public Player81WorldSync.Context player81Sync(){
            return null;
        }

        @Override public SceneUpdatePublisher scenePublisher(){
            return publisher;
        }

        @Override public void saveAccount(
            String tag,
            String reason
        ){}

        @Override public void savePlayerAccount(
            WorldPlayer player,
            long expectedGeneration,
            String tag,
            String reason
        ){}

        @Override public void publishOpponentOverlay(
            NpcEntity target,
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}

        @Override public void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        ){}

        @Override public long petFollowDeadline(){
            return petDeadline;
        }

        @Override public void setPetFollowDeadline(
            long value
        ){
            petDeadline=value;
        }

        @Override public void ensurePetFollowScheduled(long now){}
        @Override public void ensurePetTestSequenceScheduled(long now){}
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

    private G95TournamentPvpDeathIntegrationTest(){}
}
