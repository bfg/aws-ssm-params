package com.github.bfg.awsssmparams;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.util.List;

/**
 * {@link SsmParamFacade#removeParameters(SsmRemoveParamConfig)} task configuration
 *
 * @param paths     list of paths/glob patterns to remove
 * @param recursive enable recursive removal?
 * @param doIt      whether to really remove the parameters
 */
@Builder(toBuilder = true)
public record SsmRemoveParamConfig(
    @NonNull @Singular List<String> paths,
    boolean recursive,
    boolean doIt
) {
}

