package edu.jhu.cobra.externs.phpparser

/**
 * Thrown when a required executable is missing in a certain directory.
 *
 * @param name the others of the executables that are missing
 * @param under the directory in which the executables are missing
 */
class ExternalBinaryNotFoundException(name: String, under: String? = null) :
    RuntimeException("$name do not exist under ${under ?: "the system"}.")

/**
 * Thrown when an executable is found but is invalid for some reason.
 * This could be due to incorrect version, corrupted binary, or other validation failures.
 *
 * @param name the name of the invalid executable
 * @param reason an optional description of why the executable is invalid
 */
class ExternalBinaryInvalidException(name: String, reason: String? = null) :
    RuntimeException("$name provided is valid, because $reason.")

/**
 * Exception to indicate that an expected command line argument is missing.
 * @param argName the name of the missing argument.
 */
class ExternalBinaryArgumentMissException(argName: String) :
    Exception("Argument $argName has not been initialized. ")
