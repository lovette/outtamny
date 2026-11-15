package io.github.lovette.outtamny;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.spannm.jackcess.Column;
import io.github.spannm.jackcess.DataType;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.DatabaseBuilder;
import io.github.spannm.jackcess.encrypt.CryptCodecProvider;
import io.github.spannm.jackcess.encrypt.InvalidCredentialsException;
import io.github.spannm.jackcess.util.ErrorHandler.Location;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

public class BaseSubcommand extends BaseCommand {

    @ParentCommand
    protected App parent; // Parent command reference

    @Option(names = { "-h", "--help" }, usageHelp = true, description = "Display this help and exit", order = 20)
    private boolean help;

    /**
     * Resolves the Money database password from various sources (prompt, inline,
     * environment variable, or file) or empty string if none are provided.
     *
     * @return The resolved Money database password.
     * @throws FileNotFoundException
     * @throws IOException
     */
    protected String resolveMnyPassword() throws FileNotFoundException, IOException {
        if (parent.mnyPasswordPrompt != null) {
            return parent.mnyPasswordPrompt;
        }

        if (parent.mnyPasswordInline != null) {
            return parent.mnyPasswordInline;
        }

        if (parent.mnyPasswordEnv != null) {
            String mnyPassword = System.getenv(parent.mnyPasswordEnv);
            if (mnyPassword == null) {
                String errmsg = "Error: Environment variable '" + parent.mnyPasswordEnv + "' is not set.";
                throw new UserFacingException(errmsg);
            }

            return mnyPassword;
        }

        if (parent.mnyPasswordFile != null) {
            parent.mnyPasswordFile = parent.mnyPasswordFile.toAbsolutePath().normalize();
            if (!Files.isRegularFile(parent.mnyPasswordFile)) {
                throw new FileNotFoundException("Error: '" + parent.mnyPasswordFile + "': No such file.");
            }

            return Files.readString(parent.mnyPasswordFile).stripTrailing();
        }

        return "";
    }

    /**
     * Opens the specified Money database file in read-only mode using the resolved
     * password.
     * Sets a custom error handler for column read errors.
     *
     * @param mnyPath the path to the Money database file
     * @return the opened Money database instance
     * @throws IOException
     * @throws InvalidCredentialsException
     */
    protected Database openMnyFile(Path mnyPath) throws IOException, InvalidCredentialsException {
        String mnyPassword = resolveMnyPassword();

        Database db = new DatabaseBuilder()
                .withPath(mnyPath)
                .withReadOnly(true)
                .withCodecProvider(new CryptCodecProvider(mnyPassword))
                .open();

        db.setErrorHandler(this::handleColumnError);

        return db;
    }

    /**
     * Handles column read errors and provides a substitute value.
     *
     * @param column     the column that caused the error
     * @param columnData the raw data of the column
     * @param location   the location of the error in the database
     * @param error      the exception that occurred
     * @return the substitute value for the column, or null if none is available
     */
    Object handleColumnError(Column column, byte[] columnData, Location location, Exception error) {
        String err = String.format("Error reading row/col value: %s(%s): %s",
                column.getTable().getName(),
                column.getName(),
                error.getMessage());

        Object ret = null; // substitute for value causing the exception

        if (error instanceof java.time.DateTimeException && columnData != null && columnData.length == 8) {
            // Microsoft Access stores its DateTime fields as an 8-byte double.
            // ByteBuffer buf = ByteBuffer.wrap(columnData).order(ByteOrder.LITTLE_ENDIAN);
            // double columnDouble = buf.getDouble();

            // Deserialize the bytes into the underlying strongly-typed Object.
            // if (column instanceof ColumnImpl) {
            // ColumnImpl columnImpl = (ColumnImpl) column;
            // Object decodedValue = columnImpl.read(columnData);
            // }

            ret = java.time.LocalDateTime.of(1900, 1, 1, 0, 0);
            err = String.format("%s: using %s instead", err, ret);
        } else {
            err = String.format("%s: using NULL instead", err);
        }

        stdErr().println(err);

        return ret;
    }

    /**
     * Returns the list of table names in the Money database, sorted alphabetically.
     *
     * @param db           the Money database instance
     * @param filterTables the list of table names to include, or null to include
     *                     all tables
     * @return the list of table names in the Money database, sorted alphabetically
     * @throws IOException
     */
    protected List<String> getMnyTableNames(Database db, List<String> filterTables) throws IOException {
        List<String> t_names = new ArrayList<>(db.getTableNames());
        Collections.sort(t_names);

        if (filterTables != null && !filterTables.isEmpty()) {
            // The 'tables' command wraps table names containing spaces with double quotes.
            filterTables = filterTables.stream()
                    .map(t -> t.startsWith("\"") && t.endsWith("\"") ? t.substring(1, t.length() - 1) : t)
                    .collect(Collectors.toList());

            Set<String> t_namesSet = t_names.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            for (String table : filterTables) {
                if (!t_namesSet.contains(table.toLowerCase())) {
                    throw new UserFacingException("Error: '" + table + "': No such table.");
                }
            }

            Set<String> filterSet = filterTables.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            t_names.removeIf(t_name -> !filterSet.contains(t_name.toLowerCase()));
        }

        return t_names;
    }

    /**
     * Converts a Java object to its SQL value such as "null > NULL" or "true > 1".
     *
     * @param obj the value to convert
     * @return the SQL value
     */
    protected String sqlValue(Object obj) {
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
     * Returns the SQLite data type corresponding to the given Jackcess DataType.
     *
     * @param t the Jackcess DataType to convert
     * @return the SQLite data type as a string
     */
    protected String sqliteDataType(DataType t) {
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
     * Returns a SQL identifier stripped of all non-alphanumeric or underscore
     * characters, replacing them with an underscore.
     *
     * @param name a SQL identifier (e.g. table or column name)
     * @return the new identifier
     */
    static public String sqlNameU(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    // Not the best way to do this but we don't have access to SQLite
    // `sqlite3_keyword_*` functions.
    // https://www.sqlite.org/lang_keywords.html
    static final Set<String> SQLITE_KEYWORDS = new HashSet<>(Arrays.asList(
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
        outDirPath = Paths.get(outDirPath.toString(), SharedUtils.safeFileName(SharedUtils.getPathStem(inFilePath)));
        if (fileName == null) {
            return outDirPath;
        }

        return Paths.get(outDirPath.toString(), fileName);
    }
}
