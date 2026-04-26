package edu.jhu.cobra.externs.phpparser

/**
 * Thrown when a required executable is missing in a certain directory.
 *
 * @param name the name of the missing executable
 * @param under the directory in which the executables are missing
 */
class ExternalBinaryNotFoundException(name: String, under: String? = null) :
    RuntimeException("$name do not exist under ${under ?: "the system"}.")

/**
 * Thrown when a located executable fails validation.
 *
 * @param name the name of the invalid executable
 * @param reason an optional description of why the executable is invalid
 */
class ExternalBinaryInvalidException(name: String, reason: String? = null) :
    RuntimeException("$name provided is invalid: $reason")

/**
 * Exception to indicate that an expected command line argument is missing.
 * @param argName the name of the missing argument.
 */
class ExternalBinaryArgumentMissException(argName: String) :
    Exception("Argument $argName has not been initialized. ")
