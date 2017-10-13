package rhmodding.bccadeditor.bccad

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class BCCAD(file: File) {
	var timestamp: Int
	var unkX: Short
	var unkY: Short
	val sprites = mutableListOf<Sprite>()
	val animations = mutableListOf<Animation>()

	init {
		val b = Files.readAllBytes(Paths.get(file.absolutePath))
		val buf = ByteBuffer.wrap(b)
		buf.order(ByteOrder.LITTLE_ENDIAN)
		buf.position(0)
		timestamp = buf.int
		unkX = buf.short
		unkY = buf.short
		repeat(buf.int) {
			sprites.add(Sprite.fromBuffer(buf))
		}
		repeat(buf.int) {
			animations.add(Animation.fromBuffer(buf))
		}
	}

	fun toBytes(): ByteArray {
		val firstBytes = ByteArray(12)
		val buf = ByteBuffer.wrap(firstBytes).order(ByteOrder.LITTLE_ENDIAN)
		buf.putInt(timestamp)
		buf.putShort(unkX)
		buf.putShort(unkY)
		buf.putInt(sprites.size)
		val l = firstBytes.toMutableList()
		for (s in sprites) {
			l.addAll(s.toBytes())
		}
		val animationSizeBytes = ByteArray(4)
		ByteBuffer.wrap(animationSizeBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(animations.size)
		l.addAll(animationSizeBytes.toList())
		for (a in animations) {
			l.addAll(a.toBytes())
		}
		l.add(0)
		return l.toByteArray()
	}

	override fun toString(): String {
		return "$timestamp $unkX $unkY ${sprites.size} ${animations.size}\n" +
				"Sprites: {\n" + sprites.map { "\t" + it.toString() }.joinToString("\n") + "\n}" +
				"\nAnimations: {\n" + animations.map { "\t" + it.toString() }.joinToString("\n") + "\n}"
	}
}