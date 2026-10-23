package dev.colorclash.rules

import dev.colorclash.rules.Action.CallLastCard
import dev.colorclash.rules.Action.CatchLastCard
import dev.colorclash.rules.Action.ChooseColor
import dev.colorclash.rules.Action.Draw
import dev.colorclash.rules.Action.PlayCard
import dev.colorclash.rules.Face.DRAW_TWO
import dev.colorclash.rules.Face.FIVE
import dev.colorclash.rules.Face.NINE
import dev.colorclash.rules.Face.ONE
import dev.colorclash.rules.Face.SKIP
import dev.colorclash.rules.Face.THREE
import dev.colorclash.rules.Face.TWO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BotTest {
    private val t = Table()
    private val bot = SimpleBot(Random(1))
    private fun many() = listOf(t.green(ONE), t.green(TWO), t.yellow(THREE), t.yellow(ONE))

    @Test
    fun `picks the color it holds most`() {
        val wild = t.wild()
        val s = t.state(listOf(wild, t.blue(ONE), t.blue(TWO), t.red(ONE)), many(), top = t.red(NINE))
        val picking = s.act(PlayCard(0, wild.id)).state
        assertEquals(ChooseColor(0, CardColor.BLUE), bot.chooseAction(picking, 0))
    }

    @Test
    fun `dumps number cards when nobody is close to winning`() {
        val r5 = t.red(FIVE)
        val s = t.state(listOf(t.red(SKIP), r5, t.wild(), t.blue(ONE)), many(), top = t.red(NINE))
        assertEquals(PlayCard(0, r5.id), bot.chooseAction(s, 0))
    }

    @Test
    fun `attacks when an opponent is low`() {
        val d2 = t.red(DRAW_TWO)
        val s = t.state(listOf(t.red(SKIP), t.red(FIVE), d2, t.blue(ONE)), listOf(t.green(ONE), t.green(TWO)), top = t.red(NINE))
        assertEquals(PlayCard(0, d2.id), bot.chooseAction(s, 0))
    }

    @Test
    fun `won't bluff a plus four while holding the active color`() {
        val r5 = t.red(FIVE)
        val s = t.state(listOf(t.wild4(), r5, t.blue(ONE)), listOf(t.green(ONE), t.green(TWO)), top = t.red(NINE))
        assertEquals(PlayCard(0, r5.id), bot.chooseAction(s, 0))
    }

    @Test
    fun `draws when nothing is playable`() {
        val s = t.state(listOf(t.blue(ONE), t.green(TWO), t.yellow(THREE)), many(), top = t.red(NINE))
        assertEquals(Draw(0), bot.chooseAction(s, 0))
    }

    @Test
    fun `calls last card at two cards`() {
        val s = t.state(listOf(t.red(FIVE), t.blue(ONE)), many(), top = t.red(NINE))
        assertEquals(CallLastCard(0), bot.chooseAction(s, 0))
    }

    @Test
    fun `catches an exposed opponent`() {
        val r5 = t.red(FIVE)
        val s = t.state(listOf(r5, t.blue(ONE)), many(), top = t.red(NINE))
        val exposed = s.act(PlayCard(0, r5.id)).state
        assertEquals(CatchLastCard(1, 0), bot.chooseAction(exposed, 1))
    }

    @Test
    fun `does nothing when it isn't its turn`() {
        val s = t.state(many(), many(), top = t.red(NINE))
        assertNull(bot.chooseAction(s, 1))
    }
}
