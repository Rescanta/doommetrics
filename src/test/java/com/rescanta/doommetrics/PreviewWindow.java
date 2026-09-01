package com.rescanta.doommetrics;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;

/**
 * The plugin's interfaces, on screen, with no game underneath them.
 *
 * <p>Run it with {@code gradlew preview}. The overlay is on the left over a backdrop you can
 * change, the side panel is on the right at the width RuneLite gives it, and the history window
 * opens from a button. Every option that changes how they look is a control down the side, so
 * something you would otherwise log in, walk to the Doom and delve twenty times to see is a
 * checkbox away instead.
 *
 * <p>These are the plugin's real widgets, drawn by their own code through RuneLite's own look and
 * feel - not mock-ups of them. All that is faked is the numbers they are handed, which is what
 * {@link PreviewScene} is for.
 */
public class PreviewWindow
{
	public static void main(String[] args)
	{
		PreviewRender.requireDisplay();

		// The client installs this before it builds a single widget, and the buttons, scrollbars
		// and dropdowns here are all drawn by it.
		RuneLiteLAF.setup();

		SwingUtilities.invokeLater(() -> new PreviewWindow().open());
	}

	private final List<PreviewScene> scenes = PreviewScene.all();

	/** The one config every widget here reads: loaded from a scene, then edited by hand. */
	private final PreviewConfig config = new PreviewConfig();

	private final PreviewPlugin plugin = new PreviewPlugin();
	private final DoomMetricsOverlay overlay = new DoomMetricsOverlay(plugin, config);
	private final DoomMetricsPanel panel = new DoomMetricsPanel(this::openHistory);

	/** The same square the plugin puts up, reading the same config the overlay beside it does. */
	private final DoomMetricsInfoBox infoBox =
		new DoomMetricsInfoBox(PreviewRender.icon(), plugin, config);

	private final JComboBox<PreviewScene> scenePicker = new JComboBox<>(
		new DefaultComboBoxModel<>(scenes.toArray(new PreviewScene[0])));
	private final JComboBox<DisplayStyle> stylePicker = new JComboBox<>(DisplayStyle.values());
	private final JComboBox<InfoBoxFigure> figurePicker = new JComboBox<>(InfoBoxFigure.values());
	private final JComboBox<PreviewRender.Backdrop> backdropPicker =
		new JComboBox<>(PreviewRender.Backdrop.values());
	private final JComboBox<Integer> zoomPicker = new JComboBox<>(new Integer[]{1, 2, 3});
	private final JComboBox<PaceMode> pacePicker = new JComboBox<>(PaceMode.values());
	private final JComboBox<MetricDisplay> groupingPicker = new JComboBox<>(MetricDisplay.values());

	/** Puts every control back to what the config says, for when a scene changes it underneath. */
	private final List<Runnable> restaters = new ArrayList<>();

	private final JLabel note = new JLabel();
	private final OverlayCanvas canvas = new OverlayCanvas();

	private PreviewScene scene;
	private HistoryWindow history;

	private void open()
	{
		JFrame frame = new JFrame("Doom Metrics - interface preview");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setContentPane(content());
		frame.setSize(1180, 780);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		load(scenes.get(0));

		// The run clock and the pace move on their own in the client, so they move here too: a
		// timer that only looks right the instant it was drawn is not one worth judging.
		new Timer(500, event -> refresh()).start();
	}

