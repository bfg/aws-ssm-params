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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

@Command(name = Main.CMD_NAME,
    description = "%nExports parameters from AWS SSM parameter store to environment variables or envfile.%n",
    usageHelpWidth = 120,
    mixinStandardHelpOptions = true,
    versionProvider = GitVersionProvider.class,
    sortOptions = false,
    footerHeading = "%n%n",
    footer = {
        "ENVIRONMENT VARIABLES",
        "",
        "The behaviour of this tool can be altered by setting the following environment variables:",
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
        "# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, /dev/my-app/special",
        "# without recursive querying",
        "  " + Main.CMD_NAME + " /dev/common /dev/my-app /dev/my-app/special",
        "",
        "# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking first parameter if there",
        "# is pa parameter with the same name  in subsequential parameter paths",
        "  " + Main.CMD_NAME + " -r /dev/common /dev/my-app",
        "",
        "# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking last parameter if there",
        "# is pa parameter with the same name  in subsequential parameter paths",
        "  " + Main.CMD_NAME + " -l -r /dev/common /dev/my-app",
        "",
        "# completely env variable driven invocation, useful for ENTRYPOINT integration",
        "  SSM_PATHS=\"/dev/common,/dev/my-app\" SSM_RECURSIVE=1 SSM_USE_LAST=1 " + Main.CMD_NAME,
    })
@Slf4j
public final class Main implements Callable<Integer> {
  static final String CMD_NAME = "aws-ssm-params-env";

  @Option(names = {"-r", "--recursive"}, negatable = true,
      showDefaultValue = Visibility.ALWAYS,
      description = "Recursively fetch parameters from sub-paths")
  boolean recursive = envBool(false, "SSM_RECURSIVE");

  @Option(names = {"-s", "--sort"}, negatable = true, fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      description = "Sort fetched parameters by name in output?")
  boolean sort = envBool(true, "SSM_SORT");

  @Option(names = {"-R", "--reverse-sort"},
      showDefaultValue = Visibility.ALWAYS,
      description = "Sort fetched parameters by name in reverse order?")
  boolean reverseSort = envBool(false, "SSM_SORT_REVERSE");

  @Option(names = {"-l", "--use-last"}, negatable = true,
      showDefaultValue = Visibility.ALWAYS,
      description = "Use last parameter with distinct name instead of the first one? Useful when same parameter name is contained in multiple paths")
  boolean useLast = envBool(false, "SSM_USE_LAST");

  @Option(names = {"-u", "--uppercase"}, negatable = true, fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      description = "Uppercase parameter names?")
  boolean uppercase = envBool(true, "SSM_UPPERCASE");

  @Option(names = {"-t", "--trim"}, negatable = true, fallbackValue = "true",
      showDefaultValue = Visibility.ALWAYS,
      description = "Trim fetched AWS SSM parameter values?")
  boolean trimValues = envBool(true, "SSM_TRIM");

  @Option(names = {"-f", "-F", "--format"},
      showDefaultValue = Visibility.ALWAYS,
      description = "Output format (possible values: exports, dotenv)")
  OutFormat outputFormat = envObj(OutFormat.exports, OutFormat::valueOf, "SSM_FORMAT");

  @Option(names = {"--endpoint"},
      showDefaultValue = Visibility.ALWAYS,
      description = "AWS endpoint (optional, useful for testing)")
  String endpoint = "";

  @Option(names = {"--region"},
      showDefaultValue = Visibility.ALWAYS,
      description = "AWS region (optional, useful for testing)")
  String region = "";

  @Option(names = {"-L", "--log-level"}, description = "Logger level (off/warn/info/debug/trace)")
  String logLevel = "";

  @Option(names = {"--full-version"}, description = "Display detailed application version")
  boolean fullVersion = false;

  @Parameters(paramLabel = "PATH",
      showDefaultValue = Visibility.ALWAYS,
      description = "One or more AWS SSM parameter store paths to fetch parameters from")
  List<String> paths = envStrList("SSM_PATHS", "AWS_ENV_PATH");

  // actual output stream (needed for testing)
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
      return displayFullVersion();
    }
    maybeSetLogLevel(logLevel);

    try (var fetcher = new AwsSsmParamFetcher(createSsmClient())) {
      var query = SsmParamQuery.builder()
          .paths(paths)
          .recursive(recursive)
          .sort(sort)
          .reverseSort(reverseSort)
          .useLast(useLast)
          .uppercase(uppercase)
          .trimValues(trimValues)
          .build();


      log.warn("created query {}", query);

      var ssmParams = fetcher.fetchParameters(query);

      // format output and print it
      var formattedResult = formatParams(ssmParams);
      if (!formattedResult.isBlank()) {
        output.println(formattedResult);
      }

      return 0;
    } catch (Throwable t) {
      log.debug("failed to read SSM parameters", t);
      return die("failed to read SSM parameters", t);
    }
  }

  private int displayFullVersion() {
    var gitPropStr = new GitVersionProvider().fullVersion();

    var sysProps = System.getProperties();

    var shouldContain = Set.of(
        "java.runtime.version",
        "java.vm.name",
        "java.vendor.version",
        "os.name",
        "os.arch"
    );
    var sysPropsStr = sysProps.keySet().stream()
        .map(Object::toString)
        .filter(shouldContain::contains)
        .sorted()
        .map(it -> it + "=" + sysProps.get(it))
        .collect(Collectors.joining(System.lineSeparator()));

    var separator = System.lineSeparator() + System.lineSeparator();
    output.println(gitPropStr + separator + sysPropsStr);
    return 0;
  }

  private String formatParams(Map<String, String> params) {
    return switch (outputFormat) {
      case dotenv -> formatParamsDotEnv(params);
      case exports -> formatParamsExports(params);
    };
  }

  private String formatParamsDotEnv(Map<String, String> params) {
    return formatParams(params, it ->
        it.getKey() + "=\"" + quoteDotenvValue(it.getValue()) + "\"");
  }

  private String formatParamsExports(Map<String, String> params) {
    return formatParams(params, it ->
        "export '" + it.getKey() + "=" + quoteExportValue(it.getValue()) + "'");
  }

  static String quoteExportValue(String value) {
    if (value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Shell values cannot contain NUL characters.");
    }
    return value
        .replace("'", "'\\''")
        .replace("\\r", "\n")
        .replace("\\n", "\n")
        ;
  }

  static String quoteDotenvValue(String value) {
    if (value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Shell values cannot contain NUL characters.");
    }
    return value
        .replace("\"", "\\\"")
        .replace("`", "\\`")
        .replace("\\r", "\n")
        .replace("\\n", "\n")
        .replace("$", "\\$")
        .replace("\n", "\\n")
        ;
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
