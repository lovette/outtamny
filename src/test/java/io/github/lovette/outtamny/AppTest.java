package io.github.lovette.outtamny;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for App.
 */
public class AppTest extends BaseTest {
    @Test
    public void testNoArgs() {
        assertThat(cmd.execute()).isZero();
        assertThat(stdout()).isEmpty();
        assertThat(stderr()).startsWith("Please specify a subcommand. Use --help for choices.");
    }

    @Test
    public void testUnknownSubcommand() {
        executeCommand(2, "foo");
        assertThat(stderr()).containsIgnoringNewLines("Error: Unknown command 'foo'.")
                .containsIgnoringNewLines("Possible commands are: [schema]")
                .containsIgnoringNewLines("Try 'outtamny --help' for more information.");
    }

    @Test
    public void testHelp() {
        executeCommand("--help");
        assertThat(stdout()).startsWith("Usage: outtamny")
                .containsIgnoringNewLines("--help")
                .containsIgnoringNewLines("Commands:")
                .containsIgnoringNewLines("schema");
    }

    @Test
    public void testVersion() {
        executeCommand("--version");
        assertThat(stdout()).hasLineCount(1).hasSizeBetween(5, 10);
    }
}
