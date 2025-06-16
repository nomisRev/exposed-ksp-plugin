# Exposed CRUD KSP Generator

A Kotlin Symbol Processing (KSP) library that automatically generates CRUD operations for JetBrains Exposed tables.

## Features

This KSP processor generates:
- **Data classes**: `NewEntity`, `UpdateEntity`, and `Entity` classes for your tables
- **CRUD extension functions**: Type-safe extension functions for common database operations
- **Proper type mapping**: Converts Exposed column types to appropriate Kotlin types
- **Nullable handling**: Respects nullable columns defined with `.nullable()`
- **Primary key detection**: Automatically excludes primary keys from insert operations

## Setup

### 1. Add the KSP plugin and dependencies

In your `build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

dependencies {
    // Use the CRUD generator
    ksp("org.jetbrains:exposed-CRUD-ksp:1.0-SNAPSHOT")
    
    // Include the annotation at compile time
    implementation("org.jetbrains:exposed-CRUD-ksp:1.0-SNAPSHOT")
    
    // Exposed dependencies
    implementation("org.jetbrains.exposed:exposed-core:0.57.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.57.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.57.0")
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}
```

### 2. Annotate your table classes

```kotlin
import org.jetbrains.exposed.crud.ksp.GenerateCrud
import org.jetbrains.exposed.sql.Table

@GenerateCrud
object UsersTable : Table("complex_users") {
    val id = integer("id")
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val age = integer("age").nullable()
    override val primaryKey = PrimaryKey(id)
}
```

### 3. Build your project

```bash
./gradlew build
```

## Generated Code

For the example table above, the processor generates:

### Data Classes

```kotlin
// For creating new records (excludes primary key)
data class NewUsers(
    val name: String,
    val email: String,
    val age: Int?
)

// For updating records (all fields nullable)
data class UpdateUsers(
    val name: String?,
    val email: String?,
    val age: Int?
)

// Complete entity (includes primary key)
data class Users(
    val id: Int,
    val name: String,
    val email: String,
    val age: Int?
)
```

### Extension Functions

```kotlin
// Insert operation
fun UsersTable.insert(new: NewUsers): Users

// More CRUD operations will be added in future versions:
// fun UsersTable.insertAll(new: List<NewUsers>): List<Users>
// fun UsersTable.update(id: Int, update: UpdateUsers): Users
// fun UsersTable.findByIdOrNull(id: Int): Users?
// fun UsersTable.findByEmailOrNull(email: String): Users? // for unique indexes
// fun UsersTable.existsById(id: Int): Boolean
// fun UsersTable.findAll(): List<Users>
// fun UsersTable.count(): Int
// fun UsersTable.deleteById(id: Int): Boolean
// fun UsersTable.deleteAll(): Unit
```

## Usage Example

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    
    transaction {
        SchemaUtils.create(UsersTable)
        
        // Create a new user
        val newUser = NewUsers(
            name = "John Doe",
            email = "john@example.com",
            age = 30
        )
        
        // Insert and get the created user with generated ID
        val createdUser = UsersTable.insert(newUser)
        println("Created user: $createdUser")
    }
}
```

## Current Status

✅ **Implemented:**
- Basic KSP processor setup
- Table analysis and column detection
- Data class generation (NewEntity, UpdateEntity, Entity)
- Primary key detection and exclusion from NewEntity
- Nullable column handling
- Type mapping (Int, String, nullable types)
- Insert function generation

🚧 **In Progress:**
- Complete CRUD operations (update, find, delete, etc.)
- Unique index detection for additional find/delete methods
- Better AST analysis for nullable detection
- Support for more Exposed column types

## Building the Project

To build the KSP processor itself:

```bash
./gradlew build
```

To test with the example:

```bash
./gradlew :example:build
```

## Project Structure

```
exposed-CRUD-ksp/
├── src/main/kotlin/org/jetbrains/exposed/crud/ksp/
│   ├── ExposedCrudProcessor.kt      # Main KSP processor
│   └── GenerateCrud.kt              # Annotation definition
├── src/main/resources/META-INF/services/
│   └── com.google.devtools.ksp.processing.SymbolProcessorProvider
├── example/                         # Test project
│   ├── build.gradle.kts
│   └── src/main/kotlin/ExampleTable.kt
└── REQUIREMENTS.md                  # Original requirements
```

## Requirements

- Kotlin 2.1.0+
- KSP 2.1.0-1.0.29+
- JetBrains Exposed 0.57.0+

## Contributing

This project implements the requirements specified in `REQUIREMENTS.md`. See that file for the complete list of CRUD operations that need to be implemented.