package rhmodding.bccadeditor

import com.madgag.gif.fmsware.AnimatedGifEncoder
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.WritableImage
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.transform.Affine
import javafx.scene.transform.Scale
import javafx.stage.FileChooser
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.Window
import javafx.util.Duration
import rhmodding.bccadeditor.bccad.*
import tornadofx.*
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow

val VERSION: Version = Version(1, 3, 0, "DEVELOPMENT")

class EditorApp : App(EditorView::class)

class EditorView : View("BCCAD Editor $VERSION") {
    override val root = VBox()
    var frame: Long = 30
    var currentAnimation: Animation? = null
    var currentStep: AnimationStep? = null
    var currentSprite: Sprite? = null
    var currentPart: SpritePart? = null
    var bccad: BCCAD? = null
    var path: String = "/"

    var sheet: BufferedImage? = null

    lateinit var animationSpinner: Spinner<Int>
    lateinit var aNameLabel: Label
    lateinit var stepSpinner: Spinner<Int>
    lateinit var spriteSpinner: Spinner<Int>
    lateinit var partSpinner: Spinner<Int>
    lateinit var stepCanvas: Canvas
    lateinit var spriteCanvas: Canvas

    lateinit var stepSpriteSpinner: Spinner<Int>
    lateinit var stepDurationSpinner: Spinner<Int>

    lateinit var stepXSpinner: Spinner<Int>
    lateinit var stepYSpinner: Spinner<Int>

    lateinit var stepUnkSpinner: Spinner<Double>

    lateinit var stepScaleXSpinner: Spinner<Double>
    lateinit var stepScaleYSpinner: Spinner<Double>

    lateinit var stepRotationSpinner: Spinner<Double>

    lateinit var stepColorPicker: ColorPicker

    lateinit var stepOpacitySpinner: Spinner<Int>
    lateinit var stepUnknownDataBox: TextArea

    lateinit var partXSpinner: Spinner<Int>
    lateinit var partYSpinner: Spinner<Int>

    lateinit var partScaleXSpinner: Spinner<Double>
    lateinit var partScaleYSpinner: Spinner<Double>

    lateinit var partRotationSpinner: Spinner<Double>

    lateinit var partFlipXCheck: CheckBox
    lateinit var partFlipYCheck: CheckBox

    lateinit var partColorPicker: ColorPicker
    lateinit var partScreenPicker: ColorPicker

    lateinit var partDesignationSpinner: Spinner<Int>

    lateinit var partTLDepthSpinner: Spinner<Double>
    lateinit var partBLDepthSpinner: Spinner<Double>
    lateinit var partTRDepthSpinner: Spinner<Double>
    lateinit var partBRDepthSpinner: Spinner<Double>

    lateinit var partOpacitySpinner: Spinner<Int>
    lateinit var partUnkSpinner: Spinner<Int>

    lateinit var partUnknownDataBox: TextArea

    lateinit var tp: TabPane

    lateinit var zoomLabel: Label

    var zoomFactor = 1.0

    var animation: Timeline? = null

