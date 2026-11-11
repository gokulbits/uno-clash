package dev.colorclash.rules

import kotlinx.serialization.Serializable

/** Which face of a Flip deck is in play. Every other mode stays on [LIGHT] all game. */
enum class Side { LIGHT, DARK }

/** The four light colors, then the four a Flip deck shows on its dark side. */
enum class CardColor(val side: Side = Side.LIGHT) {
    RED, YELLOW, GREEN, BLUE,
    PINK(Side.DARK), TEAL(Side.DARK), ORANGE(Side.DARK), PURPLE(Side.DARK);

    companion object {
        val light: List<CardColor> = entries.filter { it.side == Side.LIGHT }
        val dark: List<CardColor> = entries.filter { it.side == Side.DARK }

        fun of(side: Side): List<CardColor> = if (side == Side.DARK) dark else light
    }
}

/** [drawCount] is what the next player draws, and also what a stack has to match or beat. */
enum class Face(val number: Int? = null, val drawCount: Int = 0) {
    ZERO(0), ONE(1), TWO(2), THREE(3), FOUR(4), FIVE(5), SIX(6), SEVEN(7), EIGHT(8), NINE(9),
    SKIP, REVERSE, DRAW_TWO(drawCount = 2),

    // Flip only. Draw One and Flip are light-side cards, Draw Five is dark-side.
    DRAW_ONE(drawCount = 1), DRAW_FIVE(drawCount = 5), FLIP,

    // No Mercy only.
    DRAW_FOUR(drawCount = 4), SKIP_EVERYONE, DISCARD_ALL,

    WILD, WILD_DRAW_FOUR(drawCount = 4),

    // A house rule in Classic: one card, in the deck only when it's switched on.
    WILD_SWAP_HANDS,

    // Flip only. The dark side's wild makes you draw until a named color turns up.
    WILD_DRAW_TWO(drawCount = 2), WILD_DRAW_COLOR,

    // No Mercy only.
    WILD_REVERSE_DRAW_FOUR(drawCount = 4), WILD_DRAW_SIX(drawCount = 6), WILD_DRAW_TEN(drawCount = 10),
    WILD_COLOR_ROULETTE;

    val isWild: Boolean get() = this in WILDS

    /** Anything that isn't a plain number card. */
    val isAction: Boolean get() = number == null

    val isDraw: Boolean get() = drawCount > 0

    val points: Int get() = when {
        number != null -> number
        // The printed card is worth 40, between a colored action and the other wilds.
        this == WILD_SWAP_HANDS -> 40
        isWild -> 50
        else -> 20
    }

    /** Flip prices its own cards, and not the way the other decks do. */
    val flipPoints: Int get() = when (this) {
        DRAW_ONE -> 10
        SKIP_EVERYONE -> 30
        WILD -> 40
        WILD_DRAW_TWO -> 50
        WILD_DRAW_COLOR -> 60
        else -> points
    }

    fun pointsIn(mode: GameMode): Int = if (mode == GameMode.FLIP) flipPoints else points

    companion object {
        val WILDS: Set<Face> = setOf(
            WILD, WILD_DRAW_FOUR, WILD_SWAP_HANDS, WILD_DRAW_TWO, WILD_DRAW_COLOR, WILD_REVERSE_DRAW_FOUR,
            WILD_DRAW_SIX, WILD_DRAW_TEN, WILD_COLOR_ROULETTE,
        )
    }
}

/** The side of a Flip card that isn't in play: what everyone except its holder is looking at. */
@Serializable
data class CardBack(val face: Face, val color: CardColor? = null) {
    init {
        require(face.isWild == (color == null)) { "Wild cards have no color, every other card has one" }
    }
}

/**
 * [id] is unique within a deck so cards can be tracked across zones and animated. [back] is only
 * set in Flip, where every card is printed on both sides; [face] and [color] are always the side
 * in play, so everything else in the engine can ignore that there are two.
 */
@Serializable
data class Card(val id: Int, val face: Face, val color: CardColor? = null, val back: CardBack? = null) {
    init {
        require(face.isWild == (color == null)) { "Wild cards have no color, every other card has one" }
    }

