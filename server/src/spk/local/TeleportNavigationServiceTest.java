package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class TeleportNavigationServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_TELEPORT_NAVIGATION";

    public static void main(String[] args){
        catalogContract();
        authorityFence();

        ArrayList<String> calls=
            new ArrayList<>();

        TeleportNavigationService service=
            new TeleportNavigationService(
                (player,entry,authority)->{
                    calls.add(
                        player+"|"+
                        entry+"|"+
                        authority
                    );

                    if(entry==
                            TeleportNavigationService
                                .EntryKind.BOUNTY)
                        return TeleportNavigationService
                            .ExecutionResult.failure(
                                "caller-owned eligibility denied"
                            );

                    if(entry==
                            TeleportNavigationService
                                .EntryKind.BOSS)
                        return TeleportNavigationService
                            .ExecutionResult.success(
                                "open semantic boss network"
                            );

                    return TeleportNavigationService
                        .ExecutionResult.success();
                },
                POLICY
            );

        TeleportNavigationService.PlayerSnapshot
            empty=
                service.snapshot(
                    " Player:Alice "
                );

        require(
            "player:alice".equals(
                empty.playerRef
            )&&
            empty.totalSuccessfulRequests==0L&&
            service.playerCount()==0,
            "read-only navigation snapshot"
        );

        TeleportNavigationService.RequestResult
            money=
                service.request(
                    "PLAYER:ALICE",
                    TeleportNavigationService
                        .EntryKind.MONEY
                );

        require(
            money.executedSuccessfully&&
            money.entry.kind==
                TeleportNavigationService
                    .EntryKind.MONEY&&
            money.player.successful(
                TeleportNavigationService
                    .EntryKind.MONEY)==1L&&
            money.player.totalSuccessfulRequests==1L,
            "money navigation success"
        );

        TeleportNavigationService.RequestResult
            boss=
                service.request(
                    "player:alice",
                    TeleportNavigationService
                        .EntryKind.BOSS
                );

        require(
            boss.executedSuccessfully&&
            "open semantic boss network"
                .equals(boss.detail)&&
            boss.player.successful(
                TeleportNavigationService
                    .EntryKind.BOSS)==1L&&
            boss.player.totalSuccessfulRequests==2L,
            "boss semantic delegation"
        );

        TeleportNavigationService.RequestResult
            bounty=
                service.request(
                    "player:alice",
                    TeleportNavigationService
                        .EntryKind.BOUNTY
                );

        require(
            !bounty.executedSuccessfully&&
            bounty.player.successful(
                TeleportNavigationService
                    .EntryKind.BOUNTY)==0L&&
            bounty.player.totalSuccessfulRequests==2L&&
            "caller-owned eligibility denied"
                .equals(bounty.detail),
            "caller-owned bounty denial"
        );

        require(
            calls.equals(
                Arrays.asList(
                    "player:alice|MONEY|"+
                        POLICY,
                    "player:alice|BOSS|"+
                        POLICY,
                    "player:alice|BOUNTY|"+
                        POLICY
                )
            ),
            "semantic executor calls"
        );

        TeleportNavigationService.RequestResult
            home=
                service.request(
                    "player:bob",
                    TeleportNavigationService
                        .EntryKind.HOME
                );

        require(
            home.executedSuccessfully&&
            home.player.totalSuccessfulRequests==1L&&
            service.snapshot(
                "player:alice"
            ).totalSuccessfulRequests==2L&&
            service.playerCount()==2,
            "player navigation isolation"
        );

        immutableViews(service);
        protocolBoundary();
        noServerTeleportPolicy();

        System.out.println(
            "TELEPORT_NAVIGATION_SERVICE_PASS "+
            "exactEntries=8 "+
            "home=true "+
            "money=true "+
            "training=true "+
            "boss=true "+
            "pk=true "+
            "minigame=true "+
            "house=true "+
            "bounty=true "+
            "semanticExecutor=true "+
            "successfulIntentCount=true "+
            "failedIntentNotCounted=true "+
            "bossServiceComposable=true "+
            "bountyClientLockOwned=false "+
            "coordinatesOwned=false "+
            "destinationListsOwned=false "+
            "magicLevelRequirementOwned=false "+
            "eligibilityOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void catalogContract(){
        List<TeleportNavigationService.Entry>
            entries=
                TeleportNavigationService
                    .entries();

        require(
            entries.size()==8,
            "exact teleport entry count"
        );

        TeleportNavigationService.EntryKind[]
            expected={
                TeleportNavigationService
                    .EntryKind.HOME,
                TeleportNavigationService
                    .EntryKind.MONEY,
                TeleportNavigationService
                    .EntryKind.TRAINING,
                TeleportNavigationService
                    .EntryKind.BOSS,
                TeleportNavigationService
                    .EntryKind.PK,
                TeleportNavigationService
                    .EntryKind.MINIGAME,
                TeleportNavigationService
                    .EntryKind.HOUSE,
                TeleportNavigationService
                    .EntryKind.BOUNTY
            };

        for(int i=0;i<
                expected.length;i++)
            require(
                entries.get(i).kind==
                    expected[i],
                "teleport entry order "+
                i
            );

        require(
            "Teleport to shop areas"
                .equals(
                    entries.get(5)
                        .description
                ),
            "exact shipped minigame description preserved"
        );

        require(
            "Cast Home Teleport"
                .equals(
                    entries.get(0)
                        .description
                ),
            "exact home presentation"
        );

        for(TeleportNavigationService.Entry
                entry:entries)
            require(
                TeleportNavigationService
                    .PRESENTATION_AUTHORITY
                    .equals(
                        entry
                            .presentationAuthority
                    ),
                "teleport presentation authority "+
                entry.kind
            );

        expect(
            UnsupportedOperationException.class,
            ()->entries.clear(),
            "teleport catalog immutability"
        );
    }

    private static void authorityFence(){
        expect(
            IllegalArgumentException.class,
            ()->new TeleportNavigationService(
                (player,entry,authority)->
                    TeleportNavigationService
                        .ExecutionResult.success(),
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority used as teleport policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new TeleportNavigationService(
                (player,entry,authority)->
                    TeleportNavigationService
                        .ExecutionResult.success(),
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority used as teleport policy"
        );
    }

    private static void immutableViews(
        TeleportNavigationService service
    ){
        TeleportNavigationService.PlayerSnapshot
            snapshot=
                service.snapshot(
                    "player:alice"
                );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.successfulRequests
                .clear(),
            "navigation count snapshot immutability"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                TeleportNavigationService.class,
                TeleportNavigationService.Entry.class,
                TeleportNavigationService
                    .PlayerSnapshot.class,
                TeleportNavigationService
                    .RequestResult.class
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
                   name.contains("alias")||
                   name.contains("coordinate")||
                   name.equals("x")||
                   name.equals("y")||
                   name.contains("region")||
                   name.contains("magicrequirement")||
                   name.contains("clientq"))
                    throw new AssertionError(
                        "raw transport/world location leaked into teleport navigation "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void noServerTeleportPolicy(){
        for(Method method:
                TeleportNavigationService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("teleblock")||
               name.contains("wilderness")||
               name.contains("cooldown")||
               name.contains("cost")||
               name.contains("destinationlist")||
               name.contains("coordinate"))
                throw new AssertionError(
                    "unrecovered teleport policy leaked into "+
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

    private TeleportNavigationServiceTest(){}
}
