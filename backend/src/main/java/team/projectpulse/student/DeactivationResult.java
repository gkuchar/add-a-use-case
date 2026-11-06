package team.projectpulse.student;

/**
 * The outcome of {@link StudentService#deactivateStudent}: the student as she now stands, and whether she was already
 * deactivated before the call (UC-STU-deactivate-student extension 6a), in which case nothing was changed.
 */
public record DeactivationResult(Student student, boolean alreadyDeactivated) {
}
