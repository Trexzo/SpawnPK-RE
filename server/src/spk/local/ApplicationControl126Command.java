package spk.local;

import java.util.Objects;

/**
 * Typed exact-current S2C126 application-control publication.
 *
 * This is protocol/presentation state only. It carries no server-side eligibility,
 * economy, reward, persistence or gameplay authority.
 */
final class ApplicationControl126Command {
    enum Authority {
        EXACT_CURRENT_CLIENT,
        UNKNOWN_SERVER_AUTHORITY
    }

    enum Target {
        GLOBAL_CONTROL(
            1,
            "global-control-dispatch"
        ),
        BLOOD_POOL_SLOT_APPEND(
            37,
            "blood-pool-slot-append"
        ),
        DAILY_CHALLENGE_DEFINITION(
            55,
            "daily-challenge-definition"
        ),
        DAILY_MONEY_MAKING_TRACKING_TEXT(
            26,
            "daily-money-making-tracking-text"
        ),
        DAILY_MONEY_MAKING_SELECTED_CATEGORY(
            36,
            "daily-money-making-selected-category"
        ),
        COLLECTION_CLEAR_ROWS(
            54315,
            "collection-log-clear-rows"
        ),
        COLLECTION_SELECT_ROW(
            54421,
            "collection-log-select-row"
        ),
        COLLECTION_SELECT_CATEGORY(
            54422,
            "collection-log-select-category"
        );

        private final int key;
        private final String semantic;

        Target(
            int key,
            String semantic
        ){
            this.key=key;
            this.semantic=semantic;
        }

        int key(){
            return key;
        }

        String semantic(){
            return semantic;
        }
    }

    enum Token {
        CONSTRUCTION_BUILD_ON("CONSTRUCTION_BUILD_ON"),
        CONSTRUCTION_BUILD_OFF("CONSTRUCTION_BUILD_OFF"),
        BEGIN_ADVENTURE("BEGIN_ADVENTURE"),
        BEGIN_ADVENTURE_ORB("BEGIN_ADVENTURE_ORB"),
        BEGIN_ADVENTURE_BOOK("BEGIN_ADVENTURE_BOOK"),
        END_ADVENTURE("END_ADVENTURE"),
        RAID_INSTANCE_ON("RAID_INSTANCE_ON"),
        RAID_INSTANCE_OFF("RAID_INSTANCE_OFF"),
        CLEAR_ACHIEVEMENT_TAB("CLEAR_ACHIEVEMENT_TAB"),
        BUILD_ACHIEVEMENT_TAB("BUILD_ACHIEVEMENT_TAB"),
        CLEAR_EXCHANGE("clear_exchange"),
        UPDATE_EXCHANGE("update_exchange"),
        CLEAR_SELL_MARKET("clearsellmarket"),
        CLEAR_BUY_MARKET("clearbuymarket"),
        CLEAR_INVENTORY_OVERLAY("clearinvoverlay"),
        CLEAR_DIALOG("cleardialog"),
        TOGGLE_BOUNTY_HUNTER("togglebh"),
        QUICK_PRAYERS_ON("QUICK_PRAYERS_ON"),
        QUICK_PRAYERS_OFF("QUICK_PRAYERS_OFF"),
        DISABLE_QUICK_PRAYERS("DISABLE_QUICK_PRAYERS"),
        RESET_BLOOD_POOL_SHOP_SLOTS("RESET_BLOOD_POOL_SHOP_SLOTS");

        private final String payload;

        Token(String payload){
            this.payload=payload;
        }

        String payload(){
            return payload;
        }
    }

    private final Target target;
    private final String payload;
    private final Authority authority;
    private final String semantic;

    private ApplicationControl126Command(
        Target target,
        String payload,
        Authority authority,
        String semantic
    ){
        this.target=Objects.requireNonNull(
            target,
            "target"
        );
        this.payload=Objects.requireNonNull(
            payload,
            "payload"
        );
        this.authority=Objects.requireNonNull(
            authority,
            "authority"
        );
        this.semantic=Objects.requireNonNull(
            semantic,
            "semantic"
        );
    }

    static ApplicationControl126Command token(
        Token token
    ){
        Objects.requireNonNull(
            token,
            "token"
        );

        return exact(
            Target.GLOBAL_CONTROL,
            token.payload(),
            "token:"+
                token.name()
        );
    }

    static ApplicationControl126Command addExchange(
        int first,
        int second
    ){
        return exact(
            Target.GLOBAL_CONTROL,
            "add_exchange "+
                first+
                ","+
                second,
            "exchange:add"
        );
    }

    static ApplicationControl126Command setSellItem(
        int itemId
    ){
        if(itemId<0)
            throw new IllegalArgumentException(
                "itemId="+itemId
            );

        return exact(
            Target.GLOBAL_CONTROL,
            "setsellitem,"+
                itemId,
            "market:set-sell-item"
        );
    }

    static ApplicationControl126Command loginRewardIndex(
        int index
    ){
        return exact(
            Target.GLOBAL_CONTROL,
            "LOGIN_REWARD_IDX "+
                index,
            "login-reward:index"
        );
    }

