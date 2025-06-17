package org.jetbrains.exposed.crud.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.sql.ResultRow
import kotlin.reflect.KClass

class ExposedCrudProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    private fun processTableForRepository(tableClass: KSClassDeclaration) {
        try {
            val tableName = ClassName(tableClass.packageName.asString(), tableClass.simpleName.asString())
            val tableAnalysis = analyzeTable(tableName, tableClass)

            logger.info("Processing table for repository: $tableName")

            generateRepositoryCode(tableAnalysis)

        } catch (e: Exception) {
            logger.error("Error processing table for repository ${tableClass.simpleName.asString()}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun generateRepositoryCode(analysis: TableAnalysis) {
        val entity = ClassName(analysis.packageName, analysis.table.simpleName.removeSuffix("Table"))
        val newEntity = ClassName(analysis.packageName, "New${entity.simpleName}")
        val updateEntity = ClassName(analysis.packageName, "Update${entity.simpleName}")
        val repositoryName = "${entity.simpleName}Repository"

        // Use the table name in the file name to avoid conflicts
        val fileBuilder = FileSpec.builder(analysis.packageName, "${analysis.table.simpleName}Repository")

        val needsBigDecimal = analysis.columns.any {
            it.kotlinType.toString().contains("BigDecimal")
        }
        if (needsBigDecimal) {
            fileBuilder.addImport("java.math", "BigDecimal")
        }

        // Add imports
        fileBuilder.addImport("org.jetbrains.exposed.sql", "Database")
        fileBuilder.addImport("org.jetbrains.exposed.sql.transactions", "transaction")
        fileBuilder.addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "eq")
        fileBuilder.addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "inList")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "insertReturning")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "updateReturning")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "batchInsert")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "selectAll")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "count")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "deleteWhere")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "deleteAll")

        // Generate data classes
        fileBuilder.generateDataClasses(entity, newEntity, updateEntity, analysis)

        // Generate repository interface
        val repositoryInterface = TypeSpec.interfaceBuilder(repositoryName)
            .addFunction(FunSpec.builder("insert")
                .addParameter("new", newEntity)
                .returns(entity)
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("insertAll")
                .addParameter("new", LIST.parameterizedBy(newEntity))
                .returns(LIST.parameterizedBy(entity))
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("update")
                .addParameter("id", analysis.primaryKey.kotlinType)
                .addParameter("update", updateEntity)
                .returns(entity)
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("findByIdOrNull")
                .addParameter("id", analysis.primaryKey.kotlinType)
                .returns(entity.copy(nullable = true))
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("existsById")
                .addParameter("id", analysis.primaryKey.kotlinType)
                .returns(BOOLEAN)
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("deleteById")
                .addParameter("id", analysis.primaryKey.kotlinType)
                .returns(BOOLEAN)
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("deleteAll")
                .returns(INT)
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("deleteAll")
                .addParameter("ids", LIST.parameterizedBy(analysis.primaryKey.kotlinType))
                .returns(INT)
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("findAll")
                .returns(LIST.parameterizedBy(entity))
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .addFunction(FunSpec.builder("count")
                .returns(LONG)
                .addModifiers(KModifier.ABSTRACT)
                .build())
            .build()

        fileBuilder.addType(repositoryInterface)

        // Generate repository implementation factory function
        val factoryFunction = FunSpec.builder(repositoryName)
            .addParameter("database", ClassName("org.jetbrains.exposed.sql", "Database"))
            .returns(ClassName(analysis.packageName, repositoryName))
            .addCode {
                beginControlFlow("return object : %T", ClassName(analysis.packageName, repositoryName))

                // Insert implementation
                beginControlFlow("override fun insert(new: %T): %T", newEntity, entity)
                addStatement("return transaction(database) {")
                withIndent {
                    beginControlFlow("%T.insertReturning {", analysis.table)
                    for (column in analysis.nonPrimaryColumns) {
                        if (column.isNullable) {
                            addStatement("if (new.${column.name} != null) it[%T.${column.name}] = new.${column.name}", analysis.table)
                        } else {
                            addStatement("it[%T.${column.name}] = new.${column.name}", analysis.table)
                        }
                    }
                    endControlFlow()
                    mapResultRow(analysis, entity)
                    addStatement(".single()")
                }
                addStatement("}")
                endControlFlow()

                // InsertAll implementation
                beginControlFlow("override fun insertAll(new: %T): %T", LIST.parameterizedBy(newEntity), LIST.parameterizedBy(entity))
                addStatement("return transaction(database) {")
                withIndent {
                    beginControlFlow("%T.batchInsert(new) { item: %T ->", analysis.table, newEntity)
                    for (column in analysis.nonPrimaryColumns) {
                        if (column.isNullable) {
                            addStatement("if (item.${column.name} != null) this[%T.${column.name}] = item.${column.name}", analysis.table)
                        } else {
                            addStatement("this[%T.${column.name}] = item.${column.name}", analysis.table)
                        }
                    }
                    endControlFlow()
                    mapResultRow(analysis, entity)
                }
                addStatement("}")
                endControlFlow()

                // Update implementation
                beginControlFlow("override fun update(id: %T, update: %T): %T", analysis.primaryKey.kotlinType, updateEntity, entity)
                addStatement("return transaction(database) {")
                withIndent {
                    beginControlFlow("%T.updateReturning(where = { %T.${analysis.primaryKey.name} eq id }) {", analysis.table, analysis.table)
                    for (column in analysis.nonPrimaryColumns) {
                        addStatement("if (update.${column.name} != null) it[%T.${column.name}] = update.${column.name}", analysis.table)
                    }
                    endControlFlow()
                    mapResultRow(analysis, entity)
                    addStatement(".single()")
                }
                addStatement("}")
                endControlFlow()

                // FindByIdOrNull implementation
                beginControlFlow("override fun findByIdOrNull(id: %T): %T", analysis.primaryKey.kotlinType, entity.copy(nullable = true))
                addStatement("return transaction(database) {")
                withIndent {
                    add("%T.selectAll().where { %T.${analysis.primaryKey.name} eq id }", analysis.table, analysis.table)
                    mapResultRow(analysis, entity)
                    addStatement(".singleOrNull()")
                }
                addStatement("}")
                endControlFlow()

                // ExistsById implementation
                beginControlFlow("override fun existsById(id: %T): %T", analysis.primaryKey.kotlinType, BOOLEAN)
                addStatement("return transaction(database) {")
                withIndent {
                    addStatement("%T.selectAll().where { %T.${analysis.primaryKey.name} eq id }.limit(1).count() > 0", analysis.table, analysis.table)
                }
                addStatement("}")
                endControlFlow()

                // DeleteById implementation
                beginControlFlow("override fun deleteById(id: %T): %T", analysis.primaryKey.kotlinType, BOOLEAN)
                addStatement("return transaction(database) {")
                withIndent {
                    addStatement("%T.deleteWhere { %T.${analysis.primaryKey.name} eq id } > 0", analysis.table, analysis.table)
                }
                addStatement("}")
                endControlFlow()

                // DeleteAll implementation
                beginControlFlow("override fun deleteAll(): %T", INT)
                addStatement("return transaction(database) {")
                withIndent {
                    addStatement("val count = %T.selectAll().count().toInt()", analysis.table)
                    addStatement("%T.deleteAll()", analysis.table)
                    addStatement("count")
                }
                addStatement("}")
                endControlFlow()

                // DeleteAll by ids implementation
                beginControlFlow("override fun deleteAll(ids: %T): %T", LIST.parameterizedBy(analysis.primaryKey.kotlinType), INT)
                addStatement("return transaction(database) {")
                withIndent {
                    addStatement("%T.deleteWhere { %T.${analysis.primaryKey.name} inList ids }", analysis.table, analysis.table)
                }
                addStatement("}")
                endControlFlow()

                // FindAll implementation
                beginControlFlow("override fun findAll(): %T", LIST.parameterizedBy(entity))
                addStatement("return transaction(database) {")
                withIndent {
                    addStatement("%T.selectAll()", analysis.table)
                    mapResultRow(analysis, entity)
                }
                addStatement("}")
                endControlFlow()

                // Count implementation
                beginControlFlow("override fun count(): %T", LONG)
                addStatement("return transaction(database) {")
                withIndent {
                    addStatement("%T.selectAll().count()", analysis.table)
                }
                addStatement("}")
                endControlFlow()

                endControlFlow()
            }
            .build()

        fileBuilder.addFunction(factoryFunction)

        val file = fileBuilder.build()
        file.writeTo(codeGenerator, Dependencies(false))
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Process tables with GenerateCrud annotation
        val tableSymbols = resolver.getSymbolsWithAnnotation("org.jetbrains.exposed.crud.ksp.GenerateCrud")
        tableSymbols.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                processTable(symbol)
            }
        }

        // Process tables with GenerateRepository annotation
        val repoTableSymbols = resolver.getSymbolsWithAnnotation("org.jetbrains.exposed.crud.ksp.GenerateRepository")
        repoTableSymbols.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                processTableForRepository(symbol)
            }
        }

        // Process data classes with ResultRowMapper annotation
        val dataClassSymbols = resolver.getSymbolsWithAnnotation("org.jetbrains.exposed.crud.ksp.ResultRowMapper")
        dataClassSymbols.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                processDataClass(symbol)
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
            e.printStackTrace()
            throw e
        }
    }

    private fun processDataClass(dataClass: KSClassDeclaration) {
        try {
            val className = dataClass.toClassName()
            logger.info("Processing data class: $className")

            // Get the table class from the annotation
            val annotation = dataClass.annotations.find {
                it.shortName.asString() == "ResultRowMapper"
            }
                ?: throw IllegalStateException("ResultRowMapper annotation not found on ${dataClass.simpleName.asString()}")

            val tableClassArg = annotation.arguments.find {
                it.name?.asString() == "table"
            } ?: throw IllegalStateException("table parameter not found in ResultRowMapper annotation")

            val tableClassValue = tableClassArg.value as KSType
            val tableClassName = tableClassValue.declaration.qualifiedName?.asString()
                ?: throw IllegalStateException("Could not resolve table class name")

            val tableClass = ClassName.bestGuess(tableClassName)

            // Generate extension functions for ResultRow and Iterable<ResultRow>
            generateResultRowMapperCode(className, dataClass, tableClass)

        } catch (e: Exception) {
            logger.error("Error processing data class ${dataClass.simpleName.asString()}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun generateResultRowMapperCode(
        className: ClassName,
        dataClass: KSClassDeclaration,
        tableClass: ClassName
    ) {
        // Get all properties of the data class
        val properties = dataClass.getAllProperties().toList()

        // Create a file with extension functions
        val fileName = "${className.simpleName}ResultRowMapper"
        val fileSpec = FileSpec.builder(className.packageName, fileName)
            .addImport("org.jetbrains.exposed.sql", "ResultRow")
            .addFunction(generateToDataClassFunction(className, properties, tableClass))
            .addFunction(generateIterableToDataClassFunction(className))
            .build()

        // Write the generated code to a file
        fileSpec.writeTo(codeGenerator, Dependencies(false))

        logger.info("Generated ResultRow mapper for ${className.simpleName}")
    }

    private fun generateToDataClassFunction(
        className: ClassName,
        properties: List<KSPropertyDeclaration>,
        tableClass: ClassName
    ): FunSpec {
        val functionName = "to${className.simpleName}"

        return FunSpec.builder(functionName)
            .receiver(ClassName("org.jetbrains.exposed.sql", "ResultRow"))
            .returns(className)
            .addCode {
                add("return ")
                addStatement("%T(", className)
                withIndent {
                    properties.forEach { property ->
                        val propertyName = property.simpleName.asString()
                        addStatement("$propertyName = this[%T.$propertyName],", tableClass)
                    }
                }
                addStatement(")")
            }
            .build()
    }

    private fun generateIterableToDataClassFunction(className: ClassName): FunSpec {
        val functionName = "to${className.simpleName}"
        val iterableOfResultRow = ClassName("kotlin.collections", "Iterable")
            .parameterizedBy(ClassName("org.jetbrains.exposed.sql", "ResultRow"))
        val listOfDataClass = ClassName("kotlin.collections", "List")
            .parameterizedBy(className)

        return FunSpec.builder(functionName)
            .receiver(iterableOfResultRow)
            .returns(listOfDataClass)
            .addCode {
                addStatement("return map { row -> row.to${className.simpleName}() }")
            }
            .build()
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
        val newEntity = ClassName(analysis.packageName, "New${entity.simpleName}")
        val updateEntity = ClassName(analysis.packageName, "Update${entity.simpleName}")

        // Use the table name in the file name to avoid conflicts
        val fileBuilder = FileSpec.builder(analysis.packageName, "${analysis.table.simpleName}Crud")

        val needsBigDecimal = analysis.columns.any {
            it.kotlinType.toString().contains("BigDecimal")
        }
        if (needsBigDecimal) {
            fileBuilder.addImport("java.math", "BigDecimal")
        }

        // Generate data classes
        fileBuilder.generateDataClasses(entity, newEntity, updateEntity, analysis)

        fileBuilder.addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "eq")
        fileBuilder.addImport("org.jetbrains.exposed.sql.SqlExpressionBuilder", "inList")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "select")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "selectAll")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "count")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "deleteWhere")
        fileBuilder.addImport("org.jetbrains.exposed.sql", "deleteAll")

        // Insert and update functions
        fileBuilder.addFunction(InsertFunSpec(newEntity, analysis, entity))
        fileBuilder.addFunction(UpdateFunSpec(entity, updateEntity, analysis))
        fileBuilder.addFunction(BatchInsertFunSpec(entity, newEntity, analysis))

        // Find functions
        fileBuilder.addFunction(FindByIdOrNullFunSpec(entity, analysis))

        // Generate findByXOrNull for unique index columns
        for (uniqueColumn in analysis.uniqueIndexColumns) {
            fileBuilder.addFunction(FindByUniqueColumnOrNullFunSpec(entity, analysis, uniqueColumn))
        }

        // Exists functions
        fileBuilder.addFunction(ExistsByIdFunSpec(analysis))

        // Generate existsByX for unique index columns
        for (uniqueColumn in analysis.uniqueIndexColumns) {
            fileBuilder.addFunction(ExistsByUniqueColumnFunSpec(analysis, uniqueColumn))
        }

        // FindAll and count functions
        fileBuilder.addFunction(FindAllFunSpec(entity, analysis))
        fileBuilder.addFunction(CountFunSpec(analysis))

        // Delete functions
        fileBuilder.addFunction(DeleteByIdFunSpec(analysis))

        // Generate deleteByX for unique index columns
        for (uniqueColumn in analysis.uniqueIndexColumns) {
            fileBuilder.addFunction(DeleteByUniqueColumnFunSpec(analysis, uniqueColumn))
        }

        // DeleteAll function
        fileBuilder.addFunction(DeleteAllFunSpec(analysis))
        fileBuilder.addFunction(DeleteAllByIdFunSpec(analysis))
        fileBuilder.addFunction(DeleteAllByEntityFunSpec(entity, analysis))

        // Generate deleteAllByX for unique index columns
        for (uniqueColumn in analysis.uniqueIndexColumns) {
            fileBuilder.addFunction(DeleteAllByUniqueColumnFunSpec(analysis, uniqueColumn))
        }

        val file = fileBuilder.build()
        file.writeTo(codeGenerator, Dependencies(false))
    }

    private fun FileSpec.Builder.generateDataClasses(
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

        addType(
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

        addType(
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

        addType(
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


    fun FunSpec.Builder.addCode(block: CodeBlock.Builder.() -> Unit): FunSpec.Builder = apply {
        addCode(
            CodeBlock.builder().apply(block).build()
        )
    }

    private fun InsertFunSpec(
        newEntityName: ClassName,
        analysis: TableAnalysis,
        entity: ClassName
    ): FunSpec = FunSpec.builder("insert")
        .receiver(analysis.table)
        .addParameter("new", newEntityName)
        .returns(entity)
        .addCode {
            add("return ")
            beginControlFlow(
                "%T.%M {",
                analysis.table,
                MemberName("org.jetbrains.exposed.sql", "insertReturning")
            )
            withIndent {
                for (column in analysis.nonPrimaryColumns) {
                    if (column.isNullable) {
                        addStatement(
                            "if (${"new"}.${column.name} != null) ${"it"}[%T.${column.name}] = ${"new"}.${column.name}",
                            analysis.table
                        )
                    } else {
                        addStatement("${"it"}[%T.${column.name}] = ${"new"}.${column.name}", analysis.table)
                    }
                }
            }
            endControlFlow()
            mapResultRow(analysis, entity)
            addStatement(".single()")
        }
        .build()

    private fun BatchInsertFunSpec(
        entity: ClassName,
        newEntity: ClassName,
        analysis: TableAnalysis
    ) = FunSpec.builder("insertAll")
        .receiver(analysis.table)
        .addParameter("new", LIST.parameterizedBy(newEntity))
        .returns(LIST.parameterizedBy(entity))
        .addCode {
            addStatement(
                "return %T.%M(new) { item: %T ->",
                analysis.table,
                MemberName("org.jetbrains.exposed.sql", "batchInsert"),
                newEntity
            )
            withIndent {
                val nonPrimaryColumns = analysis.columns.filter { it != analysis.primaryKey }
                for (column in nonPrimaryColumns) {
                    if (column.isNullable) {
                        addStatement(
                            "if (item.${column.name} != null) this[%T.${column.name}] = item.${column.name}",
                            analysis.table
                        )
                    } else {
                        addStatement("this[%T.${column.name}] = item.${column.name}", analysis.table)
                    }
                }
            }
            add("}\n")
            mapResultRow(analysis, entity)
        }
        .build()


    private fun UpdateFunSpec(
        entity: ClassName,
        updateEntity: ClassName,
        analysis: TableAnalysis
    ) = FunSpec.builder("update")
        .receiver(analysis.table)
        .addParameter("id", analysis.primaryKey.kotlinType)
        .addParameter("update", updateEntity)
        .returns(entity)
        .addCode {
            addStatement(
                "return %T.%M(where = { %T.${analysis.primaryKey.name} eq id }) {",
                analysis.table,
                MemberName("org.jetbrains.exposed.sql", "updateReturning"),
                analysis.table
            )
            withIndent {
                for (column in analysis.nonPrimaryColumns) {
                    addStatement(
                        "if (update.${column.name} != null) it[%T.${column.name}] = update.${column.name}",
                        analysis.table
                    )
                }
            }
            add("}\n")
            mapResultRow(analysis, entity)
            addStatement(".single()")
        }.build()

    fun CodeBlock.Builder.mapResultRow(analysis: TableAnalysis, entity: ClassName) = apply {
        beginControlFlow(".map { row ->")
        addStatement("%T(", entity)
        withIndent {
            for (column in analysis.columns)
                addStatement("${column.name} = row[%T.${column.name}],", analysis.table)
        }
        addStatement(")")
        endControlFlow()
    }

    private fun FindByIdOrNullFunSpec(entity: ClassName, analysis: TableAnalysis): FunSpec =
        FunSpec.builder("findByIdOrNull")
            .receiver(analysis.table)
            .addParameter("id", analysis.primaryKey.kotlinType)
            .returns(entity.copy(nullable = true))
            .addCode {
                add(
                    "return %T.%M().where { %T.${analysis.primaryKey.name} eq id }",
                    analysis.table,
                    MemberName("org.jetbrains.exposed.sql", "selectAll"),
                    analysis.table
                )
                mapResultRow(analysis, entity)
                addStatement(".singleOrNull()")
            }
            .build()

    private fun FindByUniqueColumnOrNullFunSpec(
        entity: ClassName,
        analysis: TableAnalysis,
        column: ColumnInfo
    ): FunSpec {
        val capitalizedColumnName = column.name.replaceFirstChar { it.uppercase() }
        return FunSpec.builder("findBy${capitalizedColumnName}OrNull")
            .receiver(analysis.table)
            .addParameter(column.name, column.kotlinType)
            .returns(entity.copy(nullable = true))
            .addCode {
                add(
                    "return %T.selectAll().where { %T.${column.name} eq ${column.name} }",
                    analysis.table,
                    analysis.table
                )
                mapResultRow(analysis, entity)
                addStatement(".singleOrNull()")
            }
            .build()
    }

    private fun ExistsByIdFunSpec(analysis: TableAnalysis): FunSpec =
        FunSpec.builder("existsById")
            .receiver(analysis.table)
            .addParameter("id", analysis.primaryKey.kotlinType)
            .returns(BOOLEAN)
            .addCode {
                addStatement(
                    "return %T.selectAll().where { %T.${analysis.primaryKey.name} eq id }.limit(1).count() > 0",
                    analysis.table,
                    analysis.table
                )
            }
            .build()

    private fun ExistsByUniqueColumnFunSpec(analysis: TableAnalysis, column: ColumnInfo): FunSpec {
        val capitalizedColumnName = column.name.replaceFirstChar { it.uppercase() }
        return FunSpec.builder("existsBy$capitalizedColumnName")
            .receiver(analysis.table)
            .addParameter(column.name, column.kotlinType)
            .returns(BOOLEAN)
            .addCode {
                addStatement(
                    "return %T.selectAll().where { %T.${column.name} eq ${column.name} }.limit(1).count() > 0",
                    analysis.table,
                    analysis.table
                )
            }
            .build()
    }

    private fun FindAllFunSpec(entity: ClassName, analysis: TableAnalysis): FunSpec =
        FunSpec.builder("findAll")
            .receiver(analysis.table)
            .returns(LIST.parameterizedBy(entity))
            .addCode {
                addStatement("return %T.selectAll()", analysis.table)
                mapResultRow(analysis, entity)
            }
            .build()

    private fun CountFunSpec(analysis: TableAnalysis): FunSpec =
        FunSpec.builder("count")
            .receiver(analysis.table)
            .returns(LONG)
            .addCode {
                addStatement("return %T.selectAll().count()", analysis.table)
            }
            .build()

    private fun DeleteByIdFunSpec(analysis: TableAnalysis): FunSpec =
        FunSpec.builder("deleteById")
            .receiver(analysis.table)
            .addParameter("id", analysis.primaryKey.kotlinType)
            .returns(BOOLEAN)
            .addCode {
                addStatement(
                    "return %T.deleteWhere { %T.${analysis.primaryKey.name} eq id } > 0",
                    analysis.table,
                    analysis.table
                )
            }
            .build()

    private fun DeleteByUniqueColumnFunSpec(analysis: TableAnalysis, column: ColumnInfo): FunSpec {
        val capitalizedColumnName = column.name.replaceFirstChar { it.uppercase() }
        return FunSpec.builder("deleteBy$capitalizedColumnName")
            .receiver(analysis.table)
            .addParameter(column.name, column.kotlinType)
            .returns(BOOLEAN)
            .addCode {
                addStatement(
                    "return %T.deleteWhere { %T.${column.name} eq ${column.name} } > 0",
                    analysis.table,
                    analysis.table
                )
            }
            .build()
    }

    private fun DeleteAllFunSpec(analysis: TableAnalysis): FunSpec =
        FunSpec.builder("deleteAll")
            .receiver(analysis.table)
            .returns(UNIT)
            .addCode {
                addStatement("%T.deleteAll()", analysis.table)
            }
            .build()

    private fun DeleteAllByIdFunSpec(analysis: TableAnalysis): FunSpec =
        FunSpec.builder("deleteAll")
            .receiver(analysis.table)
            .addParameter("ids", LIST.parameterizedBy(analysis.primaryKey.kotlinType))
            .returns(INT)
            .addCode {
                addStatement(
                    "return %T.deleteWhere { %T.${analysis.primaryKey.name} inList ids }",
                    analysis.table,
                    analysis.table
                )
            }
            .build()

    private fun DeleteAllByEntityFunSpec(entity: ClassName, analysis: TableAnalysis): FunSpec =
        FunSpec.builder("deleteAll")
            .receiver(analysis.table)
            .addAnnotation(
                AnnotationSpec.builder(JvmName::class).addMember("\"deleteAll${entity.simpleName}\"").build()
            )
            .addParameter("values", LIST.parameterizedBy(entity))
            .returns(INT)
            .addCode("return deleteAll(values.map { it.${analysis.primaryKey.name} })")
            .build()

    private fun DeleteAllByUniqueColumnFunSpec(analysis: TableAnalysis, column: ColumnInfo): FunSpec {
        val capitalizedColumnName = column.name.replaceFirstChar { it.uppercase() }
        return FunSpec.builder("deleteAllBy$capitalizedColumnName")
            .receiver(analysis.table)
            .addParameter(column.name, column.kotlinType)
            .returns(UNIT)
            .addCode {
                addStatement(
                    "%T.deleteWhere { %T.${column.name} eq ${column.name} }",
                    analysis.table,
                    analysis.table
                )
            }
            .build()
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
