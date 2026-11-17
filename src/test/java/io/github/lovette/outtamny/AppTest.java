package io.github.lovette.outtamny;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** Unit test for App. */
public class AppTest extends AppTestBase {
    @Test
    @DisplayName("outtamny no args")
    void testNoArgs() {
        CommandLine cmd = createCommandLine();
        assertThat(cmd.execute()).isZero();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith("Please specify a subcommand. Use --help for choices.");
    }

    @Test
    @DisplayName("outtamny unknown command")
    void testUnknownSubcommand() {
        executeCommand(2, "foo");
        assertThat(stderr())
                .containsIgnoringNewLines("Error: Unknown command 'foo'.")
                .containsIgnoringNewLines("Possible commands are: [")
                .containsIgnoringNewLines("Try 'outtamny --help' for more information.");
    }

    @Test
    @DisplayName("outtamny --help")
    void testHelp() {
        executeCommand("--help");
        assertThat(stdout())
                .startsWith("Usage: outtamny")
                .containsIgnoringNewLines("--help")
                .containsIgnoringNewLines("Commands:")
                .containsIgnoringNewLines("schema");
    }

    @Test
    @DisplayName("outtamny --version")
    void testVersion() {
        executeCommand("--version");
        assertThat(stdout()).hasLineCount(1).hasSizeBetween("0.0.0".length(), "0.0.0-SNAPSHOT".length());
    }
}
