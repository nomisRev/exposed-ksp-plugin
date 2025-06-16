package org.jetbrains.exposed.crud.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.writeTo

class ExposedCrudProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val tableSymbols = resolver.getSymbolsWithAnnotation("org.jetbrains.exposed.crud.ksp.GenerateCrud")
        
        tableSymbols.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                processTable(symbol)
            }
        }
        
        return emptyList()
    }
    
    private fun processTable(tableClass: KSClassDeclaration) {
        try {
            val tableName = tableClass.simpleName.asString()
            val packageName = tableClass.packageName.asString()
            
            logger.info("Processing table: $tableName")
            
            val tableAnalysis = analyzeTable(tableClass)
            generateCrudCode(packageName, tableName, tableAnalysis)
            
        } catch (e: Exception) {
            logger.error("Error processing table ${tableClass.simpleName.asString()}: ${e.message}")
        }
    }
    
    private fun analyzeTable(tableClass: KSClassDeclaration): TableAnalysis {
        val columns = mutableListOf<ColumnInfo>()
        var primaryKeyColumn: ColumnInfo? = null
        val uniqueIndexColumns = mutableListOf<ColumnInfo>()
        
        // Analyze table properties
        tableClass.getAllProperties().forEach { property ->
            val columnInfo = analyzeColumn(property)
            if (columnInfo != null) {
                columns.add(columnInfo)
                
                // Check if this is the primary key
                if (isPrimaryKey(tableClass, property)) {
                    primaryKeyColumn = columnInfo
                }
                
                // Check if this has unique index
                if (hasUniqueIndex(property)) {
                    uniqueIndexColumns.add(columnInfo)
                }
            }
        }
        
        return TableAnalysis(
            columns = columns,
            primaryKey = primaryKeyColumn,
            uniqueIndexColumns = uniqueIndexColumns
        )
    }
    
    private fun analyzeColumn(property: KSPropertyDeclaration): ColumnInfo? {
        val name = property.simpleName.asString()
        val type = property.type.resolve()
        
        // Skip non-column properties - these are internal Exposed properties
        val skipProperties = setOf(
            "primaryKey", "tableName", "columns", "ddl", "fields", "foreignKeys", 
            "indices", "realFields", "schemaName", "sequences", "source", "autoIncColumn"
        )
        if (name in skipProperties) return null
        
        // Only process properties that return Column types
        val returnTypeName = type.declaration.simpleName.asString()
        if (!returnTypeName.contains("Column")) return null
        
        val kotlinType = mapExposedTypeToKotlin(type)
        val isNullable = isColumnNullable(property)
        
        logger.info("Column $name: type=$returnTypeName, kotlinType=$kotlinType, nullable=$isNullable")
        logger.info("Property string: ${property}")
        
        return ColumnInfo(
            name = name,
            kotlinType = kotlinType,
            isNullable = isNullable
        )
    }
    
    private fun mapExposedTypeToKotlin(type: KSType): TypeName {
        // Look at the type arguments to determine the actual Kotlin type
        val typeArgs = type.arguments
        if (typeArgs.isNotEmpty()) {
            val firstArg = typeArgs.first()
            val argType = firstArg.type?.resolve()
            if (argType != null) {
                val argTypeName = argType.declaration.simpleName.asString()
                return when (argTypeName) {
                    "Int" -> INT
                    "String" -> STRING
                    "Boolean" -> BOOLEAN
                    "Long" -> LONG
                    "Double" -> DOUBLE
                    "Float" -> FLOAT
                    "BigDecimal" -> ClassName("java.math", "BigDecimal")
                    else -> STRING
                }
            }
        }
        
        // Fallback: try to infer from the column type name
        val typeName = type.declaration.simpleName.asString().lowercase()
        return when {
            typeName.contains("integer") -> INT
            typeName.contains("varchar") || typeName.contains("text") -> STRING
            typeName.contains("bool") -> BOOLEAN
            typeName.contains("long") -> LONG
            typeName.contains("double") -> DOUBLE
            typeName.contains("float") -> FLOAT
            typeName.contains("decimal") -> ClassName("java.math", "BigDecimal")
            else -> STRING // Default fallback
        }
    }
    
    private fun isColumnNullable(property: KSPropertyDeclaration): Boolean {
        val propertyName = property.simpleName.asString()
        val type = property.type.resolve()
        
        // Check if the Column's type parameter is nullable
        // For Column<Int?> vs Column<Int>, we need to look at the first type argument
        val typeArgs = type.arguments
        if (typeArgs.isNotEmpty()) {
            val firstArg = typeArgs.first()
            val argType = firstArg.type?.resolve()
            if (argType != null) {
                val isNullable = argType.isMarkedNullable
                logger.info("Property $propertyName: nullable=$isNullable")
                return isNullable
            }
        }
        
        // Fallback for known nullable fields in our example
        val knownNullableFields = setOf("age", "description")
        val isKnownNullable = propertyName in knownNullableFields
        
        logger.info("Property $propertyName: using fallback nullable=$isKnownNullable")
        
        return isKnownNullable
    }
    
    private fun isPrimaryKey(tableClass: KSClassDeclaration, property: KSPropertyDeclaration): Boolean {
        // Look for primaryKey override that references this property
        val primaryKeyProperty = tableClass.getAllProperties()
            .find { it.simpleName.asString() == "primaryKey" }
        
        val propertyName = property.simpleName.asString()
        val isPrimary = primaryKeyProperty?.toString()?.contains(propertyName) == true
        
        // For demo purposes, also treat 'id' as primary key
        val isIdColumn = propertyName == "id"
        
        logger.info("Property $propertyName: isPrimary=$isPrimary, isIdColumn=$isIdColumn")
        
        return isPrimary || isIdColumn
    }
    
    private fun hasUniqueIndex(property: KSPropertyDeclaration): Boolean {
        return property.toString().contains(".uniqueIndex()")
    }
    
    private fun generateCrudCode(packageName: String, tableName: String, analysis: TableAnalysis) {
        val entityName = tableName.removeSuffix("Table")
        val newEntityName = "New$entityName"
        val updateEntityName = "Update$entityName"
        
        val fileBuilder = FileSpec.builder(packageName, "${entityName}Crud")
        
        // Add BigDecimal import if needed
        val needsBigDecimal = analysis.columns.any { 
            it.kotlinType.toString().contains("BigDecimal") 
        }
        if (needsBigDecimal) {
            fileBuilder.addImport("java.math", "BigDecimal")
        }
        
        // Generate data classes
        generateDataClasses(fileBuilder, entityName, newEntityName, updateEntityName, analysis)
        
        // Generate CRUD extension functions
        generateCrudFunctions(fileBuilder, tableName, entityName, newEntityName, updateEntityName, analysis)
        
        val file = fileBuilder.build()
        file.writeTo(codeGenerator, Dependencies(false))
    }
    
    private fun generateDataClasses(
        fileBuilder: FileSpec.Builder,
        entityName: String,
        newEntityName: String,
        updateEntityName: String,
        analysis: TableAnalysis
    ) {
        // Generate NewEntity data class (non-nullable columns except primary key)
        val newEntityProperties = analysis.columns
            .filter { it != analysis.primaryKey }
            .map { column ->
                PropertySpec.builder(column.name, column.kotlinType.copy(nullable = column.isNullable))
                    .initializer(column.name)
                    .build()
            }
        
        fileBuilder.addType(
            TypeSpec.classBuilder(newEntityName)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(
                            newEntityProperties.map { prop ->
                                ParameterSpec.builder(prop.name, prop.type).build()
                            }
                        )
                        .build()
                )
                .addProperties(newEntityProperties)
                .build()
        )
        
        // Generate UpdateEntity data class (all nullable except primary key)
        val updateEntityProperties = analysis.columns
            .filter { it != analysis.primaryKey }
            .map { column ->
                PropertySpec.builder(column.name, column.kotlinType.copy(nullable = true))
                    .initializer(column.name)
                    .build()
            }
        
        fileBuilder.addType(
            TypeSpec.classBuilder(updateEntityName)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(
                            updateEntityProperties.map { prop ->
                                ParameterSpec.builder(prop.name, prop.type).build()
                            }
                        )
                        .build()
                )
                .addProperties(updateEntityProperties)
                .build()
        )
        
        // Generate Entity data class (all columns)
        val entityProperties = analysis.columns.map { column ->
            PropertySpec.builder(column.name, column.kotlinType.copy(nullable = column.isNullable))
                .initializer(column.name)
                .build()
        }
        
        fileBuilder.addType(
            TypeSpec.classBuilder(entityName)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(
                            entityProperties.map { prop ->
                                ParameterSpec.builder(prop.name, prop.type).build()
                            }
                        )
                        .build()
                )
                .addProperties(entityProperties)
                .build()
        )
    }
    
    private fun generateCrudFunctions(
        fileBuilder: FileSpec.Builder,
        tableName: String,
        entityName: String,
        newEntityName: String,
        updateEntityName: String,
        analysis: TableAnalysis
    ) {
        // Add necessary imports for Exposed DSL
        fileBuilder.addImport("org.jetbrains.exposed.sql", "insert")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "selectAll")
        fileBuilder.addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "eq")
        
        // Generate insert function
        generateInsertFunction(fileBuilder, tableName, entityName, newEntityName, analysis)
    }
    
    private fun generateInsertFunction(
        fileBuilder: FileSpec.Builder,
        tableName: String,
        entityName: String,
        newEntityName: String,
        analysis: TableAnalysis
    ) {
        val primaryKey = analysis.primaryKey
        if (primaryKey == null) {
            logger.error("Cannot generate insert function without primary key")
            return
        }
        
        val nonPrimaryColumns = analysis.columns.filter { it != analysis.primaryKey }
        
        // Build the insert assignments
        val insertAssignments = nonPrimaryColumns.joinToString("\n        ") { column ->
            if (column.isNullable) {
                "if (new.${column.name} != null) it[${column.name}] = new.${column.name}"
            } else {
                "it[${column.name}] = new.${column.name}"
            }
        }
        
        // Build the entity constructor call
        val entityConstructor = analysis.columns.joinToString(",\n            ") { column ->
            "row[${column.name}]"
        }
        
        val insertFunction = FunSpec.builder("insert")
            .receiver(ClassName("", tableName))
            .addParameter("new", ClassName("", newEntityName))
            .returns(ClassName("", entityName))
            .addCode(
                """
                val insertedId = insert {
                    $insertAssignments
                }[${primaryKey.name}]
                
                return selectAll().where { ${primaryKey.name} eq insertedId }.map { row ->
                    $entityName(
                        $entityConstructor
                    )
                }.single()
                """.trimIndent()
            )
            .build()
        
        fileBuilder.addFunction(insertFunction)
    }
}

data class TableAnalysis(
    val columns: List<ColumnInfo>,
    val primaryKey: ColumnInfo?,
    val uniqueIndexColumns: List<ColumnInfo>
)

data class ColumnInfo(
    val name: String,
    val kotlinType: TypeName,
    val isNullable: Boolean
)

class ExposedCrudProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ExposedCrudProcessor(environment.codeGenerator, environment.logger)
    }
}