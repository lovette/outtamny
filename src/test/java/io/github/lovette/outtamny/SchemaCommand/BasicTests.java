package io.github.lovette.outtamny.SchemaCommand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class BasicTests extends SchemaCommandTestBase {
    @Test
    @DisplayName("schema no args")
    public void testNoArgs() {
        executeCommand(2, "schema");
        assertThat(stdout()).isEmpty();
        assertThat(stderr())
                .startsWith("Missing required parameter: 'MNYFILE'")
                .containsIgnoringNewLines("Usage: outtamny schema");
    }

    @Test
    @DisplayName("schema no MNYFILE")
    public void testNoMnyFile() {
        executeCommand(2, "schema", "--out", tempOutDir.toString());
        assertThat(stdout()).isEmpty();
        assertThat(stderr())
                .startsWith("Missing required parameter: 'MNYFILE'")
                .containsIgnoringNewLines("Usage: outtamny schema");
    }

    @Test
    @DisplayName("schema --help")
    public void testHelp() {
        executeCommand("schema", "--help");
        assertThat(stderr()).isEmpty();
        assertThat(stdout())
                .startsWith("Usage: outtamny schema")
                .containsIgnoringNewLines("--help")
                .containsIgnoringNewLines("--password")
                .containsIgnoringNewLines("--out");
    }
}
