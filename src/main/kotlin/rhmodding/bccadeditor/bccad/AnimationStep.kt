package rhmodding.bccadeditor.bccad

import javafx.scene.canvas.GraphicsContext
import javafx.scene.transform.Affine
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AnimationStep(var spriteNum: Short, var duration: Short) {

	var tlX: Short = 0
	var tlY: Short = 0
	var opacity: Short = 255
	val unknownData = mutableListOf<Byte>()

	companion object {
		fun fromBuffer(buf: ByteBuffer): AnimationStep {
			val a = AnimationStep(buf.short, buf.short)
			a.tlX = buf.short
			a.tlY = buf.short
			repeat(22) {
				a.unknownData.add(buf.get())
			}
			a.opacity = buf.short
			return a
		}
	}

	fun toBytes(): List<Byte> {
		val firstBytes = ByteArray(8)
		val b = ByteBuffer.wrap(firstBytes).order(ByteOrder.LITTLE_ENDIAN)
		b.putShort(spriteNum)
		b.putShort(duration)
		b.putShort(tlX)
		b.putShort(tlY)
		val l = firstBytes.toMutableList()
		l.addAll(unknownData)
		val lastBytes = ByteArray(2)
		val b2 = ByteBuffer.wrap(lastBytes).order(ByteOrder.LITTLE_ENDIAN)
		b2.putShort(opacity)
		l.addAll(lastBytes.toList())
		return l
	}

	fun setTransformations(gc: GraphicsContext) {
		val transform = Affine()
		transform.appendTranslation(tlX.toDouble(), tlY.toDouble())
		gc.globalAlpha = opacity/255.0
		gc.transform(transform)
	}

	override fun toString(): String {
		return "$spriteNum $duration"
	}
}