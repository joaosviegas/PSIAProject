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
 * Loads src/main/resources/data/movies.csv into the movies table the first
 * time the table is empty. See dataset/README.md for how to generate that
 * CSV from the IMDb exports. Safe to run on every startup — it's a no-op
 * once the table has rows.
 */
@Component
public class MovieDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MovieDataLoader.class);
    private static final String CSV_PATH = "data/movies.csv";
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    public MovieDataLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        Long existing = jdbcTemplate.queryForObject("SELECT count(*) FROM movies", Long.class);
        if (existing != null && existing > 0) {
            log.info("movies table already has {} rows, skipping CSV load", existing);
            return;
        }

        ClassPathResource resource = new ClassPathResource(CSV_PATH);
        if (!resource.exists()) {
            log.warn("No {} found on the classpath — movie catalog will be empty. " +
                    "See dataset/README.md to generate it.", CSV_PATH);
            return;
        }

        log.info("Loading movie catalog from {}...", CSV_PATH);
        int total = 0;
        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {

            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);
            for (CSVRecord record : parser) {
                batch.add(new Object[]{
                        record.get("imdb_id"),
                        record.get("title"),
                        parseIntOrNull(record.get("start_year")),
                        record.get("genres"),
                        parseDecimalOrNull(record.get("avg_rating")),
                        parseIntOrNull(record.get("num_votes")),
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

    private void insertBatch(List<Object[]> batch) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO movies (imdb_id, title, start_year, genres, avg_rating, num_votes) " +
                        "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (imdb_id) DO NOTHING",
                batch
        );
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parseDecimalOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
