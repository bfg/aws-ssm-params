package com.github.bfg.awsssmparamsenv;

import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

@Command(name = "ls",
    description = "Lists parameters",
    sortOptions = false
)
public class ListCmd extends BaseCmd {
  @Option(names = {"-r", "--recursive"}, description = "List parameters in sub-paths")
  boolean recursive;

  @Parameters(arity = "1..*", paramLabel = "PATH", description = "One or more parameter paths or glob patterns")
  List<String> paths;

  public ListCmd(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    super(stdin, stdout, stderr);
  }

  @Override
  protected int doCall() {
    try (var fetcher = new AwsSsmParamFetcher(createSsmClient())) {
      val query = SsmParamQuery.builder()
          .paths(Utils.csvStream(paths).distinct().toList())
          .recursive(recursive)
          .sort(true)
          .build();


      fetcher.listParameters(query)
          .forEach(it -> stdout.println(fmtParameter(it)));
      return 0;
    } catch (Throwable t) {
      return die("List operation failed", t);
    }
  }

  private String fmtParameter(Parameter p) {
    var fmt = "%-15s %s  %s";
    return fmt.formatted(p.typeAsString(), p.lastModifiedDate(), p.name());
  }
}
