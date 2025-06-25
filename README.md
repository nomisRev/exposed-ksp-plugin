# Exposed CRUD KSP Generator

A Kotlin Symbol Processing (KSP) library that automatically generates CRUD operations for JetBrains Exposed tables.

## Features

This KSP processor provides three main annotations:

### 1. `@GenerateCrud`
- **Data classes**: `NewEntity`, `UpdateEntity`, and `Entity` classes for your tables
- **CRUD extension functions**: Type-safe extension functions for common database operations
- **Proper type mapping**: Converts Exposed column types to appropriate Kotlin types
- **Nullable handling**: Respects nullable columns defined with `.nullable()`
- **Primary key detection**: Automatically excludes primary keys from insert operations
- **Unique index support**: Generates additional find/delete methods for unique columns

### 2. `@GenerateRepository`
- **Data classes**: Same as `@GenerateCrud`
- **Repository interface**: Creates a repository interface with all CRUD operations
- **Implementation factory**: Generates a factory function to create the repository implementation

### 3. `@ResultRowMapper`
- **Mapping functions**: Generates extension functions to map ResultRow to data classes
- **Batch mapping**: Supports mapping collections of ResultRow to lists of data classes

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

### 2. Build your project

```bash
./gradlew build
```

## Usage Examples

### Example 1: Basic CRUD Operations with `@GenerateCrud`

```kotlin
@GenerateCrud
object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val email = varchar("email", 100).uniqueIndex()
    val age = integer("age").nullable()
    override val primaryKey = PrimaryKey(id)
}
```

Generated code includes:

```kotlin
// Data classes
data class NewUsers(val name: String, val email: String, val age: Int?)
data class UpdateUsers(val name: String?, val email: String?, val age: Int?)
data class Users(val id: Int, val name: String, val email: String, val age: Int?)

// CRUD operations
fun UsersTable.insert(new: NewUsers): Users
fun UsersTable.insertAll(new: List<NewUsers>): List<Users>
fun UsersTable.update(id: Int, update: UpdateUsers): Users
fun UsersTable.findByIdOrNull(id: Int): Users?
fun UsersTable.findByEmailOrNull(email: String): Users? // For unique index
fun UsersTable.existsById(id: Int): Boolean
fun UsersTable.existsByEmail(email: String): Boolean // For unique index
fun UsersTable.findAll(): List<Users>
fun UsersTable.count(): Long
fun UsersTable.deleteById(id: Int): Boolean
fun UsersTable.deleteByEmail(email: String): Boolean // For unique index
fun UsersTable.deleteAll()
fun UsersTable.deleteAll(ids: List<Int>): Int
fun UsersTable.deleteAll(values: List<Users>): Int
```

Usage example:

```kotlin
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
        
        // Update the user
        val updateData = UpdateUsers(
            name = "John Updated",
            email = null, // Keep existing email
            age = 31
        )
        val updatedUser = UsersTable.update(createdUser.id, updateData)
        
        // Find by ID
        val foundUser = UsersTable.findByIdOrNull(createdUser.id)
        
        // Find by unique index
        val userByEmail = UsersTable.findByEmailOrNull("john@example.com")
        
        // Check if exists
        val exists = UsersTable.existsById(createdUser.id)
        
        // Delete
        val deleted = UsersTable.deleteById(createdUser.id)
    }
}
```

### Example 2: Repository Pattern with `@GenerateRepository`

```kotlin
@GenerateRepository
object ProductsTable : Table("products") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 100)
    val price = decimal("price", 10, 2)
    val sku = varchar("sku", 50).uniqueIndex()
    override val primaryKey = PrimaryKey(id)
}
```

Generated code includes:

```kotlin
// Data classes
data class NewProducts(val name: String, val price: BigDecimal, val sku: String)
data class UpdateProducts(val name: String?, val price: BigDecimal?, val sku: String?)
data class Products(val id: Int, val name: String, val price: BigDecimal, val sku: String)

// Repository interface
interface ProductsRepository {
    fun insert(new: NewProducts): Products
    fun insertAll(new: List<NewProducts>): List<Products>
    fun update(id: Int, update: UpdateProducts): Products
    fun findByIdOrNull(id: Int): Products?
    fun findBySkuOrNull(sku: String): Products?
    fun existsById(id: Int): Boolean
    fun existsBySku(sku: String): Boolean
    fun findAll(): List<Products>
    fun count(): Long
    fun deleteById(id: Int): Boolean
    fun deleteBySku(sku: String): Boolean
    fun deleteAll()
    fun deleteAll(ids: List<Int>): Int
    fun deleteAll(values: List<Products>): Int
}

// Factory function
fun ProductsRepository(database: Database): ProductsRepository
```

