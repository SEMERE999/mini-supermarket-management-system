package com.minisupermarket;

import com.minisupermarket.ui.DesktopApplication;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DesktopApplication().show());
    }
}