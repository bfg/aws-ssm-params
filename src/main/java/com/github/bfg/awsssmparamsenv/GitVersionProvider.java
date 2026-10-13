package com.github.bfg.awsssmparamsenv;

import lombok.SneakyThrows;
import picocli.CommandLine.IVersionProvider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public final class GitVersionProvider implements IVersionProvider {
    private Properties props = loadAppVersion();

    @SneakyThrows
    private Properties loadAppVersion() {
        try (var stream = getClass().getResourceAsStream("/git.properties")) {
            var properties = new Properties();
            if (stream == null) return properties;

            properties.load(stream);
            return properties;
        }
    }

    @Override
    public String[] getVersion() {
        return new String[]{props.get("git.build.version").toString()};
    }

    @SneakyThrows
    public String fullVersion() {
        return props.keySet().stream()
                .map(Object::toString)
                .sorted()
                .filter(it -> !props.get(it).toString().isBlank())
                .map(it -> it + "=" + props.get(it).toString())
                .collect(Collectors.joining(System.lineSeparator()));

    }
}
