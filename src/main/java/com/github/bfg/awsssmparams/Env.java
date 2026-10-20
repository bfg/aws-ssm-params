package com.github.bfg.awsssmparams;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@UtilityClass
class Env {
  private static final Set<String> trueValues = Set.of("true", "1", "y", "t", "yes");

  public boolean asBoolean(String name) {
    return asBoolean(name, false);
  }

  public boolean asBoolean(String name, boolean defaultValue) {
    val value = asString(name).toLowerCase().trim();
    return (value.isEmpty()) ? defaultValue : trueValues.contains(value);
  }

  public String asString(String name) {
    return asString(name, "");

  }

  public String asString(String name, String defaultValue) {
    val value = System.getenv(name);
    return (value == null) ? defaultValue : value;
  }

  public List<String> asStringList(String name) {
    return Utils.csvStream(asString(name)).toList();
  }

  public <T> T asObj(String name, Function<String, T> mapper, T defaultValue) {
    val strValue = asString(name);
    val objValue = (strValue.isBlank()) ? defaultValue : mapper.apply(strValue);
    return (objValue != null) ? objValue : defaultValue;
  }

  public String firstAsString(String... names) {
    return Arrays.stream(names)
        .map(Env::asString)
        .filter(it -> !it.isEmpty())
        .findFirst()
        .orElse("");
  }
}
