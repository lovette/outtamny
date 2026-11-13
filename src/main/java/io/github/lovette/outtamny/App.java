package io.github.lovette.outtamny;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.FileAlreadyExistsException;

import io.github.lovette.outtamny.schema.SchemaCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.UnmatchedArgumentException;

@Command(name = "outtamny", version = "0.5.0", mixinStandardHelpOptions = true, description = "Gets your financial data outta a Microsoft Money data file.", subcommands = {
        SchemaCommand.class,
})
public class App extends BaseCommand implements Runnable {

    // ScopeType.INHERIT allows option to be specified as both the App and
    // subcommands, e.g. 'outtamny -v schema' or 'outtamny schema -v'.
    @Option(names = { "-v",
            "--verbose" }, description = "Increase verbose level.", scope = CommandLine.ScopeType.INHERIT)
    private boolean[] verbosity = new boolean[0];

    /**
     * Executes ONLY if the user runs `App` without any subcommand.
     */
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
