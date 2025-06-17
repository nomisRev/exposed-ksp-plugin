# Requirements

We're building a KSP Generator for JetBrians Exposed. Here are the minimal guidelines:
For a given table:

```kotlin
object UsersTable : Table("complex_users") {
    val id = integer("id")
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val age = integer("age").nullable()
    override val primaryKey = PrimaryKey(id)
}
```

We need to generate the following code:

```kotlin
data class NewUser(val name: String, val email: String, val age: Int?)
data class UpdateUser(val name: String?, val email: String?, val age: Int?)
data class User(val id: Int, val name: String, val email: String, val age: Int?)

fun UsersTable.insert(name: String, email: String, age: Int? = null): User =
    insertReturning { insert ->
        insert[UsersTable.name] = name
        insert[UsersTable.email] = email
        if (age != null) insert[UsersTable.age] = age
    }.map { row ->
        User(row[UsersTable.id], row[UsersTable.name], row[UsersTable.email], row[UsersTable.age])
    }
```

It should do so for all CRUD operations:

- [x] fun UsersTable.insert(new: NewUser): User
- [x] fun UsersTable.insertAll(new: List<NewUser>): List<User>
- [x] fun UserTable.update(id: Int, update: UpdateUser): User
- [x] fun UsersTable.findByIdOrNull(id: Int): User?
- [x] fun UsersTable.findByEmailOrNull(email: String): User? // email is uniqueIndex so also generate find
- [x] fun UsersTable.existsById(id: Int)
- [x] fun UsersTable.existsByEmail(id: Int) // email is uniqueIndex so also generate exists
- [x] fun UsersTable.findAll(): List<User>
- [x] fun UsersTable.count(): Int
- [x] fun UsersTable.deleteById(id: Int): Boolean
- [x] fun UsersTable.deleteByEmail(email: String) // // email is uniqueIndex so also generate delete
- [x] fun UsersTable.deleteAll(): Unit
- [x] fun UsersTable.deleteAllById(id: Int)
- [x] fun UsersTable.deleteAllByEmail(id: Int) // email is uniqueIndex so also generate delete
