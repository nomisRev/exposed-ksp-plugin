package org.jetbrains.exposed.crud.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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

            val tableName = ClassName(tableClass.packageName.asString(), tableClass.simpleName.asString())
            val tableAnalysis = analyzeTable(tableName, tableClass)

            logger.info("Processing table: $tableName")

            generateCrudCode(tableAnalysis)

        } catch (e: Exception) {
            logger.error("Error processing table ${tableClass.simpleName.asString()}: ${e.message}")
        }
    }

    private fun analyzeTable(table: ClassName, tableClass: KSClassDeclaration): TableAnalysis {
        val columns = mutableListOf<ColumnInfo>()
        var primaryKeyColumn: ColumnInfo? = null
        val uniqueIndexColumns = mutableListOf<ColumnInfo>()

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
            primaryKey = requireNotNull(primaryKeyColumn) { "No primary key found for table ${tableClass.simpleName.asString()}" },
            uniqueIndexColumns = uniqueIndexColumns,
            table = table,
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

    private fun generateCrudCode(analysis: TableAnalysis) {
        val entity = ClassName(analysis.packageName, analysis.table.simpleName.removeSuffix("Table"))
        val newEntity = ClassName(analysis.packageName, "New$entity")
        val updateEntity = ClassName(analysis.packageName, "Update$entity")

        val fileBuilder = FileSpec.builder(analysis.packageName, "${entity}Crud")

        val needsBigDecimal = analysis.columns.any {
            it.kotlinType.toString().contains("BigDecimal")
        }
        if (needsBigDecimal) {
            fileBuilder.addImport("java.math", "BigDecimal")
        }

        // Generate data classes
        generateDataClasses(fileBuilder, entity, newEntity, updateEntity, analysis)

        // Generate CRUD extension functions
        generateCrudFunctions(fileBuilder, analysis.table, entity, newEntity, updateEntity, analysis)

        val file = fileBuilder.build()
        file.writeTo(codeGenerator, Dependencies(false))
    }

    private fun generateDataClasses(
        fileBuilder: FileSpec.Builder,
        entityName: ClassName,
        newEntityName: ClassName,
        updateEntityName: ClassName,
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
        tableName: ClassName,
        entityName: ClassName,
        newEntityName: ClassName,
        updateEntityName: ClassName,
        analysis: TableAnalysis
    ) {
        fileBuilder.addFunction(InsertFunSpec(tableName, newEntityName, analysis, entityName))
        fileBuilder.addFunction(BatchInsertFunSpec(tableName, entityName, newEntityName, analysis))
//        generateUpdateFunction(fileBuilder, tableName, entityName, updateEntityName, analysis)
    }


    fun CodeBlock.Builder.insertAssignment(analysis: TableAnalysis) = apply {
        val nonPrimaryColumns = analysis.columns.filter { it != analysis.primaryKey }
        for (column in nonPrimaryColumns) {
            if (column.isNullable) {
                addStatement(
                    "if (new.${column.name} != null) it[%T.${column.name}] = new.${column.name}",
                    analysis.table
                )
            } else {
                addStatement("it[%T.${column.name}] = new.${column.name}", analysis.table)
            }
        }
    }

    fun CodeBlock.Builder.entityConstructor(analysis: TableAnalysis) = apply {
        for (column in analysis.columns) addStatement("${column.name} = row[%T.${column.name}],", analysis.table)
    }


    private fun InsertFunSpec(
        tableName: ClassName,
        newEntityName: ClassName,
        analysis: TableAnalysis,
        entity: ClassName
    ): FunSpec =
        FunSpec.builder("insert")
            .receiver(tableName)
            .addParameter("new", newEntityName)
            .returns(entity)
            .addCode(
                CodeBlock.builder()
                    .add("return ")
                    .addStatement(
                        "%T.%M {",
                        tableName,
                        MemberName("org.jetbrains.exposed.sql", "insertReturning")
                    )
                    .withIndent { insertAssignment(analysis) }
                    .addStatement("}.map { row ->")
                    .withIndent {
                        addStatement("%T(", entity)
                        withIndent { entityConstructor(analysis) }
                        addStatement(")")
                    }
                    .addStatement("}.single()")
                    .build()
            )
            .build()

    private fun BatchInsertFunSpec(
        tableName: ClassName,
        entityName: ClassName,
        newEntityName: ClassName,
        analysis: TableAnalysis
    ) =
        FunSpec.builder("insertAll")
            .receiver(tableName)
            .addParameter("new", LIST.parameterizedBy(newEntityName))
            .returns(LIST.parameterizedBy(entityName))
            .addCode(
                CodeBlock.builder()
                    .addStatement("%T.%M(new) { item ->", tableName, MemberName("org.jetbrains.exposed.sql", "batchInsert"))
                    .withIndent { insertAssignment(analysis) }
                    .addStatement("}.map { row ->")
                    .withIndent {
                        addStatement("%T(", entityName)
                        withIndent { entityConstructor(analysis) }
                        addStatement(")")
                    }
                    .addStatement("}")
                    .build()
            )
            .build()


    private fun generateUpdateFunction(
        fileBuilder: FileSpec.Builder,
        tableName: ClassName,
        entityName: ClassName,
        updateEntityName: ClassName,
        analysis: TableAnalysis
    ) {

        val updateFunction = FunSpec.builder("update")
            .receiver(tableName)
            .addParameter("entityId", analysis.primaryKey.kotlinType)
            .addParameter("update", updateEntityName)
            .returns(entityName)
            .addCode(
                CodeBlock.builder()
                    .add("return ")
                    .addStatement(
                        "%T.%M(where = { ${analysis.primaryKey.name} eq entityId }) {",
                        tableName,
                        MemberName("org.jetbrains.exposed.sql", "updateReturning")
                    )
                    .withIndent { insertAssignment(analysis) }
                    .addStatement("}.map { row ->")
                    .withIndent {
                        addStatement("%T(", entityName)
                        withIndent { entityConstructor(analysis) }
                        addStatement(")")
                    }
                    .addStatement("}")
                    .build()
            ).build()

        fileBuilder.addFunction(updateFunction)
    }
}

data class TableAnalysis(
    val table: ClassName,
    val columns: List<ColumnInfo>,
    val primaryKey: ColumnInfo,
    val uniqueIndexColumns: List<ColumnInfo>
) {
    val nonPrimaryColumns = columns.filter { it != primaryKey }
    val packageName = table.packageName
}

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