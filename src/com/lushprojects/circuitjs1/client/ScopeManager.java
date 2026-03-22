package com.lushprojects.circuitjs1.client;

final class ScopeManager {
    private final CirSim sim;

    ScopeManager(CirSim sim) {
        this.sim = sim;
    }

    int countScopeElms() {
	int c = 0;
	for (int i = 0; i != sim.elmList.size(); i++) {
	    if (sim.elmList.get(i) instanceof ScopeElm)
		c++;
	}
	return c;
    }

    ScopeElm getNthScopeElm(int n) {
	for (int i = 0; i != sim.elmList.size(); i++) {
	    if (sim.elmList.get(i) instanceof ScopeElm) {
		n--;
		if (n < 0)
		    return (ScopeElm) sim.elmList.get(i);
	    }
	}
	return (ScopeElm) null;
    }

    boolean canStackScope(int s) {
	if (sim.scopeCount < 2)
	    return false;
	if (s == 0)
	    s = 1;
	if (sim.scopes[s].position == sim.scopes[s-1].position)
	    return false;
	return true;
    }

    boolean canCombineScope(int s) {
	return sim.scopeCount >= 2;
    }

    boolean canUnstackScope(int s) {
	if (sim.scopeCount < 2)
	    return false;
	if (s == 0)
	    s = 1;
	if (sim.scopes[s].position != sim.scopes[s-1].position) {
	    if (s + 1 < sim.scopeCount && sim.scopes[s+1].position == sim.scopes[s].position)
		return true;
	    else
		return false;
	}
	return true;
    }

    void stackScope(int s) {
	if (!canStackScope(s))
	    return;
	if (s == 0) {
	    s = 1;
	}
	sim.scopes[s].position = sim.scopes[s-1].position;
	for (s++; s < sim.scopeCount; s++)
	    sim.scopes[s].position--;
    }

    void unstackScope(int s) {
	if (!canUnstackScope(s))
	    return;
	if (s == 0) {
	    s = 1;
	}
	if (sim.scopes[s].position != sim.scopes[s-1].position)
	    s++;
	for (; s < sim.scopeCount; s++)
	    sim.scopes[s].position++;
    }

    void combineScope(int s) {
	if (!canCombineScope(s))
	    return;
	if (s == 0) {
	    s = 1;
	}
	sim.scopes[s-1].combine(sim.scopes[s]);
	sim.scopes[s].setElm(null);
    }

    void stackAll() {
	for (int i = 0; i != sim.scopeCount; i++) {
	    sim.scopes[i].position = 0;
	    sim.scopes[i].showMax = sim.scopes[i].showMin = false;
	}
    }

    void unstackAll() {
	for (int i = 0; i != sim.scopeCount; i++) {
	    sim.scopes[i].position = i;
	    sim.scopes[i].showMax = true;
	}
    }

    void combineAll() {
	for (int i = sim.scopeCount-2; i >= 0; i--) {
	    sim.scopes[i].combine(sim.scopes[i+1]);
	    sim.scopes[i+1].setElm(null);
	}
    }

    void separateAll() {
	Scope newscopes[] = new Scope[20];
	int ct = 0;
	for (int i = 0; i < sim.scopeCount; i++)
	    ct = sim.scopes[i].separate(newscopes, ct);
	sim.scopes = newscopes;
	sim.scopeCount = ct;
    }

    void deleteUnusedScopeElms() {
	for (int i = sim.elmList.size()-1; i >= 0; i--) {
	    CircuitElm ce = sim.getElm(i);
	    if (ce instanceof ScopeElm && (((ScopeElm) ce).elmScope.needToRemove())) {
		ce.delete();
		sim.elmList.removeElementAt(i);
		sim.needAnalyze();
	    }
	}
    }

