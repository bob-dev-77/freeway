package com.jujin.freeway.ioc.internal.util;

import com.jujin.freeway.ioc.config.Orderable;
import com.jujin.freeway.ioc.internal.GlobIdMatcher;
import com.jujin.freeway.ioc.internal.IdMatcher;
import com.jujin.freeway.ioc.internal.OrIdMatcher;
import java.util.*;
import org.slf4j.Logger;

/**
 * Used to order objects into an "execution" order. Each object must have a
 * unique id. It may specify a list of constraints which identify the ordering
 * of the objects.
 */
public class Orderer<T> {

    private final OneShotLock lock = new OneShotLock();

    private final Logger logger;

    private final boolean exceptionWhenDuplicateId;

    private final List<Orderable<T>> orderables = new ArrayList<>();

    private final Map<String, Orderable<T>> idToOrderable = new TreeMap<>(
        String.CASE_INSENSITIVE_ORDER
    );

    private final Map<String, DependencyNode<T>> dependencyNodesById =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    // Special node that is always dead last: all other nodes are a dependency
    // of the trailer.

    private DependencyNode<T> trailer;

    interface DependencyLinker<T> {
        void link(DependencyNode<T> source, DependencyNode<T> target);
    }

    // before: source is added as a dependency of target, so source will
    // appear before target.

    final DependencyLinker<T> _before = new DependencyLinker<T>() {
        @Override
        public void link(DependencyNode<T> source, DependencyNode<T> target) {
            target.addDependency(source);
        }
    };

    // after: target is added as a dependency of source, so source will appear
    // after target.

    final DependencyLinker<T> _after = new DependencyLinker<T>() {
        @Override
        public void link(DependencyNode<T> source, DependencyNode<T> target) {
            source.addDependency(target);
        }
    };

    public Orderer(Logger logger) {
        this(logger, false);
    }

    public Orderer(Logger logger, boolean exceptionWhenDuplicateId) {
        this.logger = logger;
        this.exceptionWhenDuplicateId = exceptionWhenDuplicateId;
    }

    /**
     * Adds an object to be ordered.
     *
     * @param orderable
     */
    public void add(Orderable<T> orderable) {
        lock.check();

        String id = orderable.getId();

        if (idToOrderable.containsKey(id)) {
            final String message = UtilMessages.duplicateOrderer(id);
            if (exceptionWhenDuplicateId) {
                throw new IllegalArgumentException(message);
            } else {
                logger.warn(message);
                return;
            }
        }

        orderables.add(orderable);

        idToOrderable.put(id, orderable);
    }

    public void override(Orderable<T> orderable) {
        lock.check();

        String id = orderable.getId();

        Orderable<T> existing = idToOrderable.get(id);

        if (existing == null) throw new IllegalArgumentException(
            String.format(
                "Override for object '%s' is invalid as it does not match an existing object.",
                id
            )
        );

        orderables.remove(existing);
        orderables.add(orderable);

        idToOrderable.put(id, orderable);
    }

    /**
     * Adds an object to be ordered.
     *
     * @param id
     *            unique, qualified id for the target
     * @param target
     *            the object to be ordered (or null as a placeholder)
     * @param constraints
     *            optional, variable constraints
     * @see #add(Orderable)
     */

    public void add(String id, T target, String... constraints) {
        lock.check();

        add(new Orderable<T>(id, target, constraints));
    }

    public void override(String id, T target, String... constraints) {
        lock.check();

        override(new Orderable<T>(id, target, constraints));
    }

    public List<T> getOrdered() {
        lock.lock();

        initializeGraph();

        List<T> result = new ArrayList<>();

        for (Orderable<T> orderable : trailer.getOrdered()) {
            T target = orderable.getTarget();

            // Nulls are placeholders that are skipped.

            if (target != null) result.add(target);
        }

        return result;
    }

    private void initializeGraph() {
        trailer = new DependencyNode<T>(
            logger,
            new Orderable<T>("*-trailer-*", null)
        );

        addNodes();

        addDependencies();
    }

    private void addNodes() {
        for (Orderable<T> orderable : orderables) {
            DependencyNode<T> node = new DependencyNode<T>(logger, orderable);

            dependencyNodesById.put(orderable.getId(), node);

            trailer.addDependency(node);
        }
    }

    private void addDependencies() {
        for (Orderable<T> orderable : orderables) {
            addDependencies(orderable);
        }
    }

    private void addDependencies(Orderable<T> orderable) {
        String sourceId = orderable.getId();

        for (String constraint : orderable.getConstraints()) {
            addDependencies(sourceId, constraint);
        }
    }

    private void addDependencies(String sourceId, String constraint) {
        int colonx = constraint.indexOf(':');

        String type = colonx > 0 ? constraint.substring(0, colonx) : null;

        DependencyLinker<T> linker = null;

        if ("after".equals(type)) linker = _after;
        else if ("before".equals(type)) linker = _before;

        if (linker == null) {
            logger.warn(UtilMessages.constraintFormat(constraint, sourceId));
            return;
        }

        String patternList = constraint.substring(colonx + 1);

        linkNodes(sourceId, patternList, linker);
    }

    private void linkNodes(
        String sourceId,
        String patternList,
        DependencyLinker<T> linker
    ) {
        Collection<DependencyNode<T>> nodes = findDependencies(
            sourceId,
            patternList
        );

        DependencyNode<T> source = dependencyNodesById.get(sourceId);

        for (DependencyNode<T> target : nodes) {
            linker.link(source, target);
        }
    }

    private Collection<DependencyNode<T>> findDependencies(
        String sourceId,
        String patternList
    ) {
        IdMatcher matcher = buildMatcherForPattern(patternList);

        Collection<DependencyNode<T>> result = new ArrayList<>();

        for (String id : dependencyNodesById.keySet()) {
            if (sourceId.equals(id)) continue;

            if (matcher.matches(id)) result.add(dependencyNodesById.get(id));
        }

        return result;
    }

    private IdMatcher buildMatcherForPattern(String patternList) {
        List<IdMatcher> matchers = new ArrayList<>();

        for (String pattern : patternList.split(",")) {
            IdMatcher matcher = new GlobIdMatcher(pattern.trim());

            matchers.add(matcher);
        }

        return matchers.size() == 1
            ? matchers.get(0)
            : new OrIdMatcher(matchers);
    }
}
