CREATE TABLE profiles (
    user_id      UUID PRIMARY KEY,
    username     VARCHAR(30)  NOT NULL,
    display_name VARCHAR(50)  NOT NULL,
    bio          VARCHAR(500),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_profiles_updated_at ON profiles(updated_at);

CREATE TABLE profile_favorite_genres (
    user_id UUID        NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    genre   VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, genre)
);

CREATE TABLE profile_favorite_movie_ids (
    user_id  UUID    NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    movie_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, movie_id)
);

CREATE TABLE profile_disliked_movie_ids (
    user_id  UUID    NOT NULL REFERENCES profiles(user_id) ON DELETE CASCADE,
    movie_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, movie_id)
);