    static ApplicationControl126Command dailyMoneyMakingTrackingText(
        String text
    ){
        if(text==null)
            throw new NullPointerException(
                "text"
            );

        if(text.indexOf('\n')>=0||
           text.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                "Daily Money Making tracking text contains line break"
            );

        return exact(
            Target.DAILY_MONEY_MAKING_TRACKING_TEXT,
            text,
            "daily-money-making:tracking-text"
        );
    }

    static ApplicationControl126Command dailyMoneyMakingSelectedCategory(
        int state
    ){
        if(state<1||state>3)
            throw new IllegalArgumentException(
                "Daily Money Making category state="+
                state
            );

        return exact(
            Target.DAILY_MONEY_MAKING_SELECTED_CATEGORY,
            Integer.toString(state),
            "daily-money-making:selected-category"
        );
    }

    static ApplicationControl126Command bloodPoolResetSlots(){
        return token(
            Token.RESET_BLOOD_POOL_SHOP_SLOTS
        );
    }

    static ApplicationControl126Command dailyChallengeDefinition(
        int metadataA,
        int metadataB,
        String challengeKey,
        String description,
        int current,
        int target
    ){
        requireDailyChallengeField(
            challengeKey,
            "challengeKey",
            false
        );
        requireDailyChallengeField(
            description,
            "description",
            true
        );

        return exact(
            Target.DAILY_CHALLENGE_DEFINITION,
            metadataA+";"+
                metadataB+";"+
                challengeKey+";"+
                description+";"+
                current+";"+
                target,
            "daily-challenge:definition"
        );
    }

    static ApplicationControl126Command bloodPoolAppendOpaque(
        String exactRecord
    ){
        if(exactRecord==null)
            throw new NullPointerException(
                "exactRecord"
            );

        if(exactRecord.trim().isEmpty())
            throw new IllegalArgumentException(
                "Blood Pool slot record blank"
            );

        if(exactRecord.indexOf('\n')>=0||
           exactRecord.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                "Blood Pool slot record must remain one S2C126 string"
            );

        return exact(
            Target.BLOOD_POOL_SLOT_APPEND,
            exactRecord,
            "blood-pool:slot-append:opaque-fields"
        );
    }

    static ApplicationControl126Command clearClanChat(
        int widgetStart
    ){
        if(widgetStart<0||
           widgetStart>0xffff)
            throw new IllegalArgumentException(
                "widgetStart="+
                widgetStart
            );

        return exact(
            Target.GLOBAL_CONTROL,
            "clearcc "+
                widgetStart,
            "clan-chat:clear"
        );
    }

    static ApplicationControl126Command itemGuideSelected(
        int widgetId
    ){
        if(widgetId<47505||
           widgetId>47703)
            throw new IllegalArgumentException(
                "item-guide widgetId="+
                widgetId
            );

        return exact(
            Target.GLOBAL_CONTROL,
            "ITEM_GUIDE_SELECTED_"+
                widgetId,
            "item-guide:selected"
        );
    }

    static ApplicationControl126Command wikiSelected(
        int widgetId
    ){
        if(widgetId<46506||
           widgetId>46605)
            throw new IllegalArgumentException(
                "wiki widgetId="+
                widgetId
            );

        return exact(
            Target.GLOBAL_CONTROL,
            "WIKI_SELECTED_"+
                widgetId,
            "wiki:selected"
        );
    }

    static ApplicationControl126Command collectionClearRows(){
        return exact(
            Target.COLLECTION_CLEAR_ROWS,
            "",
            "collection-log:clear-rows"
        );
    }

    static ApplicationControl126Command collectionSelectRow(
        int widgetId
    ){
        if(widgetId<54314||
           widgetId>54412||
           (widgetId&1)!=0)
            throw new IllegalArgumentException(
                "collection row widgetId="+
                widgetId
            );

        return exact(
            Target.COLLECTION_SELECT_ROW,
            Integer.toString(
                widgetId
            ),
            "collection-log:select-row"
        );
    }

    static ApplicationControl126Command collectionSelectCategory(
        int widgetId
    ){
        if(widgetId<54302||
           widgetId>54306)
            throw new IllegalArgumentException(
                "collection category widgetId="+
                widgetId
            );

        return exact(
            Target.COLLECTION_SELECT_CATEGORY,
            Integer.toString(
                widgetId
            ),
            "collection-log:select-category"
        );
    }

    private static void requireDailyChallengeField(
        String value,
        String name,
        boolean allowEmpty
    ){
        if(value==null)
            throw new NullPointerException(
                name
            );

        if(!allowEmpty&&
           value.isEmpty())
            throw new IllegalArgumentException(
                name+" empty"
            );

        if(value.indexOf(';')>=0||
           value.indexOf('\n')>=0||
           value.indexOf('\r')>=0)
            throw new IllegalArgumentException(
                name+
                " contains Daily Challenge record delimiter"
            );
    }

    private static ApplicationControl126Command exact(
        Target target,
        String payload,
        String semantic
    ){
        return new ApplicationControl126Command(
            target,
            payload,
            Authority.EXACT_CURRENT_CLIENT,
            semantic
        );
    }

    Target target(){
        return target;
    }

    String payload(){
        return payload;
    }

    Authority authority(){
        return authority;
    }

    String semantic(){
        return semantic;
    }

    @Override public String toString(){
        return "ApplicationControl126Command{"+
            "target="+target+
            ",semantic="+semantic+
            ",authority="+authority+
            "}";
    }
}
