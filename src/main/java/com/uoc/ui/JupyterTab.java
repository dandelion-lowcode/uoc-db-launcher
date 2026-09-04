package com.uoc.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.uoc.i18n.Message;
import com.uoc.i18n.Translations;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

public class JupyterTab {

    private static final String ICON = "icons/jupyter.svg";
    private static final int ICON_SIZE = 32;

    private final JPanel panel = new JPanel(new BorderLayout(5, 5));
    private JButton openButton;

    public JupyterTab(Runnable onOpen, Translations translations) {
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        JLabel description = new JLabel("Jupyter notebooks");
        description.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        openButton = new JButton("Open Jupyter");
        openButton.setPreferredSize(new Dimension(180, 38));
        openButton.setIcon(new FlatSVGIcon(ICON, 16, 16));
        openButton.addActionListener(e -> onOpen.run());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(openButton);
        panel.add(description, BorderLayout.NORTH);
        panel.add(actions, BorderLayout.CENTER);
        translations.register(() -> {
            description.setText(translations.get(Message.LABEL_JUPYTER));
            openButton.setText(translations.get(Message.BUTTON_OPEN_JUPYTER));
        });
    }

    public JPanel getPanel() {
        return panel;
    }

    public JButton getOpenButton() {
        return openButton;
    }

    public FlatSVGIcon icon() {
        return new FlatSVGIcon(ICON, ICON_SIZE, ICON_SIZE);
    }
}
