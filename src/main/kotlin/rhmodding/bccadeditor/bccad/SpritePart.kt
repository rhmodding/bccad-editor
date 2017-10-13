package rhmodding.bccadeditor.bccad

import javafx.scene.canvas.GraphicsContext
import javafx.scene.transform.Affine
import javafx.scene.transform.Rotate
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpritePart(var x: Short, var y: Short, var w: Short, var h: Short, var relX: Short, var relY: Short) {
	var image: BufferedImage? = null
	var rotation: Float = 0f
	var stretchX: Float = 1f
	var stretchY: Float = 1f
	var flipX: Boolean = false
	var flipY: Boolean = false

	val unknownData = mutableListOf<Byte>()

	companion object {
		fun fromBuffer(buf: ByteBuffer): SpritePart {
			val t = SpritePart(buf.short, buf.short, buf.short, buf.short, buf.short, buf.short)
			t.stretchX = buf.float
			t.stretchY = buf.float
			t.rotation = buf.float
			t.flipX = buf.get() == 1.toByte()
			t.flipY = buf.get() == 1.toByte()
			repeat(0x40 - 0x1A) {
				t.unknownData.add(buf.get())
			}
			return t
		}
	}

	fun toBytes(): List<Byte> {
		val firstBytes = ByteArray(26)
		val b = ByteBuffer.wrap(firstBytes).order(ByteOrder.LITTLE_ENDIAN)
		b.putShort(x)
		b.putShort(y)
		b.putShort(w)
		b.putShort(h)
		b.putShort(relX)
		b.putShort(relY)
		b.putFloat(stretchX)
		b.putFloat(stretchY)
		b.putFloat(rotation)
		b.put((if (flipX) 1 else 0).toByte())
		b.put((if (flipY) 1 else 0).toByte())
		val l = firstBytes.toMutableList()
		l.addAll(unknownData)
		return l
	}

	fun setTransformations(gc: GraphicsContext) {
		val transform = Affine()
		transform.append(Scale(stretchX*1.0, stretchY*1.0, relX - 256.0, relY - 256.0))
		transform.append(Rotate(rotation*1.0, relX - 256 + w/2.0, relY - 256 + h/2.0))
		if (flipX) transform.append(Scale(-1.0, 1.0, relX - 256 + w/2.0, relY - 256 + h/2.0))
		if (flipY) transform.append(Scale(1.0, -1.0, relX - 256 + w/2.0, relY - 256 + h/2.0))
		gc.transform(transform)
	}

	override fun toString(): String {
		return "($x, $y, $w, $h), rel ($relX, $relY), rot $rotation, stretch ($stretchX, $stretchY)"
	}

	fun copy(): SpritePart {
		val p = SpritePart(x, y, w, h, 512, 512)
		p.stretchX = stretchX
		p.stretchY = stretchY
		p.rotation = rotation
		p.flipX = flipX
		p.flipY = flipY
		p.unknownData.addAll(unknownData)
		return p
	}
}