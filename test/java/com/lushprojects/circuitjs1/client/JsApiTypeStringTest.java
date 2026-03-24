package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.economics.*;
import com.lushprojects.circuitjs1.client.elements.electronics.passives.ResistorElm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("JS API type strings")
class JsApiTypeStringTest extends CircuitJavaSimTestBase {

    @Test
    @DisplayName("getClassName returns simple names independent of package")
    void getClassNameReturnsSimpleNames() {
        CircuitElm resistor = new ResistorElm(0, 0);
        CircuitElm computedSource = new ComputedValueSourceElm(0, 0);
        CircuitElm sfcFlow = new SFCFlowElm(0, 0);

        assertEquals("ResistorElm", resistor.getClassName());
        assertEquals("ComputedValueSourceElm", computedSource.getClassName());
        assertEquals("SFCFlowElm", sfcFlow.getClassName());
    }
}
