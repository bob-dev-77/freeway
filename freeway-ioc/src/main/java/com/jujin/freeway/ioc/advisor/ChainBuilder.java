package com.jujin.freeway.ioc.advisor;

import java.util.List;

/**
 * A service which can assemble an implementation based on a command interface,
 * and an ordered list of objects implementing that interface (the "commands").
 * This is an implementation of the Gang of Four Chain Of Command pattern.
 * <p>
 * For each method in the interface, the chain implementation will call the
 * corresponding method on each command object in turn (with the order defined
 * by the list). If any of the command objects return true, then the chain of
 * command stops and the initial method invocation returns true. Otherwise, the
 * chain of command continues to the next command (and will return false if none
 * of the commands returns true).
 * <p>
 * For methods whose return type is not boolean, the chain stops with the first
 * non-null (for object types), or non-zero (for numeric types). The chain
 * returns the value that was returned by the command. If the method return type
 * is void, all commands will be invoked.
 * <p>
 * Method invocations will also be terminated if an exception is thrown.
 */
public interface ChainBuilder {
    /**
     * Creates a chain instance from a command interface and a list of commands
     * (implementing the interface).
     *
     * @param <T>
     *            the command interface type
     * @param commandInterface
     *            the command interface
     * @param commands
     *            the list of commands to be chained
     * @return the chain instance
     */
    <T> T build(Class<T> commandInterface, List<T> commands);
}
