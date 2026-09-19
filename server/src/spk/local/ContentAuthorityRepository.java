package spk.local;

/** One place for the broad R7 authority census exposed to gameplay/dev tools. */
final class ContentAuthorityRepository {
    static String summary(){
        return "items="+ItemAuthorityRepository.count()+
            " petsResearch="+PetResearchAuthorityRepository.count()+
            " petsGameplay="+PetDefinitionRepository.count()+
            " prayers="+PrayerDefinitionRepository.count()+
            " spells="+SpellDefinitionRepository.count()+
            " combatRoots="+CombatStyleRepository.rootCount()+
            " combatStyles="+CombatStyleRepository.countStyles()+
            " ammoRows="+AmmoAuthorityRepository.count()+
            " specialRows="+SpecialAttackAuthorityRepository.count()+
            " worldRegions="+WorldRegionAuthorityRepository.count()+
            " collisionRegions="+WorldCollisionAuthority.regionCount()+
            " equipRelations="+EquipmentResearchAuthority.relationshipCount()+
            " teleportItems="+WorldTransitionResearchAuthority.teleportCount()+
            " transitions="+WorldTransitionResearchAuthority.transitionCount()+
            " petProcRecovered="+PetProcResearchAuthority.count();
    }
    static String itemSummary(int itemId){
        ItemAuthorityRepository.Entry item=ItemAuthorityRepository.get(itemId);
        AmmoAuthorityRepository.Entry ammo=AmmoAuthorityRepository.get(itemId);
        SpecialAttackAuthorityRepository.Entry spec=SpecialAttackAuthorityRepository.get(itemId);
        PetResearchAuthorityRepository.Entry pet=PetResearchAuthorityRepository.get(itemId);
        V913WeaponRuntimeAuthority.Profile run=V913WeaponRuntimeAuthority.resolve(itemId);
        return "item="+itemId+" name="+(item==null?"UNKNOWN":ItemAuthorityRepository.stripTags(item.name))+
            " ammo="+(ammo==null?"none":ammo.classification+"/"+ammo.effectiveAuthority)+
            " special="+(spec==null?"none":spec.metadataAuthority+"/formula="+(spec.formulaResolved()?"resolved":"unknown"))+
            " pet="+(pet==null?"none":pet.mappingCertainty)+
            " runtimeWeapon="+(run==null?"none":run.evidence);
    }
    private ContentAuthorityRepository(){}
}
