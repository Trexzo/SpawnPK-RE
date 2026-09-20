package spk.local;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Read-only semantic Knowledgebase/article query boundary. */
final class KnowledgebaseQueryService {
    static final class Article {
        private final String articleId;
        private final String title;
        private final List<String> sections;
        private final CatalogEvidenceAuthority authority;

        Article(
            String articleId,
            String title,
            List<String> sections,
            CatalogEvidenceAuthority authority
        ) {
            this.articleId = requireText(articleId, "articleId");
            this.title = requireText(title, "title");
            Objects.requireNonNull(sections, "sections");
            ArrayList<String> copy = new ArrayList<String>();
            for (String section : sections) {
                copy.add(requireText(section, "section"));
            }
            this.sections = Collections.unmodifiableList(copy);
            this.authority = Objects.requireNonNull(authority, "authority");
        }

        String articleId() { return articleId; }
        String title() { return title; }
        List<String> sections() { return sections; }
        CatalogEvidenceAuthority authority() { return authority; }
    }

    private final Map<String, Article> byId;

    KnowledgebaseQueryService(Collection<Article> articles) {
        Objects.requireNonNull(articles, "articles");
        ArrayList<Article> ordered = new ArrayList<Article>();
        for (Article article : articles) {
            ordered.add(Objects.requireNonNull(article, "article"));
        }
        ordered.sort(Comparator.comparing(Article::articleId));

        LinkedHashMap<String, Article> copy = new LinkedHashMap<String, Article>();
        for (Article article : ordered) {
            if (copy.put(article.articleId(), article) != null) {
                throw new IllegalArgumentException("Duplicate article id: " + article.articleId());
            }
        }
        this.byId = Collections.unmodifiableMap(copy);
    }

    Optional<Article> findById(String articleId) {
        return Optional.ofNullable(byId.get(requireText(articleId, "articleId")));
    }

    QueryPage<Article> searchByTitle(String substring, int offset, int limit) {
        String needle = requireText(substring, "substring").toLowerCase(Locale.ROOT);
        ArrayList<Article> matches = new ArrayList<Article>();
        for (Article article : byId.values()) {
            if (article.title().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(article);
            }
        }
        return QueryPage.slice(matches, offset, limit);
    }

    private static String requireText(String value, String label) {
        if (value == null) throw new NullPointerException(label);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return normalized;
    }
}
