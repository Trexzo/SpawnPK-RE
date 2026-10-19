package spk.local;

/**
 * Narrow, source-grounded save-intent classifier for the existing bank
 * action results. A decoded bank widget or UI response is not itself
 * evidence of a committed inventory/metadata mutation.
 *
 * String result matching is intentionally allowlisted: new bank success
 * semantics must be audited before they can trigger persistence.
 * This is a save *request*, not a durable disk receipt.
 */
final class BankMutationSaveClassifier {
    private BankMutationSaveClassifier(){}

    static boolean itemAction(String result){
        return result!=null&&(
            result.startsWith("WITHDRAW_OK amount=")||
            result.startsWith("WITHDRAW_ALL_BUT_ONE_OK amount=")||
            result.startsWith("WITHDRAW_CONFIGURED_OK amount=")||
            result.startsWith("STORE_OK amount=")
        );
    }

    static boolean amount(String result){
        return result!=null&&(
            result.startsWith("WITHDRAW_X_OK amount=")||
            result.startsWith("STORE_X_OK amount=")
        );
    }

    static boolean drag(String result){
        return result!=null&&(
            result.startsWith("INVENTORY_DRAG_OK mode=")||
            result.startsWith("DRAG_OK mode=")
        );
    }

    static boolean command(String result){
        return result!=null&&(
            result.startsWith("SETBANKTAB_OK slot=")||
            result.startsWith("SWAPBANKTAB_OK sourceTab=")
        );
    }
}
