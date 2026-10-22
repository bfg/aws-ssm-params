package com.github.bfg.awsssmparams;

import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.InputStream;
import java.io.PrintStream;

@Command(name = "mv",
    description = "Moves one or more AWS SSM parameters",
    sortOptions = false
)
public class MoveCmd extends BaseCmd {
  @Option(names = {"-r", "--recursive"}, description = "Include matching parameters in sub-paths")
  boolean recursive;

  @Option(names = {"-f", "--force"}, description = "Overwrite existing parameters")
  boolean force;

  @Mixin
  DoitMixin doit;

  @Parameters(index = "0", paramLabel = "SOURCE", description = "Parameter name or basename glob pattern")
  String source;

  @Parameters(index = "1", paramLabel = "DESTINATION", description = "Destination directory")
  String destination;

  public MoveCmd(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    super(stdin, stdout, stderr);
  }

  @Override
  protected int doCall() {
    try (var facade = createSsmFacade()) {
      val cfg = SsmCopyParamConfig.builder()
          .source(source)
          .destination(destination)
          .recursive(recursive)
          .overwrite(force)
          .doIt(doit.enabled())
          .build();

      facade.moveParameters(cfg, it ->
          stdout.println(doit.maybeDryRun("mv: " + it.getKey() + " -> " + it.getValue())));

      return 0;
    } catch (Throwable t) {
      return die("move operation failed", t);
    }
  }
}
