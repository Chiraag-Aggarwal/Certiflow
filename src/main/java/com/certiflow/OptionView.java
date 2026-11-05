package com.certiflow;

import javafx.scene.layout.VBox;

/** The application entry point uses one sign-in form for every account. */
public class OptionView {
    private final VBox view = new LoginView().getView();

    public VBox getView() { return view; }
}
