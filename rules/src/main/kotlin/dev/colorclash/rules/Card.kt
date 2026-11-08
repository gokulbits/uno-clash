package dev.colorclash.rules

import kotlinx.serialization.Serializable

enum class CardColor { RED, YELLOW, GREEN, BLUE }

/** [drawCount] is what the next player draws, and also what a stack has to match or beat. */
enum class Face(val number: Int? = null, val drawCount: Int = 0) {
    ZERO(0), ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9),
    SKIP, REVERSE, DRAW_TWO(drawCount = 2),

    // No Mercy only.
    DRAW_FOUR(drawCount = 4), SKIP_EVERYONE, DISCARD_ALL,

    WILD, WILD_DRAW_FOUR(drawCount = 4),

    // No Mercy only.
    WILD_REVERSE_DRAW_FOUR(drawCount = 4), WILD_DRAW_SIX(drawCount = 6), WILD_DRAW_TEN(drawCount = 10),
    WILD_COLOR_ROULETTE;

    val isWild: Boolean get() = this in WILDS

    /** Anything that isn't a plain number card. */
    val isAction: Boolean get() = number == null

    val isDraw: Boolean get() = drawCount > 0

    val points: Int get() = number ?: if (isWild) 50 else 20

    companion object {
        val WILDS: Set<Face> = setOf(
            WILD, WILD_DRAW_FOUR, WILD_REVERSE_DRAW_FOUR, WILD_DRAW_SIX, WILD_DRAW_TEN, WILD_COLOR_ROULETTE,
        )
    }
}

/** [id] is unique within a deck so cards can be tracked across zones and animated. */
@Serializable
data class Card(val id: Int, val face: Face, val color: CardColor? = null) {
    init {
        require(face.isWild == (color == null)) { "Wild cards have no color, every other card has one" }
    }

    val isWild: Boolean get() = face.isWild

    /** Same color and same face, ignoring id. */
    fun isIdenticalTo(other: Card) = face == other.face && color == other.color

    override fun toString() = if (color == null) "$face#$id" else "${color}_$face#$id"
}

fun List<Card>.points(): Int = sumOf { it.face.points }

object Deck {
    const val SIZE = 108
    const val NO_MERCY_SIZE = 168

    private val CLASSIC_ACTIONS = listOf(Face.SKIP, Face.REVERSE, Face.DRAW_TWO)
    private val CLASSIC_WILDS = listOf(Face.WILD, Face.WILD_DRAW_FOUR)

    private val NO_MERCY_ACTIONS = CLASSIC_ACTIONS + listOf(Face.DRAW_FOUR, Face.SKIP_EVERYONE, Face.DISCARD_ALL)
    private val NO_MERCY_WILDS = listOf(
        Face.WILD, Face.WILD_REVERSE_DRAW_FOUR, Face.WILD_DRAW_SIX, Face.WILD_DRAW_TEN, Face.WILD_COLOR_ROULETTE,
    )

    fun forMode(mode: GameMode): List<Card> = when (mode) {
        GameMode.CLASSIC -> standard()
        GameMode.NO_MERCY -> noMercy()
    }

    /** 76 numbers, two of each colored action, four of each wild. */
    fun standard(): List<Card> = build(CLASSIC_ACTIONS, perColor = 2, wilds = CLASSIC_WILDS, perWild = 4)

    /** 76 numbers, three of each of the six colored actions, four of each of the five wilds. */
    fun noMercy(): List<Card> = build(NO_MERCY_ACTIONS, perColor = 3, wilds = NO_MERCY_WILDS, perWild = 4)

    private fun build(actions: List<Face>, perColor: Int, wilds: List<Face>, perWild: Int): List<Card> {
        val cards = mutableListOf<Card>()
        fun add(face: Face, color: CardColor?) {
            cards += Card(cards.size, face, color)
        }
        for (color in CardColor.entries) {
            add(Face.ZERO, color)
            for (face in Face.entries) {
                if (face.number != null && face != Face.ZERO) repeat(2) { add(face, color) }
            }
            for (face in actions) repeat(perColor) { add(face, color) }
        }
        for (face in wilds) repeat(perWild) { add(face, null) }
        return cards
    }
}
