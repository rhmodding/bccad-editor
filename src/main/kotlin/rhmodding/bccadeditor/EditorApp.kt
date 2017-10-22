package rhmodding.bccadeditor

import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.geometry.Pos
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import rhmodding.bccadeditor.bccad.*
import tornadofx.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.transform.Affine
import java.awt.Graphics2D
import javafx.scene.transform.Scale
import javafx.stage.Modality
import javafx.stage.Stage
import javafx.stage.Window
import javafx.util.Duration


class EditorApp : App(EditorView::class)

class EditorView : View("BCCAD Editor") {
	override val root = VBox()
	var frame: Long = 30
	var currentAnimation: Animation? = null
	var currentStep: AnimationStep? = null
	var currentSprite: Sprite? = null
	var currentPart: SpritePart? = null
	var bccad: BCCAD? = null

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

	lateinit var partOpacitySpinner: Spinner<Int>

	lateinit var partUnknownDataBox: TextArea

	lateinit var zoomLabel: Label

	var zoomFactor = 1.0

	var animation: Timeline? = null

	private val animationListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
		if (bccad != null) {
			animation?.stop()
			currentAnimation = bccad!!.animations[new]
			stepSpinner.decrement(1000)
			stepListener(ReadOnlyObjectWrapper(0), 0, 0)
			val f = stepSpinner.valueFactory
			if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
				f.max = currentAnimation!!.steps.size - 1
			}
			aNameLabel.text = currentAnimation!!.name
		}
	}

	private val stepListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
		if (currentAnimation != null) {
			animation?.stop()
			currentStep = currentAnimation!![new]
			updateStep()
			drawStep(stepCanvas, currentStep!!)
		}
	}

	private val spriteListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
		currentSprite = bccad?.sprites?.get(new)
		partSpinner.decrement(1000)
		partListener(ReadOnlyObjectWrapper(0), 0, 0)
		val f = partSpinner.valueFactory
		if (f is SpinnerValueFactory.IntegerSpinnerValueFactory) {
			f.max = currentSprite!!.parts.size-1
		}
		drawCurrentSprite()
	}

	private val partListener = { _: ObservableValue<out Int>, _: Int, new: Int ->
		if (currentSprite?.parts?.size?:0 > 0) {
			currentPart = currentSprite?.parts?.get(new)
			updatePart()
			drawCurrentSprite()
		}
	}

	init {
		with (root) {
			borderpane {
				top = menubar {
					menu("File") {
						item("Open Sprite Sheet...") {
							action {
								animation?.stop()
								val f = chooseFile("Choose sheet", arrayOf(FileChooser.ExtensionFilter("PNG", "*.png"), FileChooser.ExtensionFilter("All files", "*.*")))
								if (f.isNotEmpty()) {
									val file = f[0]
									val rawIm = ImageIO.read(file)
									sheet = BufferedImage(rawIm.height, rawIm.width, rawIm.type)
									val transform = AffineTransform()
									transform.translate(0.5*rawIm.height, 0.5*rawIm.width)
									transform.rotate(-Math.PI/2)
									transform.translate(-0.5*rawIm.width, -0.5*rawIm.height)
									val g = sheet!!.createGraphics() as Graphics2D
									g.drawImage(rawIm, transform, null)
									g.dispose()
								}
							}
						}
						item("Open BCCAD...") {
							action {
								animation?.stop()
								val f = chooseFile("Choose bccad", arrayOf(FileChooser.ExtensionFilter("BCCAD", "*.bccad")))
								if (f.isNotEmpty()) {
									bccad = BCCAD(f[0])
									spriteSpinner.decrement(1000)
									spriteListener(ReadOnlyObjectWrapper(0), 0, 0)
									val sf = spriteSpinner.valueFactory
									val ssf = stepSpriteSpinner.valueFactory
									if (sf is SpinnerValueFactory.IntegerSpinnerValueFactory) {
										sf.max = bccad!!.sprites.size-1
									}
									if (ssf is SpinnerValueFactory.IntegerSpinnerValueFactory) {
										ssf.max = bccad!!.sprites.size-1
									}
									animationSpinner.decrement(1000)
									animationListener(ReadOnlyObjectWrapper(0), 0, 0)
									val v = animationSpinner.valueFactory
									if (v is SpinnerValueFactory.IntegerSpinnerValueFactory) {
										v.max = bccad!!.animations.size - 1
									}
								}
							}
						}
						item("Save", "Shortcut+S") {
							action {
								val f = chooseFile("Save bccad", arrayOf(FileChooser.ExtensionFilter("BCCAD", "*.bccad")), FileChooserMode.Save)
								if (f.isNotEmpty()) {
									val file = f[0]
									file.writeBytes(bccad!!.toBytes())
								}
							}
						}
					}
				}
				bottom = vbox {
					tabpane {
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
										aNameLabel = label("")
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
												animation = Timeline()
												var time = 0.0
												for (i in 0 until (currentAnimation?.steps?.size?:0)) {
													animation!!.keyFrames.add(KeyFrame(Duration.millis(time), EventHandler { _ ->
														drawStep(stepCanvas, currentAnimation!!.steps[i])
													}))
													time += frame*currentAnimation!!.steps[i].duration
												}
												animation!!.keyFrames.add(KeyFrame(Duration.millis(time), EventHandler {}))
												animation!!.cycleCount = javafx.animation.Animation.INDEFINITE
												animation!!.play()
											}
										}
										button("Stop") {
											action {
												animation?.stop()
												drawStep(stepCanvas, currentStep!!)
											}
										}
										label("Time per frame:")
										spinner(30, 1000, 30) {
											isEditable = true
											prefWidth = 70.0
											valueProperty().addListener({ _, _, new ->
												frame = new.toLong()
											})
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
												drawStep(stepCanvas, currentStep!!)
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
												drawStep(stepCanvas, currentStep!!)
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
												drawStep(stepCanvas, currentStep!!)
											}
										}
										label("Y: ")
										stepYSpinner = spinner(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt(), 0) {
											isEditable = true
											prefWidth = 80.0
											valueProperty().addListener { _, _, new ->
												currentStep?.tlY = new.toShort()
												drawStep(stepCanvas, currentStep!!)
											}
										}
									}

									hbox(spacing = 6) {
										alignment = Pos.CENTER_LEFT
										label("???:")
										stepUnkSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1) {
											isEditable = true
											prefWidth = 80.0
											valueFactory.valueProperty().addListener { _, _, new ->
												currentStep?.depth = new.toFloat()
												drawStep(stepCanvas, currentStep!!)
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
												drawStep(stepCanvas, currentStep!!)
											}
										}
										label("Y")
										stepScaleYSpinner = spinner(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.1) {
											isEditable = true
											prefWidth = 80.0
											valueFactory.valueProperty().addListener { _, _, new ->
												currentStep?.stretchY = new.toFloat()
												drawStep(stepCanvas, currentStep!!)
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
												drawStep(stepCanvas, currentStep!!)
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
												drawStep(stepCanvas, currentStep!!)
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
												drawStep(stepCanvas, currentStep!!)
											}
										}
									}
									label("Unknown Data:")
									stepUnknownDataBox = textarea {
										isWrapText = true
										isEditable = false
									}
								}
								stepCanvas = canvas(512.0, 512.0) {
									onScroll = EventHandler { e ->
										if (e.isControlDown) {
											if (e.deltaY > 0 || e.deltaX > 0) {
												zoomFactor *= Math.pow(2.0, 1/7.0)
											} else {
												zoomFactor /= Math.pow(2.0, 1/7.0)
											}
											zoomLabel.text = String.format("%.0f%%", zoomFactor*100)
										}
										drawStep(this, currentStep!!)
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
												with (regionPicker) {
													scene = Scene(group {
														vbox {
															val scaleFactor: Double
															if (sheet!!.width > 1024 || sheet!!.height > 900) {
																scaleFactor = 0.5
															} else {
																scaleFactor = 1.0
															}
															canvas(sheet!!.width * scaleFactor, sheet!!.height * scaleFactor) {
																val gc = graphicsContext2D
																gc.scale(scaleFactor, scaleFactor)
																for (i in 0..sheet!!.width/16) {
																	for (j in 0..sheet!!.width/16) {
																		if ((i+j)%2 == 1) {
																			gc.fill = Color.LIGHTGRAY
																		} else {
																			gc.fill = Color.WHITE
																		}
																		gc.fillRect(i * 16.0, j * 16.0, 16.0, 16.0)
																	}
																}
																gc.drawImage(SwingFXUtils.toFXImage(sheet!!, null), 0.0, 0.0)
																onMousePressed = EventHandler { e ->
																	x = (e.x/scaleFactor).toInt()
																	y = (e.y/scaleFactor).toInt()
																}
																onMouseDragged = EventHandler { e ->
																	w = (e.x/scaleFactor).toInt() - x
																	h = (e.y/scaleFactor).toInt() - y
																	gc.clearRect(0.0, 0.0, sheet!!.width.toDouble(), sheet!!.height.toDouble())
																	for (i in 0..sheet!!.width/16) {
																		for (j in 0..sheet!!.width/16) {
																			if ((i+j)%2 == 1) {
																				gc.fill = Color.LIGHTGRAY
																			} else {
																				gc.fill = Color.WHITE
																			}
																			gc.fillRect(i * 16.0, j * 16.0, 16.0, 16.0)
																		}
																	}
																	gc.drawImage(SwingFXUtils.toFXImage(sheet!!, null), 0.0, 0.0)
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
												val part = SpritePart(x.toShort(), y.toShort(), w.toShort(), h.toShort(), 512, 512)
												part.unknownData.addAll(currentPart!!.unknownData)
												currentSprite!!.parts.add(part)
												spriteListener(ReadOnlyObjectWrapper(0), 0, spriteSpinner.value)
												partSpinner.increment(1000)
											}
										}
									}

									hbox(spacing = 6) {
										button("Move Up") {
											action {
												if (partSpinner.value < (currentSprite?.parts?.size?:0) - 1) {
													Collections.swap(currentSprite!!.parts, partSpinner.value, partSpinner.value + 1)
													partSpinner.increment()
												}
											}
										}
										button("Move Down") {
											action {
												if (partSpinner.value > 0 && currentSprite != null) {
													Collections.swap(currentSprite!!.parts, partSpinner.value, partSpinner.value - 1)
													partSpinner.decrement()
												}
											}
										}
										button("Remove") {
											action {
												if (currentSprite != null) {
													currentSprite!!.parts.removeAt(partSpinner.value)
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
												if (currentSprite != null) {
													currentSprite!!.parts.add(currentPart!!.copy())
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

									label("Unknown Data:")

									partUnknownDataBox = textarea {
										isWrapText = true
										isEditable = false
									}
								}
								spriteCanvas = canvas(512.0, 512.0) {
									var prevX = -1
									var prevY = -1
									onMouseDragged = EventHandler { e ->
										if (prevX > -1) {
											if (e.isPrimaryButtonDown) {
												currentPart?.relX = (currentPart!!.relX + e.x - prevX).toShort()
												currentPart?.relY = (currentPart!!.relY + e.y - prevY).toShort()
												prevX = e.x.toInt()
												prevY = e.y.toInt()
											} else if (e.isSecondaryButtonDown) {
												val xFactor = ((e.x - (currentPart!!.relX - 256)) / (prevX - (currentPart!!.relX - 256))).toFloat()
												val yFactor = ((e.y - (currentPart!!.relY - 256)) / (prevY - (currentPart!!.relY - 256))).toFloat()
												if (xFactor != 0f &&
														xFactor.isFinite()) {
													currentPart!!.stretchX *= xFactor
													prevX = e.x.toInt()
													if (e.isShiftDown && yFactor != 0f && yFactor.isFinite()) {
														currentPart!!.stretchY *= yFactor
														prevY = e.y.toInt()
													} else {
														currentPart!!.stretchY *= xFactor
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
											if (e.deltaY > 0 || e.deltaX > 0) {
												partSpinner.increment()
											} else {
												partSpinner.decrement()
											}
										} else if (e.isControlDown) {
											if (e.deltaY > 0 || e.deltaX > 0) {
												zoomFactor *= Math.pow(2.0, 1/7.0)
											} else {
												zoomFactor /= Math.pow(2.0, 1/7.0)
											}
											zoomLabel.text = String.format("%.0f%%", zoomFactor*100)
										} else {
											currentPart!!.rotation += e.deltaY.toFloat() / 20
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
		partXSpinner.valueFactory.value = currentPart!!.relX.toInt()
		partYSpinner.valueFactory.value = currentPart!!.relY.toInt()
		partScaleXSpinner.valueFactory.value = currentPart!!.stretchX.toDouble()
		partScaleYSpinner.valueFactory.value = currentPart!!.stretchY.toDouble()
		partRotationSpinner.valueFactory.value = currentPart!!.rotation.toDouble()
		partColorPicker.value = currentPart!!.multColor
		partScreenPicker.value = currentPart!!.screenColor
		partOpacitySpinner.valueFactory.value = currentPart!!.opacity
		partFlipXCheck.isSelected = currentPart!!.flipX
		partFlipYCheck.isSelected = currentPart!!.flipY
		partUnknownDataBox.text = currentPart!!.unknownData.map { String.format("%02X", it) }.joinToString(" ")
	}

	fun updateStep() {
		stepSpriteSpinner.valueFactory.value = currentStep!!.spriteNum.toInt()
		stepDurationSpinner.valueFactory.value = currentStep!!.duration.toInt()
		stepScaleXSpinner.valueFactory.value = currentStep!!.stretchX.toDouble()
		stepScaleYSpinner.valueFactory.value = currentStep!!.stretchY.toDouble()
		stepRotationSpinner.valueFactory.value = currentStep!!.rotation.toDouble()
		stepUnkSpinner.valueFactory.value = currentStep!!.depth.toDouble()
		stepColorPicker.value = currentStep!!.color
		stepXSpinner.valueFactory.value = currentStep!!.tlX.toInt()
		stepYSpinner.valueFactory.value = currentStep!!.tlY.toInt()
		stepOpacitySpinner.valueFactory.value = currentStep!!.opacity.toInt()
		stepUnknownDataBox.text = currentStep!!.unknownData.map { String.format("%02X", it) }.joinToString(" ")
	}

	fun drawCurrentSprite() {
		drawTransparencyGrid(spriteCanvas)
		drawSprite(spriteCanvas, currentSprite!!)
		if (currentPart != null) {
			val gc = spriteCanvas.graphicsContext2D
			gc.save()
			gc.transform(Affine(Scale(zoomFactor, zoomFactor, 256.0, 256.0)))
			currentPart!!.setTransformations(gc)
			gc.globalAlpha = 1.0
			gc.stroke = Color.RED
			gc.strokeRect(currentPart!!.relX - 512.0 + 256.0, currentPart!!.relY - 512.0 + 256.0, Math.abs(currentPart!!.w*currentPart!!.stretchX)*1.0, Math.abs(currentPart!!.h*currentPart!!.stretchY)*1.0)
			gc.restore()
		}
	}

	fun drawSprite(canvas: Canvas, sprite: Sprite, multColor: Color = Color.WHITE) {
		with (canvas) {
			val gc = graphicsContext2D
			if (sheet != null) {
				for (p in sprite.parts) {
					val bufferedImage = sheet!!.getSubimage(p.x.toInt(), p.y.toInt(), p.w.toInt(), p.h.toInt())
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
		for (x in 0..(canvas.width/16).toInt()) {
			for (y in 0..(canvas.height/16).toInt()) {
				if ((x+y)%2 == 1) {
					gc.fill = Color.LIGHTGRAY
				} else {
					gc.fill = Color.WHITE
				}
				gc.fillRect(x * 16.0, y * 16.0, 16.0, 16.0)
			}
		}
	}

	fun drawStep(canvas: Canvas, step: AnimationStep) {
		with (canvas) {
			val gc = graphicsContext2D
			drawTransparencyGrid(canvas)
			gc.save()
			step.setTransformations(gc)
			drawSprite(canvas, bccad!!.sprites[step.spriteNum.toInt()], step.color)
			gc.restore()
		}
	}
}