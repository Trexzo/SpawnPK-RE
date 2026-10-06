package spk.local;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Canonical delayed player -> player hit delivery.
 *
 * Damage calculation, attack cadence, routing, presentation and reward policy
 * stay outside this service. It owns only one already-resolved damage amount,
 * its authoritative due world tick and exact attacker/target generation
 * fences.
 */
final class PlayerPvpDelayedHitService {
    static final String REQUIRE_CURRENT_PLAYER_GENERATIONS =
        "REQUIRE_CURRENT_PLAYER_GENERATIONS";

    @FunctionalInterface
    interface DeliveryObserver {
        void onDelivered(
            WorldPlayer target,
            PlayerLifecycleService.DamageResult result
        ) throws Exception;
    }

    enum State {
        SCHEDULED,
        DELIVERED,
        CANCELLED,
        STALE_ATTACKER,
        STALE_TARGET,
        FAILED
    }

    static final class HitId {
        final long value;

        private HitId(long value) {
            if (value <= 0L) {
                throw new IllegalArgumentException(
                    "value=" + value
                );
            }
            this.value = value;
        }

        @Override public boolean equals(Object other) {
            return other instanceof HitId &&
                ((HitId) other).value == value;
        }

        @Override public int hashCode() {
            return Long.hashCode(value);
        }

        @Override public String toString() {
            return Long.toUnsignedString(value);
        }
    }

    static final class Snapshot {
        final HitId hitId;
        final State state;
        final EntityId attackerId;
        final long attackerGeneration;
        final EntityId targetId;
        final long targetGeneration;
        final int requestedDamage;
        final long scheduledFromTick;
        final long dueTick;
        final String damageAuthority;
        final String damageFormula;
        final String deliveryAuthority;
        final String deliveryPolicy;
        final int appliedDamage;
        final int hitpointsBefore;
        final int hitpointsAfter;
        final boolean died;
        final boolean ignoredDead;
        final String failureType;

        private Snapshot(Entry entry) {
            hitId = entry.hitId;
            state = entry.state;
            attackerId = entry.attacker.id();
            attackerGeneration = entry.attackerGeneration;
            targetId = entry.target.id();
            targetGeneration = entry.targetGeneration;
            requestedDamage = entry.requestedDamage;
            scheduledFromTick = entry.scheduledFromTick;
            dueTick = entry.dueTick;
            damageAuthority = entry.damageAuthority;
            damageFormula = entry.damageFormula;
            deliveryAuthority = entry.deliveryAuthority;
            deliveryPolicy = entry.deliveryPolicy;

            PlayerLifecycleService.DamageResult delivered =
                entry.delivered;

            appliedDamage =
                delivered == null
                    ? -1
                    : delivered.applied;
            hitpointsBefore =
                delivered == null
                    ? -1
                    : delivered.hpBefore;
            hitpointsAfter =
                delivered == null
                    ? -1
                    : delivered.hpAfter;
            died =
                delivered != null &&
                delivered.died;
            ignoredDead =
                delivered != null &&
                delivered.ignoredDead;
            failureType = entry.failureType;
        }

        boolean terminal() {
            return state != State.SCHEDULED;
        }
    }

    private static final class Entry {
        final HitId hitId;
        final WorldPlayer attacker;
        final long attackerGeneration;
        final WorldPlayer target;
        final long targetGeneration;
        final int requestedDamage;
        final long scheduledFromTick;
        final long dueTick;
        final String damageAuthority;
        final String damageFormula;
        final String deliveryAuthority;
        final String deliveryPolicy;

        State state = State.SCHEDULED;
        boolean executing;
        WorldEventQueue.Handle handle;
        PlayerLifecycleService.DamageResult delivered;
        String failureType;

        Entry(
            HitId hitId,
            WorldPlayer attacker,
            long attackerGeneration,
            WorldPlayer target,
            long targetGeneration,
            int requestedDamage,
            long scheduledFromTick,
            long dueTick,
            String damageAuthority,
            String damageFormula,
            String deliveryAuthority,
            String deliveryPolicy
        ) {
            this.hitId = hitId;
            this.attacker = attacker;
            this.attackerGeneration = attackerGeneration;
            this.target = target;
            this.targetGeneration = targetGeneration;
            this.requestedDamage = requestedDamage;
            this.scheduledFromTick = scheduledFromTick;
            this.dueTick = dueTick;
            this.damageAuthority = damageAuthority;
            this.damageFormula = damageFormula;
            this.deliveryAuthority = deliveryAuthority;
            this.deliveryPolicy = deliveryPolicy;
        }

        Snapshot snapshot() {
            return new Snapshot(this);
        }
    }

    private static final DeliveryObserver NO_DELIVERY_OBSERVER =
        (target, result) -> {};
    private static final CombatOutcomeObserver NO_OUTCOME_OBSERVER =
        outcome -> {};

