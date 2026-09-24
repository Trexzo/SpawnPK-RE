package spk.local;

import java.util.Objects;
import spk.content.api.*;

/**
 * Internal LocalLab diagnostics exposed through the content-command lifecycle.
 *
 * This module intentionally stays in spk.local so authority/collision
 * repositories remain internal. It consumes only the generic semantic player
 * position exposed by the public content facade.
 */
final class LocalDiagnosticContentModule
    implements ContentModule {

    static final String MODULE_ID=
        "locallab-diagnostics";

    private final ContentRegistry registry;

    LocalDiagnosticContentModule(
        ContentRegistry registry
    ){
        this.registry=
            Objects.requireNonNull(
                registry,
                "registry"
            );
    }

    @Override public String id(){
        return MODULE_ID;
    }

    @Override public void register(
        ContentRegistrar registrar
    ){
        registrar.command(
            "contentregistry",
            100,
            context->
                ContentResult.handled(
                    "CONTENT_REGISTRY_DIAGNOSTIC "+
                        registry.summary(),
                    null
                )
        );

        registrar.command(
            "authority",
            100,
            context->
                ContentResult.handled(
                    authoritySummary(),
                    null
                )
        );


        registrar.command(
            "worldauth",
            100,
            this::worldAuthority
        );

        registrar.command(
            "collisionauth",
            100,
            this::collisionAuthority
        );


        registrar.command(
            "prayerinfo",
            100,
            this::prayerInfo
        );

        registrar.command(
            "magicinfo",
            100,
            this::magicInfo
        );

        registrar.command(
            "styleinfo",
            100,
            this::styleInfo
        );
    }

    private ContentResult prayerInfo(
        ContentCommandContext context
    ){
        LocalDiagnosticContentPlayer player=
            diagnosticPlayer(
                context
            );

        return ContentResult.handled(
            "V510_PRAYER_INFO "+
                player.prayerStateSummary()+
                " definitions="+
                PrayerDefinitionRepository.count(),
            null
        );
    }

    private ContentResult magicInfo(
        ContentCommandContext context
    ){
        LocalDiagnosticContentPlayer player=
            diagnosticPlayer(
                context
            );

        return ContentResult.handled(
            "V510_MAGIC_INFO "+
                player.magicStateSummary()+
                " definitions="+
                SpellDefinitionRepository.count(),
            null
        );
    }

    private ContentResult styleInfo(
        ContentCommandContext context
    ){
        LocalDiagnosticContentPlayer player=
            diagnosticPlayer(
                context
            );

        int weapon=
            player.weaponItemId();
        int root=
            CombatInterfaceRepository.forWeapon(
                weapon
            );

        return ContentResult.handled(
            "V510_STYLE_INFO weapon="+
                weapon+" "+
                player.combatStyleStateSummary(
                    root
                )+
                " roots="+
                CombatStyleRepository.rootCount()+
                " styles="+
                CombatStyleRepository.countStyles(),
            null
        );
    }

    private static LocalDiagnosticContentPlayer
        diagnosticPlayer(
            ContentCommandContext context
        ){
        ContentPlayer player=
            context.player();

        if(!(player instanceof
                LocalDiagnosticContentPlayer))
            throw new IllegalStateException(
                "LocalLab diagnostic player projection unavailable"
            );

        return (LocalDiagnosticContentPlayer)
            player;
    }

    private ContentResult worldAuthority(
        ContentCommandContext context
    ){
        ContentPlayer player=
            context.player();

        int region=
            context.arguments().isEmpty()
                ?regionId(
                    player.worldX(),
                    player.worldY()
                )
                :parseInt(
                    context.arguments().get(0),
                    -1
                );

        WorldRegionAuthorityRepository.Region
            authority=
                WorldRegionAuthorityRepository.get(
                    region
                );

        return ContentResult.handled(
            "V5150_WORLD_AUTHORITY region="+
                region+
                " result="+
                (authority==null
                    ?"UNKNOWN"
                    :authority.toString())+
                " repositoryRegions="+
                WorldRegionAuthorityRepository.count()+
                " decoded="+
                WorldRegionAuthorityRepository
                    .fullyDecodedCount()+
                " productionConfirmed="+
                WorldRegionAuthorityRepository
                    .productionConfirmedCount()+
                " behavior=DATA_ONLY_NO_TELEPORT",
            null
        );
    }

    private ContentResult collisionAuthority(
        ContentCommandContext context
    ){
        ContentPlayer player=
            context.player();

        int x=player.worldX();
        int y=player.worldY();
        int plane=player.plane();

        if(context.arguments().size()>=2){
            x=parseInt(
                context.arguments().get(0),
                x
            );
            y=parseInt(
                context.arguments().get(1),
                y
            );
        }

        if(context.arguments().size()>=3)
            plane=parseInt(
                context.arguments().get(2),
                plane
            );

        int region=
            regionId(
                x,
                y
            );

        return ContentResult.handled(
            "V5160_COLLISION_AUTH world="+
                x+","+y+","+plane+
                " region="+region+
                " mask="+
                WorldCollisionAuthority.maskAt(
                    x,
                    y,
                    plane
                )+
                " blocked="+
                WorldCollisionAuthority.blockedTile(
                    x,
                    y,
                    plane
                )+
                " repositoryRegions="+
                WorldCollisionAuthority.regionCount()+
                " entries="+
                WorldCollisionAuthority.entryCount(),
            null
        );
    }

    private static int regionId(
        int x,
        int y
    ){
        return ((x>>6)<<8)|
            (y>>6);
    }

    private static int parseInt(
        String value,
        int fallback
    ){
        try{
            return Integer.parseInt(
                value
            );
        }catch(Exception ignored){
            return fallback;
        }
    }

    static String authoritySummary(){
        return "V5124_AUTHORITY "+
            AuthorityR16R25Publisher.status()+
            " bankWrapperExact="+
            BankState.BANK_WRAPPER_ROOT+
            " bankRuntimeRoot="+
            BankState.BANK_ROOT+
            " combatProfiles="+
            CombatStyleRepository.rootCount()+
            " combatStyles="+
            CombatStyleRepository.countStyles()+
            " note=R25_core_17_59_plus_independent_exact_staff328_3; unproven_server_mechanics_remain_fail_closed";
    }
}
