package com.github.bfg.awsssmparamsenv;

import lombok.Builder;
import lombok.Singular;

import java.util.List;

@Builder
public record SsmParamQuery(
    @Singular
    List<String> paths,
    boolean recursive,
    boolean sort,
    boolean reverseSort,
    boolean lastOneWins,
    boolean uppercase,
    boolean trimValues
) {
}
