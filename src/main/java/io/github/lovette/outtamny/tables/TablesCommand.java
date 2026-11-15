package io.github.lovette.outtamny.tables;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import io.github.lovette.outtamny.BaseSubcommand;
import io.github.spannm.jackcess.Database;
import io.github.spannm.jackcess.encrypt.InvalidCredentialsException;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;

@Command(name = "tables", description = "List Money database table names.", sortOptions = false)
public class TablesCommand extends BaseSubcommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "MNYFILE", description = "Money data file (.mny).")
    private Path mnyPath;

    /**
     * Subcommand entry point.
     */
    @Override
    public Integer call() throws FileNotFoundException, IOException {
        if (mnyPath == null) {
            throw new ParameterException(spec.commandLine(), "Error: MNYFILE path must be specified.");
        }

        mnyPath = mnyPath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(mnyPath)) {
            throw new FileNotFoundException("Error: '" + mnyPath + "': No such file.");
        }

        try (Database db = openMnyFile(mnyPath)) {
            for (String t_name : getMnyTableNames(db, null)) {
                stdOut().println(sqlNameQ(t_name));
            }

        } catch (InvalidCredentialsException e) {
            stdErr().println("Error: Invalid password for the Money data file.");
            return 1;
        }

        return 0;
    }
}
