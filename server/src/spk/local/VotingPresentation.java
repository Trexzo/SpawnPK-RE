package spk.local;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 Voting interface presentation catalog.
 *
 * Current evidence closes the visible provider controls but not their
 * click-to-wire behavior. This adapter therefore exposes presentation metadata
 * and root opening only; it deliberately has no input router.
 */
final class VotingPresentation {
    static final int ROOT=60050;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    static final class SiteControl {
        final VotingService.Provider provider;
        final int actionWidget;
        final int pairedWidget;
        final String displayName;

        SiteControl(
            VotingService.Provider provider,
            int actionWidget,
            int pairedWidget,
            String displayName
        ){
            this.provider=
                Objects.requireNonNull(
                    provider,
                    "provider"
                );
            this.actionWidget=actionWidget;
            this.pairedWidget=pairedWidget;
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
                );
        }
    }

    private static final List<SiteControl>
        VISIBLE_SITES=buildVisibleSites();

    static List<SiteControl> visibleSites(){
        return VISIBLE_SITES;
    }

    static SiteControl site(
        VotingService.Provider provider
    ){
        VotingService.Provider checked=
            Objects.requireNonNull(
                provider,
                "provider"
            );

        for(SiteControl site:VISIBLE_SITES)
            if(site.provider==checked)
                return site;

        throw new IllegalArgumentException(
            "Voting provider not visible "+
            checked
        );
    }

    static void open(
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(packets,"packets")
            .fixed(
                97,
                BootstrapPackets.interface97(ROOT)
            );
    }

    private static List<SiteControl>
        buildVisibleSites()
    {
        ArrayList<SiteControl> out=
            new ArrayList<>();

        out.add(
            new SiteControl(
                VotingService.Provider.TOPG,
                60052,
                60053,
                "TopG"
            )
        );
        out.add(
            new SiteControl(
                VotingService.Provider.RUNELOCUS,
                60055,
                60056,
                "RuneLocus"
            )
        );
        out.add(
            new SiteControl(
                VotingService.Provider.RSPS_LIST,
                60058,
                60059,
                "RSPS-List"
            )
        );

        return Collections.unmodifiableList(out);
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }

    private VotingPresentation(){}
}
