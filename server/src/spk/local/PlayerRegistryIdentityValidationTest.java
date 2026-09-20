package spk.local;

public final class PlayerRegistryIdentityValidationTest {
    public static void main(String[] args){
        PlayerRegistry registry=
            new PlayerRegistry();

        assertRejected(
            registry,
            null
        );

        assertRejected(
            registry,
            ""
        );

        assertRejected(
            registry,
            "   "
        );

        if(registry.size()!=0)
            throw new AssertionError(
                "rejected identities changed registry size="+
                registry.size()
            );

        WorldPlayer first=
            new WorldPlayer();

        long generation=
            registry.register(
                first,
                "Alice"
            );

        if(generation!=1L||
           !first.registered()||
           first.generation()!=1L)
            throw new AssertionError(
                "valid registration lifecycle="+
                first
            );

        if(registry.byName("alice")!=first||
           registry.byName(" ALICE ")!=first)
            throw new AssertionError(
                "canonical lookup failed"
            );

        WorldPlayer duplicate=
            new WorldPlayer();

        boolean duplicateRejected=false;

        try{
            registry.register(
                duplicate,
                "  aLiCe  "
            );
        }catch(IllegalStateException expected){
            duplicateRejected=
                expected.getMessage()
                    .contains(
                        "DUPLICATE_LOGIN"
                    );
        }

        if(!duplicateRejected)
            throw new AssertionError(
                "canonical duplicate login accepted"
            );

        if(duplicate.registered()||
           duplicate.generation()!=0L)
            throw new AssertionError(
                "duplicate rejection mutated player="+
                duplicate
            );

        if(registry.size()!=1||
           registry.byName("alice")!=first)
            throw new AssertionError(
                "duplicate rejection mutated registry"
            );

        if(!registry.unregister(first))
            throw new AssertionError(
                "first unregister failed"
            );

        if(first.registered()||
           first.generation()!=2L)
            throw new AssertionError(
                "unregister lifecycle="+
                first
            );

        WorldPlayer replacement=
            new WorldPlayer();

        long replacementGeneration=
            registry.register(
                replacement,
                " alice "
            );

        if(replacementGeneration!=1L||
           registry.byName("ALICE")!=replacement||
           registry.size()!=1)
            throw new AssertionError(
                "canonical name not reusable after unregister"
            );

        registry.unregister(
            replacement
        );

        System.out.println(
            "PLAYER_REGISTRY_IDENTITY_VALIDATION_PASS "+
            "blankRejected=true "+
            "failureAtomic=true "+
            "canonicalDuplicate=true "+
            "nameReusable=true"
        );
    }

    private static void assertRejected(
        PlayerRegistry registry,
        String username
    ){
        WorldPlayer player=
            new WorldPlayer();

        boolean rejected=false;

        try{
            registry.register(
                player,
                username
            );
        }catch(IllegalArgumentException expected){
            rejected=
                "username".equals(
                    expected.getMessage()
                );
        }

        if(!rejected)
            throw new AssertionError(
                "blank identity accepted username="+
                String.valueOf(username)
            );

        if(player.registered()||
           player.generation()!=0L)
            throw new AssertionError(
                "rejected identity mutated player="+
                player
            );

        if(registry.size()!=0)
            throw new AssertionError(
                "rejected identity mutated registry size="+
                registry.size()
            );
    }
}