    void setupScopes() {
	int i;

	int pos = -1;
	for (i = 0; i < sim.scopeCount; i++) {
	    if (sim.scopes[i].needToRemove()) {
		int j;
		for (j = i; j != sim.scopeCount; j++)
		    sim.scopes[j] = sim.scopes[j+1];
		sim.scopeCount--;
		i--;
		continue;
	    }
	    if (sim.scopes[i].position > pos+1)
		sim.scopes[i].position = pos+1;
	    pos = sim.scopes[i].position;
	}
	while (sim.scopeCount > 0 && sim.scopes[sim.scopeCount-1].getElm() == null)
	    sim.scopeCount--;
	int h = sim.canvasHeight - sim.circuitArea.height;
	pos = 0;
	for (i = 0; i != sim.scopeCount; i++)
	    sim.scopeColCount[i] = 0;
	for (i = 0; i != sim.scopeCount; i++) {
	    pos = sim.max(sim.scopes[i].position, pos);
	    sim.scopeColCount[sim.scopes[i].position]++;
	}
	int colct = pos+1;
	int iw = CirSim.infoWidth;
	int w = (sim.canvasWidth-iw) / colct;
	int marg = 10;
	if (w < marg*2)
	    w = marg*2;
	pos = -1;
	int colh = 0;
	int row = 0;
	int speed = 0;
	for (i = 0; i != sim.scopeCount; i++) {
	    Scope s = sim.scopes[i];
	    if (s.position > pos) {
		pos = s.position;
		colh = h / sim.scopeColCount[pos];
		row = 0;
		speed = s.speed;
	    }
	    s.stackCount = sim.scopeColCount[pos];
	    if (s.speed != speed) {
		s.speed = speed;
		s.resetGraph();
	    }
	    Rectangle r = new Rectangle(pos*w, sim.canvasHeight-h+colh*row, w-marg, colh);
	    row++;
	    if (!r.equals(s.rect))
		s.setRect(r);
	}
	if (sim.oldScopeCount != sim.scopeCount) {
	    sim.getViewportController().setCircuitArea();
	    sim.oldScopeCount = sim.scopeCount;
	}
    }

    void drawScopeMinMaxButton(Graphics g) {
	int minHeightY = (int) (sim.canvasHeight * (1.0 - 0.1));
	int buttonX = sim.circuitArea.width - CirSim.SCOPE_MIN_MAX_BUTTON_SIZE - 10;
	int buttonY = minHeightY - CirSim.SCOPE_MIN_MAX_BUTTON_SIZE / 2;

	boolean hover = mouseIsOverScopeMinMaxButton(sim.mouseCursorX, sim.mouseCursorY);
	g.setColor(hover ? CircuitElm.selectColor : Color.gray);

	g.context.save();
	g.context.setLineWidth(1.5);
	g.context.strokeRect(buttonX, buttonY, CirSim.SCOPE_MIN_MAX_BUTTON_SIZE, CirSim.SCOPE_MIN_MAX_BUTTON_SIZE);

	int centerX = buttonX + CirSim.SCOPE_MIN_MAX_BUTTON_SIZE / 2;
	int centerY = buttonY + CirSim.SCOPE_MIN_MAX_BUTTON_SIZE / 2;
	int arrowSize = 6;

	g.context.beginPath();
	if (!sim.scopePanelMinimized) {
	    g.context.moveTo(centerX, centerY - arrowSize / 2);
	    g.context.lineTo(centerX - arrowSize, centerY + arrowSize / 2);
	    g.context.moveTo(centerX, centerY - arrowSize / 2);
	    g.context.lineTo(centerX + arrowSize, centerY + arrowSize / 2);
	} else {
	    g.context.moveTo(centerX, centerY + arrowSize / 2);
	    g.context.lineTo(centerX - arrowSize, centerY - arrowSize / 2);
	    g.context.moveTo(centerX, centerY + arrowSize / 2);
	    g.context.lineTo(centerX + arrowSize, centerY - arrowSize / 2);
	}
	g.context.stroke();
	g.context.restore();
    }

    boolean mouseIsOverScopeMinMaxButton(int x, int y) {
	if (sim.scopeCount == 0)
	    return false;
	int minHeightY = (int) (sim.canvasHeight * (1.0 - 0.1));
	int buttonX = sim.circuitArea.width - CirSim.SCOPE_MIN_MAX_BUTTON_SIZE - 10;
	int buttonY = minHeightY - CirSim.SCOPE_MIN_MAX_BUTTON_SIZE / 2;
	return x >= buttonX && x <= buttonX + CirSim.SCOPE_MIN_MAX_BUTTON_SIZE && y >= buttonY
		&& y <= buttonY + CirSim.SCOPE_MIN_MAX_BUTTON_SIZE;
    }

    void toggleScopePanelSize() {
	sim.scopePanelMinimized = !sim.scopePanelMinimized;
	if (sim.scopePanelMinimized) {
	    sim.normalScopeHeightFraction = sim.scopeHeightFraction;
	    sim.scopeHeightFraction = 0.1;
	} else {
	    sim.scopeHeightFraction = sim.normalScopeHeightFraction;
	}
	sim.getViewportController().setCircuitArea();
	sim.repaint();
    }
}