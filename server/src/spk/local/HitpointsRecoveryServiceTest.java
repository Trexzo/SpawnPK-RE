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
        resolverFailureAtomic();
        invalidMaximumAtomic();
        inconsistentAliveStateFailsClosed();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "HITPOINTS_RECOVERY_SERVICE_PASS " +
            "canonicalPlayerState=true " +
            "lifecycleSafe=true " +
            "deadNotResurrected=true " +
            "positiveHeal=true " +
            "clampToCallerMaximum=true " +
            "overhealPreserved=true " +
            "resolverFailureAtomic=true " +
            "invalidMaximumAtomic=true " +
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
                player,
                fixedMaximum(
                    99
                )
            );

        HitpointsRecoveryService.Result first =
            service.heal(
                25
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
                Integer.MAX_VALUE
            );

        require(
            clamped.status ==
                HitpointsRecoveryService
                    .Status.HEALED &&
            clamped.requested ==
                Integer.MAX_VALUE &&
            clamped.applied == 34 &&
            clamped.before == 65 &&
            clamped.after == 99 &&
            clamped.maximum == 99 &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 99,
            "clamped heal"
        );

        HitpointsRecoveryService.Result full =
            service.heal(
                10
            );

        require(
            full.status ==
                HitpointsRecoveryService
                    .Status.ALREADY_AT_OR_ABOVE_MAXIMUM &&
            full.applied == 0 &&
            full.before == 99 &&
            full.after == 99 &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 99,
            "already-full heal"
        );

        expect(
            IllegalArgumentException.class,
            () -> service.heal(
                0
            ),
            "zero heal"
        );

        require(
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 99,
            "invalid heal mutated HP"
        );
    }

    private static void deadDoesNotResurrect() {
        WorldPlayer player =
            new WorldPlayer();

        PlayerLifecycleService lifecycle =
            new PlayerLifecycleService(
                player
            );

        PlayerLifecycleService.DamageResult death =
            lifecycle.applyDamage(
                500,
                10L,
                "test"
            );

        require(
            death.died &&
            player.lifecycle().dead() &&
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 0,
            "dead setup"
        );

        final int[] resolverCalls = {
            0
        };

        HitpointsRecoveryService service =
            new HitpointsRecoveryService(
                player,
                new HitpointsRecoveryService
                    .MaximumHitpointsResolver() {
                    @Override public int maximumHitpoints(
                        WorldPlayer ignored
                    ) {
                        resolverCalls[0]++;
                        return 99;
                    }

                    @Override public String authority() {
                        return AUTHORITY;
                    }
                }
            );

        HitpointsRecoveryService.Result result =
            service.heal(
                50
            );

        require(
            result.status ==
                HitpointsRecoveryService
                    .Status.DEAD &&
            result.applied == 0 &&
            result.before == 0 &&
            result.after == 0 &&
            !result.maximumResolved() &&
            resolverCalls[0] == 0 &&
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
                player,
                fixedMaximum(
                    99
                )
            );

        HitpointsRecoveryService.Result result =
            service.heal(
                20
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

    private static void resolverFailureAtomic() {
        WorldPlayer player =
            new WorldPlayer();

        player.playerState()
            .setCurrentLevel(
                PlayerState.HITPOINTS,
                55
            );

        HitpointsRecoveryService service =
            new HitpointsRecoveryService(
                player,
                new HitpointsRecoveryService
                    .MaximumHitpointsResolver() {
                    @Override public int maximumHitpoints(
                        WorldPlayer ignored
                    ) {
                        throw new IllegalStateException(
                            "resolver boom"
                        );
                    }

                    @Override public String authority() {
                        return AUTHORITY;
                    }
                }
            );

        expect(
            IllegalStateException.class,
            () -> service.heal(
                20
            ),
            "resolver failure"
        );

        require(
            player.playerState()
                .currentLevel(
                    PlayerState.HITPOINTS
                ) == 55,
            "resolver failure mutated HP"
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
                    player,
                    fixedMaximum(
                        invalid
                    )
                );

            expect(
                IllegalStateException.class,
                () -> service.heal(
                    10
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
                player,
                fixedMaximum(
                    99
                )
            );

        expect(
            IllegalStateException.class,
            () -> service.heal(
                10
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

    private static void authorityGuards() {
        WorldPlayer player =
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            () -> new HitpointsRecoveryService(
                player,
                resolver(
                    99,
                    "EXACT_CURRENT_CLIENT"
                )
            ),
            "client maximum authority"
        );

        expect(
            IllegalArgumentException.class,
            () -> new HitpointsRecoveryService(
                player,
                resolver(
                    99,
                    "UNKNOWN_SERVER_AUTHORITY"
                )
            ),
            "unknown maximum authority"
        );
    }

    private static HitpointsRecoveryService
        .MaximumHitpointsResolver fixedMaximum(
            int maximum
        ) {
        return resolver(
            maximum,
            AUTHORITY
        );
    }

    private static HitpointsRecoveryService
        .MaximumHitpointsResolver resolver(
            int maximum,
            String authority
        ) {
        return new HitpointsRecoveryService
            .MaximumHitpointsResolver() {
            @Override public int maximumHitpoints(
                WorldPlayer ignored
            ) {
                return maximum;
            }

            @Override public String authority() {
                return authority;
            }
        };
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
                            "lifesteal"
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
                        "respawn"
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
