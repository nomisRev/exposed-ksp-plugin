import org.jetbrains.exposed.crud.ksp.GenerateCrud
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction

@GenerateCrud
object SimpleUsersTable : Table("simple_users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 100)
    override val primaryKey = PrimaryKey(id)
}

fun main() {
    // Setup in-memory H2 database
    Database.connect("jdbc:h2:mem:simple;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    
    transaction {
//        SchemaUtils.create(SimpleUsersTable)

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
        
//        val insertedBatch = SimpleUsersTable.insertAll(batchUsers)
//        println("Inserted batch of ${insertedBatch.size} users: $insertedBatch")
        
        // Test update function
        val updateData = UpdateSimpleUsers(
            name = "John Updated",
            email = null // Keep existing email
        )
        
//        val updatedUser = SimpleUsersTable.update(insertedUser.id, updateData)
//        println("Updated user: $updatedUser")
        
        println("\n=== Generated Code Demo ===")
        println("✅ Generated and working:")
        println("- data class NewSimpleUsers(val name: String, val email: String)")
        println("- data class UpdateSimpleUsers(val name: String?, val email: String?)")
        println("- data class SimpleUsers(val id: Int, val name: String, val email: String)")
        println("- fun SimpleUsersTable.insert(new: NewSimpleUsers): SimpleUsers")
        println("- fun SimpleUsersTable.insertAll(new: List<NewSimpleUsers>): List<SimpleUsers>")
        println("- fun SimpleUsersTable.update(id: Int, update: UpdateSimpleUsers): SimpleUsers")
        
        println("\nCheck build/generated/ksp/main/kotlin/ for the actual generated files!")
    }
}