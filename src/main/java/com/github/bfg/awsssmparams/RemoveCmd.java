package com.github.bfg.awsssmparams;

import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

@Command(name = "rm",
    description = "Removes AWS SSM parameters",
    sortOptions = false
)
public class RemoveCmd extends BaseCmd {
  @Option(names = {"-r", "--recursive"}, description = "Include parameters in sub-paths when using a glob")
  boolean recursive;

  @Mixin
  DoitMixin doit;

  @Parameters(arity = "1..*", paramLabel = "PATH", description = "One or more parameter paths (or glob patterns) to remove")
  List<String> paths;

  public RemoveCmd(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    super(stdin, stdout, stderr);
  }

  @Override
  protected int doCall() {
    try (var facade = createSsmFacade()) {
      val rmCfg = SsmRemoveParamConfig.builder()
          .paths(paths)
          .recursive(recursive)
          .doIt(doit.enabled())
          .build();

      facade.removeParameters(rmCfg,
          it -> stdout.println(doit.maybeDryRun("rm: " + it)));
      return 0;
    } catch (Exception e) {
      return die("Remove operation failed", e);
    }
  }
}
