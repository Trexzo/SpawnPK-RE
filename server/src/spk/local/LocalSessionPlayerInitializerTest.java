package spk.local;

import java.util.*;

public final class LocalSessionPlayerInitializerTest {
    public static void main(String[] args)throws Exception{
        final String[] loadedUsername={null};

        PlayerRepository repository=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                ){
                    loadedUsername[0]=username;
                    return Optional.empty();
                }

                @Override public void save(
                    PlayerSnapshot snapshot
                ){}
            };

        World world=
            World.isolatedForTest(
                50L,
                repository
            );

        try{
            WorldPlayer player=
                new WorldPlayer();
            PetAccessoryState accessory=
                new PetAccessoryState();

            LocalSessionPlayerInitializer initializer=
                new LocalSessionPlayerInitializer(
                    world,
                    player,
                    player.bank(),
                    player.equipment(),
                    player.movement(),
                    player.petState(),
                    player.playerState(),
                    player.petEffects(),
                    accessory
                );

            LocalSessionPlayerInitializer.Result result=
                initializer.initialize(
                    "testprofile",
                    "[player-init-test] "
                );

            if(!"testprofile".equals(
                    result.username))
                throw new AssertionError(
                    "arbitrary alias changed: "+
                    result.username
                );

            if(!result.persistentAccount)
                throw new AssertionError(
                    "arbitrary alias not persistence eligible"
                );

            if(!"testprofile".equals(
                    loadedUsername[0]))
                throw new AssertionError(
                    "repository did not load arbitrary profile username="+
                    loadedUsername[0]
                );

            if(result.worldPlayerGeneration<=0L)
                throw new AssertionError(
                    "world generation not assigned"
                );

            if(world.players().byName(
                    "testprofile")!=player)
                throw new AssertionError(
                    "WorldPlayer registration missing"
                );

            if(!player.registered())
                throw new AssertionError(
                    "WorldPlayer registered flag missing"
                );

            if(accessory.activeItem()!=0)
                throw new AssertionError(
                    "missing account unexpectedly loaded accessory"
                );

            System.out.println(
                "LOCAL_SESSION_PLAYER_INITIALIZER_PASS "+
                "username="+result.username+
                " persistent="+result.persistentAccount+
                " arbitraryRepositoryLoad=true "+
                "generation="+result.worldPlayerGeneration+
                " members="+world.players().size()
            );
        }finally{
            world.close();
        }
    }
}
