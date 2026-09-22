package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class DonorNavigationServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_DONOR_NAVIGATION";

    public static void main(String[] args){
        catalogContract();
        authorityFence();

        ArrayList<String> calls=
            new ArrayList<>();

        DonorNavigationService service=
            new DonorNavigationService(
                (player,intent,authority)->{
                    calls.add(
                        player+"|"+
                        intent+"|"+
                        authority
                    );

                    if(intent==
                            DonorNavigationService
                                .Intent.SPONSOR_ZONE)
                        return DonorNavigationService
                            .ExecutionResult.failure(
                                "caller-owned entitlement denied"
                            );

                    if(intent==
                            DonorNavigationService
                                .Intent.DONATE)
                        return DonorNavigationService
                            .ExecutionResult.success(
                                "external commerce handoff"
                            );

                    return DonorNavigationService
                        .ExecutionResult.success();
                },
                POLICY
            );

        DonorNavigationService.PlayerSnapshot
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
            "read-only donor navigation snapshot"
        );

        DonorNavigationService.RequestResult
            donate=
                service.request(
                    "PLAYER:ALICE",
                    DonorNavigationService
                        .Intent.DONATE
                );

        require(
            donate.executedSuccessfully&&
            "external commerce handoff"
                .equals(donate.detail)&&
            donate.player.successful(
                DonorNavigationService
                    .Intent.DONATE)==1L&&
            donate.player.totalSuccessfulRequests==1L,
            "donate intent delegation"
        );

        DonorNavigationService.RequestResult
            shop=
                service.request(
                    "player:alice",
                    DonorNavigationService
                        .Intent.SHOP
                );

        require(
            shop.executedSuccessfully&&
            shop.player.successful(
                DonorNavigationService
                    .Intent.SHOP)==1L&&
            shop.player.totalSuccessfulRequests==2L,
            "donor shop intent"
        );

        DonorNavigationService.RequestResult
            denied=
                service.request(
                    "player:alice",
                    DonorNavigationService
                        .Intent.SPONSOR_ZONE
                );

        require(
            !denied.executedSuccessfully&&
            denied.player.successful(
                DonorNavigationService
                    .Intent.SPONSOR_ZONE)==0L&&
            denied.player.totalSuccessfulRequests==2L&&
            "caller-owned entitlement denied"
                .equals(denied.detail),
            "donor entitlement denial external"
        );

        DonorNavigationService.RequestResult
            vip=
                service.request(
                    "player:bob",
                    DonorNavigationService
                        .Intent.VIP_ZONE
                );

        require(
            vip.executedSuccessfully&&
            vip.player.totalSuccessfulRequests==1L&&
            service.snapshot(
                "player:alice"
            ).totalSuccessfulRequests==2L&&
            service.playerCount()==2,
            "donor navigation player isolation"
        );

        require(
            calls.equals(
                Arrays.asList(
                    "player:alice|DONATE|"+
                        POLICY,
                    "player:alice|SHOP|"+
                        POLICY,
                    "player:alice|SPONSOR_ZONE|"+
                        POLICY,
                    "player:bob|VIP_ZONE|"+
                        POLICY
                )
            ),
            "semantic donor executor calls"
        );

        immutableViews(service);
        protocolBoundary();
        commerceAndEntitlementBoundary();

        System.out.println(
            "DONOR_NAVIGATION_SERVICE_PASS "+
            "exactIntents=7 "+
            "donate=true "+
            "perks=true "+
            "shop=true "+
            "donatorZone=true "+
            "eliteZone=true "+
            "vipZone=true "+
            "sponsorZone=true "+
            "semanticExecutor=true "+
            "successfulIntentCount=true "+
            "failedIntentNotCounted=true "+
            "commerceOwned=false "+
            "rankThresholdsOwned=false "+
            "zoneCoordinatesOwned=false "+
            "zoneEligibilityOwned=false "+
            "shopEconomicsOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void catalogContract(){
        List<DonorNavigationService.Entry>
            entries=
                DonorNavigationService.entries();

        require(
            entries.size()==7,
            "exact donor intent count"
        );

        DonorNavigationService.Intent[]
            expected={
                DonorNavigationService
                    .Intent.DONATE,
                DonorNavigationService
                    .Intent.PERKS,
                DonorNavigationService
                    .Intent.SHOP,
                DonorNavigationService
                    .Intent.DONATOR_ZONE,
                DonorNavigationService
                    .Intent.ELITE_ZONE,
                DonorNavigationService
                    .Intent.VIP_ZONE,
                DonorNavigationService
                    .Intent.SPONSOR_ZONE
            };

        for(int i=0;i<
                expected.length;i++)
            require(
                entries.get(i).intent==
                    expected[i],
                "donor intent order "+
                i
            );

        require(
            "Donate for rewards".equals(
                entries.get(0)
                    .displayName
            )&&
            "Teleport to sponsor donator zone"
                .equals(
                    entries.get(6)
                        .displayName
                ),
            "exact donor labels"
        );

        for(DonorNavigationService.Entry
                entry:entries)
            require(
                DonorNavigationService
                    .PRESENTATION_AUTHORITY
                    .equals(
                        entry
                            .presentationAuthority
                    ),
                "donor presentation authority "+
                entry.intent
            );

        expect(
            UnsupportedOperationException.class,
            ()->entries.clear(),
            "donor catalog immutability"
        );
    }

    private static void authorityFence(){
        expect(
            IllegalArgumentException.class,
            ()->new DonorNavigationService(
                (player,intent,authority)->
                    DonorNavigationService
                        .ExecutionResult.success(),
                "EXACT_CURRENT_CLIENT"
            ),
            "presentation authority as donor policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new DonorNavigationService(
                (player,intent,authority)->
                    DonorNavigationService
                        .ExecutionResult.success(),
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority as donor policy"
        );
    }

    private static void immutableViews(
        DonorNavigationService service
    ){
        DonorNavigationService.PlayerSnapshot
            snapshot=
                service.snapshot(
                    "player:alice"
                );

        expect(
            UnsupportedOperationException.class,
            ()->snapshot.successfulRequests
                .clear(),
            "donor count snapshot immutability"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                DonorNavigationService.class,
                DonorNavigationService.Entry.class,
                DonorNavigationService
                    .PlayerSnapshot.class,
                DonorNavigationService
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
                   name.contains("coordinate")||
                   name.contains("region")||
                   name.contains("url"))
                    throw new AssertionError(
                        "raw protocol/location leaked into donor navigation "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void
        commerceAndEntitlementBoundary()
    {
        for(Method method:
                DonorNavigationService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("paypal")||
               name.contains("checkout")||
               name.contains("payment")||
               name.contains("rankthreshold")||
               name.contains("eligibility")||
               name.contains("price")||
               name.contains("fulfill"))
                throw new AssertionError(
                    "unowned donor behavior leaked into "+
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

    private DonorNavigationServiceTest(){}
}
