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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

@Command(name = "aws-ssm-params-env",
    description = "%nFetches AWS SSM parameter store parameters and formats the output.%n",
    mixinStandardHelpOptions = true,
    versionProvider = GitVersionProvider.class,
    showDefaultValues = true,
    sortOptions = false,
    footerHeading = "%n%n",
    footer = {
        "ENVIRONMENT",
        "",
        "  SSM_PATHS        :: (csv string) comma separated list of AWS SSM parameter paths to fetch",
        "  SSM_RECURSIVE    :: (bool) recursively fetch parameters from sub-paths?",
        "  SSM_SORT         :: (bool) sort fetched parameters by name?",
        "  SSM_SORT_REVERSE :: (bool) sort fetched parameters by name in reverse order",
        "  SSM_USE_LAST     :: (bool) use last fetched value?",
        "  SSM_UPPERCASE    :: (bool) uppercase parameter names?",
        "  SSM_TRIM         :: (bool) trim fetched parameter values?",
        "  SSM_FORMAT       :: (string) output format (example: exports, dotenv)",
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
  @Option(names = {"-r", "--recursive"}, negatable = true,
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

  @Option(names = {"--full-version"}, description = "display detailed application version")
  boolean fullVersion = false;

  @Parameters(paramLabel = "PATH", description = "one or more SSM parameter store path(s) to fetch parameters from  (default: ${DEFAULT-VALUE})")
  List<String> paths = envStrList("SSM_PATHS", "AWS_ENV_PATH");

  // actual output (needed for testing)
  static PrintStream output = System.out;

  enum OutFormat {
    exports,
    dotenv
  }

  static void main(String... args) {
    System.exit(runMain(args));
  }

  static int runMain(String... args) {
    return new CommandLine(new Main())
        .setOut(new PrintWriter(output, true))
        .execute(args);
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
    if (fullVersion) {
      System.out.println(new GitVersionProvider().fullVersion());
      return 0;
    }
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
      output.println(formatParams(ssmParams));
      return 0;
    } catch (Throwable t) {
      log.debug("failed to read SSM parameters", t);
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
    return formatParams(params, it ->
        it.getKey() + "=\"" + quoteShellValue(it.getValue()) + "\"");
  }

  private String formatParamsExports(Map<String, String> params) {
    return formatParams(params, it ->
        "export '" + it.getKey() + "=" + quoteShellValue(it.getValue()) + "'");
  }

  static String quoteShellValue(String value) {
    if (value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Shell values cannot contain NUL characters.");
    }
    return value
        .replace("\"", "\\\"")
        .replace("'", "'\\''")
        .replace("`", "\\`")
        .replace("$", "\\$");
  }

  private String formatParams(Map<String, String> params,
                              Function<Map.Entry<String, String>, String> formatter) {
    return params.entrySet().stream()
        .map(formatter)
        .filter(Objects::nonNull)
        .filter(it -> !it.isBlank())
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

    return builder.build();
  }
}
