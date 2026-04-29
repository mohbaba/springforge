package ui

import com.babs.crudwizardrygenerator.dtos.EntityData
import com.babs.crudwizardrygenerator.dtos.PersistenceApi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBSplitter
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dtos.FieldData
import wrappers.EntityDataWrapper
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.GridLayout
import javax.swing.*
import com.intellij.ui.DocumentAdapter
import javax.swing.event.DocumentEvent
import java.awt.event.MouseEvent
import java.util.EventObject
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider
import java.awt.Component
import javax.swing.table.TableCellEditor

class GenerateEntityDialog(
    private val project: Project,
    private val defaultPackageName: String = "",
    private val initialEntities: List<EntityData> = emptyList()
) : DialogWrapper(project) {

    /* ------------------ MODELS ------------------ */

    private val entityListModel = DefaultListModel<EntityDataWrapper>()
    private val entityList = JBList(entityListModel)

    private var selectedEntity: EntityDataWrapper? = null

    private val fieldsTableModel = FieldsTableModel()
    private val fieldsTable = JBTable(fieldsTableModel)

    /* ------------------ UI COMPONENTS ------------------ */

    private val mainEditorPanel = JPanel(CardLayout())
    
    // Editor Fields
    private val entityNameField = JBTextField()
    private val packageNameField = JBTextField()
    private val persistenceApiCombo = JComboBox(PersistenceApi.values())
    private val lombokDataCheck = JBCheckBox("Lombok @Data")
    private val lombokBuilderCheck = JBCheckBox("Lombok @Builder")
    
    // CRUD Options
    private val generateControllerCheck = JBCheckBox("Controller")
    private val generateServiceCheck = JBCheckBox("Service")
    private val generateRepositoryCheck = JBCheckBox("Repository")
    private val generateDtoCheck = JBCheckBox("DTO")
    private val generateServiceInterfaceCheck = JBCheckBox("Service Interface")

    private val standardTypes = listOf(
        "String", "Integer", "Long", "Double", "Boolean", "Float", "Short", "Byte",
        "BigDecimal", "BigInteger", "LocalDate", "LocalDateTime", "LocalTime", "UUID",
        "int", "long", "double", "boolean", "float", "short", "byte", "char",
        "List", "Set", "Map", "Collection"
    )

    init {
        title = "Entity Wizard"
        setSize(1000, 650)
        
        setupEntityList()
        setupFieldsTable()
        setupEditorListeners()
        
        init()

        if (initialEntities.isNotEmpty()) {
            loadInitialEntities()
        } else {
            // Automatically add the first entity so the user isn't staring at a blank screen
            addEntity()
        }
    }

    /* ------------------ SETUP ------------------ */

    private fun setupEntityList() {
        entityList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        entityList.emptyText.text = "No entities added"
        
        entityList.addListSelectionListener { 
            if (!it.valueIsAdjusting) {
                // Stop editing any active cell in the table before switching entities
                if (fieldsTable.isEditing) {
                    fieldsTable.cellEditor?.stopCellEditing()
                }

                val selected = entityList.selectedValue
                if (selected != null) {
                    selectedEntity = selected
                    loadEntity(selected)
                    (mainEditorPanel.layout as CardLayout).show(mainEditorPanel, "EDITOR")
                } else {
                    selectedEntity = null
                    (mainEditorPanel.layout as CardLayout).show(mainEditorPanel, "EMPTY")
                }
            }
        }
    }

    private fun setupFieldsTable() {
        fieldsTable.rowHeight = 25
        fieldsTable.fillsViewportHeight = true
        
        // Set up custom editor for Type column with dynamic suggestions
        val typeColumn = fieldsTable.columnModel.getColumn(1)
        
        typeColumn.cellEditor = object : AbstractCellEditor(), TableCellEditor {
            private var editorComponent: TextFieldWithAutoCompletion<String>? = null
            
            override fun getCellEditorValue(): Any {
                return editorComponent?.text ?: ""
            }

            override fun getTableCellEditorComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                row: Int,
                column: Int
            ): Component {
                // 1. Existing classes in project
                val projectClasses = PsiShortNamesCache.getInstance(project).getAllClassNames().toList()
                
                // 2. Entities currently being defined in the wizard
                val pendingEntities = entityListModel.elements().asSequence()
                    .map { it.entityName }
                    .filter { it.isNotBlank() }
                    .toList()
                
                // Combine everything
                val allSuggestions = (standardTypes + projectClasses + pendingEntities).distinct().sorted()
                
                val provider = StringsCompletionProvider(allSuggestions, null)
                val textField = TextFieldWithAutoCompletion(project, provider, true, value as? String)
                editorComponent = textField
                return textField
            }
        }
    }

    private fun setupEditorListeners() {
        entityNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                selectedEntity?.let {
                    it.entityName = entityNameField.text
                    entityList.repaint() // Refresh list to show new name
                }
            }
        })

        packageNameField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                selectedEntity?.packageName = packageNameField.text
            }
        })

        persistenceApiCombo.addActionListener {
            selectedEntity?.persistenceApi = persistenceApiCombo.selectedItem as PersistenceApi
        }

        lombokDataCheck.addActionListener {
            selectedEntity?.lombokData = lombokDataCheck.isSelected
        }

        lombokBuilderCheck.addActionListener {
            selectedEntity?.lombokBuilder = lombokBuilderCheck.isSelected
        }
        
        generateControllerCheck.addActionListener { selectedEntity?.generateController = generateControllerCheck.isSelected }
        generateServiceCheck.addActionListener { selectedEntity?.generateService = generateServiceCheck.isSelected }
        generateRepositoryCheck.addActionListener { selectedEntity?.generateRepository = generateRepositoryCheck.isSelected }
        generateDtoCheck.addActionListener { selectedEntity?.generateDto = generateDtoCheck.isSelected }
        generateServiceInterfaceCheck.addActionListener { selectedEntity?.generateServiceInterface = generateServiceInterfaceCheck.isSelected }
    }

    /* ------------------ UI LAYOUT ------------------ */

    override fun createCenterPanel(): JComponent {
        val splitter = JBSplitter(false, 0.3f)
        
        splitter.firstComponent = createLeftPanel()
        splitter.secondComponent = createRightPanel()
        
        return splitter
    }

    private fun createLeftPanel(): JComponent {
        val decorator = ToolbarDecorator.createDecorator(entityList)
            .setAddAction { addEntity() }
            .setRemoveAction { removeEntity() }
            .disableUpDownActions()
            .setPanelBorder(JBUI.Borders.empty())
        
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyRight(5)
        panel.add(JBLabel("Entities"), BorderLayout.NORTH)
        panel.add(decorator.createPanel(), BorderLayout.CENTER)
        
        return panel
    }

    private fun createRightPanel(): JComponent {
        // Empty State
        val emptyPanel = JPanel(BorderLayout())
        val emptyLabel = JLabel("Select an entity to edit or add a new one", SwingConstants.CENTER)
        emptyLabel.foreground = UIManager.getColor("Label.disabledForeground")
        emptyPanel.add(emptyLabel, BorderLayout.CENTER)
        
        // Editor State
        val editorContent = JPanel(BorderLayout())
        editorContent.border = JBUI.Borders.emptyLeft(5)
        
        // CRUD Options Panel
        val crudPanel = JPanel(GridLayout(2, 3)) // Changed to 2 rows to accommodate new checkbox
        crudPanel.add(generateControllerCheck)
        crudPanel.add(generateServiceCheck)
        crudPanel.add(generateRepositoryCheck)
        crudPanel.add(generateDtoCheck)
        crudPanel.add(generateServiceInterfaceCheck)
        
        // Form
        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Entity name:", entityNameField)
            .addLabeledComponent("Package name:", packageNameField)
            .addLabeledComponent("JPA imports:", persistenceApiCombo)
            .addComponent(lombokDataCheck)
            .addComponent(lombokBuilderCheck)
            .addSeparator()
            .addComponent(JBLabel("Generate CRUD Components:"))
            .addComponent(crudPanel)
            .addSeparator()
            .panel
            
        editorContent.add(formPanel, BorderLayout.NORTH)
        
        // Fields Table with Decorator
        val tableDecorator = ToolbarDecorator.createDecorator(fieldsTable)
            .setAddAction { addField() }
            .setRemoveAction { removeField() }
            .disableUpDownActions()
            .setPreferredSize(java.awt.Dimension(400, 200))
        
        val fieldsPanel = JPanel(BorderLayout())
        fieldsPanel.add(JBLabel("Fields"), BorderLayout.NORTH)
        fieldsPanel.add(tableDecorator.createPanel(), BorderLayout.CENTER)
        
        editorContent.add(fieldsPanel, BorderLayout.CENTER)

        // Add to CardLayout
        mainEditorPanel.add(emptyPanel, "EMPTY")
        mainEditorPanel.add(editorContent, "EDITOR")
        
        // Show empty by default
        (mainEditorPanel.layout as CardLayout).show(mainEditorPanel, "EMPTY")
        
        return mainEditorPanel
    }

    /* ------------------ ACTIONS ------------------ */

    private fun addEntity() {
        // Stop editing before adding a new entity to ensure data is saved
        if (fieldsTable.isEditing) {
            fieldsTable.cellEditor?.stopCellEditing()
        }

        val newEntity = EntityDataWrapper()
        
        // Inherit package name from previous entity if exists, otherwise use default
        if (!entityListModel.isEmpty) {
            val last = entityListModel.lastElement()
            newEntity.packageName = last.packageName
            newEntity.persistenceApi = last.persistenceApi
        } else {
            newEntity.packageName = defaultPackageName
        }
        
        entityListModel.addElement(newEntity)
        entityList.selectedIndex = entityListModel.size() - 1
    }

    private fun removeEntity() {
        val index = entityList.selectedIndex
        if (index >= 0) {
            entityListModel.remove(index)
        }
    }

    private fun addField() {
        selectedEntity?.let {
            it.fields.add(FieldData())
            fieldsTableModel.setFields(it.fields)
        }
    }

    private fun removeField() {
        val row = fieldsTable.selectedRow
        if (row >= 0) {
            selectedEntity?.let {
                it.fields.removeAt(row)
                fieldsTableModel.setFields(it.fields)
            }
        }
    }

    private fun loadInitialEntities() {
        initialEntities.forEach { entityData ->
            entityListModel.addElement(EntityDataWrapper.fromEntityData(entityData))
        }
        if (!entityListModel.isEmpty) {
            entityList.selectedIndex = 0
        }
    }

    private fun loadEntity(entity: EntityDataWrapper) {
        entityNameField.text = entity.entityName
        packageNameField.text = entity.packageName
        persistenceApiCombo.selectedItem = entity.persistenceApi
        lombokDataCheck.isSelected = entity.lombokData
        lombokBuilderCheck.isSelected = entity.lombokBuilder
        
        generateControllerCheck.isSelected = entity.generateController
        generateServiceCheck.isSelected = entity.generateService
        generateRepositoryCheck.isSelected = entity.generateRepository
        generateDtoCheck.isSelected = entity.generateDto
        generateServiceInterfaceCheck.isSelected = entity.generateServiceInterface
        
        fieldsTableModel.setFields(entity.fields)
    }

    /* ------------------ VALIDATION & OUTPUT ------------------ */

    override fun doValidate(): ValidationInfo? {
        if (entityListModel.isEmpty) {
            return ValidationInfo("Please add at least one entity.", entityList)
        }
        
        // Check for duplicate entity names
        val names = mutableSetOf<String>()
        for (i in 0 until entityListModel.size()) {
            val entity = entityListModel.getElementAt(i)
            if (entity.entityName.isBlank()) {
                return ValidationInfo("Entity name cannot be empty.", entityList)
            }
            
            // Check for duplicates within the same package
            val key = "${entity.packageName}.${entity.entityName}"
            if (!names.add(key)) {
                // Instead of showing a dialog here (which causes the loop), just return a ValidationInfo error
                // This prevents the user from clicking OK until they fix the duplicate
                return ValidationInfo("Duplicate entity '${entity.entityName}' in package '${entity.packageName}'. Please rename one.", entityList)
            }
        }
        
        return null
    }

    fun toEntityDataList(): List<EntityData> =
        entityListModel.elements().toList().map { it.toEntityData() }
}
