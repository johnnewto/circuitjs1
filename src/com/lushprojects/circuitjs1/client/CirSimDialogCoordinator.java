package com.lushprojects.circuitjs1.client;



import com.lushprojects.circuitjs1.client.elements.ActionTimeDialog;

import com.google.gwt.user.client.ui.PopupPanel;
import com.lushprojects.circuitjs1.client.ui.AboutBox;
import com.lushprojects.circuitjs1.client.ui.VariableBrowserDialog;

public final class CirSimDialogCoordinator {
    private static EditDialog editDialog;
    private static EditDialog customLogicEditDialog;
    private static EditDialog diodeModelEditDialog;
    private static ScrollValuePopup scrollValuePopup;
    private static Dialog dialogShowing;
    private static AboutBox aboutBox;

    private CirSimDialogCoordinator() {
    }

    public static EditDialog getEditDialog() {
        return editDialog;
    }

    static void setEditDialog(EditDialog value) {
        editDialog = value;
    }

    public static EditDialog getCustomLogicEditDialog() {
        return customLogicEditDialog;
    }

    public static void setCustomLogicEditDialog(EditDialog value) {
        customLogicEditDialog = value;
    }

    static EditDialog getDiodeModelEditDialog() {
        return diodeModelEditDialog;
    }

    public static void setDiodeModelEditDialog(EditDialog value) {
        diodeModelEditDialog = value;
    }

    static ScrollValuePopup getScrollValuePopup() {
        return scrollValuePopup;
    }

    static void setScrollValuePopup(ScrollValuePopup value) {
        scrollValuePopup = value;
    }

    static Dialog getDialogShowing() {
        return dialogShowing;
    }

    public static void setDialogShowing(Dialog value) {
        dialogShowing = value;
    }

    public static void clearDialogShowingIf(Dialog value) {
        if (dialogShowing == value) {
            dialogShowing = null;
        }
    }

    static AboutBox getAboutBox() {
        return aboutBox;
    }

    static void setAboutBox(AboutBox value) {
        aboutBox = value;
    }

    static boolean isAnyDialogShowing(PopupPanel contextPanel) {
        if (editDialog != null && editDialog.isShowing())
            return true;
        if (customLogicEditDialog != null && customLogicEditDialog.isShowing())
            return true;
        if (diodeModelEditDialog != null && diodeModelEditDialog.isShowing())
            return true;
        if (dialogShowing != null && dialogShowing.isShowing())
            return true;
        if (contextPanel != null && contextPanel.isShowing())
            return true;
        if (scrollValuePopup != null && scrollValuePopup.isShowing())
            return true;
        if (aboutBox != null && aboutBox.isShowing())
            return true;
        if (VariableBrowserDialog.isOpen())
            return true;
        if (ActionTimeDialog.isOpen())
            return true;
        return false;
    }
}
