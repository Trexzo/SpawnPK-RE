package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class DonationCartServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_DONATION_CART";

    public static void main(String[] args){
        DonationCartService service=
            new DonationCartService(
                Arrays.asList(
                    new DonationCartService.Product(
                        "bond:starter",
                        "Starter Bond"
                    ),
                    new DonationCartService.Product(
                        "bond:mid",
                        "Mid Bond"
                    ),
                    new DonationCartService.Product(
                        "bond:large",
                        "Large Bond"
                    )
                ),
                POLICY
            );

        constructorGuards();

        DonationCartService.CartSnapshot initial=
            service.snapshot(
                " Player:Alice "
            );

        require(
            "player:alice".equals(
                initial.playerRef
            )&&
            initial.empty()&&
            initial.paymentMode==null&&
            initial.revision==0L&&
            POLICY.equals(
                initial.policyAuthority
            )&&
            DonationCartService
                .PRESENTATION_AUTHORITY
                .equals(
                    initial
                        .presentationAuthority
                ),
            "initial donation cart"
        );

        require(
            service.playerCartCount()==0,
            "read-only snapshot created cart"
        );

        expect(
            IllegalStateException.class,
            ()->service.prepareCheckout(
                "player:alice"
            ),
            "empty checkout"
        );

        require(
            service.playerCartCount()==0,
            "rejected checkout created cart"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.increase(
                "player:alice",
                "bond:missing"
            ),
            "unknown donation product"
        );

        require(
            service.playerCartCount()==0,
            "unknown product created cart"
        );

        DonationCartService.CartSnapshot
            one=
                service.increase(
                    "PLAYER:ALICE",
                    "BOND:STARTER"
                );

        require(
            one.revision==1L&&
            one.lines.size()==1&&
            "bond:starter".equals(
                one.lines.get(0).productKey
            )&&
            one.lines.get(0).quantity==1L,
            "first quantity increase"
        );

        DonationCartService.CartSnapshot
            two=
                service.increase(
                    "player:alice",
                    "bond:mid"
                );

        require(
            two.revision==2L&&
            two.lines.size()==2,
            "second product increase"
        );

        DonationCartService.CartSnapshot
            three=
                service.increase(
                    "player:alice",
                    "bond:starter"
                );

        require(
            three.revision==3L&&
            three.lines.get(0).quantity==2L,
            "repeat product increase"
        );

        DonationCartService.CartSnapshot
            reduced=
                service.decrease(
                    "player:alice",
                    "bond:starter"
                );

        require(
            reduced.revision==4L&&
            reduced.lines.get(0).quantity==1L,
            "quantity decrease"
        );

        expect(
            IllegalStateException.class,
            ()->service.prepareCheckout(
                "player:alice"
            ),
            "checkout without payment mode"
        );

        DonationCartService.CartSnapshot
            paypal=
                service.selectPayment(
                    "player:alice",
                    DonationCartService
                        .PaymentMode.PAYPAL
                );

        require(
            paypal.revision==5L&&
            paypal.paymentMode==
                DonationCartService
                    .PaymentMode.PAYPAL,
            "PayPal selection"
        );

        DonationCartService.CheckoutRequest
            request1=
                service.prepareCheckout(
                    "player:alice"
                );

        DonationCartService.CheckoutRequest
            request1Retry=
                service.prepareCheckout(
                    "PLAYER:ALICE"
                );

        require(
            request1==request1Retry&&
            request1.requestId==1L&&
            request1.cartRevision==5L&&
            request1.paymentMode==
                DonationCartService
                    .PaymentMode.PAYPAL&&
            request1.lines.size()==2,
            "checkout request idempotent per revision"
        );

        service.increase(
            "player:alice",
            "bond:large"
        );

        DonationCartService.CheckoutRequest
            request2=
                service.prepareCheckout(
                    "player:alice"
                );

        require(
            request2.requestId==2L&&
            request2.cartRevision==6L&&
            request2.lines.size()==3&&
            request1.lines.size()==2,
            "later cart edit does not rewrite prepared checkout"
        );

        DonationCartService.CartSnapshot
            osrs=
                service.selectPayment(
                    "player:alice",
                    DonationCartService
                        .PaymentMode.OSRS_GP
                );

        require(
            osrs.revision==7L&&
            osrs.paymentMode==
                DonationCartService
                    .PaymentMode.OSRS_GP,
            "OSRS GP selection"
        );

        DonationCartService.CheckoutRequest
            request3=
                service.prepareCheckout(
                    "player:alice"
                );

        require(
            request3.requestId==3L&&
            request3.paymentMode==
                DonationCartService
                    .PaymentMode.OSRS_GP&&
            request3.cartRevision==7L,
            "payment-mode change invalidates checkout request"
        );

        DonationCartService.CartSnapshot
            sameMode=
                service.selectPayment(
                    "player:alice",
                    DonationCartService
                        .PaymentMode.OSRS_GP
                );

        require(
            sameMode.revision==7L&&
            service.prepareCheckout(
                "player:alice"
            )==request3,
            "same payment selection idempotent"
        );

        playerIsolation(service);
        decrementZeroFailure(service);
        immutableViews(service);
        protocolAndCommerceBoundary();

        System.out.println(
            "DONATION_CART_SERVICE_PASS "+
            "visibleProductLimit=8 "+
            "callerCatalog=true "+
            "normalizedPlayerState=true "+
            "quantityRevisioned=true "+
            "paymentModes=PAYPAL_OSRS_GP "+
            "checkoutRequiresNonEmpty=true "+
            "checkoutRequiresPaymentMode=true "+
            "checkoutIdempotentPerRevision=true "+
            "preparedSnapshotStable=true "+
            "trustedPriceOwned=false "+
            "clientItemIdsOwned=false "+
            "paymentSettlementOwned=false "+
            "fulfillmentOwned=false "+
            "promotionEligibilityOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void constructorGuards(){
        ArrayList<DonationCartService.Product>
            tooMany=
                new ArrayList<>();

        for(int i=0;i<9;i++)
            tooMany.add(
                new DonationCartService.Product(
                    "product:"+i,
                    "Product "+i
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->new DonationCartService(
                tooMany,
                POLICY
            ),
            "exact visible product capacity"
        );

        expect(
            IllegalArgumentException.class,
            ()->new DonationCartService(
                Collections.singletonList(
                    new DonationCartService.Product(
                        "product:test",
                        "Test"
                    )
                ),
                "EXACT_CURRENT_CLIENT"
            ),
            "client presentation authority as policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new DonationCartService(
                Collections.singletonList(
                    new DonationCartService.Product(
                        "product:test",
                        "Test"
                    )
                ),
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown server authority as policy"
        );
    }

    private static void playerIsolation(
        DonationCartService service
    ){
        DonationCartService.CartSnapshot bob=
            service.increase(
                "player:bob",
                "bond:large"
            );

        require(
            bob.lines.size()==1&&
            bob.lines.get(0).quantity==1L&&
            service.snapshot(
                "player:alice"
            ).lines.size()==3,
            "donation cart player isolation"
        );
    }

    private static void decrementZeroFailure(
        DonationCartService service
    ){
        int before=
            service.playerCartCount();

        expect(
            IllegalStateException.class,
            ()->service.decrease(
                "player:charlie",
                "bond:starter"
            ),
            "decrement zero absent player"
        );

        require(
            service.playerCartCount()==before,
            "failed decrement created player cart"
        );

        DonationCartService.CartSnapshot
            aliceBefore=
                service.snapshot(
                    "player:alice"
                );

        expect(
            IllegalStateException.class,
            ()->service.decrease(
                "player:alice",
                "bond:unknown-zero"
            ),
            "unknown product decrement"
        );

        DonationCartService.CartSnapshot
            aliceAfter=
                service.snapshot(
                    "player:alice"
                );

        require(
            aliceAfter.revision==
                aliceBefore.revision&&
            aliceAfter.lines.size()==
                aliceBefore.lines.size(),
            "failed decrement mutated cart"
        );
    }

    private static void immutableViews(
        DonationCartService service
    ){
        expect(
            UnsupportedOperationException.class,
            ()->service.catalog().clear(),
            "catalog immutability"
        );

        DonationCartService.CartSnapshot
            snapshot=
                service.snapshot(
                    "player:alice"
                );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.lines.clear(),
            "cart line immutability"
        );

        DonationCartService.CheckoutRequest
            request=
                service.prepareCheckout(
                    "player:alice"
                );

        expect(
            UnsupportedOperationException.class,
            ()->request.lines.clear(),
            "checkout line immutability"
        );
    }

    private static void
        protocolAndCommerceBoundary()
    {
        for(Class<?> type:new Class<?>[]{
                DonationCartService.class,
                DonationCartService.Product.class,
                DonationCartService.Line.class,
                DonationCartService.CartSnapshot.class,
                DonationCartService.CheckoutRequest.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("opcode")||
                   name.contains("packet")||
                   name.contains("root")||
                   name.contains("itemid")||
                   name.contains("sku")||
                   name.contains("price")||
                   name.contains("subtotal")||
                   name.contains("currency")||
                   name.contains("exchange")||
                   name.contains("url")||
                   name.contains("settlement")||
                   name.contains("refund")||
                   name.contains("chargeback"))
                    throw new AssertionError(
                        "protocol/commerce authority leaked into donation cart "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }

        for(Method method:
                DonationCartService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("settle")||
               name.contains("fulfill")||
               name.contains("refund")||
               name.contains("chargeback")||
               name.contains("verify"))
                throw new AssertionError(
                    "external commerce behavior leaked into donation cart "+
                    method.getName()
                );
        }
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
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private DonationCartServiceTest(){}
}
