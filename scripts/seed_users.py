#!/usr/bin/env python3
"""Seed realistic demo users with varied tastes through the gateway API.

Renames the auto-generated test accounts (smoke_, feedtest_, mon, chk, eval, var) into
realistic users, then registers the rest. Safe to re-run: existing users are logged in and
their profiles overwritten. Every seeded account uses the password in SEED_PASSWORD.
"""
import json
import os
import random
import re
import subprocess
import urllib.error
import urllib.request

BASE = os.environ.get("BASE_URL", f"http://localhost:{os.environ.get('GATEWAY_PORT', '8080')}") + "/api"
PASSWORD = os.environ.get("SEED_PASSWORD", "password123")
PG = "psia-postgres-1"
TEST_ACCOUNT = re.compile(r"^(smoke_|feedtest_|mon|chk|eval|var)\d+$")

# name, username, bio, persona, secondary persona
USERS = [
    ("Ana Ferreira", "ana_ferreira", "Horror nights every Friday. The scarier the better.", "horror", "thriller"),
    ("João Silva", "joaosilva92", "Sci-fi nerd. Will defend Interstellar's ending forever.", "scifi", "action"),
    ("Mariana Costa", "mari_costa", "Rom-coms and a blanket, that's my weekend.", "romance", "comedy"),
    ("Pedro Santos", "pedro_santos", "Old Hollywood and film noir collector.", "classic", "crime"),
    ("Inês Oliveira", "ines_oli", "Mum of two, Pixar marathons are a lifestyle.", "family", "comedy"),
    ("Tiago Rodrigues", "tiagorod", "Gangster films, heist films, anything with a good twist.", "crime", "thriller"),
    ("Beatriz Martins", "bea_martins", "History teacher. I fact-check period dramas.", "history", "drama"),
    ("Rui Pereira", "rui_p", "Explosions, car chases, one-liners. No notes.", "action", "scifi"),
    ("Catarina Sousa", "catsousa", "Slow cinema and quiet character studies.", "drama", "romance"),
    ("Miguel Almeida", "miguel_almeida", "Westerns and war epics, like my grandfather.", "western", "war"),
    ("Sofia Gomes", "sofiagomes", "Musicals! Singing along is mandatory.", "music", "romance"),
    ("André Carvalho", "andre_c", "Whodunits and mind games.", "mystery", "crime"),
    ("Laura Ribeiro", "laura_rib", "Documentaries about anything and everything.", "documentary", "history"),
    ("Diogo Lopes", "diogo_lopes", "If it has dragons or wizards, I'm in.", "fantasy", "adventure"),
    ("Rita Fernandes", "rita_f", "Comedy is the best medicine.", "comedy", "family"),
    ("Hugo Marques", "hugomarques", "Sports movies make me cry, don't tell anyone.", "sport", "drama"),
    ("Carolina Pinto", "carolpinto", "Psychological thrillers that keep me up at night.", "thriller", "mystery"),
    ("Bruno Teixeira", "bruno_tx", "Adventure films and road trips.", "adventure", "action"),
    ("Joana Moreira", "joana_moreira", "Anime and animation of every kind.", "animation", "fantasy"),
    ("Ricardo Correia", "ricardo_c", "Biopics of people who changed the world.", "biography", "history"),
    ("Emma Wilson", "emmaw", "Exchange student. Watching all the classics I missed.", "classic", "romance"),
    ("Lucas Müller", "lucas_mueller", "Space, time travel, AI. Hard sci-fi only.", "scifi", "mystery"),
    ("Chloé Dubois", "chloe_dubois", "French girl who secretly loves Hollywood blockbusters.", "action", "romance"),
    ("Marco Rossi", "marcorossi", "Italian cinema fan and mafia film addict.", "crime", "drama"),
    ("Aisha Khan", "aisha_k", "Fantasy worlds and strong heroines.", "fantasy", "action"),
    ("Daniel Kim", "danielkim", "Thrillers, twists and unreliable narrators.", "thriller", "scifi"),
    ("Sara Nunes", "sara_nunes", "Feel-good movies only, life is stressful enough.", "comedy", "romance"),
    ("Gonçalo Mendes", "goncalo_m", "War films and military history.", "war", "history"),
    ("Leonor Cardoso", "leonor_c", "Indie dramas and festival picks.", "drama", "biography"),
    ("Francisco Rocha", "chico_rocha", "Horror-comedy is the perfect genre.", "horror", "comedy"),
    ("Marta Dias", "martadias", "Music biopics and concert films.", "music", "biography"),
    ("Nuno Barbosa", "nuno_barbosa", "Animated films are not just for kids.", "animation", "comedy"),
    ("Patrícia Vieira", "patricia_v", "Mystery novels reader, mystery movies watcher.", "mystery", "classic"),
    ("Filipe Monteiro", "filipe_mont", "Superheroes and space operas.", "action", "fantasy"),
    ("Helena Lima", "helena_lima", "Romantic dramas that break my heart.", "romance", "drama"),
    ("Vasco Azevedo", "vasco_az", "Westerns are the original action movies.", "western", "action"),
    ("Clara Matos", "claramatos", "Documentaries and true stories.", "documentary", "biography"),
    ("Tomás Freitas", "tomasfreitas", "Sci-fi horror is peak cinema.", "scifi", "horror"),
    ("Isabel Ramos", "isabel_ramos", "Family movie night every Sunday.", "family", "adventure"),
    ("Alexandre Faria", "alex_faria", "Sports dramas and underdog stories.", "sport", "biography"),
    ("Yara Santos", "yara_s", "Fantasy and fairy tales, forever.", "fantasy", "family"),
    ("Henrique Moura", "henrique_m", "Classic noir and detective stories.", "classic", "mystery"),
    ("Lara Cunha", "lara_cunha", "Thrillers with a political edge.", "thriller", "history"),
    ("Simão Batista", "simao_b", "Comedies from the 80s and 90s.", "comedy", "action"),
    ("Mafalda Reis", "mafalda_reis", "Coming-of-age dramas and indie gems.", "drama", "comedy"),
    ("Duarte Pires", "duarte_p", "Adventure classics and treasure hunts.", "adventure", "classic"),
    ("Olivia Brown", "olivia_b", "Horror buff from London.", "horror", "mystery"),
    ("Kenji Tanaka", "kenji_t", "Animation and samurai films.", "animation", "action"),
    ("Valentina García", "vale_garcia", "Telenovela heart, rom-com soul.", "romance", "music"),
]

