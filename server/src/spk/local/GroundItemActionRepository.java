package spk.local;

import java.util.*;

/** Exact-current ground action exceptions. Null slot 3 defaults to Take in the client. */
final class GroundItemActionRepository {
  private static final Map<Integer,String[]> ACTIONS=new HashMap<>();
  static {
    ACTIONS.put(1511,new String[]{null,null,"Take","Light",null}); // Logs
    ACTIONS.put(1513,new String[]{null,null,"Take","Light",null}); // Magic logs
    ACTIONS.put(1515,new String[]{null,null,"Take","Light",null}); // Yew logs
    ACTIONS.put(1517,new String[]{null,null,"Take","Light",null}); // Maple logs
    ACTIONS.put(1519,new String[]{null,null,"Take","Light",null}); // Willow logs
    ACTIONS.put(1521,new String[]{null,null,"Take","Light",null}); // Oak logs
    ACTIONS.put(2511,new String[]{null,null,"Take","Light",null}); // Logs
    ACTIONS.put(2862,new String[]{null,null,"Take","Light",null}); // Achey tree logs
    ACTIONS.put(3006,new String[]{null,null,"Take",null,"Light"}); // Firework
    ACTIONS.put(4653,new String[]{null,null,"Study",null,null}); // Fire
    ACTIONS.put(6332,new String[]{null,null,"Take","Light",null}); // Mahogany logs
    ACTIONS.put(6333,new String[]{null,null,"Take","Light",null}); // Teak logs
    ACTIONS.put(6888,new String[]{null,null,"Observe","Reset",null}); // Guardian statue
    ACTIONS.put(7404,new String[]{null,null,"Take","Light",null}); // Red logs
    ACTIONS.put(7405,new String[]{null,null,"Take","Light",null}); // Green logs
    ACTIONS.put(7406,new String[]{null,null,"Take","Light",null}); // Blue logs
    ACTIONS.put(8334,new String[]{"Study",null,"Take",null,"Remove"}); // Oak lectern
    ACTIONS.put(8335,new String[]{"Study",null,"Take",null,"Remove"}); // Eagle lectern
    ACTIONS.put(8336,new String[]{"Study",null,"Take",null,"Remove"}); // Demon lectern
    ACTIONS.put(8337,new String[]{"Study",null,"Take",null,"Remove"}); // Teak eagle lectern
    ACTIONS.put(8338,new String[]{"Study",null,"Take",null,"Remove"}); // Teak demon lectern
    ACTIONS.put(8339,new String[]{"Study",null,"Take",null,"Remove"}); // Mahogany eagle
    ACTIONS.put(8340,new String[]{"Study",null,"Take",null,"Remove"}); // Mahogany demon
    ACTIONS.put(8341,new String[]{null,null,"Take",null,"Remove"}); // Globe
    ACTIONS.put(8342,new String[]{null,null,"Take",null,"Remove"}); // Ornamental globe
    ACTIONS.put(8343,new String[]{null,null,"Take",null,"Remove"}); // Lunar globe
    ACTIONS.put(8344,new String[]{null,null,"Take",null,"Remove"}); // Celestial globe
    ACTIONS.put(8345,new String[]{null,null,"Take",null,"Remove"}); // Armillary sphere
    ACTIONS.put(8346,new String[]{null,null,"Take",null,"Remove"}); // Small orrery
    ACTIONS.put(8347,new String[]{null,null,"Take",null,"Remove"}); // Large orrery
    ACTIONS.put(8348,new String[]{null,null,"Take",null,"Remove"}); // Wooden telescope
    ACTIONS.put(8349,new String[]{null,null,"Take",null,"Remove"}); // Teak telescope
    ACTIONS.put(8350,new String[]{null,null,"Take",null,"Remove"}); // Mahogany 'scope
    ACTIONS.put(10006,new String[]{null,null,"Take","Lay",null}); // Bird snare
    ACTIONS.put(10008,new String[]{null,null,"Take","Lay",null}); // Box trap
    ACTIONS.put(10025,new String[]{null,null,"Take","Activate",null}); // Magic box
    ACTIONS.put(10031,new String[]{null,null,"Take","Lay",null}); // Rabbit snare
    ACTIONS.put(10328,new String[]{null,null,"Take","Light",null}); // White logs
    ACTIONS.put(10329,new String[]{null,null,"Take","Light",null}); // Purple logs
    ACTIONS.put(10810,new String[]{null,null,"Take","Light",null}); // Arctic pine logs
    ACTIONS.put(10885,new String[]{"Take",null,"Take",null,null}); // Keg
    ACTIONS.put(11770,new String[]{null,null,"Take",null,"Destroy"}); // Root cutting
    ACTIONS.put(11771,new String[]{null,null,"Take",null,"Destroy"}); // Root cutting
    ACTIONS.put(11772,new String[]{null,null,"Take",null,"Destroy"}); // Root cutting
    ACTIONS.put(11773,new String[]{null,null,"Take",null,"Destroy"}); // Root cutting
    ACTIONS.put(11774,new String[]{null,null,"Take",null,"Destroy"}); // Root cutting
    ACTIONS.put(11776,new String[]{null,null,"Take",null,"Destroy"}); // Potted root
    ACTIONS.put(12581,new String[]{null,null,"Take","Light",null}); // Eucalyptus logs
    ACTIONS.put(12583,new String[]{null,null,"Take","Light",null}); // Eucalyptus pyre logs
    ACTIONS.put(13150,new String[]{"Inspect",null,"Take",null,"Destroy"}); // Crate
    ACTIONS.put(21627,new String[]{null,null,"Take",null,"Remove"}); // @gre@Dark orrery
    ACTIONS.put(23195,new String[]{null,null,"Take","Activate",null}); // @gre@Blood-revenant catcher
    ACTIONS.put(27405,new String[]{null,null,"Take","Activate",null}); // @gre@Impling catcher
  }
  static String action(int itemId,int option1Based){
    if(option1Based<1||option1Based>5)return null; String[] a=ACTIONS.get(itemId);
    if(a!=null&&a[option1Based-1]!=null)return a[option1Based-1];
    return option1Based==3?"Take":null;
  }
  static boolean hasExplicitException(int itemId){return ACTIONS.containsKey(itemId);}
  static int exceptionCount(){return ACTIONS.size();}
}
