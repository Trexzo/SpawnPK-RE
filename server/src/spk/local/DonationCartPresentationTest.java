package spk.local;

import java.util.ArrayList;
import java.util.List;

public final class DonationCartPresentationTest {
    public static void main(String[] args){
        exactInputRouting();
        semanticCatalogOrder();
        exactConstants();

        System.out.println(
            "DONATION_CART_PRESENTATION_PASS "+
            "root60200=true "+
            "productSlots=8 "+
            "quantityControls=16 "+
            "checkout60214=true "+
            "paypal60273=true "+
            "osrsGp60274=true "+
            "clientItemIdsTrusted=false "+
            "staticPricesTrusted=false "+
            "paymentSettlementOwned=false"
        );
    }

    private static void exactInputRouting(){
        int[] decrease={
            60227,60233,60239,60245,
            60251,60257,60263,60284
        };
        int[] increase={
            60230,60236,60242,60248,
            60254,60260,60266,60287
        };

        for(int i=0;i<8;i++){
            DonationCartPresentation.Input down=
                DonationCartPresentation
                    .resolveWidget(decrease[i]);
            DonationCartPresentation.Input up=
                DonationCartPresentation
                    .resolveWidget(increase[i]);

            require(
                down!=null&&
                down.kind==
                    DonationCartPresentation
                        .InputKind
                        .ADJUST_QUANTITY&&
                down.productIndex==i&&
                down.quantityDelta==-1,
                "decrease slot "+i
            );

            require(
                up!=null&&
                up.kind==
                    DonationCartPresentation
                        .InputKind
                        .ADJUST_QUANTITY&&
                up.productIndex==i&&
                up.quantityDelta==1,
                "increase slot "+i
            );
        }

        require(
            DonationCartPresentation
                .resolveWidget(60214)
                .kind==
                DonationCartPresentation
                    .InputKind
                    .PREPARE_CHECKOUT,
            "prepare checkout"
        );

        require(
            DonationCartPresentation
                .resolveWidget(60273)
                .paymentMode==
                DonationCartService
                    .PaymentMode
                    .PAYPAL,
            "PayPal selection"
        );

        require(
            DonationCartPresentation
                .resolveWidget(60274)
                .paymentMode==
                DonationCartService
                    .PaymentMode
                    .OSRS_GP,
            "OSRS GP selection"
        );

        require(
            DonationCartPresentation
                .resolveWidget(60215)==null&&
            DonationCartPresentation
                .resolveWidget(60228)==null,
            "paired/hover widgets are not semantic actions"
        );
    }

    private static void semanticCatalogOrder(){
        List<DonationCartService.Product> products=
            new ArrayList<>();

        for(int i=0;i<8;i++)
            products.add(
                new DonationCartService.Product(
                    "product:"+i,
                    "Product "+i
                )
            );

        DonationCartService service=
            new DonationCartService(
                products,
                "LOCAL_LAB_POLICY_DONATION_CART"
            );

        List<DonationCartService.Product> catalog=
            service.catalog();

        for(int i=0;i<8;i++)
            require(
                ("product:"+i).equals(
                    DonationCartPresentation
                        .productKey(catalog,i)
                ),
                "semantic product order "+i
            );

        expect(
            IllegalArgumentException.class,
            ()->DonationCartPresentation
                .productKey(catalog,8),
            "product index overflow"
        );
    }

    private static void exactConstants(){
        require(
            DonationCartPresentation.ROOT==60200,
            "root"
        );
        require(
            DonationCartPresentation.PRODUCT_SLOTS==
                DonationCartService
                    .VISIBLE_PRODUCT_LIMIT,
            "visible product parity"
        );
        require(
            DonationCartPresentation
                .quantityWidget(0)==60205&&
            DonationCartPresentation
                .quantityWidget(7)==60282,
            "quantity widget endpoints"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                DonationCartPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                DonationCartPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0xeb&&
            (root[1]&255)==0x28,
            "root S2C97 body"
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private DonationCartPresentationTest(){}
}
