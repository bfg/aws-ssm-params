package com.github.bfg.awsssmparams;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.util.List;

@Builder(toBuilder = true)
public record SsmParamQuery(
    @Singular
    List<@NonNull String> paths,
    boolean recursive,
    boolean sort,
    boolean reverseSort,
    boolean useLast,
    boolean uppercase,
    boolean trimValues
) {
}
