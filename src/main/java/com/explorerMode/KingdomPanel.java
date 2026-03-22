package com.explorerMode;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class KingdomPanel extends JPanel
{
    private final ExplorationRegion kingdom;
    private final ExplorationRegionManager manager;
    private final JPanel subregionContainer;
    private boolean expanded = false;

    public KingdomPanel(ExplorationRegion kingdom, ExplorationRegionManager manager)
    {
        this.kingdom = kingdom;
        this.manager = manager;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR, 1),
                new EmptyBorder(4, 6, 4, 6)
        ));


        boolean discovered = manager.isRegionDiscovered(kingdom);
        double pct = manager.getExplorationPercentage(kingdom);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel arrow = new JLabel("▶ ");
        arrow.setForeground(Color.GRAY);
        arrow.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));

        // Kingdom name — Grey if undiscovered, white if discovered
        JLabel nameLabel = new JLabel(discovered ? kingdom.getName() : kingdom.getName());
        nameLabel.setForeground(discovered ? Color.WHITE : Color.DARK_GRAY);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameRow.setOpaque(false);
        nameRow.add(arrow);
        nameRow.add(nameLabel);

        JLabel pctLabel = new JLabel(discovered ? String.format("%.1f%%", pct) : "???");
        pctLabel.setForeground(Color.GRAY);
        pctLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        header.add(nameRow, BorderLayout.WEST);
        header.add(pctLabel, BorderLayout.EAST);

        add(header);

        // Will hide subregion containers by default
        subregionContainer = new JPanel();
        subregionContainer.setLayout(new BoxLayout(subregionContainer, BoxLayout.Y_AXIS));
        subregionContainer.setOpaque(false);
        subregionContainer.setBorder(new EmptyBorder(4, 12, 0, 0));
        subregionContainer.setVisible(false);

        for (ExplorationRegion sub : kingdom.getChildren())
        {
            if (sub.getType() != ExplorationRegion.RegionType.SUBREGION)
            {
                continue;
            }

            subregionContainer.add(buildSubregionRow(sub, discovered));
            subregionContainer.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        add(subregionContainer);

        // Clicking name will toggle dropdown
        MouseAdapter toggle = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                expanded = !expanded;
                subregionContainer.setVisible(expanded);
                arrow.setText(expanded ? "▼ " : "▶ ");

                Container parent = KingdomPanel.this.getParent();
                while (parent != null)
                {
                    parent.revalidate();
                    parent.repaint();
                    parent = parent.getParent();
                }
            }
        };
        header.addMouseListener(toggle);
        arrow.addMouseListener(toggle);
        nameLabel.addMouseListener(toggle);
    }

    private JPanel buildSubregionRow(ExplorationRegion sub, boolean kingdomDiscovered)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);


        boolean subDiscovered = manager.isRegionDiscovered(sub);
        double subPct = manager.getExplorationPercentage(sub);

        String subName;
        Color subColor;

        if (!kingdomDiscovered)
        {
            // If the kingdom hasn't been discovered yet, then hide subregion names
            subName = "???????????";
            subColor = new Color(99,0,39);
        }
        else if (!subDiscovered)
        {
            // Kingdom discovered but subregion not yet visited
            subName = sub.getName();
            subColor = Color.DARK_GRAY;
        }
        else if(subPct >= 100.0)
        {
            subName = sub.getName();
            subColor = new Color(20,213,0);
        }
        else
        {
            // Subregion visited but not fully mapped
            subName = sub.getName();
            subColor = Color.lightGray;
        }

        JLabel nameLabel = new JLabel(subName);
        nameLabel.setForeground(subColor);
        nameLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        JLabel pctLabel = new JLabel(subDiscovered ? String.format("%.1f%%", subPct) : "");
        pctLabel.setForeground(Color.DARK_GRAY);
        pctLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));

        row.add(nameLabel, BorderLayout.WEST);
        row.add(pctLabel, BorderLayout.EAST);

        return row;
    }
    @Override
    public Dimension getMaximumSize()
    {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}