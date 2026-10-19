package com.github.bfg.awsssmparamsenv;

import ch.qos.logback.classic.Level;
import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UtilityClass
class Logback {
  void maybeSetLogLevel(String logLevel) {
    maybeSetLogLevel(Logger.ROOT_LOGGER_NAME, logLevel);
  }

  void maybeSetLogLevel(String loggerName, String logLevel) {
    if (loggerName.isBlank() || logLevel.isBlank()) return;
    setLogLevel(loggerName, logLevel);
  }

  void setLogLevel(String loggerName, String logLevel) {
    if (loggerName.isEmpty()) loggerName = Logger.ROOT_LOGGER_NAME;
    var logbackLevel = Level.toLevel(logLevel.trim().toUpperCase());
    ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(loggerName)).setLevel(logbackLevel);
  }
}
