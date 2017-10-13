package rhmodding.bccadeditor

import javafx.scene.Parent
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import tornadofx.App
import tornadofx.View
import tornadofx.launch

class EditorApp : App(EditorView::class)

class EditorView : View() {
	override val root = VBox()

	init {
		root.add(Button("test"))
	}
}