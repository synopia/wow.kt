package wow

import autofill.AutoFillProvider
import autofill.AutoFillTextBox
import javafx.application.Application
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.ToolBar
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.util.Callback
import org.neo4j.driver.v1.GraphDatabase
import org.neo4j.driver.v1.Record
import org.neo4j.driver.v1.Session
import org.neo4j.driver.v1.Values
import java.util.concurrent.Executors

/**
 * Created by synopia on 30.07.2016.
 */
interface Presentable {
    fun getNode(): Node
}

class NeoColumn(val name: String, val node: String, val property: String) {
    fun get(record: Record?): String {
        return record?.get(node)?.asNode()?.get(property)?.asString() ?: ""
    }
}

class NeoTableDef() {
    val columns = mutableMapOf<String, NeoColumn>()

    fun addNodeProperty(name: String, node: String, property: String) {
        columns[name] = NeoColumn(name, node, property)
    }

    fun createRow(): NeoRow {
        val row = NeoRow(this)
        columns.forEach {
            row.cells[it.key] = SimpleStringProperty()
        }
        return row
    }

    fun updateRow(row: NeoRow) {
        columns.forEach {
            row.cells[it.key]!!.value = it.value.get(row.record)
        }
    }
}

class NeoRow(val tableDef: NeoTableDef) {
    var record: Record? = null
        get() {
            return field
        }
        set(value) {
            field = value; update()
        }

    val cells = mutableMapOf<String, SimpleStringProperty>()

    fun update() {
        tableDef.updateRow(this)
    }
}

class NeoTable(val session: Session, val tableDef: NeoTableDef) : Presentable {
    val table = TableView<NeoRow>()
    val items = SimpleObjectProperty<List<Record>>()

    init {
        tableDef.columns.forEach { entry ->
            val column = TableColumn<NeoRow, String>(entry.key)
            column.cellValueFactory = Callback {
                it.value.cells[entry.key]
            }
            table.columns += column
        }
        (0..9).forEach {
            table.items.add(tableDef.createRow())
        }

        items.addListener({ obs, o, n ->
            table.items.forEachIndexed { i, row ->
                row.record = if (i < n.size) {
                    n[i]
                } else {
                    null
                }
            }
        })
    }

    fun itemsProperty(): ObjectProperty<List<Record>> {
        return items
    }

    override fun getNode(): Node {
        return table
    }
}

class AuctionTable(val session: Session) : Presentable {
    val table: NeoTable
    val main = VBox()
    val executor = Executors.newSingleThreadExecutor()

    init {
        val def = NeoTableDef()
        def.addNodeProperty("Id", "char", "charId")
        def.addNodeProperty("Name", "char", "name")
        table = NeoTable(session, def)
        main.children.add(AutoFillTextBox(AutoFillProvider(Callback {
            val tx = session.beginTransaction()
            val result = tx.run("MATCH (char:Character) WHERE char.name CONTAINS {term} RETURN char LIMIT 10", Values.parameters("term", it))
            val list = result.list().map {
                it.get("char").asNode().get("name").asString()
            }
            tx.success()
            tx.close()
            list
        })))
        main.children.add(AutoFillTextBox(AutoFillProvider(Callback {
            val tx = session.beginTransaction()
            val result = tx.run("MATCH (item:Item) WHERE item.name CONTAINS {term} RETURN item LIMIT 10", Values.parameters("term", it))
            val list = result.list().map {
                it.get("item").asNode().get("name").asString()
            }
            tx.success()
            tx.close()
            list
        })))
        main.children.add(table.getNode())
    }

    override fun getNode(): Node {
        return main
    }
}

class MainWindow(val session: Session) : Presentable {
    val main = BorderPane()
    val auctionTable = AuctionTable(session)

    init {
        main.top = ToolBar()
        main.bottom = Label("Loading")
        main.center = auctionTable.getNode()
    }

    override fun getNode(): Node {
        return main
    }
}

class Gui : Application() {
    override fun start(stage: Stage) {
        val driver = GraphDatabase.driver("bolt://localhost")
        val session = driver.session()

        val main = MainWindow(session)

        val scene = Scene(StackPane(main.getNode()))
        scene.stylesheets.add(javaClass.getResource("/main.css").toExternalForm())
        stage.scene = scene

        stage.show()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(Gui::class.java, *args)
        }
    }
}

