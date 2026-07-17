package com.ttg.devknowledgeplatform.task.enums;

/**
 * Lifecycle state of a {@link com.ttg.devknowledgeplatform.task.entity.Task}.
 */
public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE;

    /**
     * Guards a status update against a no-op transition. Deliberately permissive otherwise
     * (any status may move to any other status) — this is a personal task tracker, not a team
     * approval workflow, so forcing e.g. {@code TODO -> IN_PROGRESS -> DONE} in strict order
     * would only get in the user's way. Revisit if transitions ever need to carry real side
     * effects (e.g. auto-stamping a completion timestamp), at which point a State-pattern class
     * per status would be the better fit than growing this method.
     *
     * @param target the status being transitioned to
     * @return {@code true} if the transition is allowed
     */
    public boolean canTransitionTo(TaskStatus target) {
        return target != this;
    }
}
