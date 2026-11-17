package io.github.lovette.outtamny.TablesCommand;

import io.github.lovette.outtamny.CommandTestBase;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;

public class TablesCommandTestBase extends CommandTestBase {
    @BeforeEach
    void setMnyPath() {
        mnyPath = resourcePath("sunset-sample.mny");
    }

    /**
     * Returns the lines of 'stdout' as a set of table names.
     *
     * @return a set each line of 'stdout'
     */
    protected Set<String> stdoutTableNames() {
        Set<String> tablesOut = new java.util.LinkedHashSet<>();
        for (String line : stdout().split("\n")) {
            tablesOut.add(line);
        }
        return tablesOut;
    }

    /**
     * Executes the schema command with the default temporary output directory and the specified
     * arguments. Asserts that the exit code is zero and 'stderr' is empty.
     *
     * @param args additional command arguments
     */
    protected void executeTablesCommand(String... args) {
        executeTablesCommand(0, args);
    }

    /**
     * Executes the tables command with the specified expected exit code and arguments. 'stdout' and
     * 'stderr' are not checked.
     *
     * @param expectedExitCode the expected exit code of the command
     * @param args additional command arguments
     */
    protected void executeTablesCommand(int expectedExitCode, String... args) {
        super.executeCommand(expectedExitCode, buildCommandArgs(args));
    }

    /**
     * Builds the command-line arguments for the 'tables' command, including the MNYFILE path.
     *
     * @param args additional command arguments
     * @return the complete array of command-line arguments
     */
    protected String[] buildCommandArgs(String... args) {
        return super.buildCommandArgs("tables", args);
    }
}
