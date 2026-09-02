package com.testingautomation.testautomation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FrameNode {
    private String selector;
    private String selectorType; // css, xpath, etc
    private Integer index;
    private String id;
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FrameNode frameNode = (FrameNode) o;
        return Objects.equals(selector, frameNode.selector) &&
                Objects.equals(selectorType, frameNode.selectorType) &&
                Objects.equals(index, frameNode.index) &&
                Objects.equals(id, frameNode.id) &&
                Objects.equals(name, frameNode.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, selectorType, index, id, name);
    }
}
