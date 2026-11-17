package io.github.lovette.outtamny.SchemaCommand;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class StdOutTests extends SchemaCommandTestBase {

    private static final Path OUT_STDOUT = Paths.get("-");

    @BeforeEach
    void setMnyPath() {
        mnyPath = resourcePath("sunset-sample.mny");
    }

    @Test
    @DisplayName("schema --verbose --out -")
    public void testStdoutWithVerbose() throws IOException {
        executeSchemaCommand(1, OUT_STDOUT, "-vv");
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).isEqualTo("Error: Verbosity level must be quiet when output is to stdout.");
    }

    @Test
    @DisplayName("schema --out -")
    public void testStdout() throws IOException {
        executeSchemaCommand(OUT_STDOUT);

        String out = stdout();
        assertThat(out).isNotEmpty();

        long beginTransactionCount = out.lines()
                .filter(line -> line.startsWith("BEGIN TRANSACTION;"))
                .count();
        long commitTransactionCount =
                out.lines().filter(line -> line.startsWith("COMMIT;")).count();
        long createTableCount =
                out.lines().filter(line -> line.startsWith("CREATE TABLE ")).count();
        long indexCount =
                out.lines().filter(line -> line.startsWith("CREATE INDEX ")).count();
        long uniqueIndexCount = out.lines()
                .filter(line -> line.startsWith("CREATE UNIQUE INDEX "))
                .count();
        long insertCount =
                out.lines().filter(line -> line.startsWith("INSERT INTO ")).count();

        assertThat(beginTransactionCount).isEqualTo(3);
        assertThat(commitTransactionCount).isEqualTo(beginTransactionCount);
        assertThat(createTableCount).isEqualTo(83);
        assertThat(indexCount).isEqualTo(146);
        assertThat(uniqueIndexCount).isEqualTo(31);
        assertThat(insertCount).isEqualTo(35);

        assertThat(listOutDirContents()).isEmpty();
    }
}
