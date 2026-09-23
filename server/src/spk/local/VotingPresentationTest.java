package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

public final class VotingPresentationTest {
    public static void main(String[] args){
        exactVisibleProviders();
        exactRoot();
        transportStillUnowned();

        System.out.println(
            "VOTING_PRESENTATION_PASS "+
            "root60050=true "+
            "visibleProviders=3 "+
            "topg=true "+
            "runelocus=true "+
            "rspsList=true "+
            "moparscapeVisible=false "+
            "clickTransportOwned=false "+
            "voteVerificationOwned=false "+
            "rewardPolicyOwned=false"
        );
    }

    private static void exactVisibleProviders(){
        List<VotingPresentation.SiteControl>
            sites=
                VotingPresentation
                    .visibleSites();

        require(
            sites.size()==3,
            "visible provider count"
        );

        requireSite(
            VotingService.Provider.TOPG,
            60052,
            60053,
            "TopG"
        );
        requireSite(
            VotingService.Provider.RUNELOCUS,
            60055,
            60056,
            "RuneLocus"
        );
        requireSite(
            VotingService.Provider.RSPS_LIST,
            60058,
            60059,
            "RSPS-List"
        );

        for(VotingPresentation.SiteControl site:
                sites)
            require(
                site.actionWidget!=60164&&
                site.pairedWidget!=60165,
                "Moparscape must remain dormant"
            );
    }

    private static void requireSite(
        VotingService.Provider provider,
        int actionWidget,
        int pairedWidget,
        String displayName
    ){
        VotingPresentation.SiteControl site=
            VotingPresentation.site(provider);

        require(
            site.provider==provider&&
            site.actionWidget==actionWidget&&
            site.pairedWidget==pairedWidget&&
            displayName.equals(site.displayName),
            "site "+provider
        );
    }

    private static void exactRoot(){
        require(
            VotingPresentation.ROOT==60050,
            "root"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                VotingPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                VotingPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0xea&&
            (root[1]&255)==0x92,
            "root S2C97 body"
        );
    }

    private static void transportStillUnowned(){
        for(Field field:
                VotingPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("opcode")||
               name.contains("url")||
               name.contains("cooldown")||
               name.contains("reward"))
                throw new AssertionError(
                    "unowned Voting transport/policy field "+
                    field.getName()
                );
        }

        for(Method method:
                VotingPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("resolvewidget")||
               name.contains("handleclick")||
               name.contains("openbrowser")||
               name.contains("verifyvote")||
               name.contains("grantreward"))
                throw new AssertionError(
                    "unowned Voting transport/policy method "+
                    method.getName()
                );
        }
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private VotingPresentationTest(){}
}
