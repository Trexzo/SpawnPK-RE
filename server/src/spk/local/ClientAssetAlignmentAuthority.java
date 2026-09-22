package spk.local;

/**
 * R7.1 provenance/alignment contract. This is intentionally data-only: it pins
 * the exact audited client/assets and the row counts already imported into
 * LocalLab without promoting server-owned mechanics.
 */
final class ClientAssetAlignmentAuthority {
    static final String HISTORICAL_V307_CLIENT_SHA256="6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662";
    static final String CLIENT_SHA256="854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6";
    static final String SPAWNPK_ASSET_BUNDLE_SHA256="607425ad3fe69f4cfcaaf82220d9a0d4ebff9954e896819245f37271713d299a";
    static final String SPAWNPK_AUTHORITY_SHA256="60aa028ed2299cc9dc0851bc6283e4b79262c8db0df694ccd05518bf889478cc";
    static final String WORLD_AUTHORITY_SHA256="95b7de0236b1fc717ac564914446a5d3d89af11e2e19732f1a04f9765338e5bc";
    static final String EQUIPMENT_STATS_R2_SHA256="1236016d249cc82f24d78bb07e0827be994eacfabf8d7f5c4addc4d0079e9ee9";
    static final String SUPPORTING_RUNTIME_SHA256="8361f37a2dc3f8441c5e8a304312031276255d41b1dfab8f2116a57438ff5a5c";
    static final String R30_DEEP_STATIC_SHA256="70a4f7ad12706268bd41e93657ffb272bb468d81e97289d7e0ea2812b4503e2e";
    static final String PRESENTATION_CONSOLIDATED_056_SHA256="056f0c6856cced1f03f44040e47490d61e162adc5650ced51e38c15915f1bbb9";
    static final String ITEM_PET_R4_SHA256="adc215bd8c5257ae2f6b226da3fe42e609880f86bbb8710b26909c221dae178a";
    static final int STATIC_WORLD_PLACEMENTS=2162982;
    static final int COLLISION_ENTRIES=4233244;

    static boolean countsAligned(){
        return ItemAuthorityRepository.count()==30000 &&
            PetResearchAuthorityRepository.count()==339 &&
            PrayerDefinitionRepository.count()==51 &&
            SpellDefinitionRepository.count()==126 &&
            CombatStyleRepository.rootCount()==18 &&
            CombatStyleRepository.countStyles()==62 &&
            AmmoAuthorityRepository.count()==605 &&
            SpecialAttackAuthorityRepository.count()==107 &&
            WorldRegionAuthorityRepository.count()==1279 &&
            WorldCollisionAuthority.regionCount()==1279;
    }
    static boolean root328Aligned(){
        CombatStyleRepository.Style a=CombatStyleRepository.byValue(328,0);
        CombatStyleRepository.Style b=CombatStyleRepository.byValue(328,1);
        CombatStyleRepository.Style c=CombatStyleRepository.byValue(328,2);
        return a!=null&&b!=null&&c!=null&&"Bash".equals(a.label)&&"Pound".equals(b.label)&&c.label.startsWith("Focus");
    }
    static String shortStatus(){
        return (countsAligned()&&root328Aligned()?"PASS":"FAIL")+
            " client="+CLIENT_SHA256.substring(0,12)+
            " assets="+SPAWNPK_ASSET_BUNDLE_SHA256.substring(0,12)+
            " authority="+SPAWNPK_AUTHORITY_SHA256.substring(0,12);
    }
    private ClientAssetAlignmentAuthority(){}
}
