package test.example

import org.jetbrains.exposed.crud.ksp.GenerateRepository
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

@GenerateRepository
object TestUsersTable : Table("test_users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

// The rest of the file is commented out to avoid build errors
fun main() {
    val container = PostgreSQLContainer<Nothing>("postgres:16.0-alpine")
    container.start()
    val database = Database.connect(container.jdbcUrl, user = container.username, password = container.password)

    val repository = TestUsersRepository(database)
    transaction {
        SchemaUtils.create(TestUsersTable)

        println("=== Simple Exposed CRUD KSP Example ===\n")

        // Use the generated insert function!
        val newUser = NewTestUsers(
            name = "John Doe",
            email = "john@example.com"
        )

        val insertedUser = repository.insert(newUser)
        println("Inserted user using generated function: $insertedUser")

        // Test another user
        val newUser2 = NewTestUsers(
            name = "Jane Smith",
            email = "jane@example.com"
        )

        val insertedUser2 = repository.insert(newUser2)
        println("Inserted second user: $insertedUser2")

        // Test batch insert
        val batchUsers = listOf(
            NewTestUsers("Alice Johnson", "alice@example.com"),
            NewTestUsers("Bob Wilson", "bob@example.com"),
            NewTestUsers("Carol Brown", "carol@example.com")
        )

        val insertedBatch = repository.insertAll(batchUsers)
        println("Inserted batch of ${insertedBatch.size} users: $insertedBatch")

        // Test update function
        val updateData = UpdateTestUsers(
            name = "John Updated",
            email = null // Keep existing email
        )

        val updatedUser = repository.update(insertedUser.id, updateData)
        println("Updated user: $updatedUser")

        val foundUser1 = repository.findByIdOrNull(insertedUser.id)
        println("Found user by id=${insertedUser.id}: $foundUser1")

        val findAll = repository.findAll()
        println("Found all users: $findAll")

        val count = repository.count()
        println("Found all users count: $count")

        val succeed = repository.deleteById(insertedUser.id)
        println("Deleted user by id=${insertedUser.id}. succeed = $succeed")

        val existsById0 = repository.existsById(insertedUser.id)
        println("User with id=${insertedUser.id} exists: $existsById0")

        val deleted = repository.deleteAll(insertedBatch.map { it.id })
        println("Deleted a batch of $deleted users")

        println("findAll: ${repository.findAll()}")

        val existsBatch = insertedBatch.map { repository.existsById(it.id) }
        println("Batch of users exists: $existsBatch")

        println("\n=== Generated Code Demo ===")
        println("✅ Generated and working:")
        println("- data class NewSimpleUsers(val name: String, val email: String)")
        println("- data class UpdateSimpleUsers(val name: String?, val email: String?)")
        println("- data class SimpleUsers(val id: Int, val name: String, val email: String)")
        println("- fun repository.insert(new: NewSimpleUsers): SimpleUsers")
        println("- fun repository.insertAll(new: List<NewSimpleUsers>): List<SimpleUsers>")
        println("- fun repository.update(id: Int, update: UpdateSimpleUsers): SimpleUsers")
        println("- fun repository.findByIdOrNull(id: Int): SimpleUsers?")
        println("- fun repository.existsById(id: Int): Boolean")
        println("- fun repository.deleteById(id: Int): Boolean")
        println("- fun repository.deleteAll(): Int")
        println("- fun repository.deleteAll(ids: List<Int>): Int")
        println("- fun repository.deleteAll(values: List<User>): Int")
        println("- fun repository.findAll(): List<SimpleUsers>")
        println("- fun repository.count(): Long")

        println("\nCheck build/generated/ksp/main/kotlin/ for the actual generated files!")
    }
}