# persona -> (genres liked, genres disliked, extra movie filters)
PERSONAS = {
    "horror": (["Horror"], ["Romance", "Musical", "Family"], {}),
    "scifi": (["Sci-Fi"], ["Romance", "Musical"], {}),
    "romance": (["Romance"], ["Horror", "War", "Western"], {}),
    "classic": (["Drama", "Film-Noir", "Romance"], ["Animation"], {"yearTo": 1975}),
    "family": (["Family", "Animation"], ["Horror", "Thriller", "Crime"], {}),
    "crime": (["Crime"], ["Musical", "Family", "Romance"], {}),
    "history": (["History"], ["Horror", "Animation"], {}),
    "action": (["Action"], ["Documentary", "Romance", "Musical"], {}),
    "drama": (["Drama"], ["Action", "Horror"], {}),
    "western": (["Western"], ["Musical", "Animation"], {}),
    "war": (["War"], ["Musical", "Romance", "Family"], {}),
    "music": (["Music", "Musical"], ["Horror", "War"], {}),
    "mystery": (["Mystery"], ["Family", "Musical"], {}),
    "documentary": (["Documentary"], ["Action", "Horror"], {}),
    "fantasy": (["Fantasy"], ["Documentary", "War"], {}),
    "comedy": (["Comedy"], ["Horror", "War", "Documentary"], {}),
    "sport": (["Sport"], ["Horror", "Fantasy"], {}),
    "thriller": (["Thriller"], ["Musical", "Family", "Animation"], {}),
    "adventure": (["Adventure"], ["Horror", "Documentary"], {}),
    "animation": (["Animation"], ["Horror", "War"], {}),
    "biography": (["Biography"], ["Horror", "Fantasy", "Sci-Fi"], {}),
}


def call(method, path, body=None, token=None, params=None):
    url = BASE + path
    if params:
        url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req) as res:
            text = res.read().decode()
            return res.status, json.loads(text) if text else None
    except urllib.error.HTTPError as e:
        return e.code, None


