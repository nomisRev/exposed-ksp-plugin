# Exposed CRUD KSP Example

This example demonstrates how to use the Exposed CRUD KSP processor to automatically generate CRUD data classes for your database tables.

## What This Example Shows

1. **Table Definition**: A simple table with basic column types:
   - `SimpleUsersTable`: User data with auto-increment ID, name, and email

2. **Generated Code**: The KSP processor automatically generates:
   - Data classes for the table (`NewSimpleUsers`, `UpdateSimpleUsers`, `SimpleUsers`)
   - Proper handling of primary keys and column types
   - Type-safe data structures

3. **Working Example**: Complete working example showing database setup and manual operations

## Running the Example

### Using Gradle Run Task
```bash
# From the project root
./gradlew :example:run
```

### Build and Inspect Generated Code
```bash
# Build the project (this triggers KSP code generation)
./gradlew :example:build

# Check the generated files
ls -la example/build/generated/ksp/main/kotlin/
cat example/build/generated/ksp/main/kotlin/SimpleUsersCrud.kt
```

### IDE Integration
1. Open the project in IntelliJ IDEA
2. Build the project to trigger KSP generation
3. Run the `SimpleExample.kt` file directly

## What Happens When You Run

1. **Code Generation**: KSP processes the `@GenerateCrud` annotation and generates:
   - `build/generated/ksp/main/kotlin/SimpleUsersCrud.kt`

2. **Database Operations**: The example creates an in-memory H2 database and demonstrates:
   - Creating the table schema
   - Manual insert operation (showing what the generated code would do)
   - Manual select operation to retrieve data
   - SQL logging to show what's happening under the hood

## Generated Code Structure

For the table annotated with `@GenerateCrud`, the processor generates:

### Data Classes
```kotlin
// For creating new records (excludes primary key)
data class NewSimpleUsers(
    val name: String,
    val email: String
)

// For updating records (all fields nullable)
data class UpdateSimpleUsers(
    val name: String?,
    val email: String?
)

// Complete entity (includes primary key)
data class SimpleUsers(
    val id: Int,
    val name: String,
    val email: String
)
```

### Documentation Object
```kotlin
/**
 * CRUD operations for SimpleUsers would be generated here.
 * 
 * Example operations that would be generated:
 * - fun SimpleUsersTable.insert(new: NewSimpleUsers): SimpleUsers
 * - fun SimpleUsersTable.findById(id: Int): SimpleUsers?
 * - fun SimpleUsersTable.update(id: Int, update: UpdateSimpleUsers): SimpleUsers
 * - fun SimpleUsersTable.deleteById(id: Int): Boolean
 * - fun SimpleUsersTable.findAll(): List<SimpleUsers>
 * 
 * Currently only data class generation is implemented.
 */
object SimpleUsersCrudOperations
```

## Key Features Demonstrated

- **Primary Key Handling**: Auto-increment IDs are excluded from `New*` classes
- **Type Safety**: Generated code is fully type-safe with no runtime reflection
- **Clean Data Classes**: Proper Kotlin data class generation with correct types
- **KSP Integration**: Shows how KSP processes annotations and generates code

## Inspecting Generated Code

After building, check the generated files:
```bash
ls -la example/build/generated/ksp/main/kotlin/
cat example/build/generated/ksp/main/kotlin/SimpleUsersCrud.kt
```

You'll see the generated data classes with proper type handling.

## Current Implementation Status

✅ **Implemented:**
- Data class generation (NewEntity, UpdateEntity, Entity)
- Primary key detection and exclusion from NewEntity
- Type mapping for basic types (Int, String, Boolean, BigDecimal)
- Nullable column handling
- KSP processor setup and annotation processing

🚧 **Future Implementation:**
- CRUD extension functions (insert, findById, update, delete, etc.)
- Unique index detection for additional find methods
- Support for more Exposed column types
- Batch operations
- Complex query generation

## Project Structure

```
example/
├── src/main/kotlin/
│   └── SimpleExample.kt          # Main example application
├── build/generated/ksp/main/kotlin/
│   └── SimpleUsersCrud.kt        # Generated CRUD data classes
└── build.gradle.kts              # Example project configuration
```

## Next Steps

This example shows the data class generation foundation. The full CRUD processor will build upon this to generate:
- Complete CRUD operations with proper Exposed DSL usage
- Type-safe query builders
- Batch operations and complex queries
- Integration with Exposed's transaction system

See the main project README for the complete feature roadmap and implementation details.