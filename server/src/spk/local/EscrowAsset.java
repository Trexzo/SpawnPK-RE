package spk.local;

/** Immutable protocol-independent asset reservation request. */
final class EscrowAsset {
    enum Kind { ITEM, CURRENCY, GENERIC }

    final Kind kind;
    final String semanticKey;
    final long quantity;
    final String ownerRef;
    final AtomicTransactionService.SourceAuthority sourceAuthority;

    EscrowAsset(Kind kind,String semanticKey,long quantity,String ownerRef,AtomicTransactionService.SourceAuthority sourceAuthority){
        if(kind==null)throw new NullPointerException("kind");
        this.semanticKey=requireText(semanticKey,"semanticKey");
        if(quantity<=0)throw new IllegalArgumentException("quantity="+quantity);
        this.ownerRef=requireText(ownerRef,"ownerRef");
        if(sourceAuthority==null)throw new NullPointerException("sourceAuthority");
        this.kind=kind;
        this.quantity=quantity;
        this.sourceAuthority=sourceAuthority;
    }

    private static String requireText(String value,String field){
        if(value==null)throw new NullPointerException(field);
        String clean=value.trim();
        if(clean.isEmpty())throw new IllegalArgumentException(field+" blank");
        return clean;
    }

    @Override public boolean equals(Object other){
        if(this==other)return true;
        if(!(other instanceof EscrowAsset))return false;
        EscrowAsset o=(EscrowAsset)other;
        return kind==o.kind && quantity==o.quantity && semanticKey.equals(o.semanticKey) && ownerRef.equals(o.ownerRef) && sourceAuthority==o.sourceAuthority;
    }
    @Override public int hashCode(){return java.util.Objects.hash(kind,semanticKey,quantity,ownerRef,sourceAuthority);}
    @Override public String toString(){return "EscrowAsset{"+kind+":"+semanticKey+",qty="+quantity+",owner="+ownerRef+",authority="+sourceAuthority+"}";}
}
