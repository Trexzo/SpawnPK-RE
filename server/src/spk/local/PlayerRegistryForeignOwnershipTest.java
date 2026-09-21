package spk.local;

public final class PlayerRegistryForeignOwnershipTest {
    public static void main(String[] args){
        PlayerRegistry owner=
            new PlayerRegistry();

        PlayerRegistry foreign=
            new PlayerRegistry();

        WorldPlayer player=
            new WorldPlayer();

        long generation=
            owner.register(
                player,
                "foreign-ownership"
            );

        if(generation!=1L||
           !player.registered()||
           player.generation()!=1L)
            throw new AssertionError(
                "owner registration lifecycle="+
                player
            );

        if(foreign.unregister(player))
            throw new AssertionError(
                "foreign registry claimed unregister success"
            );

        if(!player.registered()||
           player.generation()!=1L||
           !"foreign-ownership".equals(
               player.username()))
            throw new AssertionError(
                "foreign unregister mutated player="+
                player
            );

        if(owner.size()!=1||
           owner.byId(player.id())!=player||
           owner.byName(
               "FOREIGN-OWNERSHIP"
           )!=player)
            throw new AssertionError(
                "foreign unregister damaged owner membership"
            );

        if(foreign.size()!=0)
            throw new AssertionError(
                "foreign registry mutated size="+
                foreign.size()
            );

        if(!owner.unregister(player))
            throw new AssertionError(
                "owner unregister failed"
            );

        if(player.registered()||
           player.generation()!=2L)
            throw new AssertionError(
                "owner unregister lifecycle="+
                player
            );

        if(owner.size()!=0||
           owner.byId(player.id())!=null||
           owner.byName(
               "foreign-ownership"
           )!=null)
            throw new AssertionError(
                "owner membership retained after unregister"
            );

        if(foreign.unregister(player))
            throw new AssertionError(
                "repeated foreign unregister claimed success"
            );

        if(player.registered()||
           player.generation()!=2L)
            throw new AssertionError(
                "repeated nonmember unregister mutated lifecycle="+
                player
            );

        System.out.println(
            "PLAYER_REGISTRY_FOREIGN_OWNERSHIP_PASS "+
            "foreignRejected=true "+
            "ownerMembershipPreserved=true "+
            "generationStable=true "+
            "ownerCleanup=true"
        );
    }

    private PlayerRegistryForeignOwnershipTest(){}
}
