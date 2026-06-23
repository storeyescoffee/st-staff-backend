package io.storeyes.accesscontrol.tenant;

import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SchemaService {

    private final DataSource dataSource;

    private final Set<String> knownSchemas = ConcurrentHashMap.newKeySet();
    private static final Pattern VALID_SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    public void ensureSchema(String schema) {
        if ("public".equals(schema) || knownSchemas.contains(schema)) return;
        if (!VALID_SCHEMA.matcher(schema).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid store code: " + schema);
        }
        synchronized (schema.intern()) {
            if (knownSchemas.contains(schema)) return;
            createAndMigrate(schema);
            knownSchemas.add(schema);
        }
    }

    private void createAndMigrate(String schema) {
        boolean schemaExisted;
        try (Connection conn = dataSource.getConnection()) {
            try (ResultSet rs = conn.getMetaData().getSchemas(null, schema)) {
                schemaExisted = rs.next();
            }
            if (!schemaExisted) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE SCHEMA \"" + schema + "\"");
                    stmt.execute("SET search_path TO \"" + schema + "\"");
                    stmt.execute(loadInitSql());
                }
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to initialize schema: " + schema, e);
        }

        // Baseline at V11 so Flyway skips init migrations and only applies future ones (V12+)
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations("classpath:db/migration")
                .baselineVersion("11")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    private String loadInitSql() throws IOException {
        try (InputStream is = Objects.requireNonNull(
                getClass().getResourceAsStream("/db/tenant-init.sql"),
                "tenant-init.sql not found on classpath")) {
            return new String(is.readAllBytes());
        }
    }
}
