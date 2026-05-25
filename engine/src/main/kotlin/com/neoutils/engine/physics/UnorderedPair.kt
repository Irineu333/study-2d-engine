package com.neoutils.engine.physics

/**
 * Pair of references whose `equals`/`hashCode` are insensitive to the order
 * of `a` and `b`. Two `UnorderedPair(x, y)` and `UnorderedPair(y, x)` hash
 * to the same bucket and compare equal. Backed by reference identity
 * (`System.identityHashCode` + `===`) so it stays correct when the
 * referenced objects override `equals` themselves.
 */
class UnorderedPair<T : Any>(val a: T, val b: T) {

    override fun equals(other: Any?): Boolean {
        if (other !is UnorderedPair<*>) return false
        return (a === other.a && b === other.b) || (a === other.b && b === other.a)
    }

    override fun hashCode(): Int =
        System.identityHashCode(a) xor System.identityHashCode(b)
}
