package com.github.bfg.awsssmparamsenv;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Unmatched;

import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

import static java.util.Objects.requireNonNullElse;

@Command(name = CmdRunner.CMD_NAME)
@Slf4j
public final class CmdRunner extends BaseCmd {
  static final String CMD_NAME = "aws-ssm-params";
  static final String DEFAULT_CMD_NAME = EnvCmd.CMD_NAME;

  // stdio streams
  static InputStream _stdin = System.in;
  static PrintStream _stdout = System.out;
  static PrintStream _stderr = System.err;

  @Spec
  CommandSpec spec; // Injected by picocli, we'll use it to invoke a default sub-command

  // gets filled by unsupported cli args if call() from this class gets called
  @Unmatched
  private List<String> unknownCliArgs;

  public CmdRunner(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    super(stdin, stdout, stderr);
  }

  static void main(String... args) {
    System.exit(runMain(args));
  }

  static int runMain(String... args) {
    return new CommandLine(new CmdRunner(_stdin, _stdout, _stderr))
        .addSubcommand(new EnvCmd(_stdin, _stdout, _stderr))
        .addSubcommand(new ListCmd(_stdin, _stdout, _stderr))
        .setOut(new PrintWriter(_stdout, true))
        .setErr(new PrintWriter(_stderr, true))
        .execute(args);
  }

  @Override
  protected int doCall() {
    log.debug("no concrete sub-command specified via cli, will try to invoke default sub-command: {}", DEFAULT_CMD_NAME);
    val defaultCmd = spec.commandLine().getSubcommands().get(DEFAULT_CMD_NAME);
    if (defaultCmd == null) {
      return die("No sub-command specified and default sub-command not available: " + DEFAULT_CMD_NAME);
    }

    log.debug("will invoke default sub-command: {} with arguments: {}",
        defaultCmd.getCommandName(), unknownCliArgs);

    // invoke default picocli command
    return defaultCmd.execute(unknownCliArgs.toArray(new String[0]));
  }

  static void resetStdioStreams() {
    stdioStreams(null, null, null);
  }

  static void stdioStreams(PrintStream stdout, PrintStream stderr) {
    stdioStreams(null, stdout, stderr);
  }

  static void stdioStreams(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    CmdRunner._stdin = requireNonNullElse(stdin, System.in);
    CmdRunner._stdout = requireNonNullElse(stdout, System.out);
    CmdRunner._stderr = requireNonNullElse(stderr, System.err);
  }
}
