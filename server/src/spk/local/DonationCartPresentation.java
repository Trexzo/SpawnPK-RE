package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 Donation Shopping Cart presentation/input adapter.
 *
 * Exact client widget positions map onto caller-defined semantic product order.
 * Client item ids, dollar labels and payment selections are never treated as
 * trusted commerce authority.
 */
final class DonationCartPresentation {
    static final int ROOT=60200;
    static final int CHECKOUT_WIDGET=60214;
    static final int PAYPAL_WIDGET=60273;
    static final int OSRS_GP_WIDGET=60274;
    static final int PRODUCT_SLOTS=8;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    private static final int[] QUANTITY_WIDGETS={
        60205,60206,60207,60208,
        60209,60210,60211,60282
    };

    private static final int[] DECREASE_WIDGETS={
        60227,60233,60239,60245,
        60251,60257,60263,60284
    };

    private static final int[] INCREASE_WIDGETS={
        60230,60236,60242,60248,
        60254,60260,60266,60287
    };

    enum InputKind {
        ADJUST_QUANTITY,
        PREPARE_CHECKOUT,
        SELECT_PAYMENT
    }

    static final class Input {
        final InputKind kind;
        final int productIndex;
        final int quantityDelta;
        final DonationCartService.PaymentMode paymentMode;

        private Input(
            InputKind kind,
            int productIndex,
            int quantityDelta,
            DonationCartService.PaymentMode paymentMode
        ){
            this.kind=Objects.requireNonNull(kind,"kind");
            this.productIndex=productIndex;
            this.quantityDelta=quantityDelta;
            this.paymentMode=paymentMode;
        }

        static Input adjust(
            int productIndex,
            int quantityDelta
        ){
            if(quantityDelta!=-1&&quantityDelta!=1)
                throw new IllegalArgumentException(
                    "quantityDelta="+quantityDelta
                );

            return new Input(
                InputKind.ADJUST_QUANTITY,
                checkedProductIndex(productIndex),
                quantityDelta,
                null
            );
        }

        static Input checkout(){
            return new Input(
                InputKind.PREPARE_CHECKOUT,
                -1,
                0,
                null
            );
        }

        static Input payment(
            DonationCartService.PaymentMode mode
        ){
            return new Input(
                InputKind.SELECT_PAYMENT,
                -1,
                0,
                Objects.requireNonNull(
                    mode,
                    "paymentMode"
                )
            );
        }
    }

    static Input resolveWidget(int widgetId){
        if(widgetId<0||widgetId>0xffff)
            throw new IllegalArgumentException(
                "widgetId="+widgetId
            );

        for(int i=0;i<PRODUCT_SLOTS;i++){
            if(widgetId==DECREASE_WIDGETS[i])
                return Input.adjust(i,-1);

            if(widgetId==INCREASE_WIDGETS[i])
                return Input.adjust(i,1);
        }

        if(widgetId==CHECKOUT_WIDGET)
            return Input.checkout();

        if(widgetId==PAYPAL_WIDGET)
            return Input.payment(
                DonationCartService.PaymentMode.PAYPAL
            );

        if(widgetId==OSRS_GP_WIDGET)
            return Input.payment(
                DonationCartService.PaymentMode.OSRS_GP
            );

        return null;
    }

    static String productKey(
        List<DonationCartService.Product> catalog,
        int productIndex
    ){
        Objects.requireNonNull(catalog,"catalog");
        int checked=checkedProductIndex(productIndex);

        if(checked>=catalog.size())
            throw new IllegalStateException(
                "Donation Cart product slot not configured index="+
                checked+
                " catalogSize="+catalog.size()
            );

        return Objects.requireNonNull(
            catalog.get(checked),
            "catalog product"
        ).productKey;
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

    static void publishZeroQuantities(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(
            packets,
            "packets"
        );

        for(int i=0;i<PRODUCT_SLOTS;i++)
            ApplicationBus126Publisher.send(
                packets,
                QUANTITY_WIDGETS[i],
                "0"
            );
    }

    static void publishQuantities(
        ServerPacketWriter packets,
        List<DonationCartService.Product> catalog,
        DonationCartService.CartSnapshot snapshot
    )throws IOException{
        Objects.requireNonNull(packets,"packets");
        Objects.requireNonNull(catalog,"catalog");
        Objects.requireNonNull(snapshot,"snapshot");

        if(catalog.size()>PRODUCT_SLOTS)
            throw new IllegalArgumentException(
                "Donation Cart catalog size="+
                catalog.size()+
                " max="+PRODUCT_SLOTS
            );

        for(int i=0;i<PRODUCT_SLOTS;i++){
            long quantity=0L;

            if(i<catalog.size()){
                String key=
                    Objects.requireNonNull(
                        catalog.get(i),
                        "catalog product"
                    ).productKey;

                for(DonationCartService.Line line:
                        snapshot.lines)
                    if(line.productKey.equals(key)){
                        quantity=line.quantity;
                        break;
                    }
            }

            ApplicationBus126Publisher.send(
                packets,
                QUANTITY_WIDGETS[i],
                Long.toString(quantity)
            );
        }
    }

    static int quantityWidget(int productIndex){
        return QUANTITY_WIDGETS[
            checkedProductIndex(productIndex)
        ];
    }

    private static int checkedProductIndex(
        int productIndex
    ){
        if(productIndex<0||
           productIndex>=PRODUCT_SLOTS)
            throw new IllegalArgumentException(
                "productIndex="+productIndex
            );
        return productIndex;
    }

    private DonationCartPresentation(){}
}
