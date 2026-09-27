# Movie dataset (IMDb non-commercial datasets)

We use IMDb's free "non-commercial" data exports instead of a live API call.
They're plain gzipped TSVs, updated daily, no key/quota needed. Usage is
limited to personal/non-commercial use — fine for this course project, but
say so in the report.

Source: https://datasets.imdbws.com/ (also documented at
https://developer.imdb.com/non-commercial-datasets/)

## 1. Download two files

```bash
mkdir -p dataset/raw
cd dataset/raw
curl -O https://datasets.imdbws.com/title.basics.tsv.gz
curl -O https://datasets.imdbws.com/title.ratings.tsv.gz
```

- `title.basics.tsv.gz` — one row per title: id, type, primary title, year, genres...
- `title.ratings.tsv.gz` — one row per title: id, average rating, number of votes.

Don't download `title.akas`/`title.principals`/`name.basics` — we don't need them
and they're much bigger (principals alone is several GB uncompressed).

## 2. Filter down to a manageable, rated movie catalog

`title.basics` alone has ~11M rows (shorts, TV episodes, video games...). We only
want feature movies that have enough votes to be worth recommending. Run:

```bash
python3 dataset/filter_imdb.py \
    --basics dataset/raw/title.basics.tsv.gz \
    --ratings dataset/raw/title.ratings.tsv.gz \
    --min-votes 5000 \
    --out services/recommendation-service/src/main/resources/data/movies.csv
```

This streams both gzip files line-by-line (no pandas needed, no need to fully
unzip ~1 GB of TSV to disk) and writes a small CSV — with `--min-votes 5000`
you should land around 8-12k movies (a few MB), small enough to commit to git
and bake into the service's Docker image as a classpath resource.

Raise `--min-votes` (e.g. 20000) if you want a smaller/cleaner catalog, or
lower it if search results feel too sparse.

## 3. Load into the database

`recommendation-service` reads `src/main/resources/data/movies.csv` on startup
and bulk-loads it into the `movies` table the first time the table is empty
(see `MovieDataLoader.java`). You don't need to touch Postgres by hand —
just make sure `movies.csv` exists before `docker compose build
recommendation-service`, then `just restart recommendation-service` (or
`just start` the first time).

To refresh the dataset later, delete the rows (`just psql movies` →
`TRUNCATE movies;`) or `just clean` to wipe the volume, then rebuild.
