package spk.local;

/**
 * Caller-owned world policy for materializing lost PvP death items.
 *
 * This policy chooses visibility/ownership only. It does not decide which
 * items are kept or lost; PlayerDeathDispositionPolicy owns that decision.
 */
interface PlayerDeathGroundDropPolicy {
    String authority();

    String ownerRef(
        PlayerDeathAttributionRegistry.Attribution attribution,
        PlayerDeathCarriedSettlementService.Receipt carried
    );

    boolean devOwned();
}
