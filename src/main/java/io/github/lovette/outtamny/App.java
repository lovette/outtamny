package io.github.lovette.outtamny;

import io.github.lovette.outtamny.schema.SchemaCommand;
import io.github.lovette.outtamny.tables.TablesCommand;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.Properties;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.UnmatchedArgumentException;

@Command(
        name = "outtamny",
        versionProvider = App.PropertiesVersionProvider.class,
        description = "Gets your financial data outta a Microsoft Money data file.",
        subcommands = {
            SchemaCommand.class,
            TablesCommand.class,
        })
public class App extends BaseCommand implements Runnable {

    @Mixin
    CommandUsageMixin usageMixin;

    // ScopeType.INHERIT allows option to be specified as both the App and
    // subcommands, e.g. 'outtamny -v schema' or 'outtamny schema -v'.

    @Option(
            names = {"-p", "--password"},
            description = "Prompt for the password for the Money data file.",
            prompt = "Enter the password for the Money data file: ",
            interactive = true,
            order = 2,
            scope = CommandLine.ScopeType.INHERIT)
    protected String mnyPasswordPrompt;

    @Option(
            names = {"--password:pw"},
            paramLabel = "PASSWORD",
            description = "The password for the Money data file (may be visible in shell history).",
            order = 2,
            scope = CommandLine.ScopeType.INHERIT)
    protected String mnyPasswordInline;

    @Option(
            names = {"--password:file"},
            paramLabel = "FILE",
            description = "The file containing the password for the Money data file.",
            order = 2,
            scope = CommandLine.ScopeType.INHERIT)
    protected Path mnyPasswordFile;

    @Option(
            names = {"--password:env"},
            paramLabel = "ENVVAR",
            description = "The environment variable containing the password for the Money data file.",
            order = 2,
            scope = CommandLine.ScopeType.INHERIT)
    protected String mnyPasswordEnv;

    @Option(
            names = {"-v", "--verbose"},
            description = "Increase verbose level.",
            scope = CommandLine.ScopeType.INHERIT,
            order = 15)
    private boolean[] verbosity = new boolean[0];

    @Option(
            names = {"-V", "--version"},
            versionHelp = true,
            description = "Print version information and exit.",
            order = 20)
    boolean versionRequested;

    @Option(
            names = {"-h", "--help"},
            usageHelp = true,
            description = "Display this help and exit",
            order = 20)
    private boolean helpRequested;

    /**
     * Reads the version information from file 'src/main/resources/version.properties'.
     */
    static class PropertiesVersionProvider implements IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            Properties props = new Properties();
            try (InputStream is = getClass().getResourceAsStream("/version.properties")) {
                if (is != null) {
                    props.load(is);
                }
            }
            return new String[] {props.getProperty("version", "unknown")};
        }
    }

    /** Executes ONLY if the user runs `App` without any subcommand. */
    @Override
    public void run() {
        stdErr().println("Please specify a subcommand. Use --help for choices.");
    }

    /**
     * Entry point for the application.
     *
     * @param args the command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        System.exit(createCommandLine().execute(args));
    }

    /**
     * Creates and configures the CommandLine instance for this application.
     *
     * @return the configured CommandLine instance for this application.
     */
    static CommandLine createCommandLine() {
        CommandLine cmd = new CommandLine(new App());
        IParameterExceptionHandler defaultParameterHandler = cmd.getParameterExceptionHandler();
        IExecutionExceptionHandler defaultExceptionHandler = cmd.getExecutionExceptionHandler();

        cmd.setParameterExceptionHandler((ParameterException ex, String[] argsArray) -> {
            if (!(ex instanceof UnmatchedArgumentException)) {
                return defaultParameterHandler.handleParseException(ex, argsArray);
            }

            PrintWriter writer = cmd.getErr();
            CommandSpec spec = cmd.getCommandSpec();

            // Error is due to an unmatched argument (like a standalone filename.)
            UnmatchedArgumentException uae = (UnmatchedArgumentException) ex;
            String unrecognized = uae.getUnmatched().get(0);

            writer.println("Error: Unknown command '" + unrecognized + "'.");
            writer.println("Possible commands are: " + cmd.getSubcommands().keySet());
            writer.printf("Try '%s --help' for more information.%n", spec.qualifiedName());

            return cmd.getExitCodeExceptionMapper() != null
                    ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                    : spec.exitCodeOnInvalidInput();
        });

        cmd.setExecutionExceptionHandler((Exception ex, CommandLine commandLine, ParseResult parseResult) -> {
            int exitCode;

            // Note: picocli handles 'ParameterException' separately, they do not trigger
            // this handler.

            if (ex instanceof FileNotFoundException || ex instanceof FileAlreadyExistsException) {
                // Print only the error message, no usage, no stack trace.
                commandLine.getErr().println(commandLine.getColorScheme().errorText(ex.getMessage()));
                exitCode = commandLine.getCommandSpec().exitCodeOnExecutionException();
            } else if (ex instanceof UserFacingException ufe) {
                // Print only the error message, no usage, no stack trace.
                commandLine.getErr().println(commandLine.getColorScheme().errorText(ufe.getMessage()));
                exitCode = ufe.getExitCode();
            } else {
                exitCode = defaultExceptionHandler.handleExecutionException(ex, commandLine, parseResult);
            }

            return exitCode;
        });

        return cmd;
    }
}
