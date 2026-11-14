package io.github.lovette.outtamny;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SharedUtils {
    /**
     * Verbosity levels for controlling output detail.
     * ERROR: Only error messages.
     * WARN: Warnings and errors.
     * INFO: Informational messages, warnings, and errors.
     * DEBUG: Debug messages, informational messages, warnings, and errors.
     * TRACE: All messages including trace-level details.
     */
    public enum Verbosity {
        ERROR(0),
        WARN(1),
        INFO(2),
        DEBUG(3),
        TRACE(4);

        private final int verbosityLevel;
        private static final Map<Integer, Verbosity> BY_LEVEL = Arrays.stream(values())
                .collect(Collectors.toMap(Verbosity::getVerbosityLevel, Function.identity()));

        Verbosity(int verbosityLevel) {
            this.verbosityLevel = verbosityLevel;
        }

        /**
         * Returns the Verbosity level corresponding to the given integer level.
         * If the level is not recognized, it defaults to TRACE.
         *
         * @param level the integer verbosity level
         * @return the corresponding Verbosity enum value
         */
        public static Verbosity fromLevel(int level) {
            return BY_LEVEL.getOrDefault(level, TRACE);
        }

        /**
         * Returns the integer verbosity level associated with this Verbosity.
         *
         * @return the integer verbosity level
         */
        public int getVerbosityLevel() {
            return verbosityLevel;
        }

        /**
         * Checks if this verbosity level is the given verbosity level or higher.
         *
         * @param other the other verbosity level to compare against
         * @return true if this verbosity level is greater than or equal to the other,
         *         false otherwise
         */
        public boolean isAtLeast(Verbosity other) {
            return this.verbosityLevel >= other.verbosityLevel;
        }

        /**
         * Checks if this verbosity level is strictly higher than the given verbosity
         * level.
         *
         * @param other the other verbosity level to compare against
         * @return true if this verbosity level is greater than the other, false
         *         otherwise
         */
        public boolean isMoreThan(Verbosity other) {
            return this.verbosityLevel > other.verbosityLevel;
        }
    }

    /**
     * Returns a safe file name for the given name by replacing any characters
     * that are not alphanumeric, underscore, dot, or hyphen with an underscore.
     *
     * @param name the name to convert to a safe file name
     * @return the safe file name
     */
    static public String safeFileName(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    /**
     * Returns the stem of the file name (i.e., the file name without its
     * extension).
     *
     * @param p the path to the file
     * @return the stem of the file name (without its extension)
     */
    static public String getPathStem(Path p) {
        String fileName = p.getFileName().toString();
        if (fileName.contains("."))
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        return fileName;
    }

    /**
     * Returns the suffix (extension) of the file name.
     *
     * @param p the path to the file
     * @return the suffix (extension) or an empty string if none exists
     */
    static public String getPathSuffix(Path p) {
        String fileName = p.getFileName().toString();
        if (fileName.contains("."))
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        return "";
    }

    /**
     * Joins the elements of the given array with commas and wraps them in
     * parentheses.
     *
     * @param arr the array of strings to join
     * @return the joined string wrapped in parentheses
     */
    static public String joinWithParens(String[] arr) {
        StringJoiner sj = new StringJoiner(", ", "(", ")");
        for (String s : arr) {
            sj.add(s);
        }
        return sj.toString();
    }
}
