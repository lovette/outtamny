package io.github.lovette.outtamny.SchemaCommand;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class FilterTablesTests extends SchemaCommandTestBase {

    @BeforeEach
    void setMnyPath() {
        mnyPath = resourcePath("sunset-sample.mny");
    }

    @Test
    @DisplayName("schema --table")
    public void testTableNoArg() throws IOException {
        executeSchemaCommand(2, "--table");
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith("Expected parameter for option '--table' but found");
    }

    @Test
    @DisplayName("schema --table NOSUCHTABLE")
    public void testNoSuchTable() throws IOException {
        executeSchemaCommand(1, "--table", "NOSUCHTABLE");
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith("Error: ").endsWith(": No such table.");
    }

    @Test
    @DisplayName("schema --table ACCT")
    public void test1Table() throws IOException {
        executeSchemaCommand("--schema-out", "table_combined", "--data-out", "none", "--table", "ACCT");
        List<String> outSubDirContents = listOutDirFiles(Paths.get("sunset-sample/"));

        assertThat(stdout()).isEmpty();
        assertThat(outSubDirContents).containsOnly("ACCT.sql");
    }

    @Test
    @DisplayName("schema --table ACCT --table TRN")
    public void test2Table() throws IOException {
        executeSchemaCommand("--schema-out", "table_combined", "--data-out", "none", "-t", "ACCT", "-t", "TRN");
        List<String> outSubDirContents = listOutDirFiles(Paths.get("sunset-sample/"));

        assertThat(stdout()).isEmpty();
        assertThat(outSubDirContents).containsOnly("ACCT.sql", "TRN.sql");
    }

    @Test
    @DisplayName("schema --table ACCT --table TRN --table ACCT")
    public void test3Table() throws IOException {
        executeSchemaCommand(
                "--schema-out",
                "table_combined",
                "--data-out",
                "none",
                "--table",
                "ACCT",
                "--table",
                "TRN",
                "--table",
                "ACCT");
        List<String> outSubDirContents = listOutDirFiles(Paths.get("sunset-sample/"));

        assertThat(stdout()).isEmpty();
        assertThat(outSubDirContents).containsOnly("ACCT.sql", "TRN.sql");
    }

    @Test
    @DisplayName("schema --table \"Tax Rate Custom Pool\"")
    public void testTableWithQuotes() throws IOException {
        executeSchemaCommand(
                "--schema-out", "table_combined", "--data-out", "none", "--table", "\"Tax Rate Custom Pool\"");
        List<String> outSubDirContents = listOutDirFiles(Paths.get("sunset-sample/"));

        assertThat(stdout()).isEmpty();
        assertThat(outSubDirContents).containsOnly("Tax Rate Custom Pool.sql");
    }
}
