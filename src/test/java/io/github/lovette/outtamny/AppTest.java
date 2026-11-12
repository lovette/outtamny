package io.github.lovette.outtamny;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import picocli.CommandLine;

/**
 * Unit test for App.
 */
public class AppTest extends AppTestBase {
    @Test
    @DisplayName("outtamny no args")
    public void testNoArgs() {
        CommandLine cmd = createCommandLine();
        assertThat(cmd.execute()).isZero();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith("Please specify a subcommand. Use --help for choices.");
    }

    @Test
    @DisplayName("outtamny unknown command")
    public void testUnknownSubcommand() {
        executeCommand(2, "foo");
        assertThat(stderr()).containsIgnoringNewLines("Error: Unknown command 'foo'.")
                .containsIgnoringNewLines("Possible commands are: [schema]")
                .containsIgnoringNewLines("Try 'outtamny --help' for more information.");
    }

    @Test
    @DisplayName("outtamny --help")
    public void testHelp() {
        executeCommand("--help");
        assertThat(stdout()).startsWith("Usage: outtamny")
                .containsIgnoringNewLines("--help")
                .containsIgnoringNewLines("Commands:")
                .containsIgnoringNewLines("schema");
    }

    @Test
    @DisplayName("outtamny --version")
    public void testVersion() {
        executeCommand("--version");
        assertThat(stdout()).hasLineCount(1).hasSizeBetween(5, 10);
    }
}
