package edu.jhu.cobra.externs.phpparser

/**
 * Thrown when a required executable is missing in a certain directory.
 *
 * @param name the name of the missing executable
 * @param under the directory in which the executables are missing
 */
public class ExternalBinaryNotFoundException(
    name: String,
    under: String? = null,
) : RuntimeException("$name does not exist under ${under ?: "the system"}.")

/**
 * Thrown when a located executable fails validation.
 *
 * @param name the name of the invalid executable
 * @param reason an optional description of why the executable is invalid
 * @param cause the underlying failure, when validation failed because of another error
 */
public class ExternalBinaryInvalidException(
    name: String,
    reason: String? = null,
    cause: Throwable? = null,
) : RuntimeException("$name provided is invalid: $reason", cause)

/**
 * Exception to indicate that an expected command line argument is missing.
 * @param argName the name of the missing argument.
 */
public class ExternalBinaryArgumentMissException(
    argName: String,
) : RuntimeException("Argument $argName has not been initialized.")