    private val animationListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
        val bccad = bccad
        if (bccad != null) {
            animation?.stop()
            val animation = bccad.animations[new]
            currentAnimation = animation
            stepSpinner.decrement(1000)
            stepListener(ReadOnlyObjectWrapper(0), 0, 0)
            val f = stepSpinner.valueFactory
            if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                f.max = animation.steps.size - 1
            }
            aNameLabel.text = animation.name
        }
    }

    private val stepListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
        val currentAnimation = currentAnimation
        if (currentAnimation != null) {
            animation?.stop()
            val step = currentAnimation[new]
            currentStep = step
            updateStep()
            drawStep(stepCanvas, step)
        }
    }

    private val spriteListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
        val sprite = bccad?.sprites?.get(new)
        currentSprite = sprite
        partSpinner.decrement(1000)
        partListener(ReadOnlyObjectWrapper(0), 0, 0)
        val f = partSpinner.valueFactory
        if (f is SpinnerValueFactory.IntegerSpinnerValueFactory && sprite != null) {
            f.max = sprite.parts.size - 1
        }
        drawCurrentSprite()
    }

    private val partListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
        if (currentSprite?.parts?.size ?: 0 > 0) {
            currentPart = currentSprite?.parts?.get(new)
            updatePart()
            drawCurrentSprite()
        }
    }

    init {
        with(root) {
            borderpane {
                top = menubar {
                    menu("File") {
                        item("Open...", "Shortcut+O") {
                            action {
                                animation?.stop()
                                val fileChooser = FileChooser()
                                fileChooser.title = "Choose bccad"
                                fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("BCCAD", "*.bccad"))
                                fileChooser.initialDirectory = File(path)

                                val f = fileChooser.showOpenDialog(null)
                                if (f != null) {
                                    path = f.parent
                                    fileChooser.title = "Choose sprite sheet"
                                    fileChooser.initialDirectory = File(path)
                                    fileChooser.extensionFilters.remove(0, 1)
                                    fileChooser.extensionFilters.addAll(arrayOf(FileChooser.ExtensionFilter("PNG", "*.png"), FileChooser.ExtensionFilter("All files", "*.*")))
                                    val f2 = fileChooser.showOpenDialog(null)
                                    if (f2 != null) {
                                        val b = BCCAD(f)
                                        val rawIm = ImageIO.read(f2)
                                        if (rawIm.width != b.sheetH.toInt() || rawIm.height != b.sheetW.toInt()) {
                                            val button = Alert(Alert.AlertType.WARNING, "This image's dimensions do not agree with the BCCAD. Do you want to adjust the BCCAD and continue?\n\nBCCAD dim.: ${b.sheetW.toInt()} by ${b.sheetH.toInt()}", ButtonType.YES, ButtonType.NO).showAndWait()
                                            if (button.isPresent && button.get() == ButtonType.YES) {
                                                b.sheetH = rawIm.width.toShort()
                                                b.sheetW = rawIm.height.toShort()
                                            } else {
                                                return@action
                                            }
                                        }
                                        bccad = b
                                        val sheetImg = BufferedImage(rawIm.height, rawIm.width, rawIm.type)
                                        sheet = sheetImg
                                        val transform = AffineTransform()
                                        transform.translate(0.5 * rawIm.height, 0.5 * rawIm.width)
                                        transform.rotate(-Math.PI / 2)
                                        transform.translate(-0.5 * rawIm.width, -0.5 * rawIm.height)
                                        val g = sheetImg.createGraphics() as Graphics2D
                                        g.drawImage(rawIm, transform, null)
                                        g.dispose()
                                        spriteSpinner.decrement(1000)
                                        spriteListener(ReadOnlyObjectWrapper(0), 0, 0)
                                        val sf = spriteSpinner.valueFactory
                                        val ssf = stepSpriteSpinner.valueFactory
                                        if (sf is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                            sf.max = b.sprites.size - 1
                                        }
                                        if (ssf is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                            ssf.max = b.sprites.size - 1
                                        }
                                        animationSpinner.decrement(1000)
                                        animationListener(ReadOnlyObjectWrapper(0), 0, 0)
                                        val v = animationSpinner.valueFactory
                                        if (v is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                            v.max = b.animations.size - 1
                                        }
                                    }
                                }
                            }
                        }
                        item("Save", "Shortcut+S") {
                            action {
                                val bccad = bccad
                                if (bccad != null) {
                                    val fileChooser = FileChooser()
                                    fileChooser.title = "Save bccad"
                                    fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("BCCAD", "*.bccad"))
                                    fileChooser.initialDirectory = File(path)

                                    fileChooser.showSaveDialog(null)?.writeBytes(bccad.toBytes())
                                }
                            }
                        }
                        item("Export as GIF", "Shortcut+Shift+G") {
                            action {
                                val ani = currentAnimation
                                val bccad = bccad
                                if (ani != null && bccad != null) {
                                    val fileChooser = FileChooser()
                                    fileChooser.title = "Export this animation (${ani.name}) as an animated GIF"
                                    fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("GIF", "*.gif"))
                                    fileChooser.initialDirectory = File(path)

                                    val file = fileChooser.showSaveDialog(null)
                                    if (file != null) {
                                        val encoder = AnimatedGifEncoder()
                                        encoder.also { e ->
                                            e.start(file.absolutePath)
                                            e.setBackground(java.awt.Color(1f, 1f, 1f, 0f))
                                            e.setSize(512, 512)
                                            e.setRepeat(0)
                                            val writableImage = WritableImage(512, 512)
                                            val canvas = stepCanvas
                                            ani.steps.forEach { step ->
                                                e.setDelay((step.duration * frame).toInt())
                                                drawTransparencyGrid(canvas)
                                                val sprite = bccad.sprites[step.spriteNum.toInt()]
                                                drawSprite(canvas, sprite)
                                                canvas.snapshot(SnapshotParameters(), writableImage)
                                                val buf = SwingFXUtils.fromFXImage(writableImage, null)
                                                e.addFrame(buf)
                                            }
                                            e.finish()
                                        }
                                        drawCurrentSprite()
                                    }
                                }
                            }
                        }
                    }
                }
                bottom = vbox {
                    tp = tabpane {
                        tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE
                        tab("Animations", VBox()) {
                            borderpane {
                                left = vbox(spacing = 10) {
                                    prefWidth = 300.0
                                    paddingLeft = 5
                                    paddingTop = 5
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Animation #")
                                        animationSpinner = spinner(0, 0, 0) {
                                            isEditable = true
                                            prefWidth = 60.0
                                            valueProperty().addListener(animationListener)
                                        }
                                    }
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Name:")
                                        aNameLabel = label("<no animation loaded>")
                                        button(text = "Change") {
                                            this.onAction = EventHandler {
                                                val animation = currentAnimation
                                                if (animation != null) {
                                                    TextInputDialog(animation.name).apply {
                                                        this.title = "Renaming animation \"${animation.name}\""
                                                        this.headerText = "Rename animation \"${animation.name}\" to...\n"
//                                                        this.contentText = "Rename animation \"${animation.name}\" to...\n"
                                                    }.showAndWait().ifPresent { newName ->
                                                        if (newName.isNotBlank()) {
                                                            animation.name = newName
                                                            aNameLabel.text = newName
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Step #")
                                        stepSpinner = spinner(0, 0, 0) {
                                            isEditable = true
                                            prefWidth = 60.0
                                            valueProperty().addListener(stepListener)
                                        }
                                        button("Add New") {
                                            action {
                                                if (currentAnimation != null) {
                                                    currentAnimation?.addNewStep()
                                                    val f = stepSpinner.valueFactory
                                                    if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                                        f.max++
                                                        stepSpinner.increment(f.max)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        button("Play") {
                                            action {
                                                animation?.stop()
                                                val ani = Timeline()
                                                animation = ani
                                                val currentAnimation = currentAnimation
                                                var time = 0.0
                                                if (currentAnimation != null) {
                                                    for (i in 0 until currentAnimation.steps.size) {
                                                        ani.keyFrames.add(KeyFrame(Duration.millis(time), EventHandler {
                                                            drawStep(stepCanvas, currentAnimation.steps[i])
                                                        }))
                                                        time += frame * currentAnimation.steps[i].duration
                                                    }
                                                }
                                                ani.keyFrames.add(KeyFrame(Duration.millis(time), EventHandler {}))
                                                ani.cycleCount = javafx.animation.Animation.INDEFINITE
                                                ani.play()
                                            }
                                        }
                                        button("Stop") {
                                            action {
                                                animation?.stop()
                                                drawStep(stepCanvas, currentStep ?: return@action)
                                            }
                                        }
                                        label("Time per frame:")
                                        spinner(30, 1000, 30) {
                                            isEditable = true
                                            prefWidth = 70.0
                                            valueProperty().addListener { _, _, new ->
                                                frame = new.toLong()
                                            }
                                        }
                                        label("ms")
                                    }
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Sprite #")
                                        stepSpriteSpinner = spinner(0, 0, 0) {
                                            isEditable = true
                                            prefWidth = 60.0
                                            valueProperty().addListener { _, _, new ->
                                                currentStep?.spriteNum = new.toShort()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                        button("Edit") {
                                            action {
                                                spriteSpinner.valueFactory.value = stepSpriteSpinner.value
                                                tp.selectionModel.selectNext()
                                            }
                                        }
                                    }
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Duration (frames): ")
                                        stepDurationSpinner = spinner(0, Short.MAX_VALUE.toInt(), 0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueProperty().addListener { _, _, new ->
                                                currentStep?.duration = new.toShort()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                    }
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Translate X: ")
                                        stepXSpinner = spinner(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt(), 0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueProperty().addListener { _, _, new ->
                                                currentStep?.tlX = new.toShort()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                        label("Y: ")
                                        stepYSpinner = spinner(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt(), 0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueProperty().addListener { _, _, new ->
                                                currentStep?.tlY = new.toShort()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Depth:")
                                        stepUnkSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentStep?.depth = new.toFloat()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Scale X")
                                        stepScaleXSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentStep?.stretchX = new.toFloat()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                        label("Y")
                                        stepScaleYSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentStep?.stretchY = new.toFloat()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Rotation:")
                                        stepRotationSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentStep?.rotation = new.toFloat()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                        label("°")
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Multiply Color: ")
                                        stepColorPicker = colorpicker {
                                            valueProperty().addListener { _, _, new ->
                                                currentStep?.color = new
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Opacity: ")
                                        stepOpacitySpinner = spinner(0, 255, 255) {
                                            isEditable = true
                                            prefWidth = 70.0
                                            valueProperty().addListener { _, _, new ->
                                                currentStep?.opacity = new.toShort()
                                                drawStep(stepCanvas, currentStep ?: return@addListener)
                                            }
                                        }
                                    }
                                    label("Unknown Data:")
                                    stepUnknownDataBox = textarea {
                                        isWrapText = true
                                        isEditable = false
                                        prefHeight = 0.0
                                    }
                                }
                                stepCanvas = canvas(512.0, 512.0) {
                                    var prevX = -1
                                    var prevY = -1
                                    onMouseDragged = EventHandler { e ->
                                        val currentStep = currentStep
                                        if (prevX > -1 && currentStep != null) {
                                            if (e.isPrimaryButtonDown) {
                                                currentStep.tlX = (currentStep.tlX + e.x - prevX).toShort()
                                                currentStep.tlY = (currentStep.tlY + e.y - prevY).toShort()
                                                prevX = e.x.toInt()
                                                prevY = e.y.toInt()
                                            } else if (e.isSecondaryButtonDown) {
                                                val xFactor = ((e.x - 256) / (prevX - 256)).toFloat()
                                                val yFactor = ((e.y - 256) / (prevY - 256)).toFloat()
                                                if (xFactor != 0f && xFactor.isFinite()) {
                                                    currentStep.stretchX *= xFactor
                                                    prevX = e.x.toInt()
                                                    if (e.isShiftDown && yFactor != 0f && yFactor.isFinite()) {
                                                        currentStep.stretchY *= yFactor
                                                        prevY = e.y.toInt()
                                                    } else {
                                                        currentStep.stretchY *= xFactor
                                                    }
                                                }
                                            }
                                        } else {
                                            prevX = e.x.toInt()
                                            prevY = e.y.toInt()
                                        }
                                        updateStep()
                                        drawStep(stepCanvas, currentStep ?: return@EventHandler)
                                    }
                                    onScroll = EventHandler { e ->
                                        if (e.isControlDown) {
                                            if (e.isShiftDown) {
                                                if (e.deltaY > 0 || e.deltaX > 0) {
                                                    animationSpinner.increment()
                                                } else {
                                                    animationSpinner.decrement()
                                                }
                                            } else {
                                                if (e.deltaY > 0 || e.deltaX > 0) {
                                                    zoomFactor *= 2.0.pow(1 / 7.0)
                                                } else {
                                                    zoomFactor /= 2.0.pow(1 / 7.0)
                                                }
                                                zoomLabel.text = String.format("%.0f%%", zoomFactor * 100)
                                            }
                                        } else if (e.isShiftDown) {
                                            if (e.deltaY > 0 || e.deltaX > 0) {
                                                stepSpinner.increment()
                                            } else {
                                                stepSpinner.decrement()
                                            }
                                        } else {
                                            val step = currentStep
                                            if (step != null) {
                                                step.rotation += e.deltaY.toFloat() / 20
                                            }
                                        }
                                        updateStep()
                                        drawStep(this, currentStep ?: return@EventHandler)
                                    }
                                    onMouseReleased = EventHandler {
                                        prevX = -1
                                        prevY = -1
                                    }
                                    drawTransparencyGrid(this)
                                }
                                right = stepCanvas
                            }
                        }
                        tab("Sprites", VBox()) {
                            borderpane {
                                left = vbox(spacing = 10) {
                                    prefWidth = 300.0
                                    paddingLeft = 5
                                    paddingTop = 5
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Sprite #")
                                        spriteSpinner = spinner(0, 0, 0) {
                                            isEditable = true
                                            prefWidth = 60.0
                                            valueProperty().addListener(spriteListener)
                                        }
                                        button("Add New") {
                                            action {
                                                val bccad = bccad
                                                if (bccad != null) {
                                                    bccad.sprites.add(Sprite())
                                                    val f = spriteSpinner.valueFactory
                                                    if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                                        f.max++
                                                    }
                                                    spriteSpinner.increment(bccad.sprites.size)
                                                }
                                            }
                                        }
                                        button("Duplicate") {
                                            action {
                                                val bccad = bccad
                                                val currentSprite = currentSprite
                                                if (bccad != null && currentSprite != null) {
                                                    bccad.sprites.add(currentSprite.copy())
                                                    val f = spriteSpinner.valueFactory
                                                    if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                                        f.max++
                                                    }
                                                    spriteSpinner.increment(bccad.sprites.size)
                                                }
                                            }
                                        }
                                    }
                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Part #")
                                        partSpinner = spinner(0, 0, 0) {
                                            isEditable = true
                                            prefWidth = 60.0
                                            valueProperty().addListener(partListener)
                                        }
                                        button("Add New") {
                                            action {
                                                val regionPicker = Stage()
                                                var x = 0
                                                var y = 0
                                                var w = 0
                                                var h = 0
                                                with(regionPicker) {
                                                    scene = Scene(group {
                                                        vbox localvbox@{
                                                            val sheet = sheet ?: return@localvbox
                                                            val scaleFactor: Double
                                                            scaleFactor = if (sheet.width > 1024 || sheet.height > 900) 0.5 else 1.0
                                                            canvas(sheet.width * scaleFactor, sheet.height * scaleFactor) {
                                                                val gc = graphicsContext2D
                                                                gc.scale(scaleFactor, scaleFactor)
                                                                for (i in 0..sheet.width / 16) {
                                                                    for (j in 0..sheet.width / 16) {
                                                                        if ((i + j) % 2 == 1) {
                                                                            gc.fill = Color.LIGHTGRAY
                                                                        } else {
                                                                            gc.fill = Color.WHITE
                                                                        }
                                                                        gc.fillRect(i * 16.0, j * 16.0, 16.0, 16.0)
                                                                    }
                                                                }
                                                                gc.drawImage(SwingFXUtils.toFXImage(sheet, null), 0.0, 0.0)
                                                                onMousePressed = EventHandler { e ->
                                                                    x = (e.x / scaleFactor).toInt()
                                                                    y = (e.y / scaleFactor).toInt()
                                                                }
                                                                onMouseDragged = EventHandler { e ->
                                                                    w = (e.x / scaleFactor).toInt() - x
                                                                    h = (e.y / scaleFactor).toInt() - y
                                                                    gc.clearRect(0.0, 0.0, sheet.width.toDouble(), sheet.height.toDouble())
                                                                    for (i in 0..sheet.width / 16) {
                                                                        for (j in 0..sheet.width / 16) {
                                                                            if ((i + j) % 2 == 1) {
                                                                                gc.fill = Color.LIGHTGRAY
                                                                            } else {
                                                                                gc.fill = Color.WHITE
                                                                            }
                                                                            gc.fillRect(i * 16.0, j * 16.0, 16.0, 16.0)
                                                                        }
                                                                    }
                                                                    gc.drawImage(SwingFXUtils.toFXImage(sheet, null), 0.0, 0.0)
                                                                    gc.stroke = Color.RED
                                                                    gc.strokeRect(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble())
                                                                }
                                                            }
                                                            button("Confirm") {
                                                                action {
                                                                    regionPicker.close()
                                                                }
                                                            }
                                                        }
                                                    })
                                                }
                                                val r = root.parent
                                                if (r is Window) {
                                                    regionPicker.initOwner(r)
                                                }
                                                regionPicker.initModality(Modality.APPLICATION_MODAL)
                                                regionPicker.showAndWait()
                                                if (x == 0 && y == 0 && w == 0 && h == 0) {
                                                    return@action
                                                }
                                                val currentPart = currentPart
                                                val currentSprite = currentSprite
                                                if (currentPart != null && currentSprite != null) {
                                                    val part = SpritePart(x.toShort(), y.toShort(), w.toShort(), h.toShort(), 512, 512)
                                                    part.unknownData.addAll(currentPart.unknownData)
                                                    currentSprite.parts.add(part)
                                                    spriteListener(ReadOnlyObjectWrapper(0), 0, spriteSpinner.value)
                                                    partSpinner.increment(1000)
                                                }
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        button("Move Up") {
                                            action {
                                                val currentSprite = currentSprite
                                                if (currentSprite != null && partSpinner.value < currentSprite.parts.size - 1) {
                                                    Collections.swap(currentSprite.parts, partSpinner.value, partSpinner.value + 1)
                                                    partSpinner.increment()
                                                }
                                            }
                                        }
                                        button("Move Down") {
                                            action {
                                                val currentSprite = currentSprite
                                                if (currentSprite != null && partSpinner.value > 0) {
                                                    Collections.swap(currentSprite.parts, partSpinner.value, partSpinner.value - 1)
                                                    partSpinner.decrement()
                                                }
                                            }
                                        }
                                        button("Remove") {
                                            action {
                                                val currentSprite = currentSprite
                                                if (currentSprite != null) {
                                                    currentSprite.parts.removeAt(partSpinner.value)
                                                    val f = partSpinner.valueFactory
                                                    if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                                        f.max--
                                                        if (partSpinner.value > f.max) {
                                                            partSpinner.decrement()
                                                        }
                                                    }
                                                    partListener(ReadOnlyObjectWrapper(0), 0, partSpinner.value)
                                                }
                                            }
                                        }
                                        button("Duplicate") {
                                            action {
                                                val currentSprite = currentSprite
                                                val currentPart = currentPart
                                                if (currentSprite != null && currentPart != null) {
                                                    currentSprite.parts.add(currentPart.copy())
                                                    val f = partSpinner.valueFactory
                                                    if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
                                                        f.max++
                                                        partSpinner.increment(f.max)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("X")
                                        partXSpinner = spinner(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt(), 0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentPart?.relX = new.toShort()
                                                drawCurrentSprite()
                                            }
                                        }
                                        label("Y")
                                        partYSpinner = spinner(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt(), 0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentPart?.relY = new.toShort()
                                                drawCurrentSprite()
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Scale X")
                                        partScaleXSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentPart?.stretchX = new.toFloat()
                                                drawCurrentSprite()
                                            }
                                        }
                                        label("Y")
                                        partScaleYSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentPart?.stretchY = new.toFloat()
                                                drawCurrentSprite()
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Rotation:")
                                        partRotationSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentPart?.rotation = new.toFloat()
                                                drawCurrentSprite()
                                            }
                                        }
                                        label("°")
                                    }

                                    hbox(spacing = 6) {
                                        partFlipXCheck = checkbox("Flip X?") {
                                            selectedProperty().addListener { _, _, new ->
                                                currentPart?.flipX = new
                                                drawCurrentSprite()
                                            }
                                        }
                                        partFlipYCheck = checkbox("Flip Y?") {
                                            selectedProperty().addListener { _, _, new ->
                                                currentPart?.flipY = new
                                                drawCurrentSprite()
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Multiply Color: ")
                                        partColorPicker = colorpicker {
                                            valueProperty().addListener { _, _, new ->
                                                currentPart?.multColor = new
                                                drawCurrentSprite()
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Screening Color: ")
                                        partScreenPicker = colorpicker {
                                            valueProperty().addListener { _, _, new ->
                                                currentPart?.screenColor = new
                                                drawCurrentSprite()
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Opacity: ")
                                        partOpacitySpinner = spinner(0, 255, 255) {
                                            isEditable = true
                                            prefWidth = 70.0
                                            valueProperty().addListener { _, _, new ->
                                                currentPart?.opacity = new
                                                drawCurrentSprite()
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("???: ")
                                        partUnkSpinner = spinner(0, 65535, 255) {
                                            isEditable = true
                                            prefWidth = 70.0
                                            valueProperty().addListener { _, _, new ->
                                                currentPart?.unk = new
                                                drawCurrentSprite()
                                            }
                                        }
                                        label("(Crashes game if changed)")
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Designation:")
                                        partDesignationSpinner = spinner(0, 255, 0) {
                                            isEditable = true
                                            prefWidth = 80.0
                                            valueFactory.valueProperty().addListener { _, _, new ->
                                                currentPart?.designation = new
                                            }
                                        }
                                    }

                                    hbox(spacing = 6) {
                                        alignment = Pos.CENTER_LEFT
                                        label("Corner Depths:")
                                        vbox(spacing = 10) {
                                            hbox(spacing = 6) {
                                                partTLDepthSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 1.0) {
                                                    isEditable = true
                                                    prefWidth = 80.0
                                                    valueFactory.valueProperty().addListener { _, _, new ->
                                                        currentPart?.tldepth = new.toFloat()
                                                        drawCurrentSprite()
                                                    }
                                                }
                                                partTRDepthSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 1.0) {
                                                    isEditable = true
                                                    prefWidth = 80.0
                                                    valueFactory.valueProperty().addListener { _, _, new ->
                                                        currentPart?.trdepth = new.toFloat()
                                                        drawCurrentSprite()
                                                    }
                                                }
                                            }
                                            hbox(spacing = 6) {
                                                partBLDepthSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 1.0) {
                                                    isEditable = true
                                                    prefWidth = 80.0
                                                    valueFactory.valueProperty().addListener { _, _, new ->
                                                        currentPart?.bldepth = new.toFloat()
                                                        drawCurrentSprite()
                                                    }
                                                }
                                                partBRDepthSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 1.0) {
                                                    isEditable = true
                                                    prefWidth = 80.0
                                                    valueFactory.valueProperty().addListener { _, _, new ->
                                                        currentPart?.brdepth = new.toFloat()
                                                        drawCurrentSprite()
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    label("Unknown Data:")

                                    partUnknownDataBox = textarea {
                                        isWrapText = true
                                        isEditable = false
                                        prefHeight = 0.0
                                    }
                                }
                                spriteCanvas = canvas(512.0, 512.0) {
                                    var prevX = -1
                                    var prevY = -1
                                    onMouseDragged = EventHandler { e ->
                                        val currentPart = currentPart
                                        if (currentPart != null && prevX > -1) {
                                            if (e.isPrimaryButtonDown) {
                                                currentPart.relX = (currentPart.relX + e.x - prevX).toShort()
                                                currentPart.relY = (currentPart.relY + e.y - prevY).toShort()
                                                prevX = e.x.toInt()
                                                prevY = e.y.toInt()
                                            } else if (e.isSecondaryButtonDown) {
                                                val xFactor = ((e.x - (currentPart.relX - 256)) / (prevX - (currentPart.relX - 256))).toFloat()
                                                val yFactor = ((e.y - (currentPart.relY - 256)) / (prevY - (currentPart.relY - 256))).toFloat()
                                                if (xFactor != 0f &&
                                                        xFactor.isFinite()) {
                                                    currentPart.stretchX *= xFactor
                                                    prevX = e.x.toInt()
                                                    if (e.isShiftDown && yFactor != 0f && yFactor.isFinite()) {
                                                        currentPart.stretchY *= yFactor
                                                        prevY = e.y.toInt()
                                                    } else {
                                                        currentPart.stretchY *= xFactor
                                                    }
                                                }
                                            }
                                        } else {
                                            prevX = e.x.toInt()
                                            prevY = e.y.toInt()
                                        }
                                        updatePart()
                                        drawCurrentSprite()
                                    }
                                    onScroll = EventHandler { e ->
                                        if (e.isShiftDown) {
                                            if (e.isControlDown) {
                                                if (e.deltaY > 0 || e.deltaX > 0) {
                                                    spriteSpinner.increment()
                                                } else {
                                                    spriteSpinner.decrement()
                                                }
                                            } else {
                                                if (e.deltaY > 0 || e.deltaX > 0) {
                                                    partSpinner.increment()
                                                } else {
                                                    partSpinner.decrement()
                                                }
                                            }
                                        } else if (e.isControlDown) {
                                            if (e.deltaY > 0 || e.deltaX > 0) {
                                                zoomFactor *= 2.0.pow(1 / 7.0)
                                            } else {
                                                zoomFactor /= 2.0.pow(1 / 7.0)
                                            }
                                            zoomLabel.text = String.format("%.0f%%", zoomFactor * 100)
                                        } else {
                                            val part = currentPart
                                            if (part != null) {
                                                part.rotation += e.deltaY.toFloat() / 20
                                            }
                                        }
                                        updatePart()
                                        drawCurrentSprite()
                                    }
                                    onMouseReleased = EventHandler {
                                        prevX = -1
                                        prevY = -1
                                    }
                                    drawTransparencyGrid(this)
                                }
                                right = spriteCanvas
                            }
                        }
                    }
                    hbox {
                        alignment = Pos.CENTER_RIGHT
                        label("Zoom: ")
                        zoomLabel = label("100%")
                    }
                }
            }
        }
    }

    fun updatePart() {
        val part = currentPart ?: return
        partXSpinner.valueFactory.value = part.relX.toInt()
        partYSpinner.valueFactory.value = part.relY.toInt()
        partScaleXSpinner.valueFactory.value = part.stretchX.toDouble()
        partScaleYSpinner.valueFactory.value = part.stretchY.toDouble()
        partRotationSpinner.valueFactory.value = part.rotation.toDouble()
        partColorPicker.value = part.multColor
        partScreenPicker.value = part.screenColor
        partDesignationSpinner.valueFactory.value = part.designation
        partTLDepthSpinner.valueFactory.value = part.tldepth.toDouble()
        partTRDepthSpinner.valueFactory.value = part.trdepth.toDouble()
        partBLDepthSpinner.valueFactory.value = part.bldepth.toDouble()
        partBRDepthSpinner.valueFactory.value = part.brdepth.toDouble()
        partUnkSpinner.valueFactory.value = part.unk
        partOpacitySpinner.valueFactory.value = part.opacity
        partFlipXCheck.isSelected = part.flipX
        partFlipYCheck.isSelected = part.flipY
        partUnknownDataBox.text = part.unknownData.joinToString(" ") { String.format("%02X", it) }
    }

    fun updateStep() {
        val step = currentStep ?: return
        stepSpriteSpinner.valueFactory.value = step.spriteNum.toInt()
        stepDurationSpinner.valueFactory.value = step.duration.toInt()
        stepScaleXSpinner.valueFactory.value = step.stretchX.toDouble()
        stepScaleYSpinner.valueFactory.value = step.stretchY.toDouble()
        stepRotationSpinner.valueFactory.value = step.rotation.toDouble()
        stepUnkSpinner.valueFactory.value = step.depth.toDouble()
        stepColorPicker.value = step.color
        stepXSpinner.valueFactory.value = step.tlX.toInt()
        stepYSpinner.valueFactory.value = step.tlY.toInt()
        stepOpacitySpinner.valueFactory.value = step.opacity.toInt()
        stepUnknownDataBox.text = step.unknownData.joinToString(" ") { String.format("%02X", it) }
    }

    fun drawCurrentSprite() {
        drawTransparencyGrid(spriteCanvas)
        drawSprite(spriteCanvas, currentSprite ?: return)
        val currentPart = currentPart
        if (currentPart != null) {
            val gc = spriteCanvas.graphicsContext2D
            gc.save()
            gc.transform(Affine(Scale(zoomFactor, zoomFactor, 256.0, 256.0)))
            currentPart.setTransformations(gc)
            gc.globalAlpha = 1.0
            gc.stroke = Color.RED
            gc.strokeRect(currentPart.relX - 512.0 + 256.0, currentPart.relY - 512.0 + 256.0, abs(currentPart.w * currentPart.stretchX) * 1.0, abs(currentPart.h * currentPart.stretchY) * 1.0)
            gc.restore()
        }
    }

    fun drawSprite(canvas: Canvas, sprite: Sprite, multColor: Color = Color.WHITE) {
        with(canvas) {
            val gc = graphicsContext2D
            val sheet = sheet
            if (sheet != null) {
                for (p in sprite.parts) {
                    val bufferedImage = sheet.getSubimage(p.x.toInt(), p.y.toInt(), p.w.toInt(), p.h.toInt())
                    val image = p.toImage(bufferedImage, multColor)
                    gc.save()
                    gc.transform(Affine(Scale(zoomFactor, zoomFactor, 256.0, 256.0)))
                    p.setTransformations(gc)
                    gc.drawImage(image,
                            p.relX - 256.0, p.relY - 256.0)
                    gc.restore()
                }
            }
        }
    }

    fun drawTransparencyGrid(canvas: Canvas) {
        val gc = canvas.graphicsContext2D
        gc.clearRect(0.0, 0.0, canvas.width, canvas.height)
        for (x in 0..(canvas.width / 16).toInt()) {
            for (y in 0..(canvas.height / 16).toInt()) {
                if ((x + y) % 2 == 1) {
                    gc.fill = Color.LIGHTGRAY
                } else {
                    gc.fill = Color.WHITE
                }
                gc.fillRect(x * 16.0, y * 16.0, 16.0, 16.0)
            }
        }
    }

    fun drawStep(canvas: Canvas, step: AnimationStep) {
        val bccad = bccad ?: return
        with(canvas) {
            val gc = graphicsContext2D
            drawTransparencyGrid(canvas)
            gc.save()
            step.setTransformations(gc)
            drawSprite(canvas, bccad.sprites[step.spriteNum.toInt()], step.color)
            gc.restore()
        }
    }
}