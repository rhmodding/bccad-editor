package rhmodding.bccadeditor.bccad

import java.nio.ByteBuffer
import java.nio.ByteOrder

class Animation(val name: String) {
	var steps: MutableList<AnimationStep> = mutableListOf()

	operator fun get(index: Int): AnimationStep {
		return steps[index]
	}

	fun addNewStep() {
		steps.add(AnimationStep(0, 1))
	}

	companion object {
		fun fromBuffer(buf: ByteBuffer): Animation {
			var s = ""
			val n = buf.get()
			repeat(n.toInt()) {
				s += buf.get().toChar()
			}
			repeat(4 - ((n+1) % 4)) {
				buf.get()
			}
			val a = Animation(s)
			buf.int
			repeat(buf.int) {
				a.steps.add(AnimationStep.fromBuffer(buf))
			}
			return a
		}
	}

	fun toBytes(): List<Byte> {
		val l = mutableListOf<Byte>()
		l.add(name.length.toByte())
		l.addAll(name.toCharArray().map { it.toByte() })
		l.addAll(ByteArray(4 - ((name.length+1)%4)).toList())
		val a = ByteArray(8)
		val bb = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN)
		bb.putInt(4, steps.size)
		l.addAll(a.toList())
		for (s in steps) {
			l.addAll(s.toBytes())
		}
		return l
	}

	override fun toString(): String {
		return "Animation $name: {\n" + steps.map { "\t\t" + it.toString() }.joinToString("\n") + "\n\t}"
	}
}