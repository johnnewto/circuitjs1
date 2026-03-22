package com.lushprojects.circuitjs1.client;

import com.google.gwt.storage.client.Storage;

class ClipboardManager {
	private final CirSim sim;

	ClipboardManager(CirSim sim) {
		this.sim = sim;
	}

	void setMenuSelection() {
		if (sim.menuElm != null) {
			if (sim.menuElm.selected)
				return;
			sim.clearSelection();
			sim.menuElm.setSelected(true);
		}
	}

	int countSelected() {
		int count = 0;
		for (CircuitElm ce : sim.elmList)
			if (ce.isSelected())
				count++;
		return count;
	}

	void doCut() {
		int i;
		sim.pushUndo();
		setMenuSelection();
		sim.clipboard = "";
		for (i = sim.elmList.size() - 1; i >= 0; i--) {
			CircuitElm ce = sim.getElm(i);
			if (willDelete(ce) && !(ce instanceof ScopeElm)) {
				sim.clipboard += sim.getElementDumpWithUid(ce) + "\n";
			}
		}
		writeClipboardToStorage();
		doDelete(true);
		enablePaste();
	}

	void writeClipboardToStorage() {
		Storage stor = Storage.getLocalStorageIfSupported();
		if (stor == null)
			return;
		stor.setItem("circuitClipboard", sim.clipboard);
	}

	void readClipboardFromStorage() {
		Storage stor = Storage.getLocalStorageIfSupported();
		if (stor == null)
			return;
		sim.clipboard = stor.getItem("circuitClipboard");
	}

	void doDelete(boolean pushUndoFlag) {
		int i;
		if (pushUndoFlag)
			sim.pushUndo();
		boolean hasDeleted = false;

		for (i = sim.elmList.size() - 1; i >= 0; i--) {
			CircuitElm ce = sim.getElm(i);
			if (willDelete(ce)) {
				if (ce.isMouseElm())
					sim.setMouseElm(null);
				ce.delete();
				sim.elmList.removeElementAt(i);
				hasDeleted = true;
			}
		}
		if (hasDeleted) {
			sim.deleteUnusedScopeElms();
			sim.needAnalyze();
			sim.writeRecoveryToStorage();
		}
	}

	boolean willDelete(CircuitElm ce) {
		return ce.isSelected() || ce.isMouseElm();
	}

	String copyOfSelectedElms() {
		String r = sim.dumpOptions();
		CustomLogicModel.clearDumpedFlags();
		CustomCompositeModel.clearDumpedFlags();
		DiodeModel.clearDumpedFlags();
		TransistorModel.clearDumpedFlags();
		for (int i = sim.elmList.size() - 1; i >= 0; i--) {
			CircuitElm ce = sim.getElm(i);
			String m = ce.dumpModel();
			if (m != null && !m.isEmpty())
				r += m + "\n";
			if (ce.isSelected() && !(ce instanceof ScopeElm))
				r += sim.getElementDumpWithUid(ce) + "\n";
		}
		return r;
	}

	void doCopy() {
		boolean clearSel = (sim.menuElm != null && !sim.menuElm.selected);

		setMenuSelection();
		sim.clipboard = copyOfSelectedElms();

		if (clearSel)
			clearSelection();

		writeClipboardToStorage();
		enablePaste();
	}

	void enablePaste() {
		if (sim.clipboard == null || sim.clipboard.length() == 0)
			readClipboardFromStorage();
		sim.pasteItem.setEnabled(sim.clipboard != null && sim.clipboard.length() > 0);
	}

	void doDuplicate() {
		String s;
		setMenuSelection();
		s = copyOfSelectedElms();
		doPaste(s);
	}

	void doPaste(String dump) {
		sim.pushUndo();
		clearSelection();
		int i;
		Rectangle oldbb = null;

		for (i = 0; i != sim.elmList.size(); i++) {
			CircuitElm ce = sim.getElm(i);
			Rectangle bb = ce.getBoundingBox();
			if (oldbb != null)
				oldbb = oldbb.union(bb);
			else
				oldbb = bb;
		}

		int oldsz = sim.elmList.size();
		int flags = CirSim.RC_RETAIN;

		if (oldsz > 0)
			flags |= CirSim.RC_NO_CENTER;

		if (dump != null)
			sim.readCircuit(dump, flags);
		else {
			readClipboardFromStorage();
			sim.readCircuit(sim.clipboard, flags);
		}

		Rectangle newbb = null;
		for (i = oldsz; i != sim.elmList.size(); i++) {
			CircuitElm ce = sim.getElm(i);
			ce.setSelected(true);
			Rectangle bb = ce.getBoundingBox();
			if (newbb != null)
				newbb = newbb.union(bb);
			else
				newbb = bb;
		}

		if (oldbb != null && newbb != null) {
			int dx = 0, dy = 0;
			int spacew = sim.circuitArea.width - oldbb.width - newbb.width;
			int spaceh = sim.circuitArea.height - oldbb.height - newbb.height;

			if (!oldbb.intersects(newbb)) {
				dx = sim.snapGrid(oldbb.x - newbb.x);
				dy = sim.snapGrid(oldbb.y - newbb.y);
			}

			if (spacew > spaceh) {
				dx = sim.snapGrid(oldbb.x + oldbb.width - newbb.x + sim.gridSize);
			} else {
				dy = sim.snapGrid(oldbb.y + oldbb.height - newbb.y + sim.gridSize);
			}

			if (sim.mouseCursorX > 0 && sim.circuitArea.contains(sim.mouseCursorX, sim.mouseCursorY)) {
				int gx = sim.inverseTransformX(sim.mouseCursorX);
				int gy = sim.inverseTransformY(sim.mouseCursorY);
				int mdx = sim.snapGrid(gx - (newbb.x + newbb.width / 2));
				int mdy = sim.snapGrid(gy - (newbb.y + newbb.height / 2));
				for (i = oldsz; i != sim.elmList.size(); i++) {
					if (!sim.getElm(i).allowMove(mdx, mdy))
						break;
				}
				if (i == sim.elmList.size()) {
					dx = mdx;
					dy = mdy;
				}
			}

			for (i = oldsz; i != sim.elmList.size(); i++) {
				CircuitElm ce = sim.getElm(i);
				ce.move(dx, dy);
			}
		}
		sim.needAnalyze();
		sim.writeRecoveryToStorage();
	}

	void clearSelection() {
		int i;
		for (i = 0; i != sim.elmList.size(); i++) {
			CircuitElm ce = sim.getElm(i);
			ce.setSelected(false);
		}
		sim.enableDisableMenuItems();
	}

	void doSelectAll() {
		int i;
		for (i = 0; i != sim.elmList.size(); i++) {
			CircuitElm ce = sim.getElm(i);
			ce.setSelected(true);
		}
		sim.enableDisableMenuItems();
	}

	boolean anySelectedButMouse() {
		CircuitElm mouseElm = sim.getMouseElmForRouting();
		for (int i = 0; i != sim.elmList.size(); i++)
			if (sim.getElm(i) != mouseElm && sim.getElm(i).selected)
				return true;
		return false;
	}
}