	private JPanel content()
	{
		JPanel root = new JPanel(new BorderLayout(10, 0));
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);
		root.setBorder(new EmptyBorder(10, 10, 10, 10));
		root.add(controls(), BorderLayout.WEST);
		root.add(canvas, BorderLayout.CENTER);
		root.add(sidePanel(), BorderLayout.EAST);
		return root;
	}

	/** The side panel as RuneLite hangs it: fixed width, scrolled, nothing else beside it. */
	private JComponent sidePanel()
	{
		JScrollPane scroller = new JScrollPane(panel,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroller.getVerticalScrollBar().setUnitIncrement(16);
		scroller.setPreferredSize(new Dimension(
			PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH + 10, 0));
		return titled("Side panel", scroller);
	}

	private JComponent controls()
	{
		scenePicker.addActionListener(event -> load((PreviewScene) scenePicker.getSelectedItem()));

		zoomPicker.setSelectedItem(2);
		zoomPicker.addActionListener(event -> canvas.repaint());
		backdropPicker.addActionListener(event -> canvas.repaint());

		stylePicker.addActionListener(event ->
		{
			config.displayStyle = (DisplayStyle) stylePicker.getSelectedItem();
			refresh();
		});
		restaters.add(() -> stylePicker.setSelectedItem(config.displayStyle));

		figurePicker.addActionListener(event ->
		{
			config.infoboxFigure = (InfoBoxFigure) figurePicker.getSelectedItem();
			refresh();
		});
		restaters.add(() -> figurePicker.setSelectedItem(config.infoboxFigure));

		pacePicker.addActionListener(event ->
		{
			config.paceMode = (PaceMode) pacePicker.getSelectedItem();
			refresh();
		});
		restaters.add(() -> pacePicker.setSelectedItem(config.paceMode));

		groupingPicker.addActionListener(event ->
		{
			config.grouping = (MetricDisplay) groupingPicker.getSelectedItem();
			refresh();
		});
		restaters.add(() -> groupingPicker.setSelectedItem(config.grouping));

		note.setFont(FontManager.getRunescapeSmallFont());
		note.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		note.setAlignmentX(0f);

		JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setBackground(ColorScheme.DARK_GRAY_COLOR);

		stack.add(labelled("Scene", scenePicker));
		stack.add(note);
		stack.add(Box.createVerticalStrut(10));
		stack.add(labelled("Backdrop", backdropPicker));
		stack.add(labelled("Zoom", zoomPicker));

		stack.add(heading("Display"));
		stack.add(labelled("Style", stylePicker));
		stack.add(labelled("Square", figurePicker));

		stack.add(heading("Overlay rows"));
		stack.add(toggle("Delve number",
			() -> config.showDelveNumber, on -> config.showDelveNumber = on));
		stack.add(toggle("Run timer", () -> config.showRunTimer, on -> config.showRunTimer = on));
		stack.add(toggle("Pace", () -> config.showPace, on -> config.showPace = on));
		stack.add(toggle("Target delve",
			() -> config.showTargetDelve, on -> config.showTargetDelve = on));
		stack.add(labelled("Pace mode", pacePicker));
		stack.add(labelled("Counters", groupingPicker));

		stack.add(heading("Counters shown"));

		for (CombatMetric metric : CombatMetric.values())
		{
			stack.add(toggle(metric.overlayLabel(),
				() -> config.counter(metric), on -> config.counter(metric, on)));
		}

		stack.add(Box.createVerticalStrut(10));
		stack.add(button("Open history window", this::openHistory));
		stack.add(button("Write PNGs", this::writeShots));
		stack.add(Box.createVerticalGlue());

		JScrollPane scroller = new JScrollPane(stack,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroller.getVerticalScrollBar().setUnitIncrement(16);
		scroller.setPreferredSize(new Dimension(240, 0));
		return titled("Controls", scroller);
	}

	/** Loads a scene into the live config and into every widget that draws from it. */
	private void load(PreviewScene loaded)
	{
		if (loaded == null || loaded == scene)
		{
			return;
		}

		scene = loaded;
		config.adopt(loaded.config);
		note.setText("<html><body style='width:210px'>" + loaded.note + "</body></html>");
		panel.setRows(loaded.rows);

		if (history != null)
		{
			openHistory();
		}

		for (Runnable restate : restaters)
		{
			restate.run();
		}

		refresh();
	}

	/**
	 * Redraws everything that moves. Cheap enough to do twice a second: the panel is a handful of
	 * labels and the overlay a dozen lines of text.
	 */
	private void refresh()
	{
		plugin.run = scene.run;
		panel.setLive(scene.live(config));
		panel.setStats(scene.stats);
		panel.setCombat(scene.panelCombat());
		canvas.repaint();
	}

	private void openHistory()
	{
		if (history == null)
		{
			history = new HistoryWindow(null, () -> history = null);
		}

		history.setRows(scene.rows);
		history.setLifetimeCombat(scene.lifetime);
		history.setSeries(scene.series);
		history.open(canvas);
	}

	private void writeShots()
	{
		try
		{
			Path directory = PreviewShots.write(Paths.get("build", "preview"));
			JOptionPane.showMessageDialog(canvas, "Written to " + directory.toAbsolutePath());
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(canvas, "Could not write the images: " + e);
		}
	}

	/** The overlay over a backdrop, in the corner of the screen it is drawn in on the game. */
	private class OverlayCanvas extends JComponent
	{
		@Override
		protected void paintComponent(Graphics graphics)
		{
			PreviewRender.Backdrop backdrop =
				(PreviewRender.Backdrop) backdropPicker.getSelectedItem();
			int zoom = (Integer) zoomPicker.getSelectedItem();

			Graphics2D target = (Graphics2D) graphics;
			target.setColor(backdrop.color);
			target.fillRect(0, 0, getWidth(), getHeight());

			BufferedImage drawn = config.displayStyle == DisplayStyle.INFOBOX
				? PreviewRender.infoBox(infoBox)
				: PreviewRender.overlay(overlay);

			if (drawn == null)
			{
				target.setFont(FontManager.getRunescapeFont());
				target.setColor(ColorScheme.TEXT_COLOR);
				target.drawString(nothing(), 12, 30);
				return;
			}

			target.drawImage(drawn, 10, 10,
				drawn.getWidth() * zoom, drawn.getHeight() * zoom, null);
		}

		/** Why there is nothing on the canvas, which is three different answers. */
		private String nothing()
		{
			switch (config.displayStyle)
			{
				case OFF:
					return "Display is Off, so nothing is drawn over the game";

				case INFOBOX:
					return "No run, so the square is not up";

				default:
					return "No run to draw, so the overlay draws nothing";
			}
		}
	}

	private JCheckBox toggle(String text, BooleanSupplier state, Consumer<Boolean> set)
	{
		JCheckBox box = new JCheckBox(text, state.getAsBoolean());
		box.setBackground(ColorScheme.DARK_GRAY_COLOR);
		box.setForeground(ColorScheme.TEXT_COLOR);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setAlignmentX(0f);
		box.addActionListener(event ->
		{
			set.accept(box.isSelected());
			refresh();
		});

		restaters.add(() -> box.setSelected(state.getAsBoolean()));
		return box;
	}

	private JButton button(String text, Runnable action)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setAlignmentX(0f);
		button.addActionListener(event -> action.run());
		return button;
	}

	private static JPanel labelled(String text, JComponent control)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(0f);
		row.add(label);
		row.add(control);
		return row;
	}

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setBorder(new EmptyBorder(8, 0, 2, 0));
		label.setAlignmentX(0f);
		return label;
	}

	private static JComponent titled(String title, JComponent content)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(heading(title), BorderLayout.NORTH);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
	}
}
