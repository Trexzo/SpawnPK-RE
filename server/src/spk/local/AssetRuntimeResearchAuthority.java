package spk.local;
/** R8.2 detailed actual Asset/Runtime R8 archive authority. */
final class AssetRuntimeResearchAuthority {
    static final int CONFIGURED_MAYA=107,UNIQUE_KEYFRAMES=105,SKELETON_FAMILIES=28,BOUND_MODELS=39,EXACT_SKELETON_REUSE_ADDITIONAL=184;
    static final int OBJECT_MODEL_PAIRS_ATTEMPTED=17506,OBJECT_MODEL_PARSED=17505,LEGACY_SKIN_MODELS=5420,RIG_EQUIVALENCE_FAMILIES=644,CURRENT_PLACED_CANDIDATE_ROWS=37,CURRENT_PLACED_CANDIDATE_REFS=676,FULL_GROUP_ADDRESS_ROWS=2,FULL_GROUP_ADDRESS_REFS=3,MISSING_MODEL_IDS=41,MISSING_MODEL_ROWS=44,MISSING_MODEL_REFS=742;
    static final int R7_OLDSCHOOL_RESOLVED_RECORDS=48,R7_OLDSCHOOL_UNRESOLVED_RECORDS=7,R7_RESOLVED_PLACEMENTS=81150,R7_UNIQUE_OBJECTS=1237;
    static int count(){return FullResearchArchiveAuthority.countRows("/spk/local/data/research_r82/asset_object_rig_families_r82.tsv");}
    static String mayaSummary(){return "maya configured="+CONFIGURED_MAYA+" keyframes="+UNIQUE_KEYFRAMES+" skeletonFamilies="+SKELETON_FAMILIES+" boundModels="+BOUND_MODELS+" additionalExactSkeletonReuse="+EXACT_SKELETON_REUSE_ADDITIONAL+" semanticBinding=false";}
    static String objectRawSummary(){return "objectPairs="+OBJECT_MODEL_PAIRS_ATTEMPTED+" parsed="+OBJECT_MODEL_PARSED+" rigFamilies="+RIG_EQUIVALENCE_FAMILIES+" currentPlacedCandidates="+CURRENT_PLACED_CANDIDATE_ROWS+"/"+CURRENT_PLACED_CANDIDATE_REFS+" fullAddress="+FULL_GROUP_ADDRESS_ROWS+"/"+FULL_GROUP_ADDRESS_REFS+" missingModelRefs="+MISSING_MODEL_REFS+"; compatibility!=semantic animation";}
    static String updaterBoundary(){return "raw_version absent from exact client v150; SPK.jar-side or legacy/unused; no guessed RAW updater path";}
    static String archiveSummary(){return "assetArchiveSha="+FullResearchArchiveAuthority.ASSET_R8_SHA.substring(0,12)+" detailedTables=true r7ResolvedPlacements="+R7_RESOLVED_PLACEMENTS+" confirmedRAWOnly=object26353/model50096/region11602";}
    private AssetRuntimeResearchAuthority(){}
}
