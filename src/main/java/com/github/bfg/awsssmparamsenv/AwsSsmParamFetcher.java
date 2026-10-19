package com.github.bfg.awsssmparamsenv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.io.Closeable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public final class AwsSsmParamFetcher implements Closeable {
  private final SsmClient ssmClient;

  public Map<String, String> fetchParameters(SsmParamQuery query) {
    val sanitizedPaths = sanitizeParameterPaths(query.paths());
    if (sanitizedPaths.isEmpty()) {
      throw new IllegalArgumentException("No AWS SSM parameter paths given.");
    }

    var paramStream = sanitizedPaths.stream()
        .flatMap(it -> fetchParameters(it, query).entrySet().stream());

    if (query.sort() || query.reverseSort()) {
      var comparator = Map.Entry.<String, String>comparingByKey();
      if (query.reverseSort()) comparator = comparator.reversed();
      paramStream = paramStream.sorted(comparator);
    }

    val result = paramStream.collect(
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
            Utils.mergeFunction(query.useLast()), LinkedHashMap::new));

    log.debug("fetched {} AWS SSM parameters: {}", result.size(), result.keySet());
    return result;
  }

  private Map<String, String> fetchParameters(String path, SsmParamQuery query) {
    val result = ssmClient.getParametersByPathPaginator(it ->
            it.path(path)
                .recursive(query.recursive())
                .maxResults(10)
                .withDecryption(true))
        .stream()
        .flatMap(it -> it.parameters().stream())
        .filter(it -> !paramNameAtPath(path, it, query).isBlank())
        .collect(Collectors.toMap(
            it -> paramNameAtPath(path, it, query),
            it -> paramValueAtPath(path, it, query),
            Utils.mergeFunction(query.useLast()),
            LinkedHashMap::new
        ));

    log.debug("[{}] retrieved {} parameters", path, result.size());
    return result;
  }

  private String paramNameAtPath(String path, Parameter it, SsmParamQuery query) {
    var withPathStripped = it.name().substring(path.length() + 1);

    var result = withPathStripped.replaceAll("[^0-9A-Za-z_]+", "_")
        .replace("__", "_")
        .trim();

    if (result.matches("^[0-9]+.*")) {
      log.warn("invalid parameter name at path: {} (starts with a number)", it.name());
      return "";
    }

    return (query.uppercase()) ? result.toUpperCase() : result;
  }

  private String paramValueAtPath(String path, Parameter it, SsmParamQuery query) {
    var value = it.value();
    log.debug("param value at '{}' -> '{}'", it.name(), value);
    return (query.trimValues()) ? value.trim() : value;
  }

  private static List<String> sanitizeParameterPaths(Collection<String> paths) {
    return paths.stream()
        .map(String::trim)
        .map(it -> it.replaceAll("/+$", "")) // remove trailing slashes
        .filter(it -> !it.isEmpty())
        .map(it -> it.startsWith("/") ? it : "/" + it)
        .distinct()
        .toList();
  }

  @Override
  public void close() {
    ssmClient.close();
  }
}
