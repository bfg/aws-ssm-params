package com.github.bfg.awsssmparamsenv

class BufPrintStream extends PrintStream {
  private final String name;
  private final ByteArrayOutputStream baos

  private BufPrintStream(String name, ByteArrayOutputStream baos) {
    super(baos, true)
    this.name = name
    this.baos = baos
  }

  static BufPrintStream create(String name) {
    new BufPrintStream(name, new ByteArrayOutputStream())
  }

  List<String> lines() {
    baos.flush()
    baos.toString().lines().toList()
  }

  BufPrintStream clear() {
    baos.reset()
    return this
  }

  String text() {
    baos.toString()
  }

  @Override
  String toString() {
    return "PrintStream[$name]"
  }
}
