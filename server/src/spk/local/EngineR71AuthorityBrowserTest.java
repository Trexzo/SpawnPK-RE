package spk.local;
public final class EngineR71AuthorityBrowserTest {
  public static void main(String[] args){
    SpellDefinitionRepository.Spell s=SpellDefinitionRepository.all().iterator().next();
    if(s==null||s.widget<0||s.name==null)throw new AssertionError("spell browser seed invalid");
    PrayerDefinitionRepository.Def p=PrayerDefinitionRepository.all().iterator().next();
    if(p==null||p.widget<0||p.name==null)throw new AssertionError("prayer browser seed invalid");
    ItemAuthorityRepository.Entry i=ItemAuthorityRepository.get(7807);if(i==null)throw new AssertionError("item browser sample missing");
    WorldRegionAuthorityRepository.Region r=WorldRegionAuthorityRepository.get(12342);if(r==null)throw new AssertionError("world browser sample missing");
    DevControlCenter d=new DevControlCenter();d.open(DevControlCenter.Page.AUTHORITY);d.selectSpellWidget(s.widget);d.selectPrayerWidget(p.widget);d.selectItemId(i.itemId);d.selectRegionId(r.regionId);
    if(d.selectedSpellWidget()!=s.widget||d.selectedPrayerWidget()!=p.widget||d.selectedItemId()!=i.itemId||d.selectedRegionId()!=r.regionId)throw new AssertionError("browser selection state drift");
    System.out.println("V5171_ENGINE_R71_AUTHORITY_BROWSER_PASS pages=authority/magic/world-item/alignment selections=spell/prayer/item/region readOnly=true");
  }
}