Usage example:

```kotlin
fun main() {
    val database = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    
    // Create repository
    val repository = ProductsRepository(database)
    
    transaction {
        SchemaUtils.create(ProductsTable)
        
        // Create a new product
        val newProduct = NewProducts(
            name = "Smartphone",
            price = BigDecimal("999.99"),
            sku = "PHONE-123"
        )
        
        // Insert using repository
        val createdProduct = repository.insert(newProduct)
        
        // Find by SKU
        val productBySku = repository.findBySkuOrNull("PHONE-123")
        
        // Get all products
        val allProducts = repository.findAll()
        
        // Delete by ID
        repository.deleteById(createdProduct.id)
    }
}
```

### Example 3: ResultRow Mapping with `@ResultRowMapper`

```kotlin
object OrdersTable : Table("orders") {
    val id = integer("id").autoIncrement()
    val customerName = varchar("customer_name", 100)
    val total = decimal("total", 10, 2)
    val status = varchar("status", 20)
    override val primaryKey = PrimaryKey(id)
}

@ResultRowMapper(table = OrdersTable::class)
data class OrderSummary(
    val id: Int,
    val customerName: String,
    val total: BigDecimal
)
```

Generated code includes:

```kotlin
// Extension functions for mapping ResultRow to OrderSummary
fun ResultRow.toOrderSummary(): OrderSummary = OrderSummary(
    id = this[OrdersTable.id],
    customerName = this[OrdersTable.customerName],
    total = this[OrdersTable.total]
)

// Extension function for mapping Iterable<ResultRow> to List<OrderSummary>
fun Iterable<ResultRow>.toOrderSummary(): List<OrderSummary> = map { it.toOrderSummary() }
```

Usage example:

```kotlin
fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")
    
    transaction {
        SchemaUtils.create(OrdersTable)
        
        // Insert some test data
        OrdersTable.insert {
            it[customerName] = "Alice Johnson"
            it[total] = BigDecimal("123.45")
            it[status] = "COMPLETED"
        }
        
        // Query the database
        val query = OrdersTable.selectAll()
        
        // Use the generated mapper to convert a single row
        val firstOrder = query.first().toOrderSummary()
        println("First order: $firstOrder")
        
        // Use the generated mapper to convert all rows
        val allOrders = query.toOrderSummary()
        println("All orders: $allOrders")
    }
}
```

## Current Status

All features described in the requirements have been implemented:

✅ **Implemented:**
- Data class generation (NewEntity, UpdateEntity, Entity)
- Primary key detection and exclusion from NewEntity
- Nullable column handling
- Type mapping for all Exposed column types
- Complete CRUD operations:
  - insert
  - insertAll
  - update
  - findByIdOrNull
  - findByUniqueColumnOrNull
  - existsById
  - existsByUniqueColumn
  - findAll
  - count
  - deleteById
  - deleteByUniqueColumn
  - deleteAll
  - deleteAll(ids)
  - deleteAll(entities)
- Unique index detection for additional find/delete methods
- Repository pattern support
- ResultRow mapping

## Requirements

- Kotlin 2.1.0+
- KSP 2.1.0-1.0.29+
- JetBrains Exposed 0.57.0+

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
│   ├── GenerateCrud.kt              # CRUD annotation
│   ├── GenerateRepository.kt        # Repository annotation
│   └── ResultRowMapper.kt           # ResultRow mapper annotation
├── src/main/resources/META-INF/services/
│   └── com.google.devtools.ksp.processing.SymbolProcessorProvider
├── example/                         # Test project
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── SimpleExample.kt         # @GenerateCrud example
│       ├── RepositoryExample.kt     # @GenerateRepository example
│       └── ResultRowMapperExample.kt # @ResultRowMapper example
└── REQUIREMENTS.md                  # Original requirements
```

## Contributing

This project implements the requirements specified in `REQUIREMENTS.md`. See that file for the complete list of CRUD operations that have been implemented.