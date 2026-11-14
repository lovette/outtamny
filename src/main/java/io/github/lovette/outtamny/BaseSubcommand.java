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
     * @param db the Money database instance
     * @return the list of table names in the Money database, sorted alphabetically
     * @throws IOException
     */
    protected List<String> getMnyTableNames(Database db) throws IOException {
        List<String> t_names = new ArrayList<>(db.getTableNames());
        Collections.sort(t_names);
        return t_names;
    }
}
