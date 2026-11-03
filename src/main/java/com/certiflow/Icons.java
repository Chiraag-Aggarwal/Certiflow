package com.certiflow;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;

/** Small vector symbols stay sharp at every display scale. */
final class Icons {
    private Icons() {}
    static Node of(String name) {
        String path = switch (name) {
            case "home" -> "M3 10 L12 3 L21 10 M5 9 V21 H10 V14 H14 V21 H19 V9";
            case "portfolios", "students" -> "M3 5 Q7 3 12 6 Q17 3 21 5 V20 Q17 18 12 21 Q7 18 3 20 Z M12 6 V21";
            case "noc", "requests" -> "M6 3 H15 L20 8 V21 H6 Z M15 3 V8 H20 M9 12 H17 M9 16 H15";
            case "history" -> "M4 8 A9 9 0 1 1 3 14 M3 3 V9 H9 M12 7 V12 L16 14";
            case "profile" -> "M16 7 A4 4 0 1 1 8 7 A4 4 0 1 1 16 7 M4 21 V19 A8 8 0 0 1 20 19 V21";
            case "export" -> "M12 3 V15 M7 10 L12 15 L17 10 M4 16 V21 H20 V16";
            case "menu" -> "M4 6 H20 M4 12 H20 M4 18 H20";
            case "settings" -> "M3 6 H21 M3 12 H21 M3 18 H21 M8 3 V9 M16 9 V15 M10 15 V21";
            case "back" -> "M14 5 L7 12 L14 19 M7 12 H21";
            case "close" -> "M6 6 L18 18 M18 6 L6 18";
            default -> "M3 9 L12 4 L21 9 L12 14 Z M6 11 V17 Q12 22 18 17 V11 M21 9 V17";
        };
        SVGPath icon = new SVGPath();
        icon.setContent(path);
        icon.getStyleClass().add("line-icon");
        icon.setMouseTransparent(true);
        StackPane box = new StackPane(icon);
        box.setMinSize(24, 24);
        box.setPrefSize(24, 24);
        box.setMaxSize(24, 24);
        box.setMouseTransparent(true);
        return box;
    }
}
