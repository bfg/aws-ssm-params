package com.github.bfg.awsssmparams;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.io.Closeable;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
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

  /**
   * Removes one or more parameters.
   *
   * @param cfg parameter removal config
   * @return number of removed parameters
   */
  public long removeParameters(SsmRemoveParamConfig cfg) {
    return removeParameters(cfg, this::emptyConsumer);
  }

  /**
   * Removes one or more parameters.
   *
   * @param cfg      parameter removal config
   * @param consumer removal consumer - invoked with removed path as soon it's removed
   * @return number of removed parameters
   */
  public long removeParameters(SsmRemoveParamConfig cfg, Consumer<String> consumer) {
    val pathsToRemove = sanitizeParameterPaths(cfg.paths())
        .stream()
        .flatMap(it -> findParametersForRemoval(it, cfg.recursive()).stream())
        .distinct()
        .toList();

    log.debug("will remove {} paths: {}", pathsToRemove.size(), pathsToRemove);
    val numRemoved = pathsToRemove.stream()
        .map(it -> {
          var wasRemoved = false;
          if (cfg.doIt()) {
            removeParameter(it);
            wasRemoved = true;
          }
          runConsumer(consumer, it);

          return wasRemoved;
        })
        .filter(it -> it)
        .count();

    log.debug("removed {} SSM parameter(s)", numRemoved);
    return numRemoved;
  }

  /**
   * Removes a single SSM parameter
   *
   * @param path parameter path
   * @return whether SSM parameter was removed
   */
  public boolean removeParameter(String path) {
    try {
      ssmClient.deleteParameter(it -> it.name(path));
      log.debug("removed parameter: {}", path);
      return true;
    } catch (ParameterNotFoundException t) {
      return false;
    }
  }

  public long removeParameters(Collection<String> paths) {
    val numRemoved = paths.stream()
        .filter(this::removeParameter)
        .count();

    log.debug("removed {} parameter(s)", numRemoved);
    return numRemoved;
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

  /**
   * Tells whether SSM parameter exists.
   *
   * @param path AWS SSM path
   * @return true/false
   * @see #isDirectory(String)
   */
  public boolean exists(String path) {
    var result = fetchParameter(path) != null;
    log.debug("parameter exists: {} -> {}", path, result);
    return result;
  }

  public Parameter fetchParameter(String path) {
    val finalPath = sanitizeParameterPath(path);
    try {
      val result = ssmClient.getParameter(it -> it.name(finalPath).withDecryption(true)).parameter();
      log.debug("fetched parameter: {}", path);
      return result;
    } catch (ParameterNotFoundException e) {
      // not needed
    }

    return null;
  }

  /**
   * Tells whether given path is a "directory"
   *
   * @param path AWS SSM parameter path
   * @return true/false
   * @see #exists(String)
   */
  public boolean isDirectory(String path) {
    val sanitizedPath = sanitizeParameterPath(path);
    val nameFilter = ParameterStringFilter.builder()
        .key("Path")
        .values(sanitizedPath)
        .option("Recursive")
        .build();

    val pathPrefixLength = sanitizedPath.length() + 1;
    val numChildren = ssmClient.describeParameters(it -> it.maxResults(5).parameterFilters(nameFilter))
        .parameters()
        .stream()
        .map(ParameterMetadata::name)
        .filter(it -> it.length() >= pathPrefixLength)
        .map(it -> it.substring(pathPrefixLength))
        .filter(it -> !it.isBlank())
        .count();

    val result = numChildren > 0;
    log.debug("isDirectory: {} => {} ({} children)", sanitizedPath, result, numChildren);
    return result;
  }

  /**
   * Copies one or more parameters from one location to another.
   *
   * @param cfg copy task configuration
   * @return number of copied parameters
   */
  public long copyParameters(@NonNull SsmCopyParamConfig cfg) {
    return copyParameters(cfg, this::emptyConsumer);
  }

  /**
   * Copies one or more parameters from one location to another.
   *
   * @param cfg      copy task configuration
   * @param consumer consumer that gets invoked as soon as single copy operation is complete
   * @return number of copied parameters
   */
  public long copyParameters(@NonNull SsmCopyParamConfig cfg,
                             @NonNull Consumer<Map.Entry<String, String>> consumer) {
    return copyParameters(cfg, false, consumer);
  }

  private long copyParameters(@NonNull SsmCopyParamConfig cfg,
                              boolean removeSrc,
                              @NonNull Consumer<Map.Entry<String, String>> consumer) {
    val srcPath = checkParameterPath(cfg.source());
    val dstPath = checkParameterPath(cfg.destination());
    if (Utils.containsGlobSymbols(dstPath)) {
      throw new IllegalArgumentException("Invalid destination path: " + dstPath);
    }

    log.debug("copy parameters: {} -> {} -> {}", srcPath, dstPath, cfg);

    val newParamSrcPath = Utils.containsGlobSymbols(srcPath) ? Utils.dirname(srcPath) : srcPath;
    val q = SsmParamQuery.builder().path(srcPath).recursive(cfg.recursive()).build();
    val parametersToCopy = fetchParameters(srcPath, q);

    // time to copy them
    val numOps = parametersToCopy.stream()
        .map(parameter -> {
          val newParameterPutReq = newParameterBuilder(parameter, newParamSrcPath, dstPath)
              .overwrite(cfg.overwrite())
              .build();

          var wasCopied = false;

          if (cfg.doIt()) {
            ssmClient.putParameter(newParameterPutReq);
            log.debug("copied parameter: {} -> {}", parameter.name(), newParameterPutReq.name());
            if (removeSrc) {
              removeParameter(parameter.name());
            }
            wasCopied = true;
          }

          val entry = Map.entry(parameter.name(), newParameterPutReq.name());
          runConsumer(consumer, entry);

          return wasCopied;
        })
        .filter(it -> it)
        .count();

    val opName = (removeSrc ? "moved" : "copied");
    log.debug("{} {} parameter(s)", opName, numOps);
    return numOps;
  }

  private PutParameterRequest.Builder newParameterBuilder(Parameter parameter, String srcPath, String dstPath) {
    val srcParamPath = parameter.name();
    var bn = srcParamPath.substring(srcPath.length());
    if (bn.isEmpty()) bn = "/" + Utils.basename(srcParamPath);

    val dstParamPath = dstPath + bn;
    val nameFilter = ParameterStringFilter.builder().key("Name").option("Equals").values(srcParamPath).build();
    val metadata = ssmClient.describeParameters(it -> it
            .parameterFilters(nameFilter))
        .parameters().stream()
        .filter(it -> it.name().equals(srcParamPath))
        .findFirst()
        .orElse(null);
    val tags = ssmClient.listTagsForResource(it -> it
            .resourceType(ResourceTypeForTagging.PARAMETER)
            .resourceId(srcParamPath))
        .tagList();

    val reqBuilder = PutParameterRequest.builder()
        .name(dstParamPath)
        .value(parameter.value())
        .type(parameter.typeAsString())
        .overwrite(false);

    if (metadata != null) {
      if (metadata.description() != null) reqBuilder.description(metadata.description());
      if (metadata.keyId() != null) reqBuilder.keyId(metadata.keyId());
      if (metadata.allowedPattern() != null) reqBuilder.allowedPattern(metadata.allowedPattern());
      if (metadata.tierAsString() != null) reqBuilder.tier(metadata.tierAsString());
      if (metadata.dataType() != null && parameter.type() == ParameterType.STRING)
        reqBuilder.dataType(metadata.dataType());
      if (!metadata.policies().isEmpty()) {
        reqBuilder.policies(metadata.policies().stream()
            .map(it -> it.policyText())
            .collect(Collectors.joining(",", "[", "]")));
      }
    }

    if (!tags.isEmpty()) reqBuilder.tags(tags);

    log.debug("copying parameter: {} -> {}", srcParamPath, dstParamPath);
    return reqBuilder;
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
    path = sanitizeParameterPath(path);

    // does basename contain glob pattern?
    val basename = Utils.basename(path);
    val containsGlob = Utils.containsGlobSymbols(basename);

    var searchPath = "";
    if (containsGlob) {
      // contains glob, we'll use parent to search for parameters
      searchPath = Utils.dirname(path);
    } else if (isDirectory(path)) {
      // if it's a directory, use it for search path
      searchPath = path;
    } else {
      // maybe it's a concrete, single existing parameter
      val parameter = fetchParameter(path);

      // return the parameter or empty list
      return (parameter != null) ? List.of(parameter) : List.of();
    }

    // well, time to get some parameters :-)
    val finalSearchPath = searchPath;
    var paramStream = ssmClient.getParametersByPathPaginator(it -> it
            .path(finalSearchPath)
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

  private <T> void runConsumer(Consumer<T> consumer, T arg) {
    try {
      consumer.accept(arg);
    } catch (Throwable t) {
      log.warn("exception while running consumer with {}: {}", arg, t.getMessage(), t);
    }
  }

  private void emptyConsumer(Object arg) {
  }

  @Override
  public void close() {
    ssmClient.close();
  }
}
