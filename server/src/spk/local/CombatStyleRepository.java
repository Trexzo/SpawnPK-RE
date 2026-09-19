package spk.local;

import java.util.*;

/**
 * Exact current combat-style selector authority.
 * R25 supplied 17 Java-builder profiles; v5.10 adds the exact cache-native
 * staff root328 selectors (Bash/Pound/Focus-Block).
 */
final class CombatStyleRepository {
  static final int VARP=43;
  static final class Style {
    final int root,widget,value; final String label,mode,attackType; final String[] trainedSkills;
    Style(int root,int widget,int value,String label,String mode,String attackType,String[] skills){this.root=root;this.widget=widget;this.value=value;this.label=label;this.mode=mode;this.attackType=attackType;this.trainedSkills=skills;}
    @Override public String toString(){return "root="+root+" "+label+" widget="+widget+" value="+value+" mode="+mode+" type="+attackType+" xp="+Arrays.toString(trainedSkills);}
  }
  private static final LinkedHashMap<Integer,List<Style>> BY_ROOT=new LinkedHashMap<>();
  static {
    add(new Style(1698,1704,0,"Chop","Accurate","SLASH",new String[]{"ATTACK"}));
    add(new Style(1698,1707,1,"Hack","Aggressive","SLASH",new String[]{"STRENGTH"}));
    add(new Style(1698,1706,2,"Smash","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(1698,1705,3,"Block","Defensive","SLASH",new String[]{"DEFENCE"}));
    add(new Style(2276,2282,0,"Stab","Accurate","STAB",new String[]{"ATTACK"}));
    add(new Style(2276,2285,1,"Lunge","Aggressive","STAB",new String[]{"STRENGTH"}));
    add(new Style(2276,2284,2,"Slash","Aggressive","SLASH",new String[]{"STRENGTH"}));
    add(new Style(2276,2283,3,"Block","Defensive","STAB",new String[]{"DEFENCE"}));
    add(new Style(2423,2429,0,"Chop","Accurate","SLASH",new String[]{"ATTACK"}));
    add(new Style(2423,2432,1,"Slash","Aggressive","SLASH",new String[]{"STRENGTH"}));
    add(new Style(2423,2431,2,"Lunge","Controlled","STAB",new String[]{}));
    add(new Style(2423,2430,3,"Block","Defensive","SLASH",new String[]{"DEFENCE"}));
    add(new Style(3796,3802,0,"Pound","Accurate","CRUSH",new String[]{"ATTACK"}));
    add(new Style(3796,3805,1,"Pummel","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(3796,3804,2,"Spike","Controlled","STAB",new String[]{}));
    add(new Style(3796,3803,3,"Block","Defensive","CRUSH",new String[]{"DEFENCE"}));
    add(new Style(4679,4685,0,"Lunge","Controlled","STAB",new String[]{}));
    add(new Style(4679,4688,1,"Swipe","Controlled","SLASH",new String[]{}));
    add(new Style(4679,4687,2,"Pound","Controlled","CRUSH",new String[]{}));
    add(new Style(4679,4686,3,"Block","Defensive","STAB",new String[]{"DEFENCE"}));
    add(new Style(4705,4711,0,"Chop","Accurate","SLASH",new String[]{"ATTACK"}));
    add(new Style(4705,4714,1,"Slash","Aggressive","SLASH",new String[]{"STRENGTH"}));
    add(new Style(4705,4713,2,"Smash","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(4705,4712,3,"Block","Defensive","SLASH",new String[]{"DEFENCE"}));
    add(new Style(5570,5576,0,"Spike","Accurate","STAB",new String[]{"ATTACK"}));
    add(new Style(5570,5579,1,"Impale","Aggressive","STAB",new String[]{"STRENGTH"}));
    add(new Style(5570,5578,2,"Smash","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(5570,5577,3,"Block","Defensive","STAB",new String[]{"DEFENCE"}));
    add(new Style(7762,7768,0,"Chop","Accurate","SLASH",new String[]{"ATTACK"}));
    add(new Style(7762,7771,1,"Slash","Aggressive","SLASH",new String[]{"STRENGTH"}));
    add(new Style(7762,7770,2,"Lunge","Controlled","STAB",new String[]{}));
    add(new Style(7762,7769,3,"Block","Defensive","SLASH",new String[]{"DEFENCE"}));
    add(new Style(776,782,0,"Reap","Accurate","SLASH",new String[]{"ATTACK"}));
    add(new Style(776,785,1,"Chop","Aggressive","SLASH",new String[]{"STRENGTH"}));
    add(new Style(776,784,2,"Jab","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(776,783,3,"Block","Defensive","SLASH",new String[]{"DEFENCE"}));
    add(new Style(425,433,0,"Pound","Accurate","CRUSH",new String[]{"ATTACK"}));
    add(new Style(425,432,1,"Pummel","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(425,431,2,"Block","Defensive","CRUSH",new String[]{"DEFENCE"}));
    add(new Style(1749,1757,0,"Accurate","Accurate",null,new String[]{"RANGED"}));
    add(new Style(1749,1756,1,"Rapid","Rapid",null,new String[]{"RANGED"}));
    add(new Style(1749,1755,2,"Longrange","Long range",null,new String[]{"RANGED","DEFENCE"}));
    add(new Style(1764,1772,0,"Accurate","Accurate",null,new String[]{"RANGED"}));
    add(new Style(1764,1771,1,"Rapid","Rapid",null,new String[]{"RANGED"}));
    add(new Style(1764,1770,2,"Longrange","Long range",null,new String[]{"RANGED","DEFENCE"}));
    add(new Style(4446,4454,0,"Accurate","Accurate",null,new String[]{"RANGED"}));
    add(new Style(4446,4453,1,"Rapid","Rapid",null,new String[]{"RANGED"}));
    add(new Style(4446,4452,2,"Longrange","Long range",null,new String[]{"RANGED","DEFENCE"}));
    add(new Style(5855,5862,1,"Punch","Accurate","CRUSH",new String[]{"ATTACK"}));
    add(new Style(5855,5861,2,"Kick","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(5855,5860,0,"Block","Defensive","CRUSH",new String[]{"DEFENCE"}));
    add(new Style(6103,433,0,"Pound","Accurate","CRUSH",new String[]{"ATTACK"}));
    add(new Style(6103,431,2,"Block","Defensive","CRUSH",new String[]{"DEFENCE"}));
    add(new Style(8460,8468,1,"Jab","Controlled","STAB",new String[]{}));
    add(new Style(8460,8467,2,"Swipe","Aggressive","SLASH",new String[]{"STRENGTH"}));
    add(new Style(8460,8466,0,"Fend","Defensive","STAB",new String[]{"DEFENCE"}));
    add(new Style(12290,12298,0,"Flick","Accurate","SLASH",new String[]{"ATTACK"}));
    add(new Style(12290,12297,1,"Lash","Controlled","SLASH",new String[]{}));
    add(new Style(12290,12296,2,"Deflect","Defensive","SLASH",new String[]{"DEFENCE"}));
    add(new Style(328,336,0,"Bash","Accurate","CRUSH",new String[]{"ATTACK"}));
    add(new Style(328,335,1,"Pound","Aggressive","CRUSH",new String[]{"STRENGTH"}));
    add(new Style(328,334,2,"Focus - Block","Defensive","CRUSH",new String[]{"DEFENCE"}));
    if(BY_ROOT.size()!=18 || countStyles()!=62) throw new IllegalStateException("combat style authority count roots="+BY_ROOT.size()+" styles="+countStyles());
  }
  private static void add(Style s){BY_ROOT.computeIfAbsent(s.root,k->new ArrayList<>()).add(s);}
  static Style byWidget(int root,int widget){List<Style> xs=BY_ROOT.get(root);if(xs==null)return null;for(Style s:xs)if(s.widget==widget)return s;return null;}
  static Style byValue(int root,int value){List<Style> xs=BY_ROOT.get(root);if(xs==null)return null;for(Style s:xs)if(s.value==value)return s;return null;}
  static Style defaultForRoot(int root){Style z=byValue(root,0);if(z!=null)return z;List<Style>xs=BY_ROOT.get(root);return xs==null||xs.isEmpty()?null:xs.get(0);}
  static int rootCount(){return BY_ROOT.size();}
  static int countStyles(){int n=0;for(List<Style>x:BY_ROOT.values())n+=x.size();return n;}
  static java.util.Set<Integer> roots(){return java.util.Collections.unmodifiableSet(BY_ROOT.keySet());}
  private CombatStyleRepository(){}
}