    private final World world;
    private final String deliveryAuthority;
    private final String deliveryPolicy;
    private final CombatOutcomeObserver outcomeObserver;
    private final DeliveryObserver deliveryObserver;
    private final LinkedHashMap<HitId, Entry> entries =
        new LinkedHashMap<>();
    private long nextHitId;

    PlayerPvpDelayedHitService(
        World world,
        String deliveryAuthority,
        String deliveryPolicy
    ) {
        this(
            world,
            deliveryAuthority,
            deliveryPolicy,
            NO_OUTCOME_OBSERVER,
            NO_DELIVERY_OBSERVER
        );
    }

    PlayerPvpDelayedHitService(
        World world,
        String deliveryAuthority,
        String deliveryPolicy,
        CombatOutcomeObserver outcomeObserver,
        DeliveryObserver deliveryObserver
    ) {
        this.world =
            Objects.requireNonNull(
                world,
                "world"
            );
        this.deliveryAuthority =
            requireGameplayAuthority(
                deliveryAuthority
            );
        this.deliveryPolicy =
            requireText(
                deliveryPolicy,
                "deliveryPolicy"
            );
        this.outcomeObserver =
            Objects.requireNonNull(
                outcomeObserver,
                "outcomeObserver"
            );
        this.deliveryObserver =
            Objects.requireNonNull(
                deliveryObserver,
                "deliveryObserver"
            );

        if (!REQUIRE_CURRENT_PLAYER_GENERATIONS.equals(
                this.deliveryPolicy)) {
            throw new IllegalArgumentException(
                "unsupported delayed-hit delivery policy " +
                this.deliveryPolicy
            );
        }
    }

    Snapshot scheduleAtExpectedTick(
        WorldPlayer attacker,
        long attackerGeneration,
        WorldPlayer target,
        long targetGeneration,
        int resolvedDamage,
        int delayTicks,
        String damageAuthority,
        String damageFormula,
        long expectedScheduledFromTick
    ) throws Exception {
        WorldPlayer checkedAttacker =
            Objects.requireNonNull(
                attacker,
                "attacker"
            );
        WorldPlayer checkedTarget =
            Objects.requireNonNull(
                target,
                "target"
            );

        if (checkedAttacker == checkedTarget) {
            throw new IllegalArgumentException(
                "attacker and target must differ"
            );
        }
        if (attackerGeneration <= 0L) {
            throw new IllegalArgumentException(
                "attackerGeneration=" +
                attackerGeneration
            );
        }
        if (targetGeneration <= 0L) {
            throw new IllegalArgumentException(
                "targetGeneration=" +
                targetGeneration
            );
        }
        if (resolvedDamage < 0) {
            throw new IllegalArgumentException(
                "resolvedDamage=" +
                resolvedDamage
            );
        }
        if (delayTicks <= 0) {
            throw new IllegalArgumentException(
                "delayTicks=" +
                delayTicks
            );
        }
        if (expectedScheduledFromTick < 0L) {
            throw new IllegalArgumentException(
                "expectedScheduledFromTick=" +
                expectedScheduledFromTick
            );
        }

        String checkedDamageAuthority =
            requireText(
                damageAuthority,
                "damageAuthority"
            );
        String checkedDamageFormula =
            requireText(
                damageFormula,
                "damageFormula"
            );

        final Snapshot[] result =
            new Snapshot[1];

        boolean current =
            world.withOpenTwoPlayerOwnershipIfCurrent(
                checkedAttacker,
                attackerGeneration,
                checkedTarget,
                targetGeneration,
                () ->
                    world.withClockEventPublicationOwnership(
                        scheduledFromTick -> {
                            if (scheduledFromTick !=
                                    expectedScheduledFromTick) {
                                throw new IllegalStateException(
                                    "delayed PvP schedule tick drift expected=" +
                                    expectedScheduledFromTick +
                                    " actual=" +
                                    scheduledFromTick
                                );
                            }

                            final long dueTick;

                            try {
                                dueTick =
                                    Math.addExact(
                                        scheduledFromTick,
                                        (long) delayTicks
                                    );
                            } catch (
                                ArithmeticException overflow
                            ) {
                                throw new IllegalStateException(
                                    "delayed PvP hit tick overflow tick=" +
                                    scheduledFromTick +
                                    " delay=" +
                                    delayTicks,
                                    overflow
                                );
                            }

                            Entry entry;

                            synchronized (this) {
                                HitId id =
                                    new HitId(
                                        nextId()
                                    );

                                entry =
                                    new Entry(
                                        id,
                                        checkedAttacker,
                                        attackerGeneration,
                                        checkedTarget,
                                        targetGeneration,
                                        resolvedDamage,
                                        scheduledFromTick,
                                        dueTick,
                                        checkedDamageAuthority,
                                        checkedDamageFormula,
                                        deliveryAuthority,
                                        deliveryPolicy
                                    );

                                entries.put(
                                    id,
                                    entry
                                );
                            }

                            WorldEventQueue.Handle handle;

                            try {
                                handle =
                                    world.events()
                                        .schedule(
                                            dueTick,
                                            () ->
                                                deliverFromWorldEvent(
                                                    entry.hitId
                                                )
                                        );
                            } catch (Throwable failure) {
                                synchronized (this) {
                                    entries.remove(
                                        entry.hitId,
                                        entry
                                    );
                                }
                                rethrowUnchecked(
                                    failure
                                );
                                return;
                            }

                            boolean cancelHandle = false;

                            synchronized (this) {
                                Entry currentEntry =
                                    entries.get(
                                        entry.hitId
                                    );

                                if (currentEntry == entry &&
                                    entry.state ==
                                        State.SCHEDULED) {
                                    entry.handle =
                                        handle;
                                } else {
                                    cancelHandle = true;
                                }

                                result[0] =
                                    entry.snapshot();
                            }

                            if (cancelHandle) {
                                handle.cancel();
                            }
                        }
                    )
            );

        if (!current) {
            if (!world.players().owns(
                    checkedAttacker,
                    attackerGeneration)) {
                throw new IllegalStateException(
                    "player attacker is not exact current world generation id=" +
                    checkedAttacker.id() +
                    " expectedGeneration=" +
                    attackerGeneration
                );
            }

            throw new IllegalStateException(
                "player target is not exact current world generation id=" +
                checkedTarget.id() +
                " expectedGeneration=" +
                targetGeneration
            );
        }

        return Objects.requireNonNull(
            result[0],
            "scheduled hit"
        );
    }

