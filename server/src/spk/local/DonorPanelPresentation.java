package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Main Donor Panel presentation/input adapter.
 *
 * This adapter normalizes the seven proven donor-navigation controls and
 * caller-authored promotion text. Payment processing, entitlements, shop
 * policy and teleport destinations remain outside this layer.
 */
final class DonorPanelPresentation {
    static final int ROOT=60062;
    static final int DONATE_WIDGET=60073;
    static final int VIEW_PERKS_WIDGET=60074;
    static final int OPEN_SHOP_WIDGET=60075;
    static final int TELEPORT_DONATOR_WIDGET=60076;
    static final int TELEPORT_ELITE_WIDGET=60077;
    static final int TELEPORT_VIP_WIDGET=60078;
    static final int TELEPORT_SPONSOR_WIDGET=60079;
    static final int PROMOTION_PROGRESS_WIDGET=60091;
    static final int NEXT_PROMOTION_WIDGET=60100;
    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    enum Intent {
        DONATE_FOR_REWARDS,
        VIEW_DONATOR_PERKS,
        OPEN_DONATOR_SHOP,
        TELEPORT_DONATOR_ZONE,
        TELEPORT_ELITE_DONATOR_ZONE,
        TELEPORT_VIP_DONATOR_ZONE,
        TELEPORT_SPONSOR_DONATOR_ZONE
    }

    static Intent resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        switch(widgetId){
            case DONATE_WIDGET:
                return Intent.DONATE_FOR_REWARDS;
            case VIEW_PERKS_WIDGET:
                return Intent.VIEW_DONATOR_PERKS;
            case OPEN_SHOP_WIDGET:
                return Intent.OPEN_DONATOR_SHOP;
            case TELEPORT_DONATOR_WIDGET:
                return Intent.TELEPORT_DONATOR_ZONE;
            case TELEPORT_ELITE_WIDGET:
                return Intent.TELEPORT_ELITE_DONATOR_ZONE;
            case TELEPORT_VIP_WIDGET:
                return Intent.TELEPORT_VIP_DONATOR_ZONE;
            case TELEPORT_SPONSOR_WIDGET:
                return Intent.TELEPORT_SPONSOR_DONATOR_ZONE;
            default:
                return null;
        }
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(ROOT)
            );
    }

    static void publishPromotionText(
        ServerPacketWriter packets,
        String promotionProgressText,
        String nextPromotionText
    )throws IOException{
        Objects.requireNonNull(packets,"packets");

        ApplicationBus126Publisher.send(
            packets,
            PROMOTION_PROGRESS_WIDGET,
            wireText(
                promotionProgressText,
                "promotionProgressText"
            )
        );
        ApplicationBus126Publisher.send(
            packets,
            NEXT_PROMOTION_WIDGET,
            wireText(
                nextPromotionText,
                "nextPromotionText"
            )
        );
    }

    private static String wireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);

            if(ch=='\n'||ch=='\r')
                throw new IllegalArgumentException(
                    field+" contains line terminator"
                );

            if(ch>0xff)
                throw new IllegalArgumentException(
                    field+
                    " not ISO-8859-1 at index="+
                    i
                );
        }

        return value;
    }

    private DonorPanelPresentation(){}
}
