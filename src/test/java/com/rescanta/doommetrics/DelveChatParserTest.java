package com.rescanta.doommetrics;

import java.time.Duration;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;

public class DelveChatParserTest
{
	private static Optional<DelveChatParser.DelveBoundary> parse(String raw)
	{
		return DelveChatParser.parse(raw);
	}

	@Test
	public void openSimple()
	{
		Optional<DelveChatParser.DelveBoundary> b = parse("@mes_hl_red@Delve level: 3</col>");
		Assert.assertTrue(b.isPresent());
		Assert.assertEquals(3, b.get().getLevel());
		Assert.assertFalse(b.get().isClear());
	}

	@Test
	public void openBracketWins()
	{
		Optional<DelveChatParser.DelveBoundary> b = parse("@mes_hl_red@Delve level: 8+ (15)</col>");
		Assert.assertTrue(b.isPresent());
		Assert.assertEquals(15, b.get().getLevel());
		Assert.assertFalse(b.get().isClear());
	}

	@Test
	public void openDelve8HasNoBrackets()
	{
		Optional<DelveChatParser.DelveBoundary> b = parse("@mes_hl_red@Delve level: 8</col>");
		Assert.assertTrue(b.isPresent());
		Assert.assertEquals(8, b.get().getLevel());
	}

	@Test
	public void clearWithPersonalBest()
	{
		Optional<DelveChatParser.DelveBoundary> b = parse(
			"Delve level: 3 duration: @mes_hl_red@1:05.40</col>. Personal best: @mes_hl_red@0:37.80</col>");
		Assert.assertTrue(b.isPresent());
		Assert.assertEquals(3, b.get().getLevel());
		Assert.assertTrue(b.get().isClear());
		Assert.assertEquals(Duration.ofMillis(65_400), b.get().getFight());
	}

	@Test
	public void clearBracketWithPersonalBest()
	{
		Optional<DelveChatParser.DelveBoundary> b = parse(
			"Delve level: 8+ (15) duration: @mes_hl_red@1:30.60</col>. Personal best: @mes_hl_red@0:48.60</col>");
		Assert.assertTrue(b.isPresent());
		Assert.assertEquals(15, b.get().getLevel());
		Assert.assertEquals(Duration.ofMillis(90_600), b.get().getFight());
	}

	@Test
	public void clearNewPersonalBestVariant()
	{
		Optional<DelveChatParser.DelveBoundary> b = parse(
			"Delve level: 2 duration: @mes_hl_red@0:23.40</col> (new personal best)");
		Assert.assertTrue(b.isPresent());
		Assert.assertEquals(2, b.get().getLevel());
		Assert.assertEquals(Duration.ofMillis(23_400), b.get().getFight());
	}

	@Test
	public void milestoneSummaryRejected()
	{
		Assert.assertFalse(parse(
			"Delve level 1 - 8 duration: @mes_hl_red@9:35.40</col>. Personal best: @mes_hl_red@6:45.00</col>").isPresent());
	}

	@Test
	public void totalDurationRejected()
	{
		Assert.assertFalse(parse("Total duration: @mes_hl_red@0:38.40</col>").isPresent());
	}

	@Test
	public void lifetimeChatterRejected()
	{
		Assert.assertFalse(parse("Deep delves completed: @mes_hl_red@6,694</col>").isPresent());
	}

	@Test
	public void ownEchoRejected()
	{
		Assert.assertFalse(parse("[Doom] Delve 20 in 1:30.0 | 28:00 elapsed | 27.9/hr").isPresent());
	}

	@Test
	public void nullAndEmptyRejected()
	{
		Assert.assertFalse(parse(null).isPresent());
		Assert.assertFalse(parse("").isPresent());
	}

	@Test
	public void hourFormDuration()
	{
		Optional<DelveChatParser.DelveBoundary> b = parse(
			"Delve level: 8+ (42) duration: @mes_hl_red@1:02:03.40</col>. Personal best: @mes_hl_red@0:48.60</col>");
		Assert.assertTrue(b.isPresent());
		Assert.assertEquals(42, b.get().getLevel());
		Assert.assertEquals(Duration.ofMillis(3_723_400), b.get().getFight());
	}

	@Test
	public void fixtureLines()
	{
		// Verbatim game strings from src/test/resources/doom-log-sample.txt must parse.
		Assert.assertEquals(1, parse("@mes_hl_red@Delve level: 1</col>").get().getLevel());
		Assert.assertEquals(Duration.ofMillis(38_400), parse(
			"Delve level: 1 duration: @mes_hl_red@0:38.40</col>. Personal best: @mes_hl_red@0:19.80</col>")
			.get().getFight());
		Assert.assertFalse(parse("Total duration: @mes_hl_red@0:38.40</col>").isPresent());
	}

	@Test
	public void configGroupIsSpecific()
	{
		Assert.assertEquals("doom-of-mokhaiotl-metrics", DoomMetricsConfig.GROUP);
		Assert.assertEquals("doom-of-mokhaiotl-metrics",
			DoomMetricsConfig.class.getAnnotation(net.runelite.client.config.ConfigGroup.class).value());
	}
}
