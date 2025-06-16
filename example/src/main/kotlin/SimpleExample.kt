import org.jetbrains.exposed.crud.ksp.GenerateCrud
import org.jetbrains.exposed.sql.*
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
        // Enable SQL logging
        addLogger(StdOutSqlLogger)
        
        // Create table
        SchemaUtils.create(SimpleUsersTable)
        
        println("=== Simple Exposed CRUD KSP Example ===\n")
        
        // Manual insert to demonstrate the concept
        val insertedId = SimpleUsersTable.insert {
            it[name] = "John Doe"
            it[email] = "john@example.com"
        }[SimpleUsersTable.id]
        
        println("Inserted user with ID: $insertedId")
        
        // Manual select to get the user back
        val user = SimpleUsersTable.selectAll()
            .where { SimpleUsersTable.id eq insertedId }
            .map { row ->
                // This is what the generated code would look like:
                // SimpleUsers(
                //     id = row[SimpleUsersTable.id],
                //     name = row[SimpleUsersTable.name], 
                //     email = row[SimpleUsersTable.email]
                // )
                "User(id=${row[SimpleUsersTable.id]}, name=${row[SimpleUsersTable.name]}, email=${row[SimpleUsersTable.email]})"
            }.single()
        
        println("Retrieved user: $user")
        
        println("\n=== Generated Code Demo ===")
        println("The KSP processor should generate:")
        println("- data class NewSimpleUsers(val name: String, val email: String)")
        println("- data class SimpleUsers(val id: Int, val name: String, val email: String)")
        println("- fun SimpleUsersTable.insert(new: NewSimpleUsers): SimpleUsers")
        
        println("\nCheck build/generated/ksp/main/kotlin/ for the actual generated files!")
    }
}