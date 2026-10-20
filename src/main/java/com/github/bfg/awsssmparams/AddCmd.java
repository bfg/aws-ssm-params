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

@Command(name = "add",
    description = "Creates parameters from dotenv files",
    sortOptions = false
)
public class AddCmd extends BaseCmd {
  @Option(names = {"-f", "--force"}, description = "Overwrite existing parameters")
  boolean force;

  @Option(names = {"-l", "--list"}, description = "Create StringList parameters")
  boolean list;

  @Option(names = {"-s", "--secure"}, description = "Create SecureString parameters")
  boolean secure;

  @Parameters(index = "0", paramLabel = "PATH", description = "Parameter Store path")
  String path;

  @Parameters(index = "1..*", arity = "0..*", paramLabel = "FILE", description = "Dotenv files (stdin if omitted)")
  List<String> files = List.of();

  public AddCmd(InputStream stdin, PrintStream stdout, PrintStream stderr) {
    super(stdin, stdout, stderr);
  }

  @Override
  protected int doCall() {
    if (list && secure) {
      return die("--list and --secure cannot be used together");
    }

    String parameterPath = path.replaceAll("/+$", "");
    if (!path.startsWith("/")) {
      return die("Bad AWS SSM parameter path: " + path);
    }

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

    try (val ssmClient = createSsmClient()) {
      params.forEach((name, value) -> {
        val ssmName = parameterPath + "/" + name;
        val res = ssmClient.putParameter(it ->
            it.name(ssmName)
                .type(paramType)
                .value(value)
                .overwrite(force)
        );

        stdout.println("add: " + ssmName);
      });
    } catch (Throwable t) {
      return die("Add operation failed", t);
    }

    return 0;
  }

  @SneakyThrows
  private Map<String, String> readDotenv(String file, Map<String, String> acc) {
    val isStdin = (file.isBlank() || file.trim().equals("-"));
    val sourceName = isStdin ? "stdin" : file;
    val inputStream = isStdin ? this.stdin : Files.newInputStream(Paths.get(file));

    try (val reader = new BufferedReader(new InputStreamReader(inputStream))) {
      readDotenv(reader, sourceName, acc);
    }

    return acc;
  }

  @SneakyThrows
  private void readDotenv(BufferedReader reader, String source, Map<String, String> values) {
    String line;
    int lineNumber = 0;
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      String assignment = line.trim();
      if (assignment.isEmpty() || assignment.startsWith("#")) {
        continue;
      }
      if (assignment.startsWith("export ")) {
        assignment = assignment.substring("export ".length()).trim();
      }

      int equals = assignment.indexOf('=');
      if (equals < 0) {
        throw new IllegalArgumentException(source + ":" + lineNumber + ": expected KEY=VALUE");
      }
      String key = assignment.substring(0, equals).trim();
      if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        throw new IllegalArgumentException(source + ":" + lineNumber + ": invalid parameter name: " + key);
      }

      String rawValue = assignment.substring(equals + 1).trim();
      String value;
      if (rawValue.startsWith("\"") || rawValue.startsWith("'")) {
        char quote = rawValue.charAt(0);
        StringBuilder parsed = new StringBuilder();
        int index = 1;
        while (true) {
          if (index == rawValue.length()) {
            String nextLine = reader.readLine();
            if (nextLine == null) {
              throw new IllegalArgumentException(source + ":" + lineNumber + ": unterminated quoted value");
            }
            lineNumber++;
            parsed.append('\n');
            rawValue = nextLine;
            index = 0;
            continue;
          }
          char current = rawValue.charAt(index++);
          if (current == quote) {
            String remainder = rawValue.substring(index).trim();
            if (!remainder.isEmpty() && !remainder.startsWith("#")) {
              throw new IllegalArgumentException(source + ":" + lineNumber + ": unexpected text after quoted value");
            }
            break;
          }
          if (quote == '"' && current == '\\' && index < rawValue.length()) {
            char escaped = rawValue.charAt(index++);
            current = switch (escaped) {
              case 'n' -> '\n';
              case 'r' -> '\r';
              case 't' -> '\t';
              case '"', '\\', '$', '`' -> escaped;
              default -> {
                parsed.append('\\');
                yield escaped;
              }
            };
          }
          parsed.append(current);
        }
        value = parsed.toString();
      } else {
        int comment = -1;
        for (int i = 1; i < rawValue.length(); i++) {
          if (rawValue.charAt(i) == '#' && Character.isWhitespace(rawValue.charAt(i - 1))) {
            comment = i;
            break;
          }
        }
        value = (comment < 0 ? rawValue : rawValue.substring(0, comment)).trim();
      }
      values.put(key, value);
    }
  }
}
