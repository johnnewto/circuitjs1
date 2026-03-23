package com.lushprojects.circuitjs1.client;

public interface Editable {
    EditInfo getEditInfo(int n);
    void setEditValue(int n, EditInfo ei);
}
