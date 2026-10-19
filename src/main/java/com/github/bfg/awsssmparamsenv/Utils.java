package com.github.bfg.awsssmparamsenv;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

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
}