    synchronized Snapshot get(HitId hitId) {
        Entry entry =
            entries.get(
                Objects.requireNonNull(
                    hitId,
                    "hitId"
                )
            );

        return entry == null
            ? null
            : entry.snapshot();
    }

    boolean cancel(HitId hitId) {
        Entry entry;
        WorldEventQueue.Handle handle;

        synchronized (this) {
            entry =
                entries.get(
                    Objects.requireNonNull(
                        hitId,
                        "hitId"
                    )
                );

            if (entry == null ||
                entry.state != State.SCHEDULED) {
                return false;
            }

            entry.state = State.CANCELLED;
            entry.executing = false;
            handle = entry.handle;
        }

        if (handle != null) {
            handle.cancel();
        }

        return true;
    }

    synchronized boolean retireTerminal(
        HitId hitId
    ) {
        HitId checked =
            Objects.requireNonNull(
                hitId,
                "hitId"
            );

        Entry entry =
            entries.get(
                checked
            );

        if (entry == null ||
            entry.state == State.SCHEDULED) {
            return false;
        }

        return entries.remove(
            checked,
            entry
        );
    }

    synchronized int size() {
        return entries.size();
    }

    boolean isBoundTo(
        World expectedWorld
    ) {
        return world == expectedWorld;
    }

    String deliveryAuthority() {
        return deliveryAuthority;
    }

    String deliveryPolicy() {
        return deliveryPolicy;
    }

    private void deliverFromWorldEvent(
        HitId hitId
    ) {
        final Entry entry;

        synchronized (this) {
            entry =
                entries.get(
                    hitId
                );

            if (entry == null ||
                entry.state != State.SCHEDULED ||
                entry.executing) {
                return;
            }

            entry.executing = true;
        }

        try {
            final PlayerLifecycleService.DamageResult[]
                delivered = new PlayerLifecycleService.DamageResult[1];
            final boolean[] cancelled = {false};

            boolean current =
                world.withOpenTwoPlayerOwnershipIfCurrent(
                    entry.attacker,
                    entry.attackerGeneration,
                    entry.target,
                    entry.targetGeneration,
                    () -> {
                        synchronized (this) {
                            if (entries.get(
                                    entry.hitId
                                ) != entry ||
                                entry.state !=
                                    State.SCHEDULED) {
                                cancelled[0] = true;
                                return;
                            }
                        }

                        delivered[0] =
                            new PlayerLifecycleService(
                                entry.target
                            ).applyDamage(
                                entry.requestedDamage,
                                entry.dueTick,
                                "PVP_DELAYED_ATTACK attacker=" +
                                entry.attacker.id() +
                                " damageAuthority=" +
                                entry.damageAuthority
                            );

                        if(delivered[0].died&&
                           !delivered[0].ignoredDead)
                            entry.target.lifecycle()
                                .attributeCurrentDeath(
                                    entry.target.lifecycle()
                                        .deathSequence(),
                                    entry.attacker.id(),
                                    entry.attackerGeneration,
                                    "PLAYER_PVP"
                                );

                        synchronized (this) {
                            if (entries.get(
                                    entry.hitId
                                ) == entry &&
                                entry.state ==
                                    State.SCHEDULED) {
                                entry.delivered =
                                    delivered[0];
                                entry.state =
                                    State.DELIVERED;
                            }

                            entry.executing = false;
                        }
                    }
                );

            if (cancelled[0]) {
                return;
            }

            if (!current) {
                if (!world.players().owns(
                        entry.attacker,
                        entry.attackerGeneration)) {
                    terminalWithoutDamage(
                        entry,
                        State.STALE_ATTACKER
                    );
                } else {
                    terminalWithoutDamage(
                        entry,
                        State.STALE_TARGET
                    );
                }
                return;
            }

            PlayerLifecycleService.DamageResult result =
                delivered[0];

            if (result == null) {
                terminalFailure(
                    entry,
                    IllegalStateException.class
                        .getName()
                );
                return;
            }

            publishOutcomes(
                entry,
                result
            );
            publishDelivery(
                entry.target,
                result
            );
        } catch (Throwable failure) {
            terminalFailure(
                entry,
                failure.getClass()
                    .getName()
            );
            rethrowUnchecked(
                failure
            );
        }
    }

