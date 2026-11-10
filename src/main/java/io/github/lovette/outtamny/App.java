package io.github.lovette.outtamny;

import java.io.PrintWriter;

import io.github.lovette.outtamny.schema.SchemaCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.UnmatchedArgumentException;

@Command(name = "outtamny", version = "1.0.0", mixinStandardHelpOptions = true, description = "Gets your financial data outta a Microsoft Money data file.", subcommands = {
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

        cmd.setParameterExceptionHandler((ParameterException ex, String[] argsArray) -> {
            PrintWriter writer = cmd.getErr();
            CommandSpec spec = cmd.getCommandSpec();

            if (ex instanceof UnmatchedArgumentException) {
                // Error is due to an unmatched argument (like a standalone filename.)
                UnmatchedArgumentException uae = (UnmatchedArgumentException) ex;
                String unrecognized = uae.getUnmatched().get(0);

                writer.println("Error: Unknown command '" + unrecognized + "'.");
                writer.println("Possible commands are: " + cmd.getSubcommands().keySet());
            } else {
                // Any other normal error (missing required options, etc.)
                writer.println(ex.getMessage());
                UnmatchedArgumentException.printSuggestions(ex, writer);
                writer.print(cmd.getHelp().fullSynopsis());
            }

            writer.printf("Try '%s --help' for more information.%n", spec.qualifiedName());

            return cmd.getExitCodeExceptionMapper() != null
                    ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                    : spec.exitCodeOnInvalidInput();
        });

        return cmd;
    }
}
