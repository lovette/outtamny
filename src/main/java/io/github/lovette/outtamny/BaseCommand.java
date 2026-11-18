package io.github.lovette.outtamny;

import io.github.lovette.outtamny.SharedUtils.Verbosity;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

public abstract class BaseCommand {

    // Picocli command context injected after instantiating command class and right
    // before invoking run() or call().
    @Spec
    @Nullable
    protected CommandSpec spec;

    @Nullable
    private Verbosity cachedVerbosity;

    public abstract static class LowercaseEnumCandidates<E extends Enum<E>> implements Iterable<String> {
        private final Class<E> enumClass;

        protected LowercaseEnumCandidates(Class<E> enumClass) {
            this.enumClass = enumClass;
        }

        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(enumClass.getEnumConstants())
                    .map(e -> e.name().toLowerCase(Locale.ROOT))
                    .iterator();
        }
    }

    /**
     * Gets the standard output PrintWriter. Use this instead of 'System.out' to respect Picocli's
     * output redirection.
     *
     * @return the PrintWriter for standard output.
     */
    protected PrintWriter stdOut() {
        Objects.requireNonNull(spec);
        return spec.commandLine().getOut();
    }

    /**
     * Gets the standard error PrintWriter. Use this instead of 'System.err' to respect Picocli's
     * output redirection.
     *
     * @return the PrintWriter for standard error output.
     */
    protected PrintWriter stdErr() {
        Objects.requireNonNull(spec);
        return spec.commandLine().getErr();
    }

    /**
     * Gets the verbosity level based on the number of times the -v/--verbose flag is passed.
     *
     * @return the verbosity level as an integer.
     */
    protected Verbosity getVerbosityLevel() {
        if (cachedVerbosity == null) {
            Objects.requireNonNull(spec);
            boolean[] verbosity = spec.findOption("verbose").getValue();
            cachedVerbosity = Verbosity.fromLevel(verbosity.length);
        }
        return cachedVerbosity;
    }
}
