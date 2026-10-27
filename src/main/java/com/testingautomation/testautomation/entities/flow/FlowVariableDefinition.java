package com.testingautomation.testautomation.entities.flow;

import com.testingautomation.testautomation.enums.flow.VariableCharset;
import com.testingautomation.testautomation.enums.flow.VariableType;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Defines how a flow-level variable is generated.
 * Persisted inside the Flow document; never holds runtime-generated values.
 *
 * <p>The {@code length} field refers to the <em>generated portion</em> only.
 * The final value is: {@code prefix + generatedPortion + suffix}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlowVariableDefinition {

    /**
     * Variable type: STRING or NUMBER.
     */
    private VariableType type;

    /**
     * Length of the randomly generated portion (not counting prefix/suffix).
     * Must be >= 1.
     */
    private Integer length;

    /**
     * Character set used to generate the random portion.
     */
    private VariableCharset charset;

    /**
     * Optional fixed prefix prepended to the generated portion.
     */
    private String prefix;

    /**
     * Optional fixed suffix appended to the generated portion.
     */
    private String suffix;

    @Override
    public String toString() {
        return "FlowVariableDefinition{" +
                "type=" + type +
                ", length=" + length +
                ", charset=" + charset +
                ", prefix='" + prefix + '\'' +
                ", suffix='" + suffix + '\'' +
                '}';
    }
}
