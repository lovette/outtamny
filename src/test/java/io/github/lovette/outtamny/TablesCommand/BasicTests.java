package io.github.lovette.outtamny.TablesCommand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicTests extends TablesCommandTestBase {
    @Test
    @DisplayName("tables no args")
    public void testNoArgs() {
        executeCommand(2, "tables");
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith(
                "Missing required parameter: 'MNYFILE'")
                .containsIgnoringNewLines("Usage: outtamny tables");
    }

    @Test
    @DisplayName("tables --help")
    public void testHelp() {
        executeCommand("tables", "--help");
        assertThat(stderr()).isEmpty();
        assertThat(stdout()).startsWith("Usage: outtamny tables")
                .containsIgnoringNewLines("--password:env")
                .containsIgnoringNewLines("--help");
    }
}
