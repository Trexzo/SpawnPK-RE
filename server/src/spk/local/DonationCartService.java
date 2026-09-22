package spk.local;

import java.util.*;

/**
 * Semantic Donation Shopping Cart state.
 *
 * Exact-current client evidence proves an eight-position cart surface and
 * PayPal / OSRS GP selection. Trusted pricing, payment and fulfillment remain
 * outside this gameplay boundary.
 */
final class DonationCartService {
    static final int VISIBLE_PRODUCT_LIMIT=8;
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum PaymentMode {
        PAYPAL,
        OSRS_GP
    }

    static final class Product {
        final String productKey;
        final String displayName;

        Product(
            String productKey,
            String displayName
        ){
            this.productKey=
                normalizeKey(
                    productKey,
                    "productKey"
                );
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
                );
        }
    }

    static final class Line {
        final String productKey;
        final String displayName;
        final long quantity;

        Line(
            Product product,
            long quantity
        ){
            this.productKey=
                product.productKey;
            this.displayName=
                product.displayName;

            if(quantity<=0L)
                throw new IllegalArgumentException(
                    "quantity="+quantity
                );

            this.quantity=quantity;
        }
    }

    static final class CartSnapshot {
        final String playerRef;
        final List<Line> lines;
        final PaymentMode paymentMode;
        final long revision;
        final String policyAuthority;
        final String presentationAuthority;

        CartSnapshot(
            PlayerCart cart,
            LinkedHashMap<String,Product>
                catalog,
            String policyAuthority
        ){
            this.playerRef=cart.playerRef;

            ArrayList<Line> copy=
                new ArrayList<>();

            for(Product product:
                    catalog.values()){
                long quantity=
                    cart.quantities
                        .getOrDefault(
                            product.productKey,
                            0L
                        );

                if(quantity>0L)
                    copy.add(
                        new Line(
                            product,
                            quantity
                        )
                    );
            }

            this.lines=
                Collections.unmodifiableList(
                    copy
                );
            this.paymentMode=
                cart.paymentMode;
            this.revision=
                cart.revision;
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        boolean empty(){
            return lines.isEmpty();
        }
    }

    static final class CheckoutRequest {
        final long requestId;
        final String playerRef;
        final List<Line> lines;
        final PaymentMode paymentMode;
        final long cartRevision;
        final String policyAuthority;
        final String presentationAuthority;

        CheckoutRequest(
            long requestId,
            CartSnapshot cart
        ){
            if(requestId<=0L)
                throw new IllegalArgumentException(
                    "requestId="+requestId
                );

            this.requestId=requestId;
            this.playerRef=cart.playerRef;
            this.lines=cart.lines;
            this.paymentMode=
                Objects.requireNonNull(
                    cart.paymentMode,
                    "paymentMode"
                );
            this.cartRevision=
                cart.revision;
            this.policyAuthority=
                cart.policyAuthority;
            this.presentationAuthority=
                cart.presentationAuthority;
        }
    }

    private static final class PlayerCart {
        final String playerRef;
        final LinkedHashMap<String,Long>
            quantities=
                new LinkedHashMap<>();

        PaymentMode paymentMode;
        long revision;
        CheckoutRequest prepared;

        PlayerCart(String playerRef){
            this.playerRef=playerRef;
        }
    }

    private final LinkedHashMap<String,Product>
        catalog=
            new LinkedHashMap<>();

    private final LinkedHashMap<String,PlayerCart>
        carts=
            new LinkedHashMap<>();

    private final String policyAuthority;
    private long nextRequestId=1L;

    DonationCartService(
        Collection<Product> products,
        String policyAuthority
    ){
        Objects.requireNonNull(
            products,
            "products"
        );

        this.policyAuthority=
            localPolicyAuthority(
                policyAuthority
            );

        if(products.isEmpty()||
           products.size()>
                VISIBLE_PRODUCT_LIMIT)
            throw new IllegalArgumentException(
                "donation cart product count="+
                products.size()+
                " expected=1.."+
                VISIBLE_PRODUCT_LIMIT
            );

        for(Product product:products){
            Product checked=
                Objects.requireNonNull(
                    product,
                    "product"
                );

            if(catalog.put(
                    checked.productKey,
                    checked)!=null)
                throw new IllegalArgumentException(
                    "duplicate donation product "+
                    checked.productKey
                );
        }
    }

    synchronized List<Product> catalog(){
        return Collections.unmodifiableList(
            new ArrayList<>(
                catalog.values()
            )
        );
    }

    synchronized CartSnapshot increase(
        String playerRef,
        String productKey
    ){
        Product product=
            requireProduct(
                productKey
            );
        PlayerCart cart=
            cart(playerRef);

        long current=
            cart.quantities
                .getOrDefault(
                    product.productKey,
                    0L
                );

        long next;

        try{
            next=
                Math.addExact(
                    current,
                    1L
                );
        }catch(ArithmeticException error){
            throw new IllegalStateException(
                "donation cart quantity overflow product="+
                product.productKey,
                error
            );
        }

        long revision=
            nextRevision(cart);

        cart.quantities.put(
            product.productKey,
            next
        );
        commitChange(
            cart,
            revision
        );

        return snapshot(cart);
    }

    synchronized CartSnapshot decrease(
        String playerRef,
        String productKey
    ){
        Product product=
            requireProduct(
                productKey
            );
        String player=
            normalizePlayer(
                playerRef
            );
        PlayerCart cart=
            carts.get(player);

        if(cart==null)
            throw new IllegalStateException(
                "donation cart quantity already zero product="+
                product.productKey
            );

        long current=
            cart.quantities
                .getOrDefault(
                    product.productKey,
                    0L
                );

        if(current<=0L)
            throw new IllegalStateException(
                "donation cart quantity already zero product="+
                product.productKey
            );

        long revision=
            nextRevision(cart);

        if(current==1L)
            cart.quantities.remove(
                product.productKey
            );
        else
            cart.quantities.put(
                product.productKey,
                current-1L
            );

        commitChange(
            cart,
            revision
        );

        return snapshot(cart);
    }

    synchronized CartSnapshot selectPayment(
        String playerRef,
        PaymentMode paymentMode
    ){
        PlayerCart cart=
            cart(playerRef);
        PaymentMode checked=
            Objects.requireNonNull(
                paymentMode,
                "paymentMode"
            );

        if(cart.paymentMode==checked)
            return snapshot(cart);

        long revision=
            nextRevision(cart);

        cart.paymentMode=checked;
        commitChange(
            cart,
            revision
        );

        return snapshot(cart);
    }

    synchronized CartSnapshot snapshot(
        String playerRef
    ){
        PlayerCart cart=
            carts.get(
                normalizePlayer(
                    playerRef
                )
            );

        return cart==null
            ?emptySnapshot(
                normalizePlayer(
                    playerRef
                )
            )
            :snapshot(cart);
    }

    synchronized CheckoutRequest
        prepareCheckout(
            String playerRef
        ){
        String player=
            normalizePlayer(
                playerRef
            );
        PlayerCart cart=
            carts.get(player);

        if(cart==null)
            throw new IllegalStateException(
                "donation cart checkout empty player="+
                player
            );

        CartSnapshot snapshot=
            snapshot(cart);

        if(snapshot.empty())
            throw new IllegalStateException(
                "donation cart checkout empty player="+
                cart.playerRef
            );

        if(snapshot.paymentMode==null)
            throw new IllegalStateException(
                "donation cart payment mode not selected player="+
                cart.playerRef
            );

        if(cart.prepared!=null&&
           cart.prepared.cartRevision==
                cart.revision)
            return cart.prepared;

        long requestId=
            nextRequestId;

        try{
            nextRequestId=
                Math.addExact(
                    nextRequestId,
                    1L
                );
        }catch(ArithmeticException error){
            throw new IllegalStateException(
                "donation checkout request id overflow",
                error
            );
        }

        CheckoutRequest request=
            new CheckoutRequest(
                requestId,
                snapshot
            );

        cart.prepared=request;
        return request;
    }

    synchronized int playerCartCount(){
        return carts.size();
    }

    private CartSnapshot snapshot(
        PlayerCart cart
    ){
        return new CartSnapshot(
            cart,
            catalog,
            policyAuthority
        );
    }

    private CartSnapshot emptySnapshot(
        String playerRef
    ){
        return new CartSnapshot(
            new PlayerCart(
                playerRef
            ),
            catalog,
            policyAuthority
        );
    }

    private PlayerCart cart(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        PlayerCart cart=
            carts.get(player);

        if(cart==null){
            cart=
                new PlayerCart(
                    player
                );
            carts.put(
                player,
                cart
            );
        }

        return cart;
    }

    private Product requireProduct(
        String productKey
    ){
        String key=
            normalizeKey(
                productKey,
                "productKey"
            );
        Product product=
            catalog.get(key);

        if(product==null)
            throw new IllegalArgumentException(
                "unknown donation product "+
                key
            );

        return product;
    }

    private static long nextRevision(
        PlayerCart cart
    ){
        try{
            return Math.addExact(
                cart.revision,
                1L
            );
        }catch(ArithmeticException error){
            throw new IllegalStateException(
                "donation cart revision overflow player="+
                cart.playerRef,
                error
            );
        }
    }

    private static void commitChange(
        PlayerCart cart,
        long revision
    ){
        cart.revision=revision;
        cart.prepared=null;
    }

    private static String normalizePlayer(
        String value
    ){
        return requireText(
            value,
            "playerRef"
        ).toLowerCase(
            Locale.ROOT
        );
    }

    private static String normalizeKey(
        String value,
        String field
    ){
        String normalized=
            requireText(
                value,
                field
            ).toLowerCase(
                Locale.ROOT
            );

        for(int i=0;i<
                normalized.length();i++){
            char c=
                normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    field+" invalid="+
                    value
                );
        }

        return normalized;
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }

    private static String localPolicyAuthority(
        String value
    ){
        String authority=
            requireText(
                value,
                "policyAuthority"
            );

        if(!authority
                .toUpperCase(
                    Locale.ROOT
                )
                .startsWith(
                    "LOCAL_LAB_POLICY_"))
            throw new IllegalArgumentException(
                "donation cart requires explicit LOCAL_LAB_POLICY_* authority actual="+
                authority
            );

        return authority;
    }
}
