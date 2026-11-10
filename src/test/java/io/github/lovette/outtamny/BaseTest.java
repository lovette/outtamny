package io.github.lovette.outtamny;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

import picocli.CommandLine;

/**
 * Base class for unit tests that provides common setup and utility methods.
 */
public abstract class BaseTest {
    protected CommandLine cmd;
    protected int exitCode;
    private StringWriter outWriter;
    private StringWriter errWriter;

    @BeforeEach
    void initTest() {
        cmd = createCommandLine();
    }

    /**
     * Creates and configures a new CommandLine instance for testing.
     *
     * @return the configured CommandLine instance.
     */
    protected CommandLine createCommandLine() {
        CommandLine cmd = App.createCommandLine();

        outWriter = new StringWriter();
        cmd.setOut(new PrintWriter(outWriter));

        errWriter = new StringWriter();
        cmd.setErr(new PrintWriter(errWriter));

        exitCode = -1;

        return cmd;
    }

    /**
     * Executes the command with the specified arguments and asserts that the exit
     * code is zero and stderr is empty.
     *
     * @param args the command-line arguments to pass to the command.
     */
    protected void executeCommand(String... args) {
        exitCode = cmd.execute(args);
        assertThat(exitCode).isZero();
        assertThat(stderr()).isEmpty();
    }

    /**
     * Executes the command with the specified arguments and asserts that the exit
     * code matches the expected exit code.
     *
     * @param expectedExitCode the expected exit code of the command.
     * @param args             the command-line arguments to pass to the command.
     */
    protected void executeCommand(int expectedExitCode, String... args) {
        exitCode = cmd.execute(args);
        assertThat(exitCode).isEqualTo(expectedExitCode);
    }

    /**
     * Returns the standard output captured during the command execution.
     *
     * @return the captured standard output as a string.
     */
    protected String stdout() {
        return outWriter.toString();
    }

    /**
     * Returns the standard error output captured during the command execution.
     *
     * @return the captured standard error output as a string.
     */
    protected String stderr() {
        return errWriter.toString();
    }

    /**
     * Returns the path to a test resource file relative to the test resources
     * directory.
     *
     * @param relativePath the relative path to the test resource file.
     * @return the Path to the test resource file.
     */
    protected Path resourcePath(String relativePath) {
        Path resourcesDirPath = Path.of("src", "test", "resources").toAbsolutePath();
        Path resourcePath = resourcesDirPath.resolve(relativePath);
        if (!Files.exists(resourcePath)) {
            throw new IllegalArgumentException("Test resource not found: '" + resourcePath.getFileName() + "'");
        }
        return resourcePath;
    }
}
