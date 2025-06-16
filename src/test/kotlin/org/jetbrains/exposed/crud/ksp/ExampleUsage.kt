package org.jetbrains.exposed.crud.ksp

import org.jetbrains.exposed.sql.Table

@GenerateCrud
object UsersTable : Table("complex_users") {
    val id = integer("id")
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val age = integer("age").nullable()
    override val primaryKey = PrimaryKey(id)
}