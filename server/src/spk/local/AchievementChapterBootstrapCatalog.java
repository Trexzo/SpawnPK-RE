package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Read-only recovered chapter bootstrap presentation evidence.
 *
 * Rows are not authoritative server objective definitions. In particular,
 * reward grant, eligibility, prerequisite and reset rules remain external.
 */
final class AchievementChapterBootstrapCatalog {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final String RESOURCE=
        "/spk/local/data/research_r83/05_ACHIEVEMENT_CHAPTER_BOOTSTRAP.csv";

    static final class Row {
        final int index;
        final String renderType;
        final int renderId;
        final String taskText;
        final String subtext;
        final String rewardPairs;
        final long initialProgress;
        final long goal;
        final boolean claimed;
        final String presentationAuthority;

        Row(
            int index,
            String renderType,
            int renderId,
            String taskText,
            String subtext,
            String rewardPairs,
            long initialProgress,
            long goal,
            boolean claimed
        ){
            this.index=index;
            this.renderType=renderType;
            this.renderId=renderId;
            this.taskText=taskText;
            this.subtext=subtext;
            this.rewardPairs=rewardPairs;
            this.initialProgress=
                initialProgress;
            this.goal=goal;
            this.claimed=claimed;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        @Override public String toString(){
            return "ChapterBootstrapRow{"+
                "index="+index+
                ",renderType="+renderType+
                ",renderId="+renderId+
                ",goal="+goal+
                ",claimed="+claimed+
                "}";
        }
    }

    private static final List<Row> ALL=
        load();

    static int size(){
        return ALL.size();
    }

    static List<Row> all(){
        return ALL;
    }

    static Row get(int index){
        if(index<0||
           index>=ALL.size())
            throw new IllegalArgumentException(
                "index="+index
            );

        return ALL.get(index);
    }

    private static List<Row> load(){
        ArrayList<Row> out=
            new ArrayList<>();

        try(InputStream in=
                AchievementChapterBootstrapCatalog.class
                    .getResourceAsStream(
                        RESOURCE
                    )){
            if(in==null)
                throw new IllegalStateException(
                    "missing resource "+
                    RESOURCE
                );

            try(BufferedReader reader=
                    new BufferedReader(
                        new InputStreamReader(
                            in,
                            StandardCharsets.UTF_8
                        )
                    )){
                String header=
                    reader.readLine();

                if(header==null||
                   !header.startsWith(
                       "index,render_type,"))
                    throw new IllegalStateException(
                        "unexpected chapter bootstrap header"
                    );

                String line;

                while((line=
                        reader.readLine())!=null){
                    if(line.trim().isEmpty())
                        continue;

                    String[] row=
                        parseCsv(line);

                    if(row.length!=9)
                        throw new IllegalStateException(
                            "chapter row columns="+
                            row.length+
                            " line="+line
                        );

                    out.add(
                        new Row(
                            Integer.parseInt(
                                row[0]
                            ),
                            row[1],
                            Integer.parseInt(
                                row[2]
                            ),
                            row[3],
                            row[4],
                            row[5],
                            Long.parseLong(
                                row[6]
                            ),
                            Long.parseLong(
                                row[7]
                            ),
                            Boolean.parseBoolean(
                                row[8]
                            )
                        )
                    );
                }
            }
        }catch(IOException e){
            throw new ExceptionInInitializerError(
                e
            );
        }

        validate(out);

        return Collections.unmodifiableList(
            out
        );
    }

    private static void validate(
        List<Row> rows
    ){
        if(rows.size()!=9)
            throw new IllegalStateException(
                "expected 9 chapter rows, got "+
                rows.size()
            );

        for(int i=0;
            i<rows.size();
            i++){
            Row row=rows.get(i);

            if(row.index!=i)
                throw new IllegalStateException(
                    "non-contiguous chapter index "+
                    row.index+
                    " at row "+i
                );

            if(row.goal<=0)
                throw new IllegalStateException(
                    "non-positive chapter goal "+
                    row.index
                );

            if(row.initialProgress<0||
               row.initialProgress>row.goal)
                throw new IllegalStateException(
                    "invalid chapter progress "+
                    row.index
                );

            if(row.claimed&&
               row.initialProgress<row.goal)
                throw new IllegalStateException(
                    "claimed incomplete chapter row "+
                    row.index
                );
        }
    }

    private static String[] parseCsv(
        String line
    ){
        ArrayList<String> cells=
            new ArrayList<>();

        StringBuilder current=
            new StringBuilder();

        boolean quoted=false;

        for(int i=0;
            i<line.length();
            i++){
            char c=line.charAt(i);

            if(c=='"'){
                if(quoted&&
                   i+1<line.length()&&
                   line.charAt(i+1)=='"'){
                    current.append('"');
                    i++;
                }else{
                    quoted=!quoted;
                }
            }else if(c==','&&!quoted){
                cells.add(
                    current.toString()
                );
                current.setLength(0);
            }else{
                current.append(c);
            }
        }

        if(quoted)
            throw new IllegalStateException(
                "unterminated csv quote: "+
                line
            );

        cells.add(
            current.toString()
        );

        return cells.toArray(
            new String[0]
        );
    }

    private AchievementChapterBootstrapCatalog(){}
}
