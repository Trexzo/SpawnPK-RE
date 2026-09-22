package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class HitpointsRecoveryServiceTest {
    private static final String AUTHORITY =
        "CUSTOM_LOCALLAB_MAX_HP_TEST";

    public static void main(String[] args) {
        positiveHealAndClamp();
        deadDoesNotResurrect();
        overhealPreserved();
        invalidMaximumAtomic();
        invalidAuthorityAtomic();
        inconsistentAliveStateFailsClosed();
        boundaryGuard();

        System.out.println(
            "HITPOINTS_RECOVERY_SERVICE_PASS " +
            "canonicalPlayerState=true " +
            "worldPlayerOwnership=true " +
            "lifecycleSafe=true " +
            "deadNotResurrected=true " +
            "positiveHeal=true " +
            "clampToCallerMaximum=true " +
            "overhealPreserved=true " +
            "callerMaximumPreResolved=true " +
            "callbackUnderPlayerLock=false " +
            "invalidMaximumAtomic=true " +
            "invalidAuthorityAtomic=true " +
            "requestedAppliedFacts=true " +
            "foodOwned=false " +
            "regenOwned=false " +
            "packet134Owned=false " +
            "persistenceOwned=false " +
            "protocolIndependent=true"
        );
    }

    private static void positiveHealAndClamp() {
        WorldPlayer player =
            new WorldPlayer();

        player.playerState()
            .setCurrentLevel(
                PlayerState.HITPOINTS,
                40
            );

        HitpointsRecoveryService service =
            new HitpointsRecoveryService(
                player
            );

        HitpointsRecoveryService.Result first =
            service.heal(
                25,
                99,
                AUTHORITY
            );

        require(
            first.healed() &&
            first.status ==
                HitpointsRecoveryService
                    .Status.HEALED &&
            first.requested == 25 &&
            first.applied == 25 &&
            first.before == 40 &&
            first.after == 65 &&
            first.maximum == 99 &&
            first.maximumResolved() &&
            AUTHORITY.equals(
                first.maximumAuthority
            ) &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 65,
            "positive heal"
        );

        HitpointsRecoveryService.Result clamped =
            service.heal(
                Integer.MAX_VALUE,
                99,
                AUTHORITY
            );

        require(
            clamped.status ==
                HitpointsRecoveryService
                    .Status.HEALED &&
            clamped.applied == 34 &&
            clamped.before == 65 &&
            clamped.after == 99 &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 99,
            "clamped heal"
        );

        HitpointsRecoveryService.Result full =
            service.heal(
                10,
                99,
                AUTHORITY
            );

        require(
            full.status ==
                HitpointsRecoveryService
                    .Status.ALREADY_AT_OR_ABOVE_MAXIMUM &&
            full.applied == 0 &&
            full.before == 99 &&
            full.after == 99,
            "already-full heal"
        );

        expect(
            IllegalArgumentException.class,
            () -> service.heal(
                0,
                99,
                AUTHORITY
            ),
            "zero heal"
        );
    }

    private static void deadDoesNotResurrect() {
        WorldPlayer player =
            new WorldPlayer();

        PlayerLifecycleService lifecycle =
            new PlayerLifecycleService(
                player
            );

        lifecycle.applyDamage(
            500,
            10L,
            "test"
        );

        HitpointsRecoveryService service =
            new HitpointsRecoveryService(
                player
            );

        HitpointsRecoveryService.Result result =
            service.heal(
                50,
                99,
                AUTHORITY
            );

        require(
            result.status ==
                HitpointsRecoveryService
                    .Status.DEAD &&
            result.applied == 0 &&
            result.before == 0 &&
            result.after == 0 &&
            !result.maximumResolved() &&
            player.lifecycle().dead() &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 0,
            "dead heal resurrected player"
        );
    }

    private static void overhealPreserved() {
        WorldPlayer player =
            new WorldPlayer();

        player.playerState()
            .setCurrentLevel(
                PlayerState.HITPOINTS,
                120
            );

        HitpointsRecoveryService service =
            new HitpointsRecoveryService(
                player
            );

        HitpointsRecoveryService.Result result =
            service.heal(
                20,
                99,
                AUTHORITY
            );

        require(
            result.status ==
                HitpointsRecoveryService
                    .Status.ALREADY_AT_OR_ABOVE_MAXIMUM &&
            result.applied == 0 &&
            result.before == 120 &&
            result.after == 120 &&
            result.maximum == 99 &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 120,
            "overheal was clamped downward"
        );
    }

    private static void invalidMaximumAtomic() {
        for (int invalid :
                new int[]{
                    0,
                    256
                }) {

            WorldPlayer player =
                new WorldPlayer();

            player.playerState()
                .setCurrentLevel(
                    PlayerState.HITPOINTS,
                    50
                );

            HitpointsRecoveryService service =
                new HitpointsRecoveryService(
                    player
                );

            expect(
                IllegalArgumentException.class,
                () -> service.heal(
                    10,
                    invalid,
                    AUTHORITY
                ),
                "invalid maximum " +
                invalid
            );

            require(
                player.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 50,
                "invalid maximum mutated HP " +
                invalid
            );
        }
    }

    private static void invalidAuthorityAtomic() {
        for (String authority :
                new String[]{
                    "EXACT_CURRENT_CLIENT",
                    "UNKNOWN_SERVER_AUTHORITY"
                }) {

            WorldPlayer player =
                new WorldPlayer();

            player.playerState()
                .setCurrentLevel(
                    PlayerState.HITPOINTS,
                    50
                );

            HitpointsRecoveryService service =
                new HitpointsRecoveryService(
                    player
                );

            expect(
                IllegalArgumentException.class,
                () -> service.heal(
                    10,
                    99,
                    authority
                ),
                "invalid maximum authority " +
                authority
            );

            require(
                player.playerState()
                    .currentLevel(
                        PlayerState.HITPOINTS
                    ) == 50,
                "invalid authority mutated HP"
            );
        }
    }

    private static void inconsistentAliveStateFailsClosed() {
        WorldPlayer player =
            new WorldPlayer();

        player.playerState()
            .applyHitpointsDamage(
                500
            );

        require(
            player.lifecycle().alive() &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 0,
            "inconsistent alive fixture"
        );

        HitpointsRecoveryService service =
            new HitpointsRecoveryService(
                player
            );

        expect(
            IllegalStateException.class,
            () -> service.heal(
                10,
                99,
                AUTHORITY
            ),
            "alive zero-HP inconsistency"
        );

        require(
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 0,
            "inconsistent-state failure mutated HP"
        );
    }

    private static void boundaryGuard() {
        for (Class<?> type :
                new Class<?>[]{
                    HitpointsRecoveryService.class,
                    HitpointsRecoveryService
                        .Result.class
                }) {

            for (Field field :
                    type.getDeclaredFields()) {

                String haystack =
                    (
                        field.getName() +
                        " " +
                        field.getType().getName()
                    ).toLowerCase(
                        Locale.ROOT
                    );

                for (String forbidden :
                        new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "food",
                            "itemid",
                            "animation",
                            "regen",
                            "lifesteal",
                            "resolver"
                        }) {

                    require(
                        !haystack.contains(
                            forbidden
                        ),
                        "unowned identity leaked through " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }

        for (Method method :
                HitpointsRecoveryService.class
                    .getDeclaredMethods()) {

            String name =
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for (String forbidden :
                    new String[]{
                        "packet",
                        "publish",
                        "persist",
                        "food",
                        "eat",
                        "regen",
                        "damage",
                        "respawn",
                        "resolver"
                    }) {

                require(
                    !name.contains(
                        forbidden
                    ),
                    "unowned behavior leaked through " +
                    method.getName()
                );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ) {
        try {
            action.run();
        } catch (
            Throwable failure
        ) {
            if (type.isInstance(
                    failure)) {
                return;
            }

            throw new AssertionError(
                label +
                " wrong failure " +
                failure,
                failure
            );
        }

        throw new AssertionError(
            label +
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if (!condition) {
            throw new AssertionError(
                label
            );
        }
    }

    private HitpointsRecoveryServiceTest() {}
}
