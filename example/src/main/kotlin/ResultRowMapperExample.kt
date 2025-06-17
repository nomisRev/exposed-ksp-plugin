package test.example

import org.jetbrains.exposed.crud.ksp.ResultRowMapper
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

// Define a data class and annotate it with @ResultRowMapper
@ResultRowMapper(table = SimpleUsersTable::class)
data class SimpleUser(
    val id: Int,
    val name: String,
    val email: String
)

fun main() {
    val container = PostgreSQLContainer<Nothing>("postgres:16.0-alpine")
    container.start()
    Database.connect(container.jdbcUrl, user = container.username, password = container.password)

    transaction {
        SchemaUtils.create(SimpleUsersTable)

        println("=== ResultRowMapper Example ===\n")

        SimpleUsersTable.insertAll(
            listOf(
                NewSimpleUsers(name = "John Doe", email = "john@example.com"),
                NewSimpleUsers(name = "Jane Smith", email = "jane@example.com")
            )
        )

        val allRows = SimpleUsersTable.selectAll()

        // Convert a single ResultRow to SimpleUser
        val firstRow = allRows.first()
        val user = firstRow.toSimpleUser()
        println("Converted single row to SimpleUser: $user")

        // Convert all ResultRows to a list of SimpleUser
        val allUsers = allRows.toSimpleUser()
        println("Converted all rows to List<SimpleUser>: $allUsers")

        println("\n=== Generated Code Demo ===")
        println("✅ Generated and working:")
        println("- fun ResultRow.toSimpleUser(): SimpleUser")
        println("- fun Iterable<ResultRow>.toSimpleUser(): List<SimpleUser>")

        println("\nCheck build/generated/ksp/main/kotlin/ for the actual generated files!")
    }
}