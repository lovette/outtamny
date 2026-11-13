package io.github.lovette.outtamny.SchemaCommand;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

public class SqliteTests extends SchemaCommandTestBase {

    private final static Path OUT_STDOUT = Paths.get("-");
    private final static String SQL_COUNT_TABLES = "SELECT COUNT(*) AS c FROM sqlite_schema WHERE type = 'table' AND name NOT LIKE 'sqlite_%'";

    // Create a stream of '--schema-out' and '--data-out' combinations for each
    // 'SchemaCommand.OutputScheme' except for where schema is 'none' (since we
    // can't INSERT INTO tables that haven't been created.)
    static Stream<Arguments> outputCombinations() {
        var values = List.of("combined", "split", "table_combined", "table", "none");
        return values.stream()
                .flatMap(schema -> values.stream()
                        .filter(data -> !(schema.equals("none")))
                        .map(data -> Arguments.of(schema, data)));
    }

    Path dbFile;
    String jdbcUrl;

    @BeforeEach
    void setMnyPath() {
        mnyPath = resourcePath("sunset-sample.mny");
        dbFile = tempOutDir.resolve("test.db");
        jdbcUrl = "jdbc:sqlite:" + dbFile;
    }

    @Test
    void testStdOut() throws IOException, InterruptedException, SQLException {

        executeSchemaCommand(OUT_STDOUT);

        String out = stdout();
        assertThat(out).isNotEmpty();

        Process process = new ProcessBuilder(
                "sqlite3",
                "-bail", // abort on error
                "--",
                dbFile.toString())
                .redirectErrorStream(true)
                .directory(tempOutDir.toFile())
                .start();

        // Write the SQL commands to the sqlite3 process's stdin.
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        process.getOutputStream(), StandardCharsets.UTF_8))) {

            writer.write(out);
            writer.flush();
        }

        // Read stdout and wait for process to exit.
        String processOut = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).withFailMessage(processOut).isZero();
        assertThat(processOut).startsWith("ok");

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(SQL_COUNT_TABLES)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("c")).isEqualTo(83);
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS c FROM TRN")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("c")).isEqualTo(3);
            }
        }
    }

    @ParameterizedTest(name = "schema={0}, data={1}")
    @MethodSource("outputCombinations")
    void testOutputCombination(String schema, String data) throws IOException, InterruptedException, SQLException {
        String[] args = { "-vv", "--schema-out", schema, "--data-out", data };
        String sqlFileName = "out.sql"; // override default name
        Path sqlFilePath = tempOutDir.resolve(sqlFileName);

        // System.out.println(String.format("schema='%s', data='%s':", schema, data));

        executeSchemaCommand(sqlFilePath, args);

        List<String> outDirSqlFiles = listOutDirFiles("*.sql");
        assertThat(outDirSqlFiles).contains(sqlFileName);

        assertThat(sqlFilePath).isRegularFile();

        executeSqliteWithFile(sqlFilePath);

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
                Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery(SQL_COUNT_TABLES)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("c")).isEqualTo(83);
            }
        }
    }

    /**
     * Executes the given SQL file using sqlite3 and verifies that it runs
     * successfully.
     *
     * @param sqlFilePath the path to the SQL file to execute
     * @throws IOException
     * @throws InterruptedException
     */
    private void executeSqliteWithFile(Path sqlFilePath) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "sqlite3",
                "-bail", // abort on error
                "--",
                dbFile.toString(),
                String.format(".read '%s'", sqlFilePath))
                .redirectErrorStream(true)
                .directory(sqlFilePath.getParent().toFile())
                .start();

        // Read stdout and wait for process to exit.
        String processOut = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).withFailMessage(processOut).isZero();
        assertThat(processOut).startsWith("ok");
    }
}
