package dev.gaferneira.notificapp.features.ruleeditor.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.util.Locale

class DayOfWeekLabelsTest {

    @Test
    fun `short label is localized and not the raw enum prefix`() {
        // Given
        val day = DayOfWeek.MONDAY

        // When / Then
        day.shortLabel(Locale.ENGLISH) shouldBe "Mon"
        day.shortLabel(Locale.ENGLISH) shouldNotBe day.name.substring(0, 3)
    }

    @Test
    fun `short label follows the Spanish locale`() {
        DayOfWeek.MONDAY.shortLabel(Locale.forLanguageTag("es")).lowercase().startsWith("lun") shouldBe true
        DayOfWeek.SATURDAY.shortLabel(Locale.forLanguageTag("es")).lowercase().startsWith("sáb") shouldBe true
    }

    @Test
    fun `full label gives the complete day name for screen readers`() {
        DayOfWeek.WEDNESDAY.fullLabel(Locale.ENGLISH) shouldBe "Wednesday"
        DayOfWeek.WEDNESDAY.fullLabel(Locale.forLanguageTag("es")).lowercase() shouldBe "miércoles"
    }

    @Test
    fun `every day has distinct short labels`() {
        DayOfWeek.entries.map { it.shortLabel(Locale.ENGLISH) }.toSet().size shouldBe 7
    }
}
