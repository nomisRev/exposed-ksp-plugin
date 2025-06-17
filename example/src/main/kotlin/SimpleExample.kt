import org.jetbrains.exposed.crud.ksp.GenerateCrud
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.transactions.transaction
import org.testcontainers.containers.PostgreSQLContainer

@GenerateCrud
object SimpleUsersTable : Table("simple_users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}

fun main() {
    val container = PostgreSQLContainer<Nothing>("postgres:16.0-alpine")
    container.start()
    Database.connect(container.jdbcUrl, user = container.username, password = container.password)

    transaction {
        SchemaUtils.create(SimpleUsersTable)

        println("=== Simple Exposed CRUD KSP Example ===\n")

        // Use the generated insert function!
        val newUser = NewSimpleUsers(
            name = "John Doe",
            email = "john@example.com"
        )

        val insertedUser = SimpleUsersTable.insert(newUser)
        println("Inserted user using generated function: $insertedUser")

        // Test another user
        val newUser2 = NewSimpleUsers(
            name = "Jane Smith",
            email = "jane@example.com"
        )

        val insertedUser2 = SimpleUsersTable.insert(newUser2)
        println("Inserted second user: $insertedUser2")

        // Test batch insert
        val batchUsers = listOf(
            NewSimpleUsers("Alice Johnson", "alice@example.com"),
            NewSimpleUsers("Bob Wilson", "bob@example.com"),
            NewSimpleUsers("Carol Brown", "carol@example.com")
        )

        val insertedBatch = SimpleUsersTable.insertAll(batchUsers)
        println("Inserted batch of ${insertedBatch.size} users: $insertedBatch")

        // Test update function
        val updateData = UpdateSimpleUsers(
            name = "John Updated",
            email = null // Keep existing email
        )

        val updatedUser = SimpleUsersTable.update(insertedUser.id, updateData)
        println("Updated user: $updatedUser")

        val foundUser1 = SimpleUsersTable.findByIdOrNull(insertedUser.id)
        println("Found user by id=${insertedUser.id}: $foundUser1")

        val findAll = SimpleUsersTable.findAll()
        println("Found all users: $findAll")

        val count = SimpleUsersTable.count()
        println("Found all users count: $count")

        val succeed = SimpleUsersTable.deleteById(insertedUser.id)
        println("Deleted user by id=${insertedUser.id}. succeed = $succeed")

        val existsById0 = SimpleUsersTable.existsById(insertedUser.id)
        println("User with id=${insertedUser.id} exists: $existsById0")

        val deleted = SimpleUsersTable.deleteAll(insertedBatch.map { it.id })
        println("Deleted a batch of $deleted users")

        println("findAll: ${SimpleUsersTable.findAll()}")

        val existsBatch = insertedBatch.map { SimpleUsersTable.existsById(it.id) }
        println("Batch of users exists: $existsBatch")

        val succeed2 = SimpleUsersTable.deleteAll(listOf(insertedUser2))
        println("Deleted a single user by id=${insertedUser2.id}. succeed = $succeed2")

        println("\n=== Generated Code Demo ===")
        println("✅ Generated and working:")
        println("- data class NewSimpleUsers(val name: String, val email: String)")
        println("- data class UpdateSimpleUsers(val name: String?, val email: String?)")
        println("- data class SimpleUsers(val id: Int, val name: String, val email: String)")
        println("- fun SimpleUsersTable.insert(new: NewSimpleUsers): SimpleUsers")
        println("- fun SimpleUsersTable.insertAll(new: List<NewSimpleUsers>): List<SimpleUsers>")
        println("- fun SimpleUsersTable.update(id: Int, update: UpdateSimpleUsers): SimpleUsers")
        println("- fun SimpleUsersTable.findByIdOrNull(id: Int): SimpleUsers?")
        println("- fun SimpleUsersTable.existsById(id: Int): Boolean")
        println("- fun SimpleUsersTable.deleteById(id: Int): Boolean")
        println("- fun SimpleUsersTable.deleteAll(): Int")
        println("- fun SimpleUsersTable.deleteAll(ids: List<Int>): Int")
        println("- fun SimpleUsersTable.deleteAll(values: List<User>): Int")
        println("- fun SimpleUsersTable.findAll(): List<SimpleUsers>")
        println("- fun SimpleUsersTable.count(): Long")

        println("\nCheck build/generated/ksp/main/kotlin/ for the actual generated files!")
    }
}
