package pt.psia.reco.domain;

import java.util.List;

/**
 * Static list of IMDb's genre tags. We hardcode this instead of running a
 * DISTINCT-over-split-column query against the dataset: it's stable, cheap,
 * and works even before the catalog is loaded.
 */
public final class GenreCatalog {

    public static final List<String> ALL = List.of(
            "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
            "Documentary", "Drama", "Family", "Fantasy", "Film-Noir", "History",
            "Horror", "Music", "Musical", "Mystery", "Romance", "Sci-Fi",
            "Sport", "Thriller", "War", "Western"
    );

    private GenreCatalog() {
    }
}
