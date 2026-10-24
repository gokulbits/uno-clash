package dev.colorclash.rules

import kotlinx.serialization.Serializable

enum class CardColor { RED, YELLOW, GREEN, BLUE }

enum class Face(val number: Int? = null) {
    ZERO(0), ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9),
    SKIP, REVERSE, DRAW_TWO, WILD, WILD_DRAW_FOUR;

    val isWild: Boolean get() = this == WILD || this == WILD_DRAW_FOUR

    val points: Int get() = number ?: if (isWild) 50 else 20
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

    fun standard(): List<Card> {
        val cards = mutableListOf<Card>()
        fun add(face: Face, color: CardColor?) {
            cards += Card(cards.size, face, color)
        }
        for (color in CardColor.entries) {
            add(Face.ZERO, color)
            for (face in Face.entries) {
                if (face != Face.ZERO && !face.isWild) repeat(2) { add(face, color) }
            }
        }
        repeat(4) { add(Face.WILD, null) }
        repeat(4) { add(Face.WILD_DRAW_FOUR, null) }
        return cards
    }
}
