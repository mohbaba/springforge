package wrappers

import com.babs.crudwizardrygenerator.dtos.EntityData
import dtos.FieldData

class EntityDataWrapper {

    var entityName: String = "NewEntity"
    var packageName: String = ""
    var lombokData: Boolean = true
    var lombokBuilder: Boolean = false
    
    // CRUD Options
    var generateEntity: Boolean = true
    var generateController: Boolean = true
    var generateService: Boolean = true
    var generateRepository: Boolean = true
    var generateDto: Boolean = true
    var generateServiceInterface: Boolean = false

    val fields: MutableList<FieldData> = mutableListOf()

    init {
        // Add default ID field
        fields.add(FieldData("id", "Long", false))
    }

    override fun toString(): String = entityName

    fun toEntityData(): EntityData =
        EntityData(
            entityName,
            packageName,
            lombokData,
            lombokBuilder,
            fields.toList(),
            generateEntity,
            generateController,
            generateService,
            generateRepository,
            generateDto,
            generateServiceInterface
        )
}
