package io.github.lovette.outtamny;

/** Exception class for user-facing errors with an associated exit code. */
public class UserFacingException extends RuntimeException {
    private final int exitCode;

    /**
     * Constructs a new UserFacingException with the specified message and a default exit code of 1.
     *
     * @param message the detail message
     */
    public UserFacingException(String message) {
        this(1, message);
    }

    /**
     * Constructs a new UserFacingException with the specified exit code and message.
     *
     * @param exitCode the exit code to be returned
     * @param message the detail message
     */
    public UserFacingException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    /**
     * Returns the exit code associated with this exception.
     *
     * @return the exit code
     */
    public int getExitCode() {
        return exitCode;
    }
}
