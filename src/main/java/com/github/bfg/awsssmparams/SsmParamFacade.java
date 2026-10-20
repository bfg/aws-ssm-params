package com.github.bfg.awsssmparams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterResponse;

import java.io.Closeable;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public final class SsmParamFacade implements Closeable {
  private final SsmClient ssmClient;

  /**
   * Adds parameter.
   *
   * @param path      parameter path
   * @param value     parameter value
   * @param type      parameter type
   * @param overwrite whether to overwrite existing parameter
   * @return create response on success
   * @throws IllegalArgumentException                           if bad parameter name was given
   * @throws software.amazon.awssdk.core.exception.SdkException if AWS SDK call failed
   */
  public PutParameterResponse addParameter(String path, String value, ParameterType type, boolean overwrite) {
    val paramPath = checkParameterPath(path);
    val res = ssmClient.putParameter(it -> it
        .name(paramPath)
        .type(type)
        .value(value)
        .overwrite(overwrite)
    );
    log.debug("added parameter: {} -> {}", paramPath, res);
    return res;
  }

  public List<String> removeParameters(Collection<String> paths, boolean recursive, boolean doIt) {
    val pathsToRemove = sanitizeParameterPaths(paths)
        .stream()
        .flatMap(it -> findParametersForRemoval(it, recursive).stream())
        .distinct()
        .toList();

    log.debug("will remove {} paths: {}", pathsToRemove.size(), pathsToRemove);
    if (doIt) {
      pathsToRemove.forEach(this::removeParameter);
    }

    return pathsToRemove;
  }

  private List<String> findParametersForRemoval(String path, boolean recursive) {
    // is somebody trying to remove a single parameter?
    if (!Utils.containsGlobSymbols(path) && exists(path)) {
      return List.of(path);
    }

    val q = SsmParamQuery.builder()
        .path(path)
        .recursive(recursive)
        .build();

    return fetchParameters(q)
        .stream()
        .map(Parameter::name)
        .distinct()
        .toList();
  }

  public boolean removeParameter(String path) {
    val res = ssmClient.deleteParameter(it -> it.name(path));
    log.debug("removed parameter: {}", path);
    return true;
  }

  public boolean exists(String path) {
    var result = false;
    try {
      val parameter = ssmClient.getParameter(it -> it.name(path));
      result = parameter != null;
    } catch (ParameterNotFoundException e) {
      // not needed
    }

    log.debug("parameter exists: {} -> {}", path, result);
    return result;
  }

  /**
   * Fetches parameters for a given query and returns a {@link Map} of sanitized parameter key -> {@link Parameter} value
   *
   * @param query parameter query
   * @return map
   * @see #paramNameAtPath(String, Parameter, SsmParamQuery)
   * @see #paramValueAtPath(String, Parameter, SsmParamQuery)
   */
  public Map<String, Parameter> fetchParametersAsMap(SsmParamQuery query) {
    var paramStream = sanitizeParameterPaths(query.paths())
        .stream()
        .flatMap(path -> fetchParametersAsMapForPath(path, query));

    if (query.sort() || query.reverseSort()) {
      var comparator = Map.Entry.<String, Parameter>comparingByKey();
      if (query.reverseSort()) comparator = comparator.reversed();
      paramStream = paramStream.sorted(comparator);
    }

    val result = paramStream.collect(mapCollector(query));

    log.debug("fetched {} AWS SSM parameters: {}", result.size(), result.keySet());
    return result;
  }

  private Stream<Map.Entry<String, Parameter>> fetchParametersAsMapForPath(String path, SsmParamQuery query) {
    val pathQuery = query.toBuilder().clearPaths().path(path).build();
    return fetchParameters(pathQuery).stream()
        // convert to a stream of env var -> value entries
        .map(x -> Map.entry(paramNameAtPath(path, x, pathQuery), x))
        .filter(it -> !it.getKey().isBlank())
        ;
  }

  /**
   * Fetches parameters for a given query and returns a {@link Map} of sanitized parameter key
   * [{@link #paramNameAtPath(String, Parameter, SsmParamQuery)}] => [{@link #paramValueAtPath(String, Parameter, SsmParamQuery)}]
   * value.
   *
   * @param query parameter query
   * @return map
   * @see #paramNameAtPath(String, Parameter, SsmParamQuery)
   * @see #paramValueAtPath(String, Parameter, SsmParamQuery)
   */
  public Map<String, String> fetchParameterValuesAsMap(SsmParamQuery query) {
    return fetchParametersAsMap(query).entrySet().stream()
        .map(it ->
            Map.entry(it.getKey(), paramValueAtPath(it.getValue().name(), it.getValue(), query))
        )
        .collect(mapCollector(query))
        ;
  }

  private <K, V> Collector<Map.Entry<K, V>, ?, Map<K, V>> mapCollector(SsmParamQuery query) {
    return Collectors.toMap(
        Map.Entry::getKey, Map.Entry::getValue,
        Utils.mergeFunction(query.useLast()), LinkedHashMap::new);
  }

  /**
   * Returns AWS SSM parameter key.
   *
   * @param path  parameter path
   * @param it    AWS SSM parameter
   * @param query SSM parameter query
   * @return validated SSM parameter key which never contains other characters as <code>0-9A-Za-z_</code>
   */
  private String paramNameAtPath(String path, Parameter it, SsmParamQuery query) {
    var paramPath = it.name();
    if (paramPath.length() > path.length() && paramPath.startsWith(path)) {
      paramPath = paramPath.substring(path.length() + 1); // removes trailing slash
    } else {
      log.warn("paramNameAtPath() -> parameter path {} is not part of {}", paramPath, path);
    }

    var result = paramPath.replaceAll("[^0-9A-Za-z_]+", "_")
        .replace("__", "_")
        .trim();

    if (result.matches("^[0-9]+.*")) {
      log.warn("invalid parameter name at path: {} (starts with a number)", it.name());
      return "";
    }

    return (query.uppercase()) ? result.toUpperCase() : result;
  }

  /**
   * Returns AWS SSM parameter value.
   *
   * @param path  parameter path
   * @param it    AWS SSM parameter
   * @param query SSM parameter query
   * @return validated SSM parameter value
   */
  private String paramValueAtPath(String path, Parameter it, SsmParamQuery query) {
    var value = it.value();
    val result = (query.trimValues()) ? value.trim() : value;

    log.trace("param value at `{}` -> `{}` -> `{}`", it.name(), value, result);
    return result;
  }

  private List<String> sanitizeParameterPaths(Collection<String> paths) {
    val result = paths.stream()
        .map(SsmParamFacade::sanitizeParameterPath)
        .filter(it -> !it.isEmpty())
        .distinct()
        .toList();

    if (result.isEmpty()) {
      throw new IllegalArgumentException("No AWS SSM parameter paths given.");
    }

    return result;
  }

  private static String sanitizeParameterPath(String path) {
    // replace multiple slashes with a single one
    var result = path.trim().replaceAll("/+", "/");

    // remove trailing slashes if not empty or root
    if (result.length() > 1) {
      result = result.replaceAll("/+$", "");
    }

    // must start with / -> otherwise invalid path
    result = result.startsWith("/") ? result : "";

    log.trace("sanitizeParameterPath() `{}` -> `{}`", path, result);
    return result;
  }

  /**
   * Checks AWS SSM parameter path.
   *
   * @param path use-specified SSM parameter path
   * @return sanitized parameter path
   * @throws IllegalArgumentException when invalid path is given
   */
  public static String checkParameterPath(String path) {
    val sanitizedPath = sanitizeParameterPath(path);
    if (sanitizedPath.isEmpty()) {
      throw new IllegalArgumentException("Invalid SSM parameter path: `" + path + "`");
    }
    return sanitizedPath;
  }

  /**
   * Fetches parameters at a given paths with glob(3) support.
   *
   * @param query query
   * @return list of fetched parameters
   */
  public List<Parameter> fetchParameters(SsmParamQuery query) {
    var paramStream = sanitizeParameterPaths(query.paths()).stream()
        .flatMap(it -> fetchParameters(it, query).stream());

    val result = paramStream.toList();
    log.debug("retrieved {} parameters in total", result.size());
    return result;
  }

  private List<Parameter> fetchParameters(String path, SsmParamQuery query) {
    val basename = Utils.basename(path);

    val containsGlob = Utils.containsGlobSymbols(basename);
    val rootPath = (containsGlob) ? Utils.dirname(path) : path;

    var paramStream = ssmClient.getParametersByPathPaginator(it -> it
            .path(rootPath)
            .recursive(query.recursive())
            .withDecryption(true)
            .maxResults(10))
        .stream()
        .flatMap(it -> it.parameters().stream());

    if (containsGlob) {
      val globMatcher = FileSystems.getDefault().getPathMatcher("glob:" + basename);
      paramStream = paramStream.filter(it -> {
        val paramBasename = Path.of(Utils.basename(it.name()));
        return globMatcher.matches(paramBasename);
      });
    }

    if (query.sort() || query.reverseSort()) {
      var comparator = Comparator.comparing(Parameter::name);
      if (query.reverseSort()) comparator = comparator.reversed();
      paramStream = paramStream.sorted(comparator);
    }

    val result = paramStream.toList();
    log.debug("[{}] retrieved {} parameters", path, result.size());
    return result;
  }

  @Override
  public void close() {
    ssmClient.close();
  }
}
