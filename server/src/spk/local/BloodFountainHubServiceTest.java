package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class BloodFountainHubServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_BLOOD_FOUNTAIN_HUB";

    public static void main(String[] args){
        catalogContract();
        authorityFence();

        ArrayList<String> calls=
            new ArrayList<>();

        BloodFountainHubService service=
            new BloodFountainHubService(
                (player,intent,authority)->{
                    calls.add(
                        player+"|"+
                        intent+"|"+
                        authority
                    );

                    if(intent==
                            BloodFountainHubService
                                .Intent.BLOOD_DIAMOND_STORE)
                        return BloodFountainHubService
                            .ExecutionResult.failure(
                                "caller-defined store unavailable"
                            );

                    if(intent==
                            BloodFountainHubService
                                .Intent.BLOOD_DIAMOND_FUSER)
                        return BloodFountainHubService
                            .ExecutionResult.success(
                                "compose BloodDiamondFuserService"
                            );

                    return BloodFountainHubService
                        .ExecutionResult.success();
                },
                POLICY
            );

        BloodFountainHubService.PlayerSnapshot
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
            "read-only Blood Fountain hub snapshot"
        );

        BloodFountainHubService.RequestResult
            perk=
                service.request(
                    "PLAYER:ALICE",
                    BloodFountainHubService
                        .Intent.PERK_TREE
                );

        require(
            perk.executedSuccessfully&&
            perk.player.successful(
                BloodFountainHubService
                    .Intent.PERK_TREE)==1L&&
            perk.player.totalSuccessfulRequests==1L,
            "perk-tree semantic dispatch"
        );

        BloodFountainHubService.RequestResult
            fuser=
                service.request(
                    "player:alice",
                    BloodFountainHubService
                        .Intent.BLOOD_DIAMOND_FUSER
                );

        require(
            fuser.executedSuccessfully&&
            "compose BloodDiamondFuserService"
                .equals(fuser.detail)&&
            fuser.player.successful(
                BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_FUSER)==1L&&
            fuser.player.totalSuccessfulRequests==2L,
            "fuser semantic composition"
        );

        BloodFountainHubService.RequestResult
            denied=
                service.request(
                    "player:alice",
                    BloodFountainHubService
                        .Intent.BLOOD_DIAMOND_STORE
                );

        require(
            !denied.executedSuccessfully&&
            denied.player.successful(
                BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_STORE)==0L&&
            denied.player.totalSuccessfulRequests==2L&&
            "caller-defined store unavailable"
                .equals(denied.detail),
            "unavailable caller-defined store denial"
        );

        BloodFountainHubService.RequestResult
            salvage=
                service.request(
                    "player:bob",
                    BloodFountainHubService
                        .Intent.BLOOD_SHARD_SALVAGING
                );

        require(
            salvage.executedSuccessfully&&
            salvage.player.totalSuccessfulRequests==1L&&
            service.snapshot(
                "player:alice"
            ).totalSuccessfulRequests==2L&&
            service.playerCount()==2,
            "hub player isolation"
        );

        require(
            calls.equals(
                Arrays.asList(
                    "player:alice|PERK_TREE|"+
                        POLICY,
                    "player:alice|BLOOD_DIAMOND_FUSER|"+
                        POLICY,
                    "player:alice|BLOOD_DIAMOND_STORE|"+
                        POLICY,
                    "player:bob|BLOOD_SHARD_SALVAGING|"+
                        POLICY
                )
            ),
            "semantic Blood Fountain executor calls"
        );

        immutableViews(service);
        protocolBoundary();
        targetMechanicsBoundary();

        System.out.println(
            "BLOOD_FOUNTAIN_HUB_SERVICE_PASS "+
            "exactIntents=6 "+
            "perkTree=true "+
            "bloodPoolStore=true "+
            "bloodDiamondFuser=true "+
            "bloodDiamondStore=true "+
            "bloodShardSalvaging=true "+
            "bloodShardStore=true "+
            "semanticExecutor=true "+
            "successfulIntentCount=true "+
            "failedIntentNotCounted=true "+
            "targetServicesComposable=true "+
            "storeEconomicsOwned=false "+
            "recipeMechanicsOwned=false "+
            "perkMechanicsOwned=false "+
            "eligibilityOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void catalogContract(){
        List<BloodFountainHubService.Entry>
            entries=
                BloodFountainHubService.entries();

        require(
            entries.size()==6,
            "exact Blood Fountain hub intent count"
        );

        BloodFountainHubService.Intent[]
            expected={
                BloodFountainHubService
                    .Intent.PERK_TREE,
                BloodFountainHubService
                    .Intent.BLOOD_POOL_STORE,
                BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_FUSER,
                BloodFountainHubService
                    .Intent.BLOOD_DIAMOND_STORE,
                BloodFountainHubService
                    .Intent.BLOOD_SHARD_SALVAGING,
                BloodFountainHubService
                    .Intent.BLOOD_SHARD_STORE
            };

        for(int i=0;i<
                expected.length;i++)
            require(
                entries.get(i).intent==
                    expected[i],
                "Blood Fountain hub intent order "+
                i
            );

        require(
            "Blood perk tree".equals(
                entries.get(0)
                    .displayName
            )&&
            "Blood shard store".equals(
                entries.get(5)
                    .displayName
            ),
            "exact Blood Fountain labels"
        );

        for(BloodFountainHubService.Entry
                entry:entries)
            require(
                BloodFountainHubService
                    .PRESENTATION_AUTHORITY
                    .equals(
                        entry
                            .presentationAuthority
                    ),
                "Blood Fountain presentation authority "+
                entry.intent
            );

        expect(
            UnsupportedOperationException.class,
            ()->entries.clear(),
            "Blood Fountain catalog immutability"
        );
    }

    private static void authorityFence(){
        expect(
            IllegalArgumentException.class,
            ()->new BloodFountainHubService(
                (player,intent,authority)->
                    BloodFountainHubService
                        .ExecutionResult.success(),
                "EXACT_CURRENT_CLIENT"
            ),
            "presentation authority as Blood Fountain policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new BloodFountainHubService(
                (player,intent,authority)->
                    BloodFountainHubService
                        .ExecutionResult.success(),
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority as Blood Fountain policy"
        );
    }

    private static void immutableViews(
        BloodFountainHubService service
    ){
        BloodFountainHubService.PlayerSnapshot
            snapshot=
                service.snapshot(
                    "player:alice"
                );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.successfulRequests
                .clear(),
            "Blood Fountain count snapshot immutability"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                BloodFountainHubService.class,
                BloodFountainHubService.Entry.class,
                BloodFountainHubService
                    .PlayerSnapshot.class,
                BloodFountainHubService
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
                   name.contains("targetid"))
                    throw new AssertionError(
                        "raw protocol leaked into Blood Fountain hub "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void targetMechanicsBoundary(){
        for(Method method:
                BloodFountainHubService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("purchase")||
               name.contains("price")||
               name.contains("recipe")||
               name.contains("fuse")||
               name.contains("salvage")||
               name.contains("perkcost")||
               name.contains("eligibility"))
                throw new AssertionError(
                    "target mechanics leaked into Blood Fountain hub "+
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

    private BloodFountainHubServiceTest(){}
}
