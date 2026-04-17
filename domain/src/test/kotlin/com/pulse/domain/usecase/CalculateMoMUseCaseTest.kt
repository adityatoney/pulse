package com.pulse.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.Test

class CalculateMoMUseCaseTest {

    @Test
    fun `mid-month anchor compares matching day windows`() {
        val anchor = LocalDate(2026, 4, 16)
        val (current, previous) = CalculateMoMUseCase.windows(anchor)
        assertThat(current.start).isEqualTo(LocalDate(2026, 4, 1))
        assertThat(current.endInclusive).isEqualTo(LocalDate(2026, 4, 16))
        assertThat(previous.start).isEqualTo(LocalDate(2026, 3, 1))
        assertThat(previous.endInclusive).isEqualTo(LocalDate(2026, 3, 16))
    }

    @Test
    fun `first of month gives single-day windows`() {
        val anchor = LocalDate(2026, 4, 1)
        val (current, previous) = CalculateMoMUseCase.windows(anchor)
        assertThat(current.start).isEqualTo(LocalDate(2026, 4, 1))
        assertThat(current.endInclusive).isEqualTo(LocalDate(2026, 4, 1))
        assertThat(previous.start).isEqualTo(LocalDate(2026, 3, 1))
        assertThat(previous.endInclusive).isEqualTo(LocalDate(2026, 3, 1))
    }

    @Test
    fun `march 31 anchor caps previous window at feb 28`() {
        val anchor = LocalDate(2026, 3, 31)
        val (_, previous) = CalculateMoMUseCase.windows(anchor)
        assertThat(previous.start).isEqualTo(LocalDate(2026, 2, 1))
        // 2026 is not a leap year, so Feb has 28 days
        assertThat(previous.endInclusive).isEqualTo(LocalDate(2026, 2, 28))
    }

    @Test
    fun `january anchor rolls to december of prior year`() {
        val anchor = LocalDate(2026, 1, 15)
        val (current, previous) = CalculateMoMUseCase.windows(anchor)
        assertThat(current.start).isEqualTo(LocalDate(2026, 1, 1))
        assertThat(previous.start).isEqualTo(LocalDate(2025, 12, 1))
        assertThat(previous.endInclusive).isEqualTo(LocalDate(2025, 12, 15))
    }
}
