package ui

import dtos.FieldData
import javax.swing.table.AbstractTableModel

class FieldsTableModel : AbstractTableModel() {

    // Removed "Nullable" column as requested
    private val columns = arrayOf("Name", "Type")
    private var fields: MutableList<FieldData> = mutableListOf()

    fun setFields(newFields: MutableList<FieldData>) {
        fields = newFields
        fireTableDataChanged()
    }

    override fun getRowCount() = fields.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(column: Int) = columns[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

    override fun isCellEditable(rowIndex: Int, columnIndex: Int) = true

    override fun getValueAt(row: Int, col: Int): Any =
        when (col) {
            0 -> fields[row].name
            1 -> fields[row].type
            else -> ""
        }

    override fun setValueAt(value: Any?, row: Int, col: Int) {
        val field = fields[row]
        when (col) {
            0 -> field.name = value as String
            1 -> field.type = value as String
        }
    }
}
