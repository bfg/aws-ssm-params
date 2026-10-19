package com.github.bfg.awsssmparamsenv;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = CmdRunner.CMD_NAME,
    description = "AWS SSM Parameter Store CLI",
    usageHelpWidth = 120,
    mixinStandardHelpOptions = true,
    versionProvider = GitVersionProvider.class,
    sortOptions = false,
    optionListHeading = "%nOPTIONS:%n",
    commandListHeading = "%nCOMMANDS:%n"
)
@RequiredArgsConstructor
abstract class BaseCmd implements Callable<Integer> {
  protected Logger log = LoggerFactory.getLogger(getClass());

  @Option(names = {"--endpoint"},
      showDefaultValue = Visibility.ALWAYS,
      description = "AWS endpoint (optional, useful for testing)")
  String endpoint = Env.asString("AWS_ENDPOINT");

  @Option(names = {"--region"},
      showDefaultValue = Visibility.ALWAYS,
      description = "AWS region (optional, useful for testing)")
  String region = Env.asString("AWS_REGION");

  @Option(names = {"-v", "--verbose"}, description = "Verbose execution")
  boolean verbose = false;

  @Option(names = {"-L", "--log-level"}, description = "Logger level (off/warn/info/debug/trace)")
  String logLevel = "";

  @Option(names = {"--full-version"}, description = "Display detailed application version")
  boolean fullVersion = false;

  // stdio streams
  protected final InputStream stdin;
  protected final PrintStream stdout;
  protected final PrintStream stderr;

  protected BaseCmd(PrintStream stdout, PrintStream stderr, InputStream stdin) {
    this.stdout = Objects.requireNonNullElse(stdout, System.out);
    this.stderr = Objects.requireNonNullElse(stderr, System.err);
    this.stdin = Objects.requireNonNullElse(stdin, System.in);
  }

  public Integer call() {
    beforeCall();

    if (fullVersion) {
      return displayFullVersion();
    }
    Logback.maybeSetLogLevel(logLevel);

    if (verbose) {
      Logback.setLogLevel(getClass().getPackage().getName(), "DEBUG");
    }

    return doCall();
  }

  protected void beforeCall() {

  }

  /**
   * invokes the actual business logic and returns a desired exit status
   *
   * @return the exit status
   */
  protected abstract int doCall();

  protected int die(String msg, Throwable t) {
    var exMsg = t.getMessage() == null ? t.toString() : t.getMessage();
    return die(msg + ": " + exMsg);
  }

  protected int die(String msg) {
    stderr.println("FATAL: " + msg);
    return 1;
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
    stdout.println(gitPropStr + separator + sysPropsStr);
    return 0;
  }

  protected SsmClient createSsmClient() {
    var builder = SsmClient.builder();
    var endpoint = this.endpoint.trim();
    var region = this.region.trim();

    log.debug("creating AWS SSM client: verbose={}, endpoint=`{}`, region=`{}`", verbose, endpoint, region);

    if (!region.isEmpty()) {
      var awsRegion = Region.of(region.toLowerCase());
      builder.region(awsRegion);
    }
    if (!endpoint.isEmpty()) {
      var awsEndpoint = endpoint.toLowerCase();
      builder.endpointOverride(URI.create(awsEndpoint));
    }

    return builder.build();
  }
}
