package com.jobhunter.cli;

import java.util.ArrayList;
import java.util.List;

public record CommandLine(String configPath, String command, List<String> arguments) {

    public static final String USAGE = """
            Usage: java -jar job-hunter.jar [--config FILE] [command] [args]

            Commands:
              run                            Poll all sources continuously (default)
              once                           Run a single fetch cycle and exit
              list [--status S] [--limit N]  Show stored jobs, newest first
              mark <id> <status>             Set the application status of a job
                                             (id is the number shown by list, or the full job id)
              stats                          Summarise jobs by status and source
              help                           Show this message

            Statuses: %s
            """;

    public static CommandLine parse(String[] args) {
        String config = "config.yml";
        String command = null;
        List<String> rest = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--config") || arg.equals("-c")) {
                if (i + 1 >= args.length) throw new IllegalArgumentException("--config needs a file path");
                config = args[++i];
            } else if (arg.equals("--once")) {
                command = "once";
            } else if (arg.equals("--help") || arg.equals("-h")) {
                command = "help";
            } else if (command == null && isConfigFile(arg)) {
                config = arg;
            } else if (command == null) {
                command = arg.toLowerCase();
            } else {
                rest.add(arg);
            }
        }

        return new CommandLine(config, command == null ? "run" : command, List.copyOf(rest));
    }

    public String option(String name) {
        int index = arguments.indexOf(name);
        if (index < 0) return null;
        if (index + 1 >= arguments.size()) throw new IllegalArgumentException(name + " needs a value");
        return arguments.get(index + 1);
    }

    public List<String> positional() {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            if (arguments.get(i).startsWith("--")) {
                i++;
            } else {
                values.add(arguments.get(i));
            }
        }
        return values;
    }

    private static boolean isConfigFile(String arg) {
        String lower = arg.toLowerCase();
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }
}
