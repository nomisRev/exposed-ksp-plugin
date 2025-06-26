package org.jetbrains.exposed.crud.ksp

/**
 * Annotation to mark Exposed Table classes for Repository code generation.
 * 
 * When applied to a Table class, the KSP processor will generate:
 * - Data classes for the entity (User, NewUser, UpdateUser)
 * - A repository interface with CRUD operations
 * - A factory function to create an implementation of the repository
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class GenerateRepository