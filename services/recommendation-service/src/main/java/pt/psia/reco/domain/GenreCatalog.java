package pt.psia.reco.domain;

import java.util.List;

/**
 * Static list of IMDb's genre tags. We hardcode this instead of running a
 * query against the dataset: it's stable, cheap,
 * and works even before the catalog is loaded.
 */
public final class GenreCatalog {

    /** List of all IMDb genres. */
    public static final List<String> ALL = List.of(
            "Action", "Adventure", "Animation", "Biography", "Comedy", "Crime",
            "Documentary", "Drama", "Family", "Fantasy", "Film-Noir", "History",
            "Horror", "Music", "Musical", "Mystery", "Romance", "Sci-Fi",
            "Sport", "Thriller", "War", "Western"
    );

    /** Private constructor to prevent instantiation. */
    private GenreCatalog() {
    }
}
