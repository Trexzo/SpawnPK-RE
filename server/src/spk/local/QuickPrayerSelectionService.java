package spk.local;

import java.util.*;

/**
 * Semantic confirmed Quick Prayer / Quick Curse selection state.
 *
 * Exact-current client evidence proves the visible selectable prayer catalog
 * and a shared Confirm action. Raw selector widgets/configs remain outside this
 * gameplay boundary. This service does not activate prayers or persist state.
 */
final class QuickPrayerSelectionService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Book {
        NORMAL,
        CURSES
    }

    static final class Option {
        final Book book;
        final String prayerKey;
        final String displayName;
        final String presentationAuthority;

        Option(
            Book book,
            String prayerKey,
            String displayName
        ){
            this.book=
                Objects.requireNonNull(
                    book,
                    "book"
                );
            this.prayerKey=
                requireKey(
                    prayerKey,
                    "prayerKey"
                );
            this.displayName=
                requireText(
                    displayName,
                    "displayName"
                );
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }
    }

    static final class Snapshot {
        final Book currentBook;
        final Map<Book,List<String>> confirmedSelections;
        final Map<Book,Long> confirmedRevisions;
        final boolean editing;
        final Book editingBook;
        final List<String> draftSelection;
        final String policyAuthority;
        final String presentationAuthority;

        Snapshot(
            Book currentBook,
            EnumMap<Book,LinkedHashSet<String>>
                confirmed,
            EnumMap<Book,Long> revisions,
            Edit edit,
            String policyAuthority
        ){
            this.currentBook=
                Objects.requireNonNull(
                    currentBook,
                    "currentBook"
                );

            EnumMap<Book,List<String>>
                confirmedCopy=
                    new EnumMap<>(
                        Book.class
                    );
            EnumMap<Book,Long>
                revisionCopy=
                    new EnumMap<>(
                        Book.class
                    );

            for(Book book:Book.values()){
                confirmedCopy.put(
                    book,
                    ordered(
                        book,
                        confirmed.get(book)
                    )
                );
                revisionCopy.put(
                    book,
                    revisions.get(book)
                );
            }

            this.confirmedSelections=
                Collections.unmodifiableMap(
                    confirmedCopy
                );
            this.confirmedRevisions=
                Collections.unmodifiableMap(
                    revisionCopy
                );

            this.editing=edit!=null;
            this.editingBook=
                edit==null
                    ?null
                    :edit.book;
            this.draftSelection=
                edit==null
                    ?Collections.emptyList()
                    :ordered(
                        edit.book,
                        edit.selected
                    );

            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        List<String> confirmed(Book book){
            return confirmedSelections.get(
                Objects.requireNonNull(
                    book,
                    "book"
                )
            );
        }

        long revision(Book book){
            return confirmedRevisions.get(
                Objects.requireNonNull(
                    book,
                    "book"
                )
            );
        }
    }

    private static final class Edit {
        final Book book;
        final LinkedHashSet<String> selected;

        Edit(
            Book book,
            Collection<String> selected
        ){
            this.book=book;
            this.selected=
                new LinkedHashSet<>(
                    selected
                );
        }
    }

    private static final EnumMap<Book,List<Option>>
        CATALOG=
            buildCatalog();

    private static final EnumMap<Book,Set<String>>
        VISIBLE_KEYS=
            buildVisibleKeys();

    private final PrayerState prayerState;
    private final String policyAuthority;

    private final EnumMap<Book,LinkedHashSet<String>>
        confirmed=
            new EnumMap<>(
                Book.class
            );

    private final EnumMap<Book,Long>
        revisions=
            new EnumMap<>(
                Book.class
            );

    private Edit edit;

    QuickPrayerSelectionService(
        PrayerState prayerState,
        String policyAuthority
    ){
        this.prayerState=
            Objects.requireNonNull(
                prayerState,
                "prayerState"
            );
        this.policyAuthority=
            gameplayAuthority(
                policyAuthority
            );

        for(Book book:Book.values()){
            confirmed.put(
                book,
                new LinkedHashSet<>()
            );
            revisions.put(
                book,
                0L
            );
        }
    }

    static List<Option> available(
        Book book
    ){
        return CATALOG.get(
            Objects.requireNonNull(
                book,
                "book"
            )
        );
    }

    synchronized Snapshot beginEdit(){
        if(edit!=null)
            throw new IllegalStateException(
                "quick selection edit already open book="+
                edit.book
            );

        Book book=currentBook();

        edit=
            new Edit(
                book,
                confirmed.get(book)
            );

        return snapshot();
    }

    synchronized Snapshot toggleDraft(
        String prayerKey
    ){
        Edit current=requireEditContext();
        String key=
            requireKey(
                prayerKey,
                "prayerKey"
            );

        if(!VISIBLE_KEYS
                .get(current.book)
                .contains(key))
            throw new IllegalArgumentException(
                "quick selection not visible book="+
                current.book+
                " prayerKey="+key
            );

        if(!current.selected.add(key))
            current.selected.remove(key);

        return snapshot();
    }

    synchronized Snapshot confirmEdit(){
        Edit current=requireEditContext();

        long next;

        try{
            next=
                Math.addExact(
                    revisions.get(
                        current.book
                    ),
                    1L
                );
        }catch(ArithmeticException error){
            throw new IllegalStateException(
                "quick selection revision overflow book="+
                current.book,
                error
            );
        }

        LinkedHashSet<String> replacement=
            new LinkedHashSet<>(
                current.selected
            );

        confirmed.put(
            current.book,
            replacement
        );
        revisions.put(
            current.book,
            next
        );
        edit=null;

        return snapshot();
    }

    synchronized boolean cancelEdit(){
        if(edit==null)
            return false;

        edit=null;
        return true;
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            currentBook(),
            confirmed,
            revisions,
            edit,
            policyAuthority
        );
    }

    private Edit requireEditContext(){
        if(edit==null)
            throw new IllegalStateException(
                "quick selection edit not open"
            );

        Book current=currentBook();

        if(current!=edit.book)
            throw new IllegalStateException(
                "quick selection book changed editing="+
                edit.book+
                " current="+current
            );

        return edit;
    }

    private Book currentBook(){
        PrayerDefinitionRepository.Book book=
            prayerState.book();

        if(book==
                PrayerDefinitionRepository
                    .Book.NORMAL)
            return Book.NORMAL;

        if(book==
                PrayerDefinitionRepository
                    .Book.CURSES)
            return Book.CURSES;

        throw new IllegalStateException(
            "unsupported prayer book "+
            book
        );
    }

    private static EnumMap<Book,List<Option>>
        buildCatalog()
    ){
        EnumMap<Book,List<Option>> out=
            new EnumMap<>(
                Book.class
            );

        out.put(
            Book.NORMAL,
            options(
                Book.NORMAL,
                Arrays.asList(
                    "Thick Skin",
                    "Burst of Strength",
                    "Charity of Thought",
                    "Sharp Eye",
                    "Mystic Will",
                    "Rock Skin",
                    "Superhuman Strength",
                    "Improved Reflexes",
                    "Rapid Restore",
                    "Rapid Heal",
                    "Protect Item",
                    "Hawk Eye",
                    "Mystic Lore",
                    "Steel Skin",
                    "Ultimate Strength",
                    "Incredible Reflexes",
                    "Protect from Magic",
                    "Protect from Missiles",
                    "Protect from Melee",
                    "Eagle Eye",
                    "Mystic Might",
                    "Retribution",
                    "Redemption",
                    "Smite",
                    "Preserve",
                    "Chivalry",
                    "Piety",
                    "Rigour",
                    "Augury"
                )
            )
        );

        out.put(
            Book.CURSES,
            options(
                Book.CURSES,
                Arrays.asList(
                    "Protect Item",
                    "Sap Warrior",
                    "Sap Ranger",
                    "Sap Mage",
                    "Sap Spirit",
                    "Berserker",
                    "Deflect Summoning",
                    "Deflect Magic",
                    "Deflect Missiles",
                    "Deflect Melee",
                    "Leech Attack",
                    "Leech Ranged",
                    "Leech Magic",
                    "Leech Defence",
                    "Leech Strength",
                    "Leech Energy",
                    "Leech Special Attack",
                    "Wrath",
                    "Soul Split",
                    "Turmoil"
                )
            )
        );

        return out;
    }

    private static List<Option> options(
        Book book,
        List<String> names
    ){
        ArrayList<Option> out=
            new ArrayList<>();
        HashSet<String> seen=
            new HashSet<>();

        for(String name:names){
            PrayerDefinitionRepository.Def
                definition=
                    exactDefinition(
                        book,
                        name
                    );

            String key=
                semanticKey(
                    book,
                    definition.name
                );

            if(!seen.add(key))
                throw new IllegalStateException(
                    "duplicate quick prayer semantic key "+
                    key
                );

            out.add(
                new Option(
                    book,
                    key,
                    definition.name
                )
            );
        }

        return Collections.unmodifiableList(
            out
        );
    }

    private static PrayerDefinitionRepository.Def
        exactDefinition(
            Book book,
            String name
        ){
        PrayerDefinitionRepository.Book
            repositoryBook=
                book==Book.NORMAL
                    ?PrayerDefinitionRepository
                        .Book.NORMAL
                    :PrayerDefinitionRepository
                        .Book.CURSES;

        PrayerDefinitionRepository.Def found=
            null;

        for(PrayerDefinitionRepository.Def
                definition:
                PrayerDefinitionRepository
                    .all()){
            if(definition.book==
                    repositoryBook&&
               definition.name.equals(
                    name)){
                if(found!=null)
                    throw new IllegalStateException(
                        "duplicate prayer definition "+
                        book+" "+name
                    );

                found=definition;
            }
        }

        if(found==null)
            throw new IllegalStateException(
                "missing prayer definition "+
                book+" "+name
            );

        return found;
    }

    private static EnumMap<Book,Set<String>>
        buildVisibleKeys()
    ){
        EnumMap<Book,Set<String>> out=
            new EnumMap<>(
                Book.class
            );

        for(Book book:Book.values()){
            LinkedHashSet<String> keys=
                new LinkedHashSet<>();

            for(Option option:
                    CATALOG.get(book))
                keys.add(
                    option.prayerKey
                );

            out.put(
                book,
                Collections.unmodifiableSet(
                    keys
                )
            );
        }

        return out;
    }

    private static List<String> ordered(
        Book book,
        Collection<String> selected
    ){
        ArrayList<String> out=
            new ArrayList<>();

        for(Option option:
                CATALOG.get(book)){
            if(selected.contains(
                    option.prayerKey))
                out.add(
                    option.prayerKey
                );
        }

        return Collections.unmodifiableList(
            out
        );
    }

    private static String semanticKey(
        Book book,
        String displayName
    ){
        StringBuilder slug=
            new StringBuilder();
        boolean underscore=false;

        String value=
            displayName
                .trim()
                .toLowerCase(
                    Locale.ROOT
                );

        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);

            if(c>='a'&&c<='z'||
               c>='0'&&c<='9'){
                slug.append(c);
                underscore=false;
            }else if(!underscore&&
                    slug.length()>0){
                slug.append('_');
                underscore=true;
            }
        }

        while(slug.length()>0&&
              slug.charAt(
                  slug.length()-1)=='_')
            slug.setLength(
                slug.length()-1
            );

        if(slug.length()==0)
            throw new IllegalArgumentException(
                "displayName has no semantic key"
            );

        return book.name()
            .toLowerCase(
                Locale.ROOT
            )+
            ":"+
            slug;
    }

    private static String requireKey(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String normalized=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        for(int i=0;i<
                normalized.length();i++){
            char c=
                normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c==':'||
                c=='_';

            if(!ok)
                throw new IllegalArgumentException(
                    field+" invalid="+
                    value
                );
        }

        return normalized;
    }

    private static String requireText(
        String value,
        String field
    ){
        if(value==null)
            throw new NullPointerException(
                field
            );

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }

    private static String gameplayAuthority(
        String value
    ){
        String authority=
            requireText(
                value,
                "policyAuthority"
            );

        if(PRESENTATION_AUTHORITY
                .equalsIgnoreCase(
                    authority)||
           "UNKNOWN_SERVER_AUTHORITY"
                .equalsIgnoreCase(
                    authority))
            throw new IllegalArgumentException(
                "quick selection policy authority cannot be "+
                authority
            );

        return authority;
    }
}
