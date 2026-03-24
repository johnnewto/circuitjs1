package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.elements.ActionTimeDialog;

import com.lushprojects.circuitjs1.client.core.CircuitMatrixOps;
import com.lushprojects.circuitjs1.client.elements.annotation.GraphicElm;
import com.lushprojects.circuitjs1.client.elements.economics.*;
import com.lushprojects.circuitjs1.client.elements.electronics.analog.VCCSElm;
import com.lushprojects.circuitjs1.client.elements.electronics.digital.LogicInputElm;
import com.lushprojects.circuitjs1.client.elements.electronics.passives.*;
import com.lushprojects.circuitjs1.client.elements.electronics.sources.*;
import com.lushprojects.circuitjs1.client.elements.electronics.wiring.*;
import com.lushprojects.circuitjs1.client.elements.misc.ScopeElm;

import com.lushprojects.circuitjs1.client.ui.VariableBrowserDialog;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class CircuitAnalyzer {
    private final CirSim sim;
    private Vector<CircuitNode> nodeList;
    private HashMap<Point, CirSim.NodeMapEntry> nodeMap;
    private Vector<CirSim.WireInfo> wireInfoList;
    private Vector<Integer> unconnectedNodes;
    private Vector<CircuitElm> nodesWithGroundConnection;
    private int nodesWithGroundConnectionCount;

    CircuitAnalyzer(CirSim sim) {
        this.sim = sim;
    }

    public Vector<CircuitNode> getNodeList() {
        return nodeList;
    }

    Vector<CirSim.WireInfo> getWireInfoList() {
        return wireInfoList;
    }

    Vector<Integer> getUnconnectedNodes() {
        return unconnectedNodes;
    }

    Vector<CircuitElm> getNodesWithGroundConnection() {
        return nodesWithGroundConnection;
    }

    int getNodesWithGroundConnectionCount() {
        return nodesWithGroundConnectionCount;
    }

    CircuitNode getCircuitNode(int n) {
        if (nodeList == null || n >= nodeList.size())
            return null;
        return nodeList.elementAt(n);
    }

    void calculateWireClosure() {
        int i;
        LabeledNodeElm.resetNodeList();
        GroundElm.resetNodeList();
        nodeMap = new HashMap<Point, CirSim.NodeMapEntry>();

        for (i = 0; i != sim.elmList.size(); i++)
            sim.getElm(i).registerLabels();

        wireInfoList = new Vector<CirSim.WireInfo>();
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (!ce.isRemovableWire())
                continue;
            ce.hasWireInfo = false;
            wireInfoList.add(new CirSim.WireInfo(ce));
            Point p0 = ce.getPost(0);
            CirSim.NodeMapEntry cn = nodeMap.get(p0);

            Point p1 = ce.getConnectedPost();
            if (p1 == null) {
                if (cn == null) {
                    cn = new CirSim.NodeMapEntry();
                    nodeMap.put(p0, cn);
                }
                continue;
            }
            CirSim.NodeMapEntry cn2 = nodeMap.get(p1);
            if (cn != null && cn2 != null) {
                for (Map.Entry<Point, CirSim.NodeMapEntry> entry : nodeMap.entrySet()) {
                    if (entry.getValue() == cn2)
                        entry.setValue(cn);
                }
                continue;
            }
            if (cn != null) {
                nodeMap.put(p1, cn);
                continue;
            }
            if (cn2 != null) {
                nodeMap.put(p0, cn2);
                continue;
            }
            cn = new CirSim.NodeMapEntry();
            nodeMap.put(p0, cn);
            nodeMap.put(p1, cn);
        }
    }

    boolean calcWireInfo() {
        int i;
        int moved = 0;

        for (i = 0; i != wireInfoList.size(); i++) {
            CirSim.WireInfo wi = wireInfoList.get(i);
            CircuitElm wire = wi.wire;
            CircuitNode cn1 = nodeList.get(wire.getNode(0));
            int j;

            Vector<CircuitElm> neighbors0 = new Vector<CircuitElm>();
            Vector<CircuitElm> neighbors1 = new Vector<CircuitElm>();
            boolean isReady0 = true, isReady1 = !(wire instanceof GroundElm);

            for (j = 0; j != cn1.links.size(); j++) {
                CircuitNodeLink cnl = cn1.links.get(j);
                CircuitElm ce = cnl.elm;
                if (ce == wire)
                    continue;
                Point pt = ce.getPost(cnl.num);

                boolean notReady = (ce.isRemovableWire() && !ce.hasWireInfo);

                if (pt.x == wire.x && pt.y == wire.y) {
                    neighbors0.add(ce);
                    if (notReady)
                        isReady0 = false;
                } else if (wire.getPostCount() > 1) {
                    Point p2 = wire.getConnectedPost();
                    if (pt.x == p2.x && pt.y == p2.y) {
                        neighbors1.add(ce);
                        if (notReady)
                            isReady1 = false;
                    }
                } else if (ce instanceof LabeledNodeElm && wire instanceof LabeledNodeElm &&
                           ((LabeledNodeElm) ce).text == ((LabeledNodeElm) wire).text) {
                    neighbors1.add(ce);
                    if (notReady)
                        isReady1 = false;
                }
            }

            if (isReady0) {
                wi.neighbors = neighbors0;
                wi.post = 0;
                wire.hasWireInfo = true;
                moved = 0;
            } else if (isReady1) {
                wi.neighbors = neighbors1;
                wi.post = 1;
                wire.hasWireInfo = true;
                moved = 0;
            } else {
                wireInfoList.add(wireInfoList.remove(i--));
                moved++;
                if (moved > wireInfoList.size() * 2) {
                    sim.stop("wire loop detected", wire);
                    return false;
                }
            }
        }

        return true;
    }

    void setGroundNode(boolean subcircuit) {
        int i;
        boolean gotGround = false;
        boolean gotRail = false;
        CircuitElm volt = null;

        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof GroundElm) {
                gotGround = true;
                CirSim.NodeMapEntry nme = nodeMap.get(ce.getPost(0));
                nme.node = 0;
                break;
            }
            if (ce instanceof RailElm)
                gotRail = true;
            if (volt == null && ce instanceof VoltageElm)
                volt = ce;
        }

        if (!subcircuit && !gotGround && volt != null && !gotRail) {
            CircuitNode cn = new CircuitNode();
            Point pt = volt.getPost(0);
            nodeList.addElement(cn);

            CirSim.NodeMapEntry cln = nodeMap.get(pt);
            if (cln != null)
                cln.node = 0;
            else
                nodeMap.put(pt, new CirSim.NodeMapEntry(0));
        } else {
            CircuitNode cn = new CircuitNode();
            nodeList.addElement(cn);
        }
    }

    void makeNodeList() {
        int i, j;
        int vscount = 0;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            int inodes = ce.getInternalNodeCount();
            int ivs = ce.getVoltageSourceCount();
            int posts = ce.getPostCount();

            for (j = 0; j != posts; j++) {
                Point pt = ce.getPost(j);
                CirSim.NodeMapEntry cln = nodeMap.get(pt);
                if (cln == null || cln.node == -1) {
                    CircuitNode cn = new CircuitNode();
                    CircuitNodeLink cnl = new CircuitNodeLink();
                    cnl.num = j;
                    cnl.elm = ce;
                    cn.links.addElement(cnl);
                    ce.setNode(j, nodeList.size());
                    if (cln != null)
                        cln.node = nodeList.size();
                    else
                        nodeMap.put(pt, new CirSim.NodeMapEntry(nodeList.size()));
                    nodeList.addElement(cn);
                } else {
                    int n = cln.node;
                    CircuitNodeLink cnl = new CircuitNodeLink();
                    cnl.num = j;
                    cnl.elm = ce;
                    sim.getCircuitNode(n).links.addElement(cnl);
                    ce.setNode(j, n);
                    if (n == 0)
                        ce.setNodeVoltage(j, 0);
                }
            }
            for (j = 0; j != inodes; j++) {
                CircuitNode cn = new CircuitNode();
                cn.internal = true;
                CircuitNodeLink cnl = new CircuitNodeLink();
                cnl.num = j + posts;
                cnl.elm = ce;
                cn.links.addElement(cnl);
                ce.setNode(cnl.num, nodeList.size());
                nodeList.addElement(cn);
            }

            vscount += ivs;
        }

        sim.voltageSources = new CircuitElm[vscount];
    }

    void findUnconnectedNodes() {
        int i, j;
        boolean closure[] = new boolean[nodeList.size()];
        boolean changed = true;
        unconnectedNodes = new Vector<Integer>();
        nodesWithGroundConnection = new Vector<CircuitElm>();
        closure[0] = true;
        while (changed) {
            changed = false;
            for (i = 0; i != sim.elmList.size(); i++) {
                CircuitElm ce = sim.getElm(i);
                if (ce instanceof WireElm)
                    continue;
                boolean hasGround = false;
                for (j = 0; j < ce.getConnectionNodeCount(); j++) {
                    boolean hg = ce.hasGroundConnection(j);
                    if (hg)
                        hasGround = true;
                    if (!closure[ce.getConnectionNode(j)]) {
                        if (hg)
                            closure[ce.getConnectionNode(j)] = changed = true;
                        continue;
                    }
                    int k;
                    for (k = 0; k != ce.getConnectionNodeCount(); k++) {
                        if (j == k)
                            continue;
                        int kn = ce.getConnectionNode(k);
                        if (ce.getConnection(j, k) && !closure[kn]) {
                            closure[kn] = true;
                            changed = true;
                        }
                    }
                }
                if (hasGround)
                    nodesWithGroundConnection.add(ce);
            }
            if (changed)
                continue;

            for (i = 0; i != nodeList.size(); i++)
                if (!closure[i] && !sim.getCircuitNode(i).internal) {
                    unconnectedNodes.add(i);
                    closure[i] = true;
                    changed = true;
                    break;
                }
        }
    }

    void connectUnconnectedNodes() {
        int i;
        CirSim.console("Number of unconnected nodes: " + unconnectedNodes.size());
        for (i = 0; i != unconnectedNodes.size(); i++) {
            int n = unconnectedNodes.get(i);
            sim.stampResistor(0, n, 1e8);
        }
    }

    boolean validateCircuit() {
        int i;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof InductorElm) {
                FindPathInfo fpi = new FindPathInfo(FindPathInfo.INDUCT, ce, ce.getNode(1));
                if (!fpi.findPath(ce.getNode(0))) {
                    ce.reset();
                }
            }
            if (ce instanceof CurrentElm) {
                CurrentElm cur = (CurrentElm) ce;
                FindPathInfo fpi = new FindPathInfo(FindPathInfo.INDUCT, ce, ce.getNode(1));
                cur.setBroken(!fpi.findPath(ce.getNode(0)));
            }
            if (ce instanceof VCCSElm) {
                VCCSElm cur = (VCCSElm) ce;
                FindPathInfo fpi = new FindPathInfo(FindPathInfo.INDUCT, ce, cur.getOutputNode(0));
                if (cur.hasCurrentOutput() && !fpi.findPath(cur.getOutputNode(1))) {
                    cur.broken = true;
                } else
                    cur.broken = false;
            }

            if (ce.getPostCount() == 2) {
                if (ce instanceof VoltageElm) {
                    FindPathInfo fpi = new FindPathInfo(FindPathInfo.VOLTAGE, ce, ce.getNode(1));
                    if (fpi.findPath(ce.getNode(0))) {
                        sim.stop("Voltage source/wire loop with no resistance!", ce);
                        return false;
                    }
                }
            }

            if (ce instanceof RailElm || ce instanceof LogicInputElm) {
                FindPathInfo fpi = new FindPathInfo(FindPathInfo.VOLTAGE, ce, ce.getNode(0));
                if (fpi.findPath(0)) {
                    sim.stop("Path to ground with no resistance!", ce);
                    return false;
                }
            }

            if (ce.isIdealCapacitor()) {
                FindPathInfo fpi = new FindPathInfo(FindPathInfo.SHORT, ce, ce.getNode(1));
                if (fpi.findPath(ce.getNode(0))) {
                    CirSim.console(ce + " shorted");
                    ((CapacitorElm) ce).shorted();
                } else {
                    fpi = new FindPathInfo(FindPathInfo.CAP_V, ce, ce.getNode(1));
                    if (fpi.findPath(ce.getNode(0))) {
                        ((CapacitorElm) ce).setSeriesResistance(.1);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    void analyzeCircuit() {
        sim.stopMessage = null;
        sim.warningMessage = null;
        sim.stopElm = null;

        if (sim.elmList.isEmpty()) {
            sim.postDrawList = new Vector<Point>();
            sim.badConnectionList = new Vector<Point>();
            return;
        }
        makePostDrawList();

        sim.needsStamp = true;
    }

    boolean preStampCircuit(boolean subcircuit) {
        int i, j;
        nodeList = new Vector<CircuitNode>();

        sim.getStatusInfoRenderer().updateEquationParameterCollisionWarning();

        calculateWireClosure();
        setGroundNode(subcircuit);

        ComputedValues.clearMasterTables();
        sim.getTableMasterRegistryManager().registerTableMastersInPriorityOrder();

        makeNodeList();

        if (!calcWireInfo())
            return false;
        nodeMap = null;

        int vscount = 0;
        sim.getSolverMatrixState().circuitNonLinear = false;

        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce.nonLinear())
                sim.getSolverMatrixState().circuitNonLinear = true;
            int ivs = ce.getVoltageSourceCount();
            for (j = 0; j != ivs; j++) {
                sim.voltageSources[vscount] = ce;
                ce.setVoltageSource(j, vscount++);
            }
        }
        sim.voltageSourceCount = vscount;

        boolean gotVoltageSource = false;
        sim.showResistanceInVoltageSources = true;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof VoltageElm) {
                if (gotVoltageSource)
                    sim.showResistanceInVoltageSources = false;
                else
                    gotVoltageSource = true;
            }
        }

        findUnconnectedNodes();
        if (!validateCircuit())
            return false;

        nodesWithGroundConnectionCount = nodesWithGroundConnection.size();
        nodesWithGroundConnection = null;

        sim.setTimeStep(sim.getMaxTimeStep());
        sim.needsStamp = true;

        if (RuntimeMode.isGwt()) {
            sim.getJsApiBridge().callAnalyzeHook();
            VariableBrowserDialog.refreshIfOpen();
            ActionTimeDialog.refreshIfOpen();
        }

        return true;
    }

    void preStampAndStampCircuit() {
        int i;
        for (i = 0; i != 10; i++)
            if (preStampCircuit(false) || sim.stopMessage != null)
                break;
        if (sim.stopMessage != null)
            return;
        if (i == 10) {
            sim.stop("failed to stamp circuit", null);
            return;
        }

        stampCircuit();
    }

    void stampCircuit() {
        int i;
        int matrixSize = nodeList.size() - 1 + sim.voltageSourceCount;
        sim.getSolverMatrixState().circuitMatrix = new double[matrixSize][matrixSize];
        sim.getSolverMatrixState().circuitRightSide = new double[matrixSize];
        sim.getSolverMatrixState().nodeVoltages = new double[nodeList.size() - 1];
        if (sim.getSolverMatrixState().lastNodeVoltages == null || sim.getSolverMatrixState().lastNodeVoltages.length != sim.getSolverMatrixState().nodeVoltages.length)
            sim.getSolverMatrixState().lastNodeVoltages = new double[nodeList.size() - 1];
        sim.getSolverMatrixState().origMatrix = new double[matrixSize][matrixSize];
        sim.getSolverMatrixState().origRightSide = new double[matrixSize];
        sim.getSolverMatrixState().circuitMatrixSize = sim.getSolverMatrixState().circuitMatrixFullSize = matrixSize;
        sim.getSolverMatrixState().circuitRowInfo = new RowInfo[matrixSize];
        sim.getSolverMatrixState().circuitPermute = new int[matrixSize];
        for (i = 0; i != matrixSize; i++)
            sim.getSolverMatrixState().circuitRowInfo[i] = new RowInfo();
        sim.getSolverMatrixState().circuitNeedsMap = false;

        connectUnconnectedNodes();

        EquationTableElm.coordinateLabelsForStamp(sim.elmList);

        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            ce.setParentList(sim.elmList);
            ce.stamp();
        }

        sim.getCircuitValueSlotManager().buildCircuitVariableSlots();

        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            ce.postStamp();
        }

        if (!simplifyMatrix(matrixSize))
            return;

        if (sim.getSolverMatrixState().circuitMatrix == null)
            return;

        if (!sim.getSolverMatrixState().circuitNonLinear) {
            int badRow = CircuitMatrixOps.luFactor(sim.getSolverMatrixState().circuitMatrix, sim.getSolverMatrixState().circuitMatrixSize, sim.getSolverMatrixState().circuitPermute);
            if (badRow >= 0) {
                sim.stop("Singular matrix! " + sim.getMatrixStamper().getMatrixRowInfo(badRow), null);
                return;
            }
        }

        sim.elmArr = new CircuitElm[sim.elmList.size()];
        int scopeElmCount = 0;
        for (i = 0; i != sim.elmList.size(); i++) {
            sim.elmArr[i] = sim.elmList.get(i);
            if (sim.elmArr[i] instanceof ScopeElm)
                scopeElmCount++;
        }

        sim.scopeElmArr = new ScopeElm[scopeElmCount];
        int j = 0;
        for (i = 0; i != sim.elmList.size(); i++) {
            if (sim.elmArr[i] instanceof ScopeElm)
                sim.scopeElmArr[j++] = (ScopeElm) sim.elmArr[i];
        }

        sim.needsStamp = false;
    }

    boolean simplifyMatrix(int matrixSize) {
        int i, j;
        for (i = 0; i != matrixSize; i++) {
            int qp = -1;
            double qv = 0;
            RowInfo re = sim.getSolverMatrixState().circuitRowInfo[i];
            if (re.lsChanges || re.dropRow || re.rsChanges)
                continue;
            double rsadd = 0;

            for (j = 0; j != matrixSize; j++) {
                double q = sim.getSolverMatrixState().circuitMatrix[i][j];
                if (sim.getSolverMatrixState().circuitRowInfo[j].type == RowInfo.ROW_CONST) {
                    rsadd -= sim.getSolverMatrixState().circuitRowInfo[j].value * q;
                    continue;
                }
                if (q == 0)
                    continue;
                if (qp == -1) {
                    qp = j;
                    qv = q;
                    continue;
                }
                break;
            }
            if (j == matrixSize) {
                if (qp == -1) {
                    sim.stop("Matrix error", null);
                    return false;
                }
                RowInfo elt = sim.getSolverMatrixState().circuitRowInfo[qp];
                if (elt.type != RowInfo.ROW_NORMAL) {
                    System.out.println("type already " + elt.type + " for " + qp + "!");
                    continue;
                }
                if (elt.lsChanges || elt.rsChanges) {
                    CirSim.console("[simplifyMatrix] Skipping ROW_CONST for col " + qp + " (lsChanges=" + elt.lsChanges + " rsChanges=" + elt.rsChanges + ")");
                    continue;
                }
                elt.type = RowInfo.ROW_CONST;
                elt.value = (sim.getSolverMatrixState().circuitRightSide[i] + rsadd) / qv;
                sim.getSolverMatrixState().circuitRowInfo[i].dropRow = true;
                for (j = 0; j != i; j++)
                    if (sim.getSolverMatrixState().circuitMatrix[j][qp] != 0)
                        break;
                i = j - 1;
            }
        }

        int nn = 0;
        for (i = 0; i != matrixSize; i++) {
            RowInfo elt = sim.getSolverMatrixState().circuitRowInfo[i];
            if (elt.type == RowInfo.ROW_NORMAL) {
                elt.mapCol = nn++;
                continue;
            }
            if (elt.type == RowInfo.ROW_CONST)
                elt.mapCol = -1;
        }

        int newsize = nn;
        double newmatx[][] = new double[newsize][newsize];
        double newrs[] = new double[newsize];
        int ii = 0;
        for (i = 0; i != matrixSize; i++) {
            RowInfo rri = sim.getSolverMatrixState().circuitRowInfo[i];
            if (rri.dropRow) {
                rri.mapRow = -1;
                continue;
            }
            newrs[ii] = sim.getSolverMatrixState().circuitRightSide[i];
            rri.mapRow = ii;
            for (j = 0; j != matrixSize; j++) {
                RowInfo ri = sim.getSolverMatrixState().circuitRowInfo[j];
                if (ri.type == RowInfo.ROW_CONST)
                    newrs[ii] -= ri.value * sim.getSolverMatrixState().circuitMatrix[i][j];
                else
                    newmatx[ii][ri.mapCol] += sim.getSolverMatrixState().circuitMatrix[i][j];
            }
            ii++;
        }

        int rowsSaved = matrixSize - newsize;
        if (rowsSaved > 0)
            CirSim.console("Matrix simplification: " + matrixSize + " -> " + newsize + " (" + rowsSaved + " rows eliminated, " +
                           (100 * rowsSaved / matrixSize) + "% reduction)");

        sim.getSolverMatrixState().circuitMatrix = newmatx;
        sim.getSolverMatrixState().circuitRightSide = newrs;
        matrixSize = sim.getSolverMatrixState().circuitMatrixSize = newsize;
        for (i = 0; i != matrixSize; i++)
            sim.getSolverMatrixState().origRightSide[i] = sim.getSolverMatrixState().circuitRightSide[i];
        for (i = 0; i != matrixSize; i++)
            for (j = 0; j != matrixSize; j++)
                sim.getSolverMatrixState().origMatrix[i][j] = sim.getSolverMatrixState().circuitMatrix[i][j];
        sim.getSolverMatrixState().circuitNeedsMap = true;
        return true;
    }

    void makePostDrawList() {
        HashMap<Point, Integer> postCountMap = new HashMap<Point, Integer>();
        int i, j;
        for (i = 0; i != sim.elmList.size(); i++) {
            CircuitElm ce = sim.getElm(i);
            if (ce instanceof TableElm || ce instanceof EquationTableElm)
                continue;
            int posts = ce.getPostCount();
            for (j = 0; j != posts; j++) {
                Point pt = ce.getPost(j);
                Integer g = postCountMap.get(pt);
                postCountMap.put(pt, g == null ? 1 : g + 1);
            }
        }

        sim.postDrawList = new Vector<Point>();
        sim.badConnectionList = new Vector<Point>();
        for (Map.Entry<Point, Integer> entry : postCountMap.entrySet()) {
            if (entry.getValue() != 2)
                sim.postDrawList.add(entry.getKey());

            if (entry.getValue() == 1) {
                boolean bad = false;
                Point cn = entry.getKey();
                boolean hasLabelConnection = false;
                for (j = 0; j != sim.elmList.size(); j++) {
                    CircuitElm ce = sim.getElm(j);
                    if (ce.getPostCount() > 0 && ce.getPost(0).equals(cn)) {
                        if (ce instanceof SFCStockElm || ce instanceof LabeledNodeElm)
                            hasLabelConnection = true;
                    }
                }
                if (hasLabelConnection)
                    continue;

                for (j = 0; j != sim.elmList.size() && !bad; j++) {
                    CircuitElm ce = sim.getElm(j);
                    if (ce instanceof GraphicElm || ce instanceof TableElm || ce instanceof EquationTableElm)
                        continue;
                    if (!ce.boundingBox.contains(cn.x, cn.y))
                        continue;
                    int k;
                    int pc = ce.getPostCount();
                    for (k = 0; k != pc; k++)
                        if (ce.getPost(k).equals(cn))
                            break;
                    if (k == pc)
                        bad = true;
                }
                if (bad)
                    sim.badConnectionList.add(cn);
            }
        }
    }

    class FindPathInfo {
        static final int INDUCT = 1;
        static final int VOLTAGE = 2;
        static final int SHORT = 3;
        static final int CAP_V = 4;
        boolean visited[];
        int dest;
        CircuitElm firstElm;
        int type;

        FindPathInfo(int type_, CircuitElm elm_, int dest_) {
            dest = dest_;
            type = type_;
            firstElm = elm_;
            visited = new boolean[nodeList.size()];
        }

        boolean findPath(int n1) {
            if (n1 == dest)
                return true;
            if (visited[n1])
                return false;

            visited[n1] = true;
            CircuitNode cn = sim.getCircuitNode(n1);
            int i;
            if (cn == null)
                return false;
            for (i = 0; i != cn.links.size(); i++) {
                CircuitNodeLink cnl = cn.links.get(i);
                CircuitElm ce = cnl.elm;
                if (checkElm(n1, ce))
                    return true;
            }
            if (n1 == 0) {
                for (i = 0; i != nodesWithGroundConnection.size(); i++)
                    if (checkElm(0, nodesWithGroundConnection.get(i)))
                        return true;
            }
            return false;
        }

        boolean checkElm(int n1, CircuitElm ce) {
            if (ce == firstElm)
                return false;
            if (type == INDUCT) {
                if (ce instanceof CurrentElm)
                    return false;
            }
            if (type == VOLTAGE) {
                if (!(ce.isWireEquivalent() || ce instanceof VoltageElm || ce instanceof GroundElm))
                    return false;
            }
            if (type == SHORT && !ce.isWireEquivalent())
                return false;
            if (type == CAP_V) {
                if (!(ce.isWireEquivalent() || ce.isIdealCapacitor() || ce instanceof VoltageElm))
                    return false;
            }
            if (n1 == 0) {
                int j;
                for (j = 0; j != ce.getConnectionNodeCount(); j++)
                    if (ce.hasGroundConnection(j) && findPath(ce.getConnectionNode(j)))
                        return true;
            }
            int j;
            for (j = 0; j != ce.getConnectionNodeCount(); j++) {
                if (ce.getConnectionNode(j) == n1) {
                    if (ce.hasGroundConnection(j) && findPath(0))
                        return true;
                    if (type == INDUCT && ce instanceof InductorElm) {
                        double c = ce.getCurrent();
                        if (j == 0)
                            c = -c;
                        if (Math.abs(c - firstElm.getCurrent()) > 1e-10)
                            continue;
                    }
                    int k;
                    for (k = 0; k != ce.getConnectionNodeCount(); k++) {
                        if (j == k)
                            continue;
                        if (ce.getConnection(j, k) && findPath(ce.getConnectionNode(k))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
