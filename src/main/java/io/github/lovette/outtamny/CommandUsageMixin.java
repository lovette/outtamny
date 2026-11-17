package io.github.lovette.outtamny;

import picocli.CommandLine.Command;

@Command(
        descriptionHeading = "%n",
        parameterListHeading = "%nArguments:%n",
        optionListHeading = "%nOptions:%n",
        commandListHeading = "%nCommands:%n",
        abbreviateSynopsis = true,
        sortOptions = false)
public class CommandUsageMixin {}
