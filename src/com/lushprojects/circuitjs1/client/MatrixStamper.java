package com.lushprojects.circuitjs1.client;

class MatrixStamper {
    private final CirSim sim;

    MatrixStamper(CirSim sim) {
        this.sim = sim;
    }

    void stampVCVS(int n1, int n2, double coef, int vs) {
        int vn = sim.getCircuitAnalyzer().getNodeList().size() + vs;
        stampMatrix(vn, n1, coef);
        stampMatrix(vn, n2, -coef);
    }

    void stampVoltageSource(int n1, int n2, int vs, double v) {
        int vn = sim.getCircuitAnalyzer().getNodeList().size() + vs;
        stampMatrix(vn, n1, -1);
        stampMatrix(vn, n2, 1);
        stampRightSide(vn, v);
        stampMatrix(n1, vn, 1);
        stampMatrix(n2, vn, -1);
    }

    void stampVoltageSource(int n1, int n2, int vs) {
        int vn = sim.getCircuitAnalyzer().getNodeList().size() + vs;
        stampMatrix(vn, n1, -1);
        stampMatrix(vn, n2, 1);
        stampRightSide(vn);
        stampMatrix(n1, vn, 1);
        stampMatrix(n2, vn, -1);
    }

    void updateVoltageSource(int n1, int n2, int vs, double v) {
        int vn = sim.getCircuitAnalyzer().getNodeList().size() + vs;
        stampRightSide(vn, v);
    }

    void stampResistor(int n1, int n2, double r) {
        double r0 = 1 / r;
        if (Double.isNaN(r0) || Double.isInfinite(r0)) {
            System.out.print("bad resistance " + r + " " + r0 + "\n");
            int a = 0;
            a /= a;
        }
        stampMatrix(n1, n1, r0);
        stampMatrix(n2, n2, r0);
        stampMatrix(n1, n2, -r0);
        stampMatrix(n2, n1, -r0);
    }

    void stampConductance(int n1, int n2, double r0) {
        stampMatrix(n1, n1, r0);
        stampMatrix(n2, n2, r0);
        stampMatrix(n1, n2, -r0);
        stampMatrix(n2, n1, -r0);
    }

    void stampVCCurrentSource(int cn1, int cn2, int vn1, int vn2, double g) {
        stampMatrix(cn1, vn1, g);
        stampMatrix(cn2, vn2, g);
        stampMatrix(cn1, vn2, -g);
        stampMatrix(cn2, vn1, -g);
    }

    void stampCurrentSource(int n1, int n2, double i) {
        stampRightSide(n1, -i);
        stampRightSide(n2, i);
    }

    void stampCCCS(int n1, int n2, int vs, double gain) {
        int vn = sim.getCircuitAnalyzer().getNodeList().size() + vs;
        stampMatrix(n1, vn, gain);
        stampMatrix(n2, vn, -gain);
    }

    void stampMatrix(int i, int j, double x) {
        if (Double.isInfinite(x))
            CirSim.debugger();
        if (Double.isNaN(x)) {
            CirSim.console("stampMatrix: NaN at i=" + i + " j=" + j);
            CirSim.debugger();
        }
        if (i > 0 && j > 0) {
            if (sim.getSolverMatrixState().circuitNeedsMap) {
                i = sim.getSolverMatrixState().circuitRowInfo[i - 1].mapRow;
                RowInfo ri = sim.getSolverMatrixState().circuitRowInfo[j - 1];
                if (ri.type == RowInfo.ROW_CONST) {
                    sim.getSolverMatrixState().circuitRightSide[i] -= x * ri.value;
                    return;
                }
                j = ri.mapCol;
            } else {
                i--;
                j--;
            }
            sim.getSolverMatrixState().circuitMatrix[i][j] += x;
        }
    }

    void stampRightSide(int i, double x) {
        if (i > 0) {
            if (sim.getSolverMatrixState().circuitNeedsMap) {
                i = sim.getSolverMatrixState().circuitRowInfo[i - 1].mapRow;
            } else
                i--;
            sim.getSolverMatrixState().circuitRightSide[i] += x;
        }
    }

    void stampRightSide(int i) {
        if (i > 0)
            sim.getSolverMatrixState().circuitRowInfo[i - 1].rsChanges = true;
    }

    void stampNonLinear(int i) {
        if (i > 0) {
            sim.getSolverMatrixState().circuitRowInfo[i - 1].lsChanges = true;
        }
    }

    String getMatrixRowInfo(int row) {
        int nodeCount = sim.getCircuitAnalyzer().getNodeList().size();

        int origRow = row;
        if (sim.getSolverMatrixState().circuitRowInfo != null) {
            for (int i = 0; i < sim.getSolverMatrixState().circuitRowInfo.length; i++) {
                if (sim.getSolverMatrixState().circuitRowInfo[i].mapRow == row) {
                    origRow = i;
                    break;
                }
            }
        }

        CirSim.console("getMatrixRowInfo: simplifiedRow=" + row + " origRow=" + origRow + " nodeCount=" + nodeCount);

        if (origRow < nodeCount - 1) {
            int nodeNum = origRow + 1;
            CircuitNode cn = sim.getCircuitNode(nodeNum);
            if (cn != null && cn.links.size() > 0) {
                String info = "Row " + row + " (origRow " + origRow + ", node " + nodeNum + ") connected to:";
                for (int i = 0; i < cn.links.size(); i++) {
                    CircuitElm elm = cn.links.get(i).elm;
                    int elmNode = cn.links.get(i).num;
                    info += " " + elm.getClass().getSimpleName() + "[node " + elmNode + "]";
                }
                CirSim.console(info);
                return info;
            }
            String info = "Row " + row + " (origRow " + origRow + ", node " + nodeNum + ")";
            CirSim.console(info);
            return info;
        } else {
            int vsNum = origRow - (nodeCount - 1);
            CirSim.console("Looking for voltage source " + vsNum);
            for (int i = 0; i < sim.elmList.size(); i++) {
                CircuitElm elm = sim.getElm(i);
                int vsCount = elm.getVoltageSourceCount();
                if (vsCount > 0) {
                    CirSim.console("  Element " + elm.getClass().getSimpleName() + " voltSource=" + elm.voltSource + " count=" + vsCount);
                    if (elm.voltSource <= vsNum && elm.voltSource + vsCount > vsNum) {
                        String info = "Row " + row + " (origRow " + origRow + ", voltage source " + vsNum + " of " + elm.getClass().getSimpleName() + ")";
                        CirSim.console(info);
                        return info;
                    }
                }
            }
            String info = "Row " + row + " (origRow " + origRow + ", voltage source " + vsNum + " - owner not found)";
            CirSim.console(info);
            return info;
        }
    }
}