package spk.local;

import java.util.*;

/** R25 exact prayer/curse activation/config authority. */
final class PrayerDefinitionRepository {
  enum Book { NORMAL(5608), CURSES(22500); final int root; Book(int root){this.root=root;} }
  static final class Def {
    final Book book; final String name; final int level, widget, varp;
    Def(Book book,String name,int level,int widget,int varp){this.book=book;this.name=name;this.level=level;this.widget=widget;this.varp=varp;}
    @Override public String toString(){return book+":"+name+" widget="+widget+" varp="+varp+" level="+level;}
  }
  private static final LinkedHashMap<Integer,Def> BY_WIDGET=new LinkedHashMap<>();
  static {
    add(new Def(Book.NORMAL,"Augury",77,18047,610));
    add(new Def(Book.NORMAL,"Mystic Might",45,18010,606));
    add(new Def(Book.NORMAL,"Eagle Eye",44,18008,605));
    add(new Def(Book.NORMAL,"Rigour",74,18045,609));
    add(new Def(Book.NORMAL,"Thick Skin",1,5609,83));
    add(new Def(Book.NORMAL,"Burst of Strength",4,5610,84));
    add(new Def(Book.NORMAL,"Charity of Thought",7,5611,85));
    add(new Def(Book.NORMAL,"Sharp Eye",8,18000,601));
    add(new Def(Book.NORMAL,"Mystic Will",9,18002,602));
    add(new Def(Book.NORMAL,"Rock Skin",10,5612,86));
    add(new Def(Book.NORMAL,"Superhuman Strength",13,5613,87));
    add(new Def(Book.NORMAL,"Improved Reflexes",16,5614,88));
    add(new Def(Book.NORMAL,"Rapid Restore",19,5615,89));
    add(new Def(Book.NORMAL,"Rapid Heal",22,5616,90));
    add(new Def(Book.NORMAL,"Protect Item",25,5617,91));
    add(new Def(Book.NORMAL,"Hawk Eye",26,18004,603));
    add(new Def(Book.NORMAL,"Mystic Lore",27,18006,604));
    add(new Def(Book.NORMAL,"Steel Skin",28,5618,92));
    add(new Def(Book.NORMAL,"Ultimate Strength",31,5619,93));
    add(new Def(Book.NORMAL,"Incredible Reflexes",34,5620,94));
    add(new Def(Book.NORMAL,"Protect from Magic",37,5621,95));
    add(new Def(Book.NORMAL,"Protect from Missiles",40,5622,96));
    add(new Def(Book.NORMAL,"Protect from Melee",43,5623,97));
    add(new Def(Book.NORMAL,"Retribution",46,683,98));
    add(new Def(Book.NORMAL,"Redemption",49,684,99));
    add(new Def(Book.NORMAL,"Smite",52,685,100));
    add(new Def(Book.NORMAL,"Preserve",55,18553,611));
    add(new Def(Book.NORMAL,"Chivalry",60,18012,607));
    add(new Def(Book.NORMAL,"Piety",70,18014,608));
    add(new Def(Book.CURSES,"Protect Item",50,22503,83));
    add(new Def(Book.CURSES,"Sap Warrior",50,22505,84));
    add(new Def(Book.CURSES,"Sap Ranger",52,22507,85));
    add(new Def(Book.CURSES,"Sap Mage",54,22509,101));
    add(new Def(Book.CURSES,"Sap Spirit",56,22511,102));
    add(new Def(Book.CURSES,"Berserker",59,22513,86));
    add(new Def(Book.CURSES,"Deflect Summoning",62,22515,87));
    add(new Def(Book.CURSES,"Deflect Magic",65,22517,88));
    add(new Def(Book.CURSES,"Deflect Missiles",68,22519,89));
    add(new Def(Book.CURSES,"Deflect Melee",71,22521,90));
    add(new Def(Book.CURSES,"Leech Attack",74,22523,91));
    add(new Def(Book.CURSES,"Leech Ranged",76,22525,103));
    add(new Def(Book.CURSES,"Leech Magic",78,22527,104));
    add(new Def(Book.CURSES,"Leech Defence",80,22529,92));
    add(new Def(Book.CURSES,"Leech Strength",82,22531,93));
    add(new Def(Book.CURSES,"Leech Energy",84,22533,94));
    add(new Def(Book.CURSES,"Leech Special Attack",86,22535,95));
    add(new Def(Book.CURSES,"Wrath",89,22537,96));
    add(new Def(Book.CURSES,"Soul Split",92,22539,97));
    add(new Def(Book.CURSES,"Turmoil",95,22541,105));
    add(new Def(Book.CURSES,"Turmoil (range)",95,22583,106));
    add(new Def(Book.CURSES,"Turmoil (magic)",95,22585,107));
    if(BY_WIDGET.size()!=51) throw new IllegalStateException("prayer authority count");
  }
  private static void add(Def d){ if(BY_WIDGET.put(d.widget,d)!=null)throw new IllegalStateException("duplicate widget "+d.widget); }
  static Def byWidget(int widget){return BY_WIDGET.get(widget);}
  static java.util.Collection<Def> all(){return java.util.Collections.unmodifiableCollection(BY_WIDGET.values());}
  static int count(){return BY_WIDGET.size();}
  private PrayerDefinitionRepository(){}
}
