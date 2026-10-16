package pt.psia.reco.config;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads src/main/resources/data/movies.csv (the enriched dataset) into the
 * movies table the first time the table is empty. Safe to run on every
 * startup - it's a no-op once the table has rows.
 */
@Component
public class MovieDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MovieDataLoader.class);
    private static final String CSV_PATH = "data/movies.csv";
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructs a new MovieDataLoader with the given JdbcTemplate.
     *
     * @param jdbcTemplate the JdbcTemplate to use for database operations
     */
    public MovieDataLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Loads the movie catalog from the CSV file into the database if the
     * movies table is empty. This method is called on application startup.
     */
    @Override
    public void run(String... args) {
        // Check if the movies table already has rows
        Long existing = jdbcTemplate.queryForObject("SELECT count(*) FROM movies", Long.class);
        if (existing != null && existing > 0) {
            log.info("movies table already has {} rows, skipping CSV load", existing);
            return;
        }

        // Load the CSV file from the classpath
        ClassPathResource resource = new ClassPathResource(CSV_PATH);
        if (!resource.exists()) {
            log.warn("No {} found on the classpath — movie catalog will be empty.", CSV_PATH);
            return;
        }

        // Read and parse the CSV file, inserting rows into the database in batches
        log.info("Loading movie catalog from {}...", CSV_PATH);
        int total = 0;
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            for (CSVRecord record : parser) {
                // Enriched CSV uses "|" as genre separator; DB convention is ";"
                String genres = record.get("genres");
                if (genres != null) genres = genres.replace("|", ";");

                batch.add(new Object[]{
                        record.get("tconst"),
                        record.get("title"),
                        parseIntOrNull(record.get("year")),
                        genres,
                        parseDecimalOrNull(record.get("rating")),
                        parseIntOrNull(record.get("votes")),
                        nullIfEmpty(record.get("overview")),
                        nullIfEmpty(record.get("poster_url")),
                        nullIfEmpty(record.get("directors")),
                        nullIfEmpty(record.get("stars")),
                        parseIntOrNull(record.get("runtime_minutes")),
                });
                if (batch.size() == BATCH_SIZE) {
                    insertBatch(batch);
                    total += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                insertBatch(batch);
                total += batch.size();
            }
            log.info("Loaded {} movies into the catalog", total);
        } catch (Exception e) {
            log.error("Failed to load movie catalog from {}", CSV_PATH, e);
        }
    }

    /** Inserts a batch of movie data into the database. */
    private void insertBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO movies (imdb_id, title, start_year, genres, avg_rating, num_votes, " +
                        "overview, poster_url, directors, stars, runtime_minutes) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (imdb_id) DO NOTHING",
                batch
        );
    }

    /** Parses a string as an Integer, returning null if the string is null, blank, or not a valid integer. */
    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /** Parses a string as a BigDecimal, returning null if the string is null, blank, or not a valid decimal. */
    private BigDecimal parseDecimalOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }

    /** Returns null if the string is null or blank, otherwise returns the string itself. */
    private String nullIfEmpty(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
