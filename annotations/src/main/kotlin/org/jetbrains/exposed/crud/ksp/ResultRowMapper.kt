package org.jetbrains.exposed.crud.ksp

import kotlin.reflect.KClass

/**
 * Annotation to mark data classes for ResultRow mapping code generation.
 * 
 * When applied to a data class, the KSP processor will generate:
 * - Extension function on ResultRow to transform into the data class: fun ResultRow.toXXXX()
 * - Extension function on Iterable<ResultRow> to transform into a list of data classes: fun Iterable<ResultRow>.toXXXX()
 *
 * @param table The Exposed table class that contains the columns for this data class.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class ResultRowMapper(public val table: KClass<*>)