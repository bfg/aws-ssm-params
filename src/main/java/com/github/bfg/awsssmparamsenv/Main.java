package com.github.bfg.awsssmparamsenv;

import ch.qos.logback.classic.Level;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

@Command(name = "aws-ssm-params-env",
        description = "Fetches AWS SSM parameter store parameters and formats the output",
        mixinStandardHelpOptions = true,
        showDefaultValues = true,
        sortOptions = false,
        footerHeading = "%n%n",
        footer = {
                "ENVIRONMENT",
                "",
                "  SSM_PATHS        :: comma separated list of AWS SSM parameter paths to fetch",
                "  SSM_RECURSIVE    :: recursively fetch parameters from sub-paths",
                "  SSM_SORT         :: sort fetched parameters by name",
                "  SSM_SORT_REVERSE :: sort fetched parameters by name in reverse order",
                "  SSM_USE_LAST     :: use last fetched value",
                "  SSM_UPPERCASE    :: uppercase ",
                "  SSM_TRIM         :: comma separated list of AWS SSM parameter paths to fetch",
                "  SSM_FORMAT       :: comma separated list of AWS SSM parameter paths to fetch",
                "  AWS_ENV_PATH     :: alias for SSM_PATHS",
                "",
                "EXAMPLES:",
                "",
                "# fetch parameters from AWS SSM paths /foo, /bar, /baz/kaz",
                "  aws-ssm-params-env /foo /bar /baz/kaz",
                "",
                "# fetch parameters from AWS SSM paths /foo, /bar, /baz/kaz",
                "  SSM_PATHS=\"/foo, /bar, /baz/kaz\"  aws-ssm-params-env",
                "  AWS_ENV_PATH=\"/foo, /bar, /baz/kaz\"  aws-ssm-params-env"
        })
@Slf4j
public final class Main implements Callable<Integer> {
    @Option(names = {"-r", "--recursive"}, negatable = true, fallbackValue = "false",
            description = "recursively fetch parameters from sub-paths")
    boolean recursive = envBool(false, "SSM_RECURSIVE");

    @Option(names = {"-s", "--sort"}, negatable = true, fallbackValue = "true",
            description = "sort fetched parameters by name in output?")
    boolean sort = envBool(true, "SSM_SORT");

    @Option(names = {"-R", "--reverse-sort"},
            description = "sort fetched parameters by name in reverse order?")
    boolean reverseSort = envBool(false, "SSM_SORT_REVERSE");

    @Option(names = {"-l", "--last-one-wins"},
            description = "use last parameter with distinct name will be emitted instead of the first one; useful when same parameter name is contained in multiple paths")
    boolean lastOneWins = envBool(false, "SSM_USE_LAST");

    @Option(names = {"-u", "--uppercase"}, negatable = true, fallbackValue = "true",
            description = "uppercase parameter names?")
    boolean uppercase = envBool(true, "SSM_UPPERCASE");
    ;

    @Option(names = {"-t", "--trim"}, negatable = true, fallbackValue = "true",
            description = "trim fetched AWS SSM parameter values?")
    boolean trimValues = envBool(true, "SSM_TRIM");

    @Option(names = {"-F", "--format"}, showDefaultValue = Visibility.ALWAYS,
            description = "output format, possible values: (exports, dotenv)")
    OutFormat outputFormat = envObj(OutFormat.exports, OutFormat::valueOf, "SSM_FORMAT");

    @Option(names = {"--endpoint"}, description = "AWS endpoint (optional, useful for testing)")
    String endpoint = "";

    @Option(names = {"--region"}, description = "AWS region (optional, useful for testing)")
    String region = "";

    @Option(names = {"-L", "--log-level"}, description = "logger level (off/warn/info/debug/trace)")
    String logLevel = "";

    @Parameters(paramLabel = "PATH", description = "one or more SSM parameter store path(s) to fetch parameters from  (default: ${DEFAULT-VALUE})")
    List<String> paths = envStrList("SSM_PATHS", "AWS_ENV_PATH");

    enum OutFormat {
        exports,
        dotenv
    }

    static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    private String envString(String... envVarNames) {
        return Arrays.stream(envVarNames)
                .map(System::getenv)
                .filter(Objects::nonNull)
                .map(String::trim)
                .findFirst()
                .orElse("");
    }

    private <T> T envObj(T defaultValue, Function<String, T> mapper, String... envVarNames) {
        try {
            var value = envString(envVarNames);
            var result = mapper.apply(value);
            return (result == null) ? defaultValue : result;
        } catch (Throwable t) {
            return defaultValue;
        }
    }


    private boolean envBool(boolean defaultValue, String... envVarNames) {
        var boolStr = envString(envVarNames).toLowerCase();
        if (boolStr.isEmpty()) {
            return defaultValue;
        } else {
            return boolStr.equals("true") || boolStr.equals("1") || boolStr.equals("y") || boolStr.equals("yes");
        }
    }

    private List<String> envStrList(String... envVarNames) {
        var str = envString(envVarNames);
        return Arrays.stream(str.split("\\s*[,;]+\\s*", 500))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .toList();
    }

    @Override
    public Integer call() {
        maybeSetLogLevel(logLevel);

        try (var fetcher = new AwsSsmParamFetcher(createSsmClient())) {
            var query = SsmParamQuery.builder()
                    .paths(paths)
                    .recursive(recursive)
                    .sort(sort)
                    .reverseSort(reverseSort)
                    .lastOneWins(lastOneWins)
                    .uppercase(uppercase)
                    .trimValues(trimValues)
                    .build();

            var ssmParams = fetcher.fetchParameters(query);
            System.out.println(formatParams(ssmParams));
            return 0;
        } catch (Throwable t) {
            return die("failed to read SSM parameters", t);
        }
    }

    private String formatParams(Map<String, String> params) {
        return switch (outputFormat) {
            case dotenv -> formatParamsDotEnv(params);
            case exports -> formatParamsExports(params);
        };
    }

    private String formatParamsDotEnv(Map<String, String> params) {
        return formatParams(params, it -> it.getKey() + "=\"" + it.getValue() + "\"");
    }

    private String formatParamsExports(Map<String, String> params) {
        return formatParams(params, it -> "export " + it.getKey() + "=$'" + it.getValue() + "'");
    }

    private String formatParams(Map<String, String> params,
                                Function<Map.Entry<String, String>, String> formatter) {
        return params.entrySet().stream()
                .map(formatter)
                .collect(Collectors.joining("\n"));
    }

    private int die(String msg, Throwable t) {
        var exMsg = t.getMessage() == null ? t.toString() : t.getMessage();
        return die(msg + ": " + exMsg);
    }

    private int die(String msg) {
        System.err.println("FATAL: " + msg);
        return 1;
    }

    private void maybeSetLogLevel(String logLevel) {
        if (logLevel.isBlank()) return;
        setLogLevel(Logger.ROOT_LOGGER_NAME, logLevel);
    }

    private void setLogLevel(String loggerName, String logLevel) {
        if (loggerName.isEmpty()) loggerName = Logger.ROOT_LOGGER_NAME;
        var logbackLevel = Level.toLevel(logLevel.trim().toUpperCase());
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName)).setLevel(logbackLevel);
    }

    private SsmClient createSsmClient() {
        var builder = SsmClient.builder();
        if (!region.isBlank()) {
            var awsRegion = Region.of(region.trim().toLowerCase());
            builder.region(awsRegion);
        }
        if (!endpoint.isBlank()) {
            var awsEndpoint = endpoint.trim().toLowerCase();
            builder.endpointOverride(URI.create(awsEndpoint));
        }

        return SsmClient.create();
    }
}
