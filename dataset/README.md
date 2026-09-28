# Movie dataset

Pre-built enriched dataset: `movies_enriched.csv` (~7k movies with poster
URLs, overviews, directors, stars, ratings).

On startup, `MovieDataLoader` copies it from the classpath
(`services/recommendation-service/src/main/resources/data/movies.csv`) into
the `movies` table the first time the table is empty.

To refresh the dataset, wipe the table (`just psql movies` → `TRUNCATE movies;`)
or `just clean`, then rebuild the service.
