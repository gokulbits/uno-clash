package dev.colorclash.rules

import kotlin.test.assertIs
import kotlin.test.fail

/** Builds hand-crafted states. Card ids start high so they never clash with a real deck. */
class Table {
    private var nextId = 1000

    fun card(face: Face, color: CardColor? = null) = Card(nextId++, face, color)
    fun red(face: Face) = card(face, CardColor.RED)
    fun blue(face: Face) = card(face, CardColor.BLUE)
    fun green(face: Face) = card(face, CardColor.GREEN)
    fun yellow(face: Face) = card(face, CardColor.YELLOW)
    fun wild() = card(Face.WILD)
    fun wild4() = card(Face.WILD_DRAW_FOUR)

    /** Cards that never match a red or blue top card. */
    fun filler(count: Int) = List(count) { green(Face.EIGHT) }

    fun state(
        vararg hands: List<Card>,
        top: Card,
        drawPile: List<Card> = filler(20),
        rules: RuleSet = RuleSet(),
        current: Int = 0,
        activeColor: CardColor? = top.color,
        direction: Direction = Direction.CLOCKWISE,
        scores: List<Int> = List(hands.size) { 0 },
        discardBelow: List<Card> = emptyList(),
    ) = GameState(
        rules = rules,
        players = hands.mapIndexed { i, h -> Player(h, scores[i]) },
        drawPile = drawPile,
        discardPile = discardBelow + top,
        activeColor = activeColor,
        direction = direction,
        current = current,
        phase = Phase.Turn,
    )
}

fun GameState.act(action: Action): EngineResult.Ok {
    val result = GameEngine.reduce(this, action)
    if (result is EngineResult.Rejected) fail("Expected $action to be accepted but got: ${result.reason}")
    return result as EngineResult.Ok
}

fun GameState.rejects(action: Action) {
    assertIs<EngineResult.Rejected>(GameEngine.reduce(this, action), "Expected $action to be rejected")
}

fun GameState.allCards(): List<Card> = drawPile + discardPile + players.flatMap { it.hand }