    /** Turns the card over: what was facing the table now faces its holder. */
    fun flipped(): Card = back?.let { Card(id, it.face, it.color, CardBack(face, color)) } ?: this

    val isWild: Boolean get() = face.isWild

    /** Same color and same face, ignoring id. */
    fun isIdenticalTo(other: Card) = face == other.face && color == other.color

    override fun toString() = if (color == null) "$face#$id" else "${color}_$face#$id"
}

fun List<Card>.points(): Int = sumOf { it.face.points }

/** What a hand is worth, which in Flip depends on the side the round ended on. */
fun List<Card>.pointsIn(mode: GameMode): Int = sumOf { it.face.pointsIn(mode) }

object Deck {
    const val SIZE = 108
    const val NO_MERCY_SIZE = 168
    const val FLIP_SIZE = 112

    private val CLASSIC_ACTIONS = listOf(Face.SKIP, Face.REVERSE, Face.DRAW_TWO)
    private val CLASSIC_WILDS = listOf(Face.WILD, Face.WILD_DRAW_FOUR)

    private val NO_MERCY_ACTIONS = CLASSIC_ACTIONS + listOf(Face.DRAW_FOUR, Face.SKIP_EVERYONE, Face.DISCARD_ALL)
    private val NO_MERCY_WILDS = listOf(
        Face.WILD, Face.WILD_REVERSE_DRAW_FOUR, Face.WILD_DRAW_SIX, Face.WILD_DRAW_TEN, Face.WILD_COLOR_ROULETTE,
    )

    /**
     * A Flip card is printed on both sides. Which dark face backs which light one isn't a thing the
     * rulebook fixes, so [rng] pairs them fresh each round: the side your opponents can see tells
     * them something real, but never exactly what you're holding.
     */
    fun flip(rng: Rng): List<Card> {
        val dark = darkFaces().toMutableList()
        rng.shuffle(dark)
        return lightFaces().mapIndexed { i, light ->
            Card(i, light.face, light.color, CardBack(dark[i].face, dark[i].color))
        }
    }

    /** The deck a game is played with: the mode's own, plus the one card a house rule adds. */
    fun forRules(rules: RuleSet, rng: Rng): List<Card> = when (rules.mode) {
        GameMode.CLASSIC -> standard() + if (rules.swapHands) listOf(Card(SIZE, Face.WILD_SWAP_HANDS)) else emptyList()
        GameMode.NO_MERCY -> noMercy()
        GameMode.FLIP -> flip(rng)
    }

    /** How many cards that deck holds, without building it. */
    fun sizeFor(rules: RuleSet): Int = when (rules.mode) {
        GameMode.NO_MERCY -> NO_MERCY_SIZE
        GameMode.FLIP -> FLIP_SIZE
        GameMode.CLASSIC -> if (rules.swapHands) SIZE + 1 else SIZE
    }

    /** 72 numbers 1-9, two of each of the four light actions, four Wilds and four Wild Draw Twos. */
    private fun lightFaces(): List<CardBack> =
        flipSide(CardColor.light, listOf(Face.DRAW_ONE, Face.REVERSE, Face.SKIP, Face.FLIP), listOf(Face.WILD, Face.WILD_DRAW_TWO))

    /** The same shape in pink, teal, orange and purple, with Draw Five, Skip Everyone and Wild Draw Color. */
    private fun darkFaces(): List<CardBack> =
        flipSide(CardColor.dark, listOf(Face.DRAW_FIVE, Face.REVERSE, Face.SKIP_EVERYONE, Face.FLIP), listOf(Face.WILD, Face.WILD_DRAW_COLOR))

    private fun flipSide(colors: List<CardColor>, actions: List<Face>, wilds: List<Face>): List<CardBack> = buildList {
        for (color in colors) {
            // No zero on either side of a Flip deck: two each of 1 to 9.
            for (face in Face.entries) {
                if (face.number != null && face != Face.ZERO) repeat(2) { add(CardBack(face, color)) }
            }
            for (face in actions) repeat(2) { add(CardBack(face, color)) }
        }
        for (face in wilds) repeat(4) { add(CardBack(face)) }
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
        for (color in CardColor.light) {
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
