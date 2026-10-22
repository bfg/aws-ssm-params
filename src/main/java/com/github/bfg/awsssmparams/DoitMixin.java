package com.github.bfg.awsssmparams;

import lombok.val;
import picocli.CommandLine.Option;

final class DoitMixin {
  @Option(names = {"-y", "--do-it"}, description = "Actually perform potentially destructive operations")
  boolean doIt = false;

  public boolean enabled() {
    return doIt;
  }

  /**
   *
   * @param msg message to append
   * @return message prepended with dry run message if {{@link #doIt}} is false.
   */
  public String maybeDryRun(String msg) {
    val prefix = (doIt) ? "" : "[dry-run] ";
    return prefix + msg;
  }
}
