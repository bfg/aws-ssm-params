package com.github.bfg.awsssmparamsenv;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Command(name = EnvCmd.CMD_NAME,
    description = "Fetches and outputs AWS SSM parameters for shell evaluation",
    sortOptions = false,
    footerHeading = "%n%n",
    footer = {
        "ENVIRONMENT VARIABLES:",
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
        "  " + CmdRunner.CMD_NAME + " /dev/common /dev/my-app /dev/my-app/special",
        "",
        "# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking first parameter if there",
        "# is pa parameter with the same name  in subsequential parameter paths",
        "  " + CmdRunner.CMD_NAME + " -r /dev/common /dev/my-app",
        "",
        "# fetch parameters from AWS SSM paths /dev/common, /dev/my-app, recursively, taking last parameter if there",
        "# is pa parameter with the same name  in subsequential parameter paths",
        "  " + CmdRunner.CMD_NAME + " -l -r /dev/common /dev/my-app",
        "",
        "# completely env variable driven invocation, useful for ENTRYPOINT integration",
        "  SSM_PATHS=\"/dev/common,/dev/my-app\" SSM_RECURSIVE=1 SSM_USE_LAST=1 " + CmdRunner.CMD_NAME,
    }
)
@Slf4j
public class EnvCmd extends BaseCmd {
  static final String CMD_NAME = "env";

  @Option(names = {"-r", "--recursive"}, negatable = true,
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Recursively fetch parameters from sub-paths")
  boolean recursive = Env.asBoolean("SSM_RECURSIVE", false);

  @Option(names = {"-s", "--sort"}, negatable = true, fallbackValue = "true",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Sort fetched parameters by name in output?")
  boolean sort = Env.asBoolean("SSM_SORT", true);

  @Option(names = {"-R", "--reverse-sort"},
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Sort fetched parameters by name in reverse order?")
  boolean reverseSort = Env.asBoolean("SSM_SORT_REVERSE", false);

  @Option(names = {"-l", "--use-last"}, negatable = true, fallbackValue = "true",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Use last parameter with distinct name instead of the first one? Useful when same parameter name is contained in multiple paths")
  boolean useLast = Env.asBoolean("SSM_USE_LAST", true);

  @Option(names = {"-u", "--uppercase"}, negatable = true, fallbackValue = "true",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Uppercase parameter names?")
  boolean uppercase = Env.asBoolean("SSM_UPPERCASE", true);

  @Option(names = {"-t", "--trim"}, negatable = true, fallbackValue = "true",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Trim fetched AWS SSM parameter values?")
  boolean trimValues = Env.asBoolean("SSM_TRIM", true);

  @Option(names = {"-f", "-F", "--format"},
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "Output format (possible values: exports, dotenv)")
  OutFormat outputFormat = Env.asObj("SSM_FORMAT", OutFormat::valueOf, OutFormat.exports);

  @Parameters(paramLabel = "PATH",
      showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
      description = "One or more AWS SSM parameter store paths to fetch parameters from")
  List<String> paths = Env.asStringList("SSM_PATHS");

  @ParentCommand
  CmdRunner parentCmd;

  public EnvCmd(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    super(stdin, stdout, stderr);
  }

  @Override
  protected void beforeCall() {
    if (endpoint.isEmpty() && !parentCmd.endpoint.isEmpty()) {
      endpoint = parentCmd.endpoint;
    }
    if (region.isEmpty() && !parentCmd.region.isEmpty()) {
      region = parentCmd.region;
    }
    super.beforeCall();
  }

  @Override
  protected int doCall() {
    try (var fetcher = new AwsSsmParamFetcher(createSsmClient())) {
      val verifiedPaths = Utils.csvStream(paths).distinct().toList();
      val badPaths = verifiedPaths.stream().filter(it -> !it.startsWith("/")).toList();
      if (!badPaths.isEmpty()) {
        return die("Bad AWS SSM parameter paths were given: " + String.join(",", badPaths));
      }

      var query = SsmParamQuery.builder()
          .paths(verifiedPaths)
          .recursive(recursive)
          .sort(sort)
          .reverseSort(reverseSort)
          .useLast(useLast)
          .uppercase(uppercase)
          .trimValues(trimValues)
          .build();

      if (verbose) {
        log.debug("using query: {}", query);
      }

      var ssmParams = fetcher.fetchParameters(query);

      // format output and print it
      var formattedResult = formatParams(ssmParams);
      if (!formattedResult.isBlank()) {
        stdout.println(formattedResult);
      }

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
        it.getKey() + "=\"" + quoteDotenvValue(it.getValue()) + "\"");
  }

  private String formatParamsExports(Map<String, String> params) {
    return formatParams(params, it ->
        "export '" + it.getKey() + "=" + quoteExportValue(it.getValue()) + "'");
  }

  static String quoteExportValue(String value) {
    return value
        .replace("'", "'\\''")
        .replace("\\r", "\n")
        .replace("\\n", "\n")
        ;
  }

  static String quoteDotenvValue(String value) {
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
}
