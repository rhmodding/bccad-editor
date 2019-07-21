package rhmodding.bccadeditor.bccad

import java.nio.ByteBuffer
import java.nio.ByteOrder

class Animation(var name: String) {

    var steps: MutableList<AnimationStep> = mutableListOf()

    private var interpolationInt: Int = 0
    var interpolated: Boolean
        get() = (interpolationInt and 0x1) > 0
        set(value) {
            interpolationInt = if (value) (interpolationInt or (1 shl 0x1)) else (interpolationInt and (1 shl 0x1).inv())
        }

    operator fun get(index: Int): AnimationStep {
        return steps[index]
    }

    fun addNewStep() {
        val step = AnimationStep(0, 1)
        step.unknownData.addAll(listOf(0, 0))
        steps.add(step)
    }

    companion object {
        fun fromBuffer(buf: ByteBuffer): Animation {
            var s = ""
            val n = buf.get()
            repeat(n.toInt()) {
                s += buf.get().toChar()
            }
            repeat(4 - ((n + 1) % 4)) {
                buf.get()
            }
            val a = Animation(s)
            a.interpolationInt = buf.int // This is the interpolation int, supposedly
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
        l.addAll(ByteArray(4 - ((name.length + 1) % 4)).toList())
        val a = ByteArray(8)
        val bb = ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(interpolationInt)
        bb.putInt(steps.size)
        l.addAll(a.toList())
        for (s in steps) {
            l.addAll(s.toBytes())
        }
        return l
    }

    override fun toString(): String {
        return "Animation $name: {\ninterpolation: $interpolationInt\n" + steps.joinToString("\n") { "\t\t" + it.toString() } + "\n\t}"
    }
}