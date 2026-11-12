package io.github.lovette.outtamny;

import java.io.PrintWriter;

import io.github.lovette.outtamny.SharedUtils.Verbosity;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

public abstract class BaseCommand {

    // Picocli command context injected after instantiating command class and right
    // before invoking run() or call().
    @Spec
    protected CommandSpec spec;

    private Verbosity cachedVerbosity;

    /**
     * Gets the standard output PrintWriter.
     * Use this instead of 'System.out' to respect Picocli's output redirection.
     *
     * @return the PrintWriter for standard output.
     */
    protected PrintWriter stdOut() {
        return spec.commandLine().getOut();
    }

    /**
     * Gets the standard error PrintWriter.
     * Use this instead of 'System.err' to respect Picocli's output redirection.
     *
     * @return the PrintWriter for standard error output.
     */
    protected PrintWriter stdErr() {
        return spec.commandLine().getErr();
    }

    /**
     * Gets the verbosity level based on the number of times the -v/--verbose flag
     * is passed.
     *
     * @return the verbosity level as an integer.
     */
    protected Verbosity getVerbosityLevel() {
        if (cachedVerbosity == null) {
            boolean[] verbosity = spec.findOption("verbose").getValue();
            cachedVerbosity = Verbosity.fromLevel(verbosity.length);
        }
        return cachedVerbosity;
    }
}
