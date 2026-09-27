#!/usr/bin/env python3
"""Filter IMDb's title.basics.tsv.gz + title.ratings.tsv.gz into a small
movies.csv for the recommendation-service dataset loader.

No third-party dependencies (stdlib only), streams both files instead of
loading everything into memory.

Usage:
    python3 filter_imdb.py \
        --basics raw/title.basics.tsv.gz \
        --ratings raw/title.ratings.tsv.gz \
        --min-votes 5000 \
        --out movies.csv
"""
import argparse
import csv
import gzip
import sys

GENRE_SEP = ";"  # used inside the CSV's genres column and in the DB column


def load_ratings(path, min_votes):
    """tconst -> (avg_rating, num_votes), only for titles with enough votes."""
    ratings = {}
    with gzip.open(path, "rt", encoding="utf-8") as f:
        reader = csv.DictReader(f, delimiter="\t")
        for row in reader:
            try:
                num_votes = int(row["numVotes"])
            except ValueError:
                continue
            if num_votes < min_votes:
                continue
            ratings[row["tconst"]] = (row["averageRating"], num_votes)
    return ratings


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--basics", required=True)
    ap.add_argument("--ratings", required=True)
    ap.add_argument("--min-votes", type=int, default=5000)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    print(f"Reading ratings (min_votes={args.min_votes})...", file=sys.stderr)
    ratings = load_ratings(args.ratings, args.min_votes)
    print(f"  {len(ratings)} titles pass the vote threshold", file=sys.stderr)

    kept = 0
    with gzip.open(args.basics, "rt", encoding="utf-8") as fin, \
         open(args.out, "w", newline="", encoding="utf-8") as fout:
        reader = csv.DictReader(fin, delimiter="\t")
        writer = csv.writer(fout)
        writer.writerow(
            ["imdb_id", "title", "start_year", "genres", "avg_rating", "num_votes"]
        )
        for row in reader:
            tconst = row["tconst"]
            if tconst not in ratings:
                continue
            if row.get("titleType") != "movie":
                continue
            if row.get("isAdult") == "1":
                continue

            title = row.get("primaryTitle", "").strip()
            if not title:
                continue

            year = row.get("startYear", "\\N")
            year = year if year != "\\N" else ""

            genres_raw = row.get("genres", "\\N")
            genres = genres_raw.replace(",", GENRE_SEP) if genres_raw != "\\N" else ""

            avg_rating, num_votes = ratings[tconst]

            writer.writerow([tconst, title, year, genres, avg_rating, num_votes])
            kept += 1

    print(f"Wrote {kept} movies to {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
