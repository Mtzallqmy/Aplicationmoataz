package ai.alaser.agent.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolArgumentAccumulatorTest {
    @Test
    fun combinesIncrementalJsonFragments() {
        val accumulator = ToolArgumentAccumulator()
        accumulator.append("{\"path\":")
        accumulator.append("\"hello.txt\"}")
        assertEquals("{\"path\":\"hello.txt\"}", accumulator.toString())
    }

    @Test
    fun acceptsGrowingProviderSnapshotsWithoutDuplicatingJson() {
        val accumulator = ToolArgumentAccumulator()
        accumulator.append("{\"path\"")
        accumulator.append("{\"path\":\"hello.txt\"}")
        accumulator.append("{\"path\":\"hello.txt\"}")
        accumulator.append("")
        assertEquals("{\"path\":\"hello.txt\"}", accumulator.toString())
    }
}
