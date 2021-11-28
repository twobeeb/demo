package com.michelin.ns4kafka.cli;

import io.micronaut.configuration.picocli.PicocliRunner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "kafkactl",
        subcommands =
                {
                        //ApplySubcommand.class,
                        GetSubcommand.class,
                        DeleteSubcommand.class,
                        ApiResourcesSubcommand.class,
                        //DiffSubcommand.class,
                        ResetOffsetsSubcommand.class,
                        DeleteRecordsSubcommand.class,
                        ImportSubcommand.class
                        //ConnectorsSubcommand.class
                },
        mixinStandardHelpOptions = true)
public class KafkactlCommand implements Runnable {

    public static boolean VERBOSE = false;

    @Option(names = {"-v", "--verbose"}, description = "...", scope = CommandLine.ScopeType.INHERIT)
    public void setVerbose(final boolean verbose) {
        VERBOSE = verbose;
    }

    @Option(names = {"-n", "--namespace"}, description = "Override namespace defined in config or yaml resource", scope = CommandLine.ScopeType.INHERIT)
    public Optional<String> optionalNamespace;


    public static void main(String[] args) {
        // There are 3 ways to configure kafkactl :
        // 1. Setup config file in $HOME/.kafkactl/config.yml
        // 2. Setup config file anywhere and set KAFKACTL_CONFIG=/path/to/config.yml
        // 3. No file but environment variables instead
        /*boolean hasConfig = System.getenv().keySet().stream().anyMatch(s -> s.startsWith("KAFKACTL_"));
        if (!hasConfig) {
            System.setProperty("micronaut.config.files", System.getProperty("user.home") + "/.kafkactl/config.yml");
        }
        if (System.getenv("KAFKACTL_CONFIG") != null) {
            System.setProperty("micronaut.config.files", System.getenv("KAFKACTL_CONFIG"));
        }*/

        PicocliRunner.run(KafkactlCommand.class, args);

    }

    public void run() {
        CommandLine cmd = new CommandLine(new KafkactlCommand());
        // Display help
        cmd.usage(System.out);

    }

}
