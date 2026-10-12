package dev.pas.app

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatsAndModelTest {

    private val thursday = LocalDate.parse("2026-10-08")

    private fun checkIn(date: String, kind: String) =
        CheckIn(id = "$date-$kind", date = date, createdAt = "${date}T10:00:00Z", kind = kind)

    private fun cardReview(date: String, grade: CardGrade) =
        CheckIn(id = "$date-card", date = date, createdAt = "${date}T20:00:00Z", kind = CARD_KIND, cardId = "c1", grade = grade.key)

    private val card = Card(id = "c1", createdOn = "2026-10-01", question = "What is a sparse autoencoder?", answer = "An autoencoder with a sparsity penalty.")

    @Test
    fun startedDaysCountOnlyRealWorkFromMonday() {
        val checkIns = listOf(
            checkIn("2026-10-04", "gym"),
            checkIn("2026-10-05", FOCUS_KIND),
            checkIn("2026-10-06", "skip"),
            checkIn("2026-10-06", PLAN_KIND),
            checkIn("2026-10-07", CHAT_KIND),
            checkIn("2026-10-08", "screen_time"),
            checkIn("2026-10-08", "gym"),
        )
        assertEquals(2, Stats.startedDaysInWeek(checkIns, thursday))
    }

    @Test
    fun aReturnIsAStartAfterADayWithoutOne() {
        val checkIns = listOf(
            checkIn("2026-10-01", "gym"),
            checkIn("2026-10-02", "gym"),
            checkIn("2026-10-04", "learning"),
            checkIn("2026-10-06", "skip"),
            checkIn("2026-10-08", FOCUS_KIND),
        )
        assertEquals(2, Stats.returnCount(checkIns))
        assertTrue(Stats.isReturnDay(checkIns, thursday))
        assertFalse(Stats.isReturnDay(checkIns, LocalDate.parse("2026-10-02")))
    }

    @Test
    fun theFirstStartEverIsNotAReturn() {
        val checkIns = listOf(checkIn("2026-10-08", "gym"))
        assertEquals(0, Stats.returnCount(checkIns))
        assertFalse(Stats.isReturnDay(checkIns, thursday))
    }

    @Test
    fun aNewCardIsDueTheDayAfterItWasMade() {
        assertEquals(LocalDate.parse("2026-10-02"), Stats.nextReviewDay(card, emptyList()))
    }

    @Test
    fun cardsMoveAlongTheIntervalsAndStartAgainWhenForgotten() {
        val easyTwice = listOf(cardReview("2026-10-02", CardGrade.EASY), cardReview("2026-10-05", CardGrade.EASY))
        assertEquals(LocalDate.parse("2026-10-12"), Stats.nextReviewDay(card, easyTwice))

        val thenForgotten = easyTwice + cardReview("2026-10-12", CardGrade.AGAIN)
        assertEquals(LocalDate.parse("2026-10-13"), Stats.nextReviewDay(card, thenForgotten))

        val hardKeepsTheStep = listOf(cardReview("2026-10-02", CardGrade.EASY), cardReview("2026-10-05", CardGrade.HARD))
        assertEquals(LocalDate.parse("2026-10-08"), Stats.nextReviewDay(card, hardKeepsTheStep))
    }

    @Test
    fun dueCardsLeaveOutTheOnesScheduledLater() {
        val laterCard = card.copy(id = "c2", createdOn = "2026-10-08")
        assertEquals(listOf(card), Stats.dueCards(listOf(card, laterCard), emptyList(), thursday))
    }

    @Test
    fun tickedBlockersAreNoLongerOpen() {
        val done = checkIn("2026-10-08", BLOCKER_KIND).copy(note = "Log in to the CLI")
        assertEquals(listOf("Test the payment flow"), Stats.openBlockers(listOf("Log in to the CLI", "Test the payment flow"), listOf(done)))
    }

    @Test
    fun gymWeekCountsDistinctDaysFromMonday() {
        val checkIns = listOf(
            checkIn("2026-10-04", "gym"),
            checkIn("2026-10-05", "gym"),
            checkIn("2026-10-05", "gym"),
            checkIn("2026-10-08", "gym"),
            checkIn("2026-10-08", "screen_time"),
        )
        assertEquals(2, Stats.gymDaysInWeek(checkIns, thursday))
    }

    @Test
    fun daysLabelUsesEnglishPlurals() {
        assertEquals("1 day", daysLabel(1))
        assertEquals("5 days", daysLabel(5))
        assertEquals("20 days", daysLabel(20))
    }

    @Test
    fun decodesMessagesWrittenByThePc() {
        val messagesJson = """
            [{"id":"a","createdAt":"2026-10-08T15:00:31.869Z","slot":"evening","body":"Good evening.",
              "focus":"Tomorrow: 25 minutes","news":[],"quote":{"text":"One day at a time.","author":null},"extra":1}]
        """
        val message = CoachStore.json.decodeFromString<List<CoachMessage>>(messagesJson).single()
        assertNull(message.quote!!.author)
        assertEquals(emptyList<NewsItem>(), message.news)
        assertTrue(message.notify)
    }

    @Test
    fun decodesTheBoard() {
        val boardJson = """
            {"upcoming":[{"date":"2026-11-07","title":"3-5 products"}],
             "blockers":["Log in to the CLI"],
             "cards":[{"id":"c1","createdOn":"2026-10-08","question":"Q","answer":"A"}],
             "digests":[{"id":"2026-10-08","title":"Digest","summary":[{"text":"A","url":null}],"reading":[{"text":"B","url":"https://example.com"}],"watch":[]}]}
        """
        val board = CoachStore.json.decodeFromString<Board>(boardJson)
        assertEquals("2026-11-07", board.upcoming.single().date)
        assertEquals("Log in to the CLI", board.blockers.single())
        assertEquals("Q", board.cards.single().question)
        assertEquals("https://example.com", board.digests.single().reading.single().url)
    }
}
