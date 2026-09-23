package com.rescanta.doommetrics;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Two or more ways of reading a card, as a pill of segments with the one in use filled. Sits on a
 * card's title line. Swing thread only.
 */
class SegmentedControl extends RoundedPanel
{
	/** The segment in use. */
	private static final Color SELECTED = new Color(62, 62, 62);

	private final RoundedPanel[] segments;
	private final JLabel[] labels;
	private final IntConsumer onSelect;
	private int selected;

	/**
	 * @param onSelect told the index picked, whenever a click or {@link #select} changes it
	 * @param tooltips one per option, or null entries for none
	 */
	SegmentedControl(String[] options, String[] tooltips, IntConsumer onSelect)
	{
		super(PanelStyle.TILE, PanelStyle.ARC);
		this.onSelect = onSelect;
		setLayout(new GridLayout(1, options.length, 0, 0));
		setBorder(new EmptyBorder(2, 2, 2, 2));

		segments = new RoundedPanel[options.length];
		labels = new JLabel[options.length];

		for (int i = 0; i < options.length; i++)
		{
			int index = i;
			JLabel label = PanelStyle.label(options[i], SwingConstants.CENTER,
				FontManager.getRunescapeSmallFont(), ColorScheme.LIGHT_GRAY_COLOR);
			label.setBorder(new EmptyBorder(1, 6, 1, 6));

			RoundedPanel segment = new RoundedPanel(null, PanelStyle.ARC - 2);
			segment.setLayout(new GridLayout(1, 1));
			segment.add(label);
			segment.setToolTipText(tooltips == null ? null : tooltips[i]);
			segment.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			segment.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent event)
				{
					select(index);
				}
			});

			segments[i] = segment;
			labels[i] = label;
			add(segment);
		}

		paintSelection();
	}

	/** Puts an option in use, telling the listener only if that is a change. */
	void select(int index)
	{
		if (index == selected)
		{
			return;
		}

		selected = index;
		paintSelection();
		onSelect.accept(index);
	}

	private void paintSelection()
	{
		for (int i = 0; i < segments.length; i++)
		{
			segments[i].setFill(i == selected ? SELECTED : null);
			labels[i].setForeground(i == selected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		}
	}
}
