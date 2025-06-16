package org.jetbrains.exposed.crud.ksp

/**
 * Annotation to mark Exposed Table classes for CRUD code generation.
 * 
 * When applied to a Table class, the KSP processor will generate:
 * - Data classes for the entity (User, NewUser, UpdateUser)
 * - Extension functions for all CRUD operations
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateCrud