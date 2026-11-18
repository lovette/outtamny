package io.github.lovette.outtamny;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Base class for command-related tests that provides common setup and utility methods. */
public class CommandTestBase extends AppTestBase {
    // Path to test MNYFILE. Typically initialzed in a 'BeforeEach' function.
    @Nullable
    protected Path mnyPath;

    /**
     * Builds the complete array of command-line arguments for the specified command, with the
     * command name as the first argument and the MNYFILE path at the end.
     *
     * @param command subcommand name
     * @param args additional command arguments
     * @return the complete array of command-line arguments
     */
    protected String[] buildCommandArgs(String command, String... args) {
        if (mnyPath == null) {
            throw new IllegalStateException("'mnyPath' must be set before calling 'buildCommandArgs'.");
        }

        List<String> newArgs = new ArrayList<>();

        // Command name goes first
        newArgs.add(command);

        if (args != null) {
            Collections.addAll(newArgs, args);
        }

        // MNYFILE goes last
        newArgs.add(mnyPath.toString());

        return newArgs.toArray(String[]::new);
    }
}
