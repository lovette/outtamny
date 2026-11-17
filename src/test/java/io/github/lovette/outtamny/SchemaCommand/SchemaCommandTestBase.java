package io.github.lovette.outtamny.SchemaCommand;

import io.github.lovette.outtamny.CommandTestBase;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;

/** Base class for schema-related tests that provides common setup and utility methods. */
public class SchemaCommandTestBase extends CommandTestBase {
    // A unique, temporary path for every test run
    @TempDir
    protected Path tempOutDir;

    /**
     * Builds the command-line arguments for the schema command, including the output directory and
     * the MNYFILE path.
     *
     * @param out absolute path to output file or directory or "-" for stdout
     * @param args additional command arguments
     * @return the complete array of command-line arguments
     */
    protected String[] buildCommandArgs(Path out, String... args) {
        List<String> newArgs = new ArrayList<>();

        if (args != null) {
            Collections.addAll(newArgs, args);
        }

        if (out != null) {
            Collections.addAll(newArgs, "--out", out.toString());
        }

        return super.buildCommandArgs("schema", newArgs.toArray(String[]::new));
    }

    /**
     * Executes the schema command with the default temporary output directory and the specified
     * arguments. Asserts that the exit code is zero and 'stderr' is empty.
     *
     * @param args additional command arguments
     */
    protected void executeSchemaCommand(String... args) {
        executeSchemaCommand(tempOutDir, args);
    }

    /**
     * Executes the schema command with the specified output file or directory and arguments. The
     * MNYFILE argument is automatically appended at the end of the command. Asserts that the exit
     * code is zero and 'stderr' is empty.
     *
     * @param out the output file or directory
     * @param args additional command arguments
     */
    protected void executeSchemaCommand(Path out, String... args) {
        executeSchemaCommand(0, out, args);
    }

    /**
     * Executes the schema command with the default temporary output directory and the specified
     * expected exit code and arguments. 'stdout' and 'stderr' are not checked.
     *
     * @param expectedExitCode the expected exit code of the command
     * @param args additional command arguments
     */
    protected void executeSchemaCommand(int expectedExitCode, String... args) {
        executeSchemaCommand(expectedExitCode, tempOutDir, args);
    }

    /**
     * Executes the schema command with the specified expected exit code, output file or directory,
     * and arguments. 'stdout' and 'stderr' are not checked.
     *
     * @param expectedExitCode the expected exit code of the command
     * @param out the output file or directory
     * @param args additional command arguments
     */
    protected void executeSchemaCommand(int expectedExitCode, Path out, String... args) {
        super.executeCommand(expectedExitCode, buildCommandArgs(out, args));
    }

    /**
     * Lists the contents of the specified sub-directory within the temporary output directory.
     * Directories are suffixed with a '/'.
     *
     * @param subDir the sub-directory within the temporary output directory, or null for the root
     * @return a set of file and directory names
     * @throws IOException if an I/O error occurs
     */
    protected List<String> listOutContents(Path subDir) throws IOException {
        List<String> dirFilesAndDirs = new ArrayList<>();

        Path listDirPath = (subDir != null) ? tempOutDir.resolve(subDir) : tempOutDir;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(listDirPath)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    dirFilesAndDirs.add(entry.getFileName().toString() + "/");
                } else if (Files.isRegularFile(entry)) {
                    dirFilesAndDirs.add(entry.getFileName().toString());
                }
            }
        }

        Collections.sort(dirFilesAndDirs);

        return dirFilesAndDirs;
    }

    /**
     * Lists the contents of the root of the temporary output directory. Directories are suffixed
     * with a '/'.
     *
     * @return a set of file and directory names
     * @throws IOException if an I/O error occurs
     */
    protected List<String> listOutDirContents() throws IOException {
        return listOutContents(null);
    }

    /**
     * Lists the output file names in the temporary output directory or specified sub-directory
     * matching the given glob pattern.
     *
     * @param subDir the sub-directory within the temporary output directory, or null for the root
     * @param glob the glob pattern to match file names against
     * @return a set of matching file names
     * @throws IOException if an I/O error occurs
     */
    protected List<String> listOutDirFiles(Path subDir, String glob) throws IOException {
        List<String> dirFileNames = new ArrayList<>();
        Path listDirPath = (subDir != null) ? tempOutDir.resolve(subDir) : tempOutDir;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(listDirPath, glob)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) {
                    dirFileNames.add(entry.getFileName().toString());
                }
            }
        }

        Collections.sort(dirFileNames);

        return dirFileNames;
    }

    /**
     * Lists all output file names in the specified sub-directory of the temporary output directory.
     *
     * @param subDir the sub-directory within the temporary output directory
     * @return a set of file names
     * @throws IOException if an I/O error occurs
     */
    protected List<String> listOutDirFiles(Path subDir) throws IOException {
        return listOutDirFiles(subDir, "*");
    }

    /**
     * Lists output file names in the temporary output directory matching the given glob pattern.
     *
     * @param glob the glob pattern to match file names against
     * @return a set of matching file names
     * @throws IOException if an I/O error occurs
     */
    protected List<String> listOutDirFiles(String glob) throws IOException {
        return listOutDirFiles(null, glob);
    }

    /**
     * Lists all output file names in the temporary output directory.
     *
     * @return a set of file names
     * @throws IOException if an I/O error occurs
     */
    protected List<String> listOutDirFiles() throws IOException {
        return listOutDirFiles(null, "*");
    }
}
