package com.github.bfg.awsssmparamsenv;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.io.Closeable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
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

    BinaryOperator<String> mergeFunc = (query.useLast()) ?
        (a, b) -> b
        :
        (a, b) -> a;

    val result = paramStream.collect(
        Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, mergeFunc, LinkedHashMap::new));

    log.debug("fetched {} AWS SSM parameters: {}", result.size(), result.keySet());
    return result;
  }

  private Map<String, String> fetchParameters(String path, SsmParamQuery query) {
    var result = new LinkedHashMap<String, String>();

    log.debug("fetching parameters for path: {}", path);
    String nextToken = null;
    var attempt = 0;
    do {
      attempt++;
      var reqBuilder = GetParametersByPathRequest.builder()
          .path(path)
          .recursive(query.recursive())
          .maxResults(10)
          .nextToken(nextToken)
          .withDecryption(true);

      log.trace("[#{} {}] fetching parameters", attempt, path);
      var params = ssmClient.getParametersByPath(reqBuilder.build());
      log.debug("[#{} {}] retrieved {} parameter(s)", attempt, path, params.parameters().size());
      nextToken = params.nextToken();

      params.parameters().forEach(it -> {
        var name = paramNameAtPath(path, it, query);
        if (name.isEmpty()) return;
        result.put(name, paramValueAtPath(path, it, query));
      });
    } while (nextToken != null);

    log.debug("[{}] retrieved {} parameters in {} attempts", path, result.size(), attempt);
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
