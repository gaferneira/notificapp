package dev.gaferneira.notificapp.core.common

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test
import java.net.UnknownHostException

class FailureTest {

    @Test
    fun `given an already-mapped Failure, when analyzing the cause, then the same instance is returned`() {
        val failure = Failure.ServerError(code = 500, message = "boom")

        Failure.analyzeCause(failure) shouldBeSameInstanceAs failure
    }

    @Test
    fun `given a Failure, when converting to a failure result, then it is not double-wrapped`() {
        val failure = Failure.NetworkConnection()

        val result = failure.toFailureResult<Unit>()

        result.exceptionOrNull() shouldBeSameInstanceAs failure
    }

    @Test
    fun `given an IllegalStateException, when analyzing the cause, then it maps to ApplicationException`() {
        val failure = Failure.analyzeCause(IllegalStateException("bad state"))

        failure.shouldBeInstanceOf<Failure.ApplicationException>()
        failure.message shouldBe "bad state"
    }

    @Test
    fun `given an UnknownHostException, when analyzing the cause, then it maps to NetworkConnection`() {
        Failure.analyzeCause(UnknownHostException()).shouldBeInstanceOf<Failure.NetworkConnection>()
    }
}
