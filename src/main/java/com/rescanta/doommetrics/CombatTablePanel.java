package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A {@link CombatTotals} laid out as a table: what healed you, what restored your prayer and what
 * your specs hit for, under the heading each belongs to.
 *
 * <p>A component in its own right for the same reason {@link MilestoneTablePanel} is one - the side
 * panel draws the sitting's figures with it and the history window draws the character's lifetime
 * figures with it, and two tables built separately would drift apart.
 *
 * <p>Every row is built once and only ever retexted. The rows never change: eight metrics under
 * four headings, whether or not any of them has fired. A metric that has counted nothing reads zero
 * in the muted colour rather than vanishing, so the table does not reflow as a run goes on and so
 * the reader can see that a source they expected to fire has not.
 *
 * <p>Swing thread only.
 */
class CombatTablePanel extends JPanel
{
	private static final Color STRIPE = new Color(36, 36, 36);

	private static final Border CELL_PADDING = new EmptyBorder(2, 4, 2, 4);
	private static final Border HEADING_PADDING = BorderFactory.createCompoundBorder(
		BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
		CELL_PADDING);

	/** The value cell for each metric, in declaration order, so an update is eight setTexts. */
	private final JLabel[] values = new JLabel[CombatMetric.values().length];

	CombatTablePanel()
	{
		super(new GridBagLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		build();
		setTotals(null);
	}

	private void build()
	{
		CombatMetric.Group heading = null;
		int gridy = 0;
		int striped = 0;

		for (CombatMetric metric : CombatMetric.values())
		{
			if (metric.group() != heading)
			{
				heading = metric.group();
				addHeading(gridy++, heading);

				// Restarted under each heading so the stripes read as a block per group rather
				// than as one run of alternating rows the headings happen to interrupt.
				striped = 0;
			}

			JLabel label = label(metric.label(), SwingConstants.LEFT,
				FontManager.getRunescapeSmallFont());
			JLabel value = label("0", SwingConstants.RIGHT, FontManager.getRunescapeSmallFont());
			values[metric.ordinal()] = value;

			addRow(gridy++, striped++ % 2 == 0 ? ColorScheme.DARKER_GRAY_COLOR : STRIPE,
				CELL_PADDING, label, value);
		}
	}

	/** Repaints every figure. A null tally reads as all zeroes, which is what it means. */
	void setTotals(CombatTotals totals)
	{
		for (CombatMetric metric : CombatMetric.values())
		{
			long amount = totals == null ? 0 : totals.get(metric);
			JLabel value = values[metric.ordinal()];
			value.setText(DoomFormat.count(amount));
			// Coloured by what it counts, matching the overlay's lines, so the two read as the
			// same figures. A zero stays muted - see CombatMetric.Unit#color.
			value.setForeground(amount > 0
				? metric.unit().color()
				: ColorScheme.LIGHT_GRAY_COLOR);
			value.setToolTipText(amount > 0
				? DoomFormat.count(amount) + " " + metric.unit().description()
				: "Nothing counted yet");
		}
	}

	private void addHeading(int gridy, CombatMetric.Group group)
	{
		JLabel heading = label(group.heading(), SwingConstants.LEFT,
			FontManager.getRunescapeBoldFont());
		// The heading takes its group's colour so a block of rows can be found by colour alone,
		// and so the two healing headings read as one thing measured two ways.
		heading.setForeground(group.unit().color());

		JLabel filler = label("", SwingConstants.RIGHT, FontManager.getRunescapeBoldFont());

		addRow(gridy, ColorScheme.DARK_GRAY_COLOR, HEADING_PADDING, heading, filler);
	}

	/**
	 * Adds one row of cells straight into the shared grid, so every row lines its two columns up
	 * with every other row's. The cells carry the stripe themselves rather than sitting on a panel
	 * that paints it, which is what makes one grid possible.
	 */
	private void addRow(int gridy, Color background, Border border, JLabel label, JLabel value)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.BOTH;
		constraints.gridy = gridy;
		constraints.insets = new Insets(gridy == 0 ? 0 : 1, 0, 0, 0);

		label.setOpaque(true);
		label.setBackground(background);
		label.setBorder(border);
		constraints.gridx = 0;
		constraints.weightx = 0.62;
		add(label, constraints);

		value.setOpaque(true);
		value.setBackground(background);
		value.setBorder(border);
		constraints.gridx = 1;
		constraints.weightx = 0.38;
		add(value, constraints);
	}

	private static JLabel label(String text, int alignment, Font font)
	{
		JLabel label = new JLabel(text, alignment);
		label.setFont(font);
		label.setForeground(ColorScheme.TEXT_COLOR);
		return label;
	}
}
