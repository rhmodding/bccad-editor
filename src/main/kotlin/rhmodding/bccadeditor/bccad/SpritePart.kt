package rhmodding.bccadeditor.bccad

import java.awt.image.BufferedImage
import java.nio.ByteBuffer

class SpritePart(var x: Short, var y: Short, var w: Short, var h: Short, var relX: Short, var relY: Short) {
	var image: BufferedImage? = null
	var rotation: Float = 0f
	var stretchX: Float = 1f
	var stretchY: Float = 1f

	val unknownData = mutableListOf<Byte>()

	companion object {
		fun fromBuffer(buf: ByteBuffer): SpritePart {
			val t = SpritePart(buf.short, buf.short, buf.short, buf.short, buf.short, buf.short)
			t.stretchX = buf.float
			t.stretchY = buf.float
			t.rotation = buf.float
			repeat(0x40 - 0x18) {
				t.unknownData.add(buf.get())
			}
			return t
		}
	}

	override fun toString(): String {
		return "($x, $y, $w, $h), rel ($relX, $relY), rot $rotation, stretch ($stretchX, $stretchY)"
	}
}