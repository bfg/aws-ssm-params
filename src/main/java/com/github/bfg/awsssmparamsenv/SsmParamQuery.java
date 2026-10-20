package com.github.bfg.awsssmparamsenv;

import lombok.Builder;
import lombok.Singular;

import java.util.List;

@Builder(toBuilder = true)
public record SsmParamQuery(
    @Singular
    List<String> paths,
    boolean recursive,
    boolean sort,
    boolean reverseSort,
    boolean useLast,
    boolean uppercase,
    boolean trimValues
) {
}
