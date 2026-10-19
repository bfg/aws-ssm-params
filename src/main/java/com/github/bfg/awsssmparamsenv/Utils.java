package com.github.bfg.awsssmparamsenv;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

@Slf4j
@UtilityClass
class Utils {
  public Stream<String> csvStream(@NonNull Collection<String> strings) {
    return strings.stream()
        .flatMap(Utils::csvStream);
  }

  public Stream<String> csvStream(@NonNull String s) {
    return Arrays.stream(s.split("\\s*,\\s*"))
        .map(String::trim)
        .filter(it -> !it.isEmpty());
  }

  public <T> BinaryOperator<T> mergeFunction(boolean useLast) {
    return (useLast) ?
        (a, b) -> b
        :
        (a, b) -> a;
  }

  public String basename(String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  String dirname(String path) {
    path = path.substring(0, path.lastIndexOf('/'));
    if (path.isEmpty()) {
      path = "/";
    }
    return path;
  }

  private static final Set<Character> globChars = Set.of('*', '?', '[', ']', '{', '}');

  boolean containsGlobSymbols(String str) {
    return globChars.stream().anyMatch(it -> str.indexOf(it) >= 0);
  }
}
