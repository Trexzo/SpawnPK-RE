package spk.local;

/**
 * Internal diagnostic projection for the trusted LocalLab content module.
 *
 * This is deliberately package-private: public plugins continue to see only
 * spk.content.api.ContentPlayer and cannot inspect runtime diagnostic state.
 */
interface LocalDiagnosticContentPlayer {
    String prayerStateSummary();
    String magicStateSummary();
    int weaponItemId();
    String combatStyleStateSummary(int interfaceRoot);
}
