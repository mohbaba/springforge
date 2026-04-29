package wrappers

import com.babs.crudwizardrygenerator.dtos.EntityData
import com.babs.crudwizardrygenerator.dtos.PersistenceApi
import dtos.FieldData

class EntityDataWrapper {

    var entityName: String = "NewEntity"
    var packageName: String = ""
    var lombokData: Boolean = true
    var lombokBuilder: Boolean = false
    var persistenceApi: PersistenceApi = PersistenceApi.AUTO
    
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
            persistenceApi,
            fields.toList(),
            generateEntity,
            generateController,
            generateService,
            generateRepository,
            generateDto,
            generateServiceInterface
        )

    companion object {
        fun fromEntityData(entityData: EntityData): EntityDataWrapper {
            val wrapper = EntityDataWrapper()
            wrapper.entityName = entityData.entityName
            wrapper.packageName = entityData.packageName
            wrapper.lombokData = entityData.isLombokData()
            wrapper.lombokBuilder = entityData.isLombokBuilder()
            wrapper.persistenceApi = entityData.persistenceApi
            wrapper.generateEntity = entityData.isGenerateEntity()
            wrapper.generateController = entityData.isGenerateController()
            wrapper.generateService = entityData.isGenerateService()
            wrapper.generateRepository = entityData.isGenerateRepository()
            wrapper.generateDto = entityData.isGenerateDto()
            wrapper.generateServiceInterface = entityData.isGenerateServiceInterface()
            wrapper.fields.clear()
            wrapper.fields.addAll(entityData.fields.map { FieldData(it.name, it.type, it.nullable) })
            return wrapper
        }
    }
}
