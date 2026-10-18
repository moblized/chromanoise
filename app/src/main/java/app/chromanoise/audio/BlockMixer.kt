package app.chromanoise.audio

import java.util.Random

/**
 * Plays an endless stereo stream out of a small pool of windowed noise blocks.
 *
 * Each half-block of output is the fading tail of the previous block plus the rising head
 * of a randomly picked next block. Left and right pick blocks independently, so the two
 * channels are uncorrelated and the noise sounds wide on headphones. Swapping in a new
 * pool (after the spectrum changes) crossfades naturally at the next half-block.
 */
class BlockMixer(private val blockSize: Int, private val random: Random = Random()) {
    private val half = blockSize / 2
    private var prevL: FloatArray? = null
    private var prevR: FloatArray? = null
    private var curL: FloatArray? = null
    private var curR: FloatArray? = null
    private var pos = half

    /**
     * Writes [frames] interleaved stereo frames into [out], ramping the gain linearly from
     * [gainFrom] to [gainTo] across the chunk.
     */
    fun fill(out: FloatArray, frames: Int, pool: List<FloatArray>, gainFrom: Float, gainTo: Float) {
        for (i in 0 until frames) {
            if (pos >= half) advance(pool)
            val g = gainFrom + (gainTo - gainFrom) * (i + 1) / frames
            val pl = prevL
            val pr = prevR
            val cl = curL
            val cr = curR
            val l = (if (pl != null) pl[pos + half] else 0f) + (if (cl != null) cl[pos] else 0f)
            val r = (if (pr != null) pr[pos + half] else 0f) + (if (cr != null) cr[pos] else 0f)
            out[2 * i] = (l * g).coerceIn(-1f, 1f)
            out[2 * i + 1] = (r * g).coerceIn(-1f, 1f)
            pos++
        }
    }

    private fun advance(pool: List<FloatArray>) {
        prevL = curL
        prevR = curR
        if (pool.isEmpty()) {
            curL = null
            curR = null
        } else {
            val l = pickAvoiding(pool, prevL, null)
            curL = l
            curR = pickAvoiding(pool, prevR, l)
        }
        pos = 0
    }

    /** A random block that isn't [a] or [b], when the pool is big enough to manage that. */
    private fun pickAvoiding(pool: List<FloatArray>, a: FloatArray?, b: FloatArray?): FloatArray {
        repeat(8) {
            val c = pool[random.nextInt(pool.size)]
            if (c !== a && c !== b) return c
        }
        return pool[random.nextInt(pool.size)]
    }
}
