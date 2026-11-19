package spk.local;

import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.UUID;

/**
 * R89 OFFLINE TEST ONLY. A synthetic operation token belongs to an EXACT
 * real R86 source-proven Java event object, not to copied public fields.
 *
 * This issuer does NOT survive JVM restart; its UUID is NOT a real
 * authenticated, durable producer-issued operation ID. All consumers
 * must explicitly opt in to the same fixture issuer.
 */
final class R89OfflineVerifiedEmissionTokenIssuer {
    static final class Token {
        private final UUID syntheticId;
        private final CombatOutcome exactFact;
        private final R89OfflineVerifiedEmissionTokenIssuer issuer;
        private Token(UUID id,CombatOutcome fact,
                      R89OfflineVerifiedEmissionTokenIssuer origin){
            this.syntheticId=id;
            this.exactFact=fact;
            this.issuer=origin;
        }
        UUID syntheticIdForAudit(){return syntheticId;}
    }

    private final R86OfflineActivePvpOutcomeBridge source;
    private final IdentityHashMap<CombatOutcome,Token> issued=
        new IdentityHashMap<>();
    private final int capacity;

    R89OfflineVerifiedEmissionTokenIssuer(
        R86OfflineActivePvpOutcomeBridge source,int capacity
    ){
        this.source=Objects.requireNonNull(source,"source");
        if(capacity<1||capacity>4096)
            throw new IllegalArgumentException("R89 invalid issuer bound");
        this.capacity=capacity;
    }

    synchronized Token issue(CombatOutcome fact){
        if(!source.isVerifiedFactInCurrentListenerForTest(fact))
            throw new IllegalStateException(
                "R89_ISSUER_DENIED missing exact active PvP proof");
        Token previous=issued.get(fact);
        if(previous!=null)return previous;
        if(issued.size()>=capacity)
            throw new IllegalStateException(
                "R89_ISSUER_DENIED exhausted issuer bound");
        Token next=new Token(UUID.randomUUID(),fact,this);
        issued.put(fact,next);
        return next;
    }

    synchronized boolean isCurrent(CombatOutcome fact,Token token){
        return fact!=null&&token!=null&&token.issuer==this&&
            token.exactFact==fact&&issued.get(fact)==token&&
            source.isVerifiedFactInCurrentListenerForTest(fact);
    }

    synchronized int issuedCountForTest(){return issued.size();}
}
