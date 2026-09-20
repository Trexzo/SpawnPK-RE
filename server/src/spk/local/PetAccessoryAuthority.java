package spk.local;

/**
 * Evidence-gated pet-accessory definitions used by current LocalLab behavior.
 *
 * These mappings preserve their existing provenance. They are presentation
 * authority only and do not imply unrecovered original-server mechanics.
 */
final class PetAccessoryAuthority {
    static boolean isAccessory(int itemId){
        return (itemId>=20542&&itemId<=20546)||
            itemId==20699||
            itemId==21068;
    }

    static String name(int itemId){
        switch(itemId){
            case 20542:return "White pet accessory";
            case 20543:return "Red pet accessory";
            case 20544:return "Green pet accessory";
            case 20545:return "Blue pet accessory";
            case 20546:return "Gold pet accessory";
            case 20699:return "Enchanted pet accessory";
            case 21068:return "Easter pet accessory";
            default:return "Unknown pet accessory";
        }
    }

    static Integer selector(int itemId){
        switch(itemId){
            // R2.10 runtime authority: these values are the selectors that the
            // current LocalLab client rendered as the labelled accessory colors.
            case 20542:return 1;
            case 20543:return 2;
            case 20544:return 3;
            case 20545:return 4;
            case 20546:return 5;
            case 20699:return 7;
            case 21068:return 8;
            default:return null;
        }
    }

    static String selectorAuthority(int itemId){
        return itemId>=20542&&itemId<=20546
            ?"USER_RUNTIME_CERTIFIED_LABEL_TO_SELECTOR_R2_10"
            :itemId==20699
                ?"STRONG_ENCHANTED_CYCLE_BEHAVIOR_NAME_MAPPING"
                :itemId==21068
                    ?"STRONG_EASTER_CYAN_MAGENTA_BEHAVIOR_NAME_MAPPING"
                    :"UNKNOWN";
    }

    private PetAccessoryAuthority(){}
}
