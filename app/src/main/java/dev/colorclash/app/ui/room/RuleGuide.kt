package dev.colorclash.app.ui.room

import dev.colorclash.protocol.GameSettings
import dev.colorclash.rules.Card
import dev.colorclash.rules.CardColor
import dev.colorclash.rules.Face

/** A heading and a sentence or two under it. */
data class GuideItem(val title: String, val text: String)

/** A card worth explaining, shown as the card itself next to what it does. */
data class GuideCard(val card: Card, val name: String, val text: String)

/** Everything the rules popup says, worked out from the room's settings so it only covers what's in play. */
data class RuleGuide(
    val mode: String,
    val goal: String,
    /** Short names of the rules switched on, shown as tags at the top. */
    val onRules: List<String>,
    val basics: List<GuideItem>,
    val cards: List<GuideCard>,
    val special: List<GuideItem>,
    val winning: List<GuideItem>,
)

fun ruleGuide(settings: GameSettings): RuleGuide {
    val rules = settings.rules
    val noMercy = rules.noMercy

    val onRules = buildList {
        if (rules.stacking) add("Stack draws")
        if (rules.jumpIn) add("Jump in")
        if (rules.swaps) add("7-0")
        if (rules.drawsUntilPlayable) add("Draw till playable")
        if (rules.canChallenge) add("Challenge +4")
        if (rules.powerFinish > 0) add("No power finish")
        if (noMercy) add("Mercy rule")
        settings.turnTimerSeconds?.let { add("${it}s turns") }
    }

    val basics = buildList {
        add(GuideItem("Match the top card", "Play a card with the same color, number or symbol as the top of the pile. Wild cards go on anything."))
        add(
            when {
                noMercy -> GuideItem("Can't play? Keep drawing", "Draw until you get a card that fits, then you have to play it.")
                rules.drawsUntilPlayable -> GuideItem("Can't play? Keep drawing", "Draw until you get a card that fits. Play it straight away or keep it.")
                else -> GuideItem("Can't play? Draw one", "Take a card from the deck. If it fits you can play it straight away, or keep it.")
            },
        )
        if (rules.lastCardPenalty > 0) {
            add(
                GuideItem(
                    "Call UNO",
                    "Down to two cards? Tap UNO before you play your second-to-last card. " +
                        "Forget, and anyone can catch you before the next turn: you draw ${rules.lastCardPenalty}.",
                ),
            )
        }
        add(GuideItem("Empty your hand", "First to play their last card wins the round."))
    }

    val drawTail = when {
        noMercy -> ", unless they stack a draw card worth as much or more"
        rules.stacking -> ", unless they stack"
        else -> ""
    }
    val cards = buildList {
        add(GuideCard(Card(-1, Face.SKIP, CardColor.RED), "Skip", "The next player misses their turn."))
        add(GuideCard(Card(-2, Face.REVERSE, CardColor.BLUE), "Reverse", "Play turns around and goes the other way. With two players it works like a Skip."))
        add(GuideCard(Card(-3, Face.DRAW_TWO, CardColor.GREEN), "Draw Two", "The next player draws 2 and misses their turn$drawTail."))
        if (noMercy) {
            add(GuideCard(Card(-4, Face.DRAW_FOUR, CardColor.YELLOW), "Draw Four", "Like a Draw Two, but 4. It has a color, so it's played like any colored card."))
            add(GuideCard(Card(-5, Face.SKIP_EVERYONE, CardColor.RED), "Skip Everyone", "Everyone else misses a turn and you go again straight away."))
            add(GuideCard(Card(-6, Face.DISCARD_ALL, CardColor.BLUE), "Discard All", "Throw down every other card of its color from your hand along with it."))
        }
        add(GuideCard(Card(-7, Face.WILD, null), "Wild", "Goes on anything. You pick the color to play next."))
        if (noMercy) {
            add(
                GuideCard(
                    Card(-8, Face.WILD_REVERSE_DRAW_FOUR, null),
                    "Wild Reverse Draw Four",
                    "Pick the color and turn play around: the next player that way draws 4 and misses their turn. " +
                        "With two players, the 4 come back to whoever played it.",
                ),
            )
            add(GuideCard(Card(-9, Face.WILD_DRAW_SIX, null), "Wild Draw Six", "Pick the color. The next player draws 6 and misses their turn$drawTail."))
            add(GuideCard(Card(-10, Face.WILD_DRAW_TEN, null), "Wild Draw Ten", "Pick the color. The next player draws 10 and misses their turn$drawTail."))
            add(
                GuideCard(
                    Card(-11, Face.WILD_COLOR_ROULETTE, null),
                    "Color Roulette",
                    "The next player names a color, then turns over cards from the deck until that color comes up. " +
                        "They keep every card turned over and miss their turn.",
                ),
            )
        } else {
            add(
                GuideCard(
                    Card(-12, Face.WILD_DRAW_FOUR, null),
                    "Wild Draw Four",
                    "Pick the color. The next player draws 4 and misses their turn$drawTail." +
                        if (rules.canChallenge) " You're only meant to play it with no card of the current color, but you can bluff." else "",
                ),
            )
        }
        if (rules.swaps) {
            add(GuideCard(Card(-13, Face.SEVEN, CardColor.GREEN), "Seven", "Swap your whole hand with any player you choose."))
            add(GuideCard(Card(-14, Face.ZERO, CardColor.YELLOW), "Zero", "Everyone passes their hand to the next player, in the direction of play."))
        }
    }

    val special = buildList {
        if (noMercy) {
            add(
                GuideItem(
                    "Stacking",
                    "Hit by a draw card? Play any draw card worth the same or more, in any color, to pass the whole lot on. " +
                        "Whoever can't stack draws everything.",
                ),
            )
        } else if (rules.stacking) {
            add(
                GuideItem(
                    "Stack draws",
                    "Hit by a +2? Play another +2 to pass it on, and the next player takes the lot. " +
                        "A Wild +4 goes on a +2 or a +4, but a +2 can't go on a +4.",
                ),
            )
        }
        if (rules.jumpIn) {
            add(GuideItem("Jump in", "Holding the exact same card as the top one, same color and same number or symbol? Play it even if it's not your turn. Play carries on from you. Not for wilds."))
        }
        if (rules.canChallenge) {
            add(
                GuideItem(
                    "Challenge a +4",
                    "Hit by a Wild +4? Challenge it instead of drawing. If they had a card of the color in play, " +
                        "they were bluffing and draw the 4 themselves. If not, you draw 6.",
                ),
            )
        }
        if (rules.powerFinish > 0) {
            add(GuideItem("No power finish", "Your last card can't be a Skip, Reverse, +2 or Wild. If it is, the card still works, but you draw ${rules.powerFinish} and play on."))
        }
        if (noMercy) {
            add(GuideItem("Mercy rule", "Reach ${rules.mercyLimit} cards and you're out of the round. You're back in for the next one."))
        }
    }

    val winning = buildList {
        val bonus = if (noMercy) " Plus ${rules.knockoutBonus} for every player knocked out that round." else ""
        add(
            GuideItem(
                "Scoring",
                "The round winner scores the cards left in everyone else's hands: numbers are worth their number, " +
                    "other colored cards 20, wilds 50.$bonus",
            ),
        )
        add(
            if (rules.targetScore == 0) {
                GuideItem("The game", "Just one round: whoever goes out first wins.")
            } else {
                GuideItem("The game", "Rounds keep going until someone reaches ${rules.targetScore} points.")
            },
        )
        settings.turnTimerSeconds?.let {
            add(
                GuideItem(
                    "Turn timer",
                    "You have $it seconds a turn. Run out and you draw a card. Miss $AWAY_AFTER turns in a row and a bot plays for you until you're back.",
                ),
            )
        }
    }

    return RuleGuide(
        mode = if (noMercy) "No Mercy" else "Classic",
        goal = if (noMercy) {
            "Be the first to get rid of all your cards. This is the brutal version: bigger draws, and you're knocked out at ${rules.mercyLimit} cards."
        } else {
            "Be the first to get rid of all your cards."
        },
        onRules = onRules,
        basics = basics,
        cards = cards,
        special = special,
        winning = winning,
    )
}

/** Mirrors the host's rule: this many timed-out turns in a row and a bot takes over. */
private const val AWAY_AFTER = 3
