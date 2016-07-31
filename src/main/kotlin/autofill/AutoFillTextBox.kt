package autofill

import javafx.application.Application
import javafx.collections.FXCollections
import javafx.concurrent.Task
import javafx.concurrent.WorkerStateEvent
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.text.Text
import javafx.stage.Popup
import javafx.stage.Stage
import javafx.util.Callback
import java.util.concurrent.Executors

/**
 * Created by synopia on 30.07.2016.
 */
val e = Executors.newSingleThreadExecutor()

fun <T> async(block: Function0<T>): Task<T> {
    val task = object : Task<T>() {
        override fun call(): T {
            return block.invoke()
        }
    }
    e.execute(task)
    return task
}

class AutoFillProvider(val callback: Callback<String, List<String>>) {
    fun filter(term: String): List<AutoFillListEntry> {
        return callback.call(term).subList(0, 7).map {
            val pos = it.toLowerCase().indexOf(term.toLowerCase())
            if (term.length > 0 && pos >= 0) {
                AutoFillListEntry(it, (pos..pos + term.length - 1).toList())
            } else {
                AutoFillListEntry(it, emptyList())
            }
        }
    }
}

class AutoFillTextBox(val provider: AutoFillProvider) : Control() {
    val textbox = TextField()
    val listview = ListView<AutoFillListEntry>()
    var filterMode = false
    var limit = 5

    init {
        styleClass.setAll("autofill-text")
    }

    override fun requestFocus() {
        super.requestFocus()
        textbox.requestFocus()
    }

    fun getText(): String {
        return textbox.text
    }


}

data class AutoFillListEntry(val text: String, val marked: List<Int>) {
}

class AutoFillListCell : ListCell<AutoFillListEntry>() {
    val label = Label()
    val overlay = Group()

    init {
        val stackpane = StackPane()
        StackPane.setAlignment(label, Pos.CENTER_LEFT)
        StackPane.setAlignment(overlay, Pos.CENTER_LEFT)
        stackpane.children.addAll(label, overlay)
        overlay.isManaged = false
        graphic = stackpane
    }

    override fun updateItem(item: AutoFillListEntry?, empty: Boolean) {
        super.updateItem(item, empty)
        overlay.children.clear()
        if (item != null && !empty) {
            label.text = item.text
            item.marked.forEach {
                val text = Text(label.text.substring(0, it))
                text.font = label.font
                val bounds = text.boundsInLocal
                val x = bounds.maxX
                text.text = label.text.substring(0, it + 1)
                val rect = Rectangle(x, 0.0, text.boundsInLocal.maxX - x, bounds.height)
                rect.fill = Color.rgb(10, 10, 10, 0.2)
                overlay.children.add(rect)
            }
        } else {
            label.text = ""
        }

    }
}

class AutoFillTextBoxSkin(val autoFill: AutoFillTextBox) : SkinBase<AutoFillTextBox>(autoFill) {
    val popup = Popup()

    init {
        val listview = autoFill.listview
        val textbox = autoFill.textbox
        if (autoFill.filterMode) {
            val task = async() { autoFill.provider.filter("") }
            task.onSucceeded = EventHandler<WorkerStateEvent> {
                listview.items = FXCollections.observableList(task.value)
            }
        }
        listview.itemsProperty().addListener { obs, o, n ->
            if (listview.items.size > 0) {
                showPopup()
            } else {
                hidePopup()
            }
        }
        listview.onMouseReleased = EventHandler<MouseEvent> {
            selectList()
        }
        textbox.onKeyPressed = EventHandler<KeyEvent> {
            if (it.code == KeyCode.DOWN) {
                if (popup.isShowing) {
                    listview.requestFocus()
                    listview.selectionModel.select(0)
                }
            }
        }
        textbox.onKeyReleased = EventHandler<KeyEvent> {
            if (it.code == KeyCode.DOWN) {
                return@EventHandler
            }
            val task = async() { autoFill.provider.filter(textbox.text) }
            task.onSucceeded = EventHandler<WorkerStateEvent> {
                listview.items = FXCollections.observableList(task.value)
            }
        }
        listview.onKeyReleased = EventHandler<KeyEvent> {
            if (it.code == KeyCode.ENTER || it.code == KeyCode.TAB) {
                selectList()
            } else if (it.code == KeyCode.UP) {
                if (listview.selectionModel.selectedIndex == 0) {
                    textbox.requestFocus()
                }
            } else if (it.code == KeyCode.ESCAPE) {
                textbox.requestFocus()
                hidePopup()
            }
        }
        listview.cellFactory = Callback {
            val cell = AutoFillListCell()
            cell
        }
        popup.isAutoHide = true
        popup.content += listview

        children.addAll(textbox)
    }

    fun selectList() {
        val item = autoFill.listview.selectionModel.selectedItem
        if (item != null) {
            autoFill.textbox.text = item.text
            autoFill.listview.items = FXCollections.observableArrayList()
            autoFill.textbox.requestFocus()
            autoFill.textbox.requestLayout()
            autoFill.textbox.end()
            hidePopup()
        }
    }

    fun showPopup() {
        val textbox = autoFill.textbox
        val listview = autoFill.listview

        val window = textbox.scene.window
        listview.prefWidth = textbox.prefWidth
        val height = Math.min(listview.items.size, autoFill.limit)
        listview.prefHeight = 26.0 * height
        popup.hide()
        popup.show(window,
                window.x + textbox.localToScene(0.0, 0.0).x + textbox.scene.x,
                window.y + textbox.localToScene(0.0, 0.0).y + textbox.scene.y + 20)
        listview.selectionModel.clearSelection()
        listview.focusModel.focus(-1)
    }

    fun hidePopup() {
        popup.hide()
    }
}

class Test : Application() {
    override fun start(stage: Stage) {
        val scene = Scene(StackPane(AutoFillTextBox(AutoFillProvider(Callback { term ->
            listOf("Karok", "Kalir", "Kout", "Tal").filter { it.toLowerCase().contains(term.toLowerCase()) }
        }))))
        scene.stylesheets.add(javaClass.getResource("/main.css").toExternalForm())
        stage.scene = scene

        stage.show()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(Test::class.java, *args)
        }
    }
}

