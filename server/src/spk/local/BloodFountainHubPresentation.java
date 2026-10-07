package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 Blood Fountain hub presentation/input adapter.
 *
 * This class owns only the recovered root and six navigation widget identities.
 * Target application mechanics remain caller/server authority.
 */
final class BloodFountainHubPresentation {
    static final int ROOT=3320;
    static final int PERK_TREE_WIDGET=60002;
    static final int BLOOD_POOL_STORE_WIDGET=60003;
    static final int BLOOD_DIAMOND_FUSER_WIDGET=60004;
    static final int BLOOD_DIAMOND_STORE_WIDGET=60005;
    static final int BLOOD_SHARD_SALVAGING_WIDGET=60006;
    static final int BLOOD_SHARD_STORE_WIDGET=60007;
    static final int WIDGET_ACTION_OPCODE=185;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static BloodFountainHubService.Intent resolveWidget(
        int widgetId
    ){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        switch(widgetId){
            case PERK_TREE_WIDGET:
                return BloodFountainHubService
                    .Intent.PERK_TREE;
            case BLOOD_POOL_STORE_WIDGET:
                return BloodFountainHubService
                    .Intent.BLOOD_POOL_STORE;
            case BLOOD_DIAMOND_FUSER_WIDGET:
                return BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_FUSER;
            case BLOOD_DIAMOND_STORE_WIDGET:
                return BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_STORE;
            case BLOOD_SHARD_SALVAGING_WIDGET:
                return BloodFountainHubService
                    .Intent.BLOOD_SHARD_SALVAGING;
            case BLOOD_SHARD_STORE_WIDGET:
                return BloodFountainHubService
                    .Intent.BLOOD_SHARD_STORE;
            default:
                return null;
        }
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        ).fixed(
            97,
            BootstrapPackets.interface97(
                ROOT
            )
        );
    }

    private BloodFountainHubPresentation(){}
}
