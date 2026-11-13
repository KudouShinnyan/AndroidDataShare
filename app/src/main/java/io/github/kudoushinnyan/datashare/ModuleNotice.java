package io.github.kudoushinnyan.datashare;

final class ModuleNotice {

    static final int HIDDEN = 0;
    static final int GENERATE = 1;
    static final int PENDING = 2;
    static final int CHECKING = 3;

    private ModuleNotice() {
    }

    static int state() {
        if (Session.isChecking()) {
            return CHECKING;
        }
        if (Backends.moduleInstalled()) {
            return HIDDEN;
        }
        if (!Perm.unlocked() || !Session.hasDetectableGroups()) {
            return HIDDEN;
        }
        if (Backends.modulePublished()) {
            return PENDING;
        }
        return GENERATE;
    }
}
