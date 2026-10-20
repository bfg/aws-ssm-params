package com.github.bfg.awsssmparams;

import lombok.SneakyThrows;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.awssdk.services.ssm.model.ParameterType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Command(name = "add",
    description = "Creates AWS SSM parameters from dotenv files",
    sortOptions = false
)
public class AddCmd extends BaseCmd {
  @Option(names = {"-f", "--force"}, description = "Overwrite existing parameters")
  boolean force;

  @Option(names = {"--list"}, description = "Create parameters of StringList type")
  boolean list;

  @Option(names = {"-s", "--secure"}, description = "Create parameters of SecureString type")
  boolean secure;

  @Parameters(index = "0", paramLabel = "PATH", description = "AWS SSM Parameter Store path to add parameters into")
  String path;

  @Parameters(index = "1..*", arity = "0..*", paramLabel = "FILE", description = "Dotenv files (stdin if omitted or \"-\")")
  List<String> files = List.of();

  public AddCmd(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    super(stdin, stdout, stderr);
  }

  @Override
  protected int doCall() {
    if (list && secure) {
      return die("--list and --secure cannot be used together");
    }

    val parameterPath = SsmParamFacade.checkParameterPath(path);

    val params = new LinkedHashMap<String, String>();
    if (files.isEmpty()) {
      readDotenv("", params);
    } else {
      files.forEach(it -> readDotenv(it, params));
    }
    if (params.isEmpty()) {
      return die("No parameters were read from input files.");
    }

    val paramType = list ? ParameterType.STRING_LIST
        : secure ? ParameterType.SECURE_STRING : ParameterType.STRING;

    try (val facade = createSsmFacade()) {
      params.forEach((name, value) -> {
        val ssmPath = parameterPath + "/" + name;
        facade.addParameter(ssmPath, value, paramType, force);
        stdout.println("add: " + ssmPath);
      });
    } catch (Throwable t) {
      return die("Add operation failed", t);
    }

    return 0;
  }

  @SneakyThrows
  private long readDotenv(String file, Map<String, String> acc) {
    val isStdin = (file.isBlank() || file.trim().equals("-"));
    val sourceName = isStdin ? "stdin" : file;
    val inputStream = isStdin ? this.stdin : Files.newInputStream(Paths.get(file));

    try (val reader = new BufferedReader(new InputStreamReader(inputStream))) {
      val numRead = readDotenv(reader, sourceName, acc);
      log.debug("read {} values from: {}", numRead, sourceName);
      return numRead;
    }
  }

  private long readDotenv(BufferedReader reader, String source, Map<String, String> values) {
    val numLines = new AtomicInteger();
    return reader.lines()
        .peek(it -> numLines.incrementAndGet())
        .map(String::trim)
        .filter(it -> !it.startsWith("#"))
        .map(it -> parseDotEnvLine(it, source, numLines.get()))
        .filter(Objects::nonNull)
        .filter(it -> {
          values.put(it.getKey(), it.getValue());
          return true;
        })
        .count();
  }

  private Map.Entry<String, String> parseDotEnvLine(String line, String source, int lineNumber) {
    val separators = Stream.of('=', ':');
    return separators
        .map(it -> parseDotEnvLine(line, it, source, lineNumber))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private Map.Entry<String, String> parseDotEnvLine(String line, char separator, String source, int lineNumber) {
    int separatorIdx = line.indexOf(separator);
    if (separatorIdx < 0) {
      log.debug("[{}:{}] invalid syntax [expected <KEY>{}<VALUE>]: no `{}` found", source, lineNumber, separator, separator);
      return null;
    }

    val key = line.substring(0, separatorIdx).trim();
    if (key.isEmpty()) {
      log.warn("[{}:{}] empty key", source, lineNumber);
      return null;
    } else if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      log.warn("[{}:{}] invalid parameter name: `{}`", source, lineNumber, key);
      return null;
    }

    var value = line.substring(separatorIdx + 1).trim();
    if (value.startsWith("\"") || value.endsWith("\"")) {
      if (!(value.startsWith("\"") && value.endsWith("\""))) {
        log.warn("[{}:{}] invalid parameter value for key `{}`: unmatched quote", source, lineNumber, key);
        return null;
      }

      value = value.substring(1, value.length() - 1);
    }

    if (value.isEmpty()) {
      log.warn("[{}:{}] empty value for key `{}`", source, lineNumber, key);
      return null;
    }

    log.debug("[{}:{}] parseDotEnvLine: `{}` -> `{}` =>`{}`", source, lineNumber, line, key, value);
    return Map.entry(key, value);
  }
}
