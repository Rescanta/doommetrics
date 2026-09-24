package com.rescanta.doommetrics;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MilestoneTablePanelTest
{
	@Test
	public void aShallowTableIsShownWhole()
	{
		assertEquals(delves(10, 20, 30, 40, 50),
			shown(rows(50, 0)));
	}

	@Test
	public void pastFiftyOnlyEveryFiftyAndTheDeepestAreKept()
	{
		assertEquals(delves(10, 20, 30, 40, 50, 100, 150, 170),
			shown(rows(170, 0)));
	}

	@Test
	public void theTargetsRowIsAlwaysKept()
	{
		assertEquals(delves(10, 20, 30, 40, 50, 100, 120, 150, 170),
			shown(rows(170, 120)));
	}

	/** Every milestone to {@code deepest}, the one at {@code target} marked. */
	private static List<MilestoneTablePanel.Row> rows(int deepest, int target)
	{
		List<MilestoneTablePanel.Row> rows = new ArrayList<>();

		for (int delve = MilestoneTable.INTERVAL; delve <= deepest; delve += MilestoneTable.INTERVAL)
		{
			rows.add(new MilestoneTablePanel.Row(delve, 1, 0, false, delve == target));
		}

		return rows;
	}

	private static List<Integer> shown(List<MilestoneTablePanel.Row> rows)
	{
		List<Integer> delves = new ArrayList<>();

		for (MilestoneTablePanel.Row row : MilestoneTablePanel.shortened(rows))
		{
			delves.add(row.delve);
		}

		return delves;
	}

	private static List<Integer> delves(Integer... delves)
	{
		return java.util.Arrays.asList(delves);
	}
}
