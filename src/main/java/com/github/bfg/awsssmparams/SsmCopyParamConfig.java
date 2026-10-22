package com.github.bfg.awsssmparams;

import lombok.Builder;
import lombok.NonNull;

@Builder(toBuilder = true)
public record SsmCopyParamConfig(
    @NonNull String source,
    @NonNull String destination,
    boolean recursive,
    boolean overwrite,
    boolean doIt
) {
}

