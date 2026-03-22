package com.explorerMode;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class ExplorationPanel extends PluginPanel
{
    private final ExplorationRegionManager regionManager;
    private final ExplorerModePlugin plugin;
    private final JPanel listContainer = new JPanel();
    private JLabel worldPctLabel;
    private JLabel modeLabel;


    @Inject
    public ExplorationPanel(ExplorationRegionManager regionManager, ExplorerModePlugin plugin)
    {
        super(false);
        this.regionManager = regionManager;
        this.plugin = plugin;

        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.setOpaque(false);

        // Total world discovery
        JLabel title = new JLabel("World Exploration Progress");
        title.setForeground(Color.WHITE);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        title.setBorder(new EmptyBorder(0, 0, 8, 0));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(title);

        JPanel worldPanel = new JPanel(new BorderLayout());
        worldPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        worldPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        worldPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        worldPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel worldLabel = new JLabel("Gielinor discovered");
        worldLabel.setForeground(Color.GRAY);
        worldLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        worldPctLabel = new JLabel("0.0%");
        worldPctLabel.setForeground(Color.WHITE);
        worldPctLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

        worldPanel.add(worldLabel, BorderLayout.WEST);
        worldPanel.add(worldPctLabel, BorderLayout.EAST);
        content.add(worldPanel);
        content.add(Box.createRigidArea(new Dimension(0, 4)));

        // Mode
        JPanel modePanel = new JPanel(new BorderLayout());
        modePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        modePanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        modePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel modeTitleLabel = new JLabel("Current mode");
        modeTitleLabel.setForeground(Color.GRAY);
        modeTitleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        modeLabel = new JLabel("-----");    //Blank default, will update each game tick to current
        modeLabel.setForeground(new Color(100, 180, 100));
        modeLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));

        modePanel.add(modeTitleLabel, BorderLayout.WEST);
        modePanel.add(modeLabel, BorderLayout.EAST);
        content.add(modePanel);
        content.add(Box.createRigidArea(new Dimension(0, 10)));

        // Kingdoms list
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);
        listContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(listContainer);

        content.add(Box.createRigidArea(new Dimension(0, 10)));

        // Placeholder for NPC discovery later
        JPanel zone2 = new JPanel(new BorderLayout());
        zone2.setBorder(new EmptyBorder(10, 0, 0, 0));
        zone2.setAlignmentX(Component.LEFT_ALIGNMENT);
        zone2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel zone2Label = new JLabel("Cartographers Discovered");
        zone2Label.setForeground(Color.GRAY);
        zone2Label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        zone2.add(zone2Label, BorderLayout.NORTH);
        zone2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        content.add(zone2);

        // Wrap everything in a scroll pane
        JScrollPane outerScroll = new JScrollPane(content);
        outerScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        outerScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        outerScroll.setBorder(null);
        outerScroll.setOpaque(false);
        outerScroll.getViewport().setOpaque(false);

        add(outerScroll, BorderLayout.CENTER);
    }

    public void rebuild()
    {
        listContainer.removeAll();

        // Update world percentage
        double worldPct = regionManager.getAllRegions().stream()
                .filter(r -> r.getType() == ExplorationRegion.RegionType.KINGDOM)
                .mapToDouble(regionManager::getExplorationPercentage)
                .average()
                .orElse(0.0);
        worldPctLabel.setText(String.format("%.1f%%", worldPct));

        // Fill with the kingdoms, other than 'world'
        regionManager.getAllRegions().stream()
                .filter(r -> r.getType() == ExplorationRegion.RegionType.KINGDOM)
                .filter(r -> !r.getId().equals("world_fog"))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .forEach(kingdom ->
                {
                    listContainer.add(new KingdomPanel(kingdom, regionManager));
                    listContainer.add(Box.createRigidArea(new Dimension(0, 5)));
                });

        listContainer.revalidate();
        listContainer.repaint();
    }

    public void refreshHeader()
    {
        double worldPct = regionManager.getAllRegions().stream()
                .filter(r -> r.getType() == ExplorationRegion.RegionType.KINGDOM)
                .filter(r -> !r.getId().equals("world_fog"))
                .mapToDouble(regionManager::getExplorationPercentage)
                .average()
                .orElse(0.0);
        worldPctLabel.setText(String.format("%.1f%%", worldPct));

        MapMode mode = plugin.getCurrentMode();
        modeLabel.setText(mode.name().charAt(0) + mode.name().substring(1).toLowerCase());
    }
}