    private void publishOutcomes(
        Entry entry,
        PlayerLifecycleService.DamageResult result
    ) {
        if (!result.died ||
            result.ignoredDead) {
            return;
        }

        publishOutcome(
            new CombatOutcome(
                entry.attacker.id().toString(),
                entry.target.id().toString(),
                CombatOutcomeType.PLAYER_KILL,
                CombatOutcomeContext.PLAYER_PVP,
                entry.dueTick,
                PlayerLifecycleService.AUTHORITY
            )
        );

        publishOutcome(
            new CombatOutcome(
                entry.attacker.id().toString(),
                entry.target.id().toString(),
                CombatOutcomeType.PLAYER_DEATH,
                CombatOutcomeContext.PLAYER_PVP,
                entry.dueTick,
                PlayerLifecycleService.AUTHORITY
            )
        );
    }

    private void publishOutcome(
        CombatOutcome outcome
    ) {
        try {
            outcomeObserver.onCombatOutcome(
                outcome
            );
        } catch (RuntimeException error) {
            System.err.println(
                "[combat] delayed outcome observer failure" +
                " type=" + outcome.type() +
                " attacker=" + outcome.attacker() +
                " victim=" + outcome.victim() +
                " tick=" + outcome.worldTick() +
                " error=" + error
            );
        }
    }

    private void publishDelivery(
        WorldPlayer target,
        PlayerLifecycleService.DamageResult result
    ) {
        try {
            deliveryObserver.onDelivered(
                target,
                result
            );
        } catch (Throwable error) {
            System.err.println(
                "[combat] delayed PvP delivery observer failure" +
                " target=" + target.id() +
                " tick=" + result.worldTick +
                " error=" + error
            );
        }
    }

    private void terminalWithoutDamage(
        Entry entry,
        State state
    ) {
        synchronized (this) {
            if (entries.get(
                    entry.hitId
                ) != entry ||
                entry.state != State.SCHEDULED) {
                return;
            }

            entry.state = state;
            entry.executing = false;
        }
    }

    private void terminalFailure(
        Entry entry,
        String failureType
    ) {
        synchronized (this) {
            if (entries.get(
                    entry.hitId
                ) != entry) {
                return;
            }

            if (entry.state == State.SCHEDULED) {
                entry.state = State.FAILED;
            }

            entry.failureType = failureType;
            entry.executing = false;
        }
    }

    private synchronized long nextId() {
        if (nextHitId == Long.MAX_VALUE) {
            throw new IllegalStateException(
                "delayed PvP hit id exhausted"
            );
        }

        return ++nextHitId;
    }

    private static String requireGameplayAuthority(
        String value
    ) {
        String clean =
            requireText(
                value,
                "deliveryAuthority"
            );

        if ("EXACT_CURRENT_CLIENT".equals(
                clean) ||
            "UNKNOWN_SERVER_AUTHORITY".equals(
                clean)) {
            throw new IllegalArgumentException(
                "client/unknown authority cannot define delayed PvP delivery actual=" +
                clean
            );
        }

        return clean;
    }

    private static String requireText(
        String value,
        String label
    ) {
        if (value == null) {
            throw new NullPointerException(
                label
            );
        }

        String clean = value.trim();

        if (clean.isEmpty()) {
            throw new IllegalArgumentException(
                label + " blank"
            );
        }

        return clean;
    }

    private static void rethrowUnchecked(
        Throwable failure
    ) {
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }

        throw new IllegalStateException(
            failure
        );
    }
}