def psql(db, sql):
    out = subprocess.run(
        ["docker", "exec", "-i", PG, "sh", "-c", f'psql -U "$POSTGRES_USER" -d {db} -tA -v ON_ERROR_STOP=1'],
        input=sql, capture_output=True, text=True, check=True,
    )
    return out.stdout.strip()


def login(username):
    status, data = call("POST", "/auth/login", {"username": username, "password": PASSWORD})
    return data["token"] if status == 200 else None


def movies_for(genre, token, extra, pages=3):
    found = []
    for page in range(pages):
        _, data = call("GET", "/movies", token=token, params={"genre": genre, "size": 50, "page": page, **extra})
        found += (data or {}).get("content", [])
        if not data or page + 1 >= data.get("totalPages", 0):
            break
    return found


def main():
    rng = random.Random(37)

    # A reference account whose bcrypt hash (of PASSWORD) is copied onto renamed test accounts
    status, _ = call("POST", "/auth/register", {"username": USERS[0][1], "password": PASSWORD})
    ref_hash = psql("auth", f"SELECT password_hash FROM users WHERE username = '{USERS[0][1]}';")

    taken = set(psql("auth", "SELECT username FROM users;").splitlines())
    test_accounts = [u for u in taken if TEST_ACCOUNT.match(u)]
    pending = [u for u in USERS if u[1] not in taken or u[1] == USERS[0][1]]
    for old, user in zip(test_accounts, [u for u in pending if u[1] != USERS[0][1]]):
        new = user[1]
        psql("auth", f"UPDATE users SET username = '{new}', password_hash = '{ref_hash}' WHERE username = '{old}';")
        psql("profile", f"UPDATE profiles SET username = '{new}' WHERE username = '{old}';")
        print(f"renamed {old} -> {new}")

    for _, username, *_ in USERS:
        call("POST", "/auth/register", {"username": username, "password": PASSWORD})

    token = login(USERS[0][1])
    pool_cache = {}

    def pool(genre, extra):
        key = (genre, tuple(sorted(extra.items())))
        if key not in pool_cache:
            pool_cache[key] = movies_for(genre, token, extra)
        return pool_cache[key]

    for name, username, bio, persona, secondary in USERS:
        tok = login(username)
        if not tok:
            print(f"skip {username}: cannot log in")
            continue
        likes_g, hates_g, extra = PERSONAS[persona]
        sec_likes, _, sec_extra = PERSONAS[secondary]
        hates_g = [g for g in hates_g if g not in likes_g + sec_likes]

        main_pool = [m for g in likes_g for m in pool(g, extra)]
        sec_pool = [m for g in sec_likes for m in pool(g, sec_extra)]
        hated = {m["id"] for g in hates_g for m in pool(g, {})}

        # Mostly well-rated picks from the main taste, some from the secondary one
        main_pool = [m for m in main_pool if m["id"] not in hated]
        main_pool.sort(key=lambda m: (m.get("avgRating") or 0) + rng.uniform(-1.5, 1.5), reverse=True)
        sec_pool = [m for m in sec_pool if m["id"] not in hated]
        rng.shuffle(sec_pool)

        n_likes = rng.randint(18, 42)
        liked = []
        for m in main_pool[: int(n_likes * 0.7)] + sec_pool:
            if m["id"] not in liked:
                liked.append(m["id"])
            if len(liked) >= n_likes:
                break

        hated_pool = [m for g in hates_g for m in pool(g, {}) if m["id"] not in liked]
        rng.shuffle(hated_pool)
        disliked = list(dict.fromkeys(m["id"] for m in hated_pool))[: rng.randint(4, 15)]

        genres = list(dict.fromkeys(likes_g + sec_likes))
        genres += rng.sample([g for g in ["Drama", "Comedy", "Adventure", "Mystery", "Crime"] if g not in genres + hates_g], rng.randint(0, 2))

        status, _ = call("PUT", "/profiles/me", {
            "displayName": name,
            "bio": bio,
            "favoriteGenres": genres[:10],
            "favoriteMovieIds": liked[:50],
            "dislikedMovieIds": disliked[:50],
        }, token=tok)
        print(f"{username:16} {status} likes={len(liked):2} dislikes={len(disliked):2} genres={','.join(genres)}")


if __name__ == "__main__":
    main()
