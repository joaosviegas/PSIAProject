-- Movie catalog, populated at application startup by MovieDataLoader from
-- src/main/resources/data/movies.csv (see dataset/README.md).
CREATE TABLE movies (
    id          BIGSERIAL PRIMARY KEY,
    imdb_id     VARCHAR(16)   NOT NULL UNIQUE,
    title       VARCHAR(512)  NOT NULL,
    start_year  INTEGER,
    -- semicolon-separated genre tags, e.g. "Action;Sci-Fi"
    genres      VARCHAR(255),
    avg_rating  NUMERIC(3, 1),
    num_votes   INTEGER
);

CREATE INDEX idx_movies_title_lower ON movies (LOWER(title));
CREATE INDEX idx_movies_genres ON movies (genres);
CREATE INDEX idx_movies_num_votes ON movies (num_votes DESC);
