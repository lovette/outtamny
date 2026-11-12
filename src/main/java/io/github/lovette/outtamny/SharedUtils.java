package io.github.lovette.outtamny;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.spannm.jackcess.DataType;

public class SharedUtils {
    // Not the best way to do this but we don't have access to SQLite
    // `sqlite3_keyword_*` functions.
    // https://www.sqlite.org/lang_keywords.html
    private static final Set<String> SQLITE_KEYWORDS = new HashSet<>(Arrays.asList(
            "ABORT", "ACTION", "ADD", "AFTER", "ALL", "ALTER", "ALWAYS", "ANALYZE", "AND", "AS", "ASC", "ATTACH",
            "AUTOINCREMENT", "BEFORE", "BEGIN", "BETWEEN", "BY", "CASCADE", "CASE", "CAST", "CHECK", "COLLATE",
            "COLUMN", "COMMIT", "CONFLICT", "CONSTRAINT", "CREATE", "CROSS", "CURRENT", "CURRENT_DATE", "CURRENT_TIME",
            "CURRENT_TIMESTAMP", "DATABASE", "DEFAULT", "DEFERRABLE", "DEFERRED", "DELETE", "DESC", "DETACH",
            "DISTINCT", "DO", "DROP", "EACH", "ELSE", "END", "ESCAPE", "EXCEPT", "EXCLUDE", "EXCLUSIVE", "EXISTS",
            "EXPLAIN", "FAIL", "FILTER", "FIRST", "FOLLOWING", "FOR", "FOREIGN", "FROM", "FULL", "GENERATED", "GLOB",
            "GROUP", "GROUPS", "HAVING", "IF", "IGNORE", "IMMEDIATE", "IN", "INDEX", "INDEXED", "INITIALLY", "INNER",
            "INSERT", "INSTEAD", "INTERSECT", "INTO", "IS", "ISNULL", "JOIN", "KEY", "LAST", "LEFT", "LIKE", "LIMIT",
            "MATCH", "MATERIALIZED", "NATURAL", "NO", "NOT", "NOTHING", "NOTNULL", "NULL", "NULLS", "OF", "OFFSET",
            "ON", "OR", "ORDER", "OTHERS", "OUTER", "OVER", "PARTITION", "PLAN", "PRAGMA", "PRECEDING", "PRIMARY",
            "QUERY", "RAISE", "RANGE", "RECURSIVE", "REFERENCES", "REGEXP", "REINDEX", "RELEASE", "RENAME", "REPLACE",
            "RESTRICT", "RETURNING", "RIGHT", "ROLLBACK", "ROW", "ROWS", "SAVEPOINT", "SELECT", "SET", "TABLE", "TEMP",
            "TEMPORARY", "THEN", "TIES", "TO", "TRANSACTION", "TRIGGER", "UNBOUNDED", "UNION", "UNIQUE", "UPDATE",
            "USING", "VACUUM", "VALUES", "VIEW", "VIRTUAL", "WHEN", "WHERE", "WINDOW", "WITH", "WITHOUT"));

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
     * Returns a SQL identifier quoted with double quotes if necessary.
     *
     * @param name a SQL identifier (e.g. table or column name)
     * @return the new identifier
     */
    static public String sqlNameQ(String name) {
        if (name.equals(sqlNameU(name)) && !SQLITE_KEYWORDS.contains(name.toUpperCase())) {
            return name;
        }
        return "\"" + name.replace("\"", "") + "\"";
    }

    /**
     * Returns a SQL identifier stripped of all non-alphanumeric or underscore
     * characters, replacing them with an underscore.
     *
     * @param name a SQL identifier (e.g. table or column name)
     * @return the new identifier
     */
    static public String sqlNameU(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    /**
     * Returns the SQLite data type corresponding to the given Jackcess DataType.
     *
     * @param t the Jackcess DataType to convert
     * @return the SQLite data type as a string
     */
    static public String sqliteDataType(DataType t) {
        switch (t) {
            case BOOLEAN:
            case BYTE:
            case INT:
            case LONG:
            case BIG_INT:
                return "INTEGER";
            case FLOAT:
            case DOUBLE:
                return "REAL";
            case MONEY:
            case NUMERIC:
                return "NUMERIC";
            case BINARY:
            case OLE:
            case BIG_BINARY:
            case UNSUPPORTED_FIXEDLEN:
            case UNSUPPORTED_VARLEN:
                return "BLOB";
            default: // TEXT, MEMO, GUID, dates, complex types
                return "TEXT";
        }
    }

    /**
     * Converts a Java object to its SQL value such as "null > NULL" or "true > 1".
     *
     * @param obj the value to convert
     * @return the SQL value
     */
    static public String sqlValue(Object obj) {
        return switch (obj) {
            case null -> "NULL";
            case Boolean b -> b ? "1" : "0";
            case Byte _,Short _,Integer _,Long _,java.math.BigInteger _ -> obj.toString();
            case Float f -> (Float.isNaN(f) || Float.isInfinite(f)) ? "NULL" : f.toString();
            case Double d -> (Double.isNaN(d) || Double.isInfinite(d)) ? "NULL" : d.toString();
            case java.math.BigDecimal bd -> "'" + bd.toPlainString() + "'"; // let SQlite convert to float itself
            case byte[] bytes -> "X'" + java.util.HexFormat.of().formatHex(bytes) + "'";
            default -> "'" + obj.toString().replace("'", "''") + "'";
        };
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
     * Returns the output path for a Money file based on the input file path, output
     * directory, and optional file name.
     *
     * @param inFilePath the path to the input Money file
     * @param outDirPath the base output directory
     * @param fileName   the optional file name for the output file; if null,
     *                   returns the output directory path
     * @return the output path for the Money file
     */
    static public Path getMnyFileOutPath(Path inFilePath, Path outDirPath, String fileName) {
        // Output directory based on Money file name.
        outDirPath = Paths.get(outDirPath.toString(), safeFileName(getPathStem(inFilePath)));
        if (fileName == null) {
            return outDirPath;
        }

        return Paths.get(outDirPath.toString(), fileName);
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
