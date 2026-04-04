package com.merstats.vex.ui;

import com.merstats.vex.model.SkillsRanking;
import com.merstats.vex.model.VexTeam;
import com.merstats.vex.service.RobotEventsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Label;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import java.util.LinkedList;
import java.util.List;
import javafx.geometry.Side;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainController {

    // --- 1. Link the UI Elements ---
    @FXML private TextField teamInput;
    @FXML private Button searchButton;
    @FXML private Label dashboardTitle;
    @FXML private Label dashboardSubtitle;


    // The dynamic message that shows when the table is empty
    private Label tablePlaceholder;

    // The floating dropdown menu
    private ContextMenu searchHistoryPopup;

    // The list that remembers your past searches (limited to 5)
    private final List<String> recentSearches = new LinkedList<>();


    @FXML private TableView<SkillsRanking> skillsTable;
    @FXML private TableColumn<SkillsRanking, String> typeCol;
    @FXML private TableColumn<SkillsRanking, Integer> scoreCol;
    @FXML private TableColumn<SkillsRanking, Integer> rankCol;
    @FXML private TableColumn<SkillsRanking, String> seasonCol;
    @FXML private TableColumn<SkillsRanking, String> eventCol;
    @FXML private TableColumn<SkillsRanking, Integer> attemptCol;

    // --- 2. Instantiate the Backend Engine ---
    private final RobotEventsService apiService = new RobotEventsService();

    @FXML
    public void initialize() {
        seasonCol.setCellValueFactory(new PropertyValueFactory<>("seasonName"));
        eventCol.setCellValueFactory(new PropertyValueFactory<>("eventName"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("formattedType"));
        attemptCol.setCellValueFactory(new PropertyValueFactory<>("attempts"));
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));



        // Forces the columns to stretch and perfectly fill the width of the table
        skillsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        setupSearchHistoryPopup();


        tablePlaceholder = new Label("Ready to query. Enter a Team ID above.");
        tablePlaceholder.getStyleClass().add("placeholder-label");
        skillsTable.setPlaceholder(tablePlaceholder);

        searchButton.setOnAction(event -> handleSearch());
    }

    private void handleSearch() {
        String teamNumber = teamInput.getText().trim();

        if (teamNumber.isEmpty()) return;

        // --- THE MEMORY SYSTEM ---
        if (!recentSearches.contains(teamNumber)) {
            recentSearches.add(0, teamNumber);
            if (recentSearches.size() > 5) {
                recentSearches.remove(5);
            }
        }

        // --- 1. Visual Feedback (UI Thread) ---
        searchButton.setDisable(true);
        searchButton.setText("Searching...");
        skillsTable.getItems().clear(); // Clear the table...

        // ...and immediately tell the user what is happening in the empty space
        tablePlaceholder.setText("Fetching telemetry for " + teamNumber + "...");

        // --- 2. The Background Thread ---
        CompletableFuture.supplyAsync(() -> {
            try {
                VexTeam team = apiService.getTeamByNumber(teamNumber);

                if (team != null) {
                    Platform.runLater(() -> {
                        dashboardTitle.setText("Team " + team.getNumber() + " - " + team.getTeam_name());
                        dashboardSubtitle.setText("Grade Level: " + team.getGrade());
                    });
                    return apiService.getSkillsByTeamId(team.getId());

                } else {
                    Platform.runLater(() -> {
                        dashboardTitle.setText("Team Not Found");
                        dashboardSubtitle.setText("Please verify the ID and try again.");
                        // NEW: Update the table placeholder to explain the failure
                        tablePlaceholder.setText("Error: Team '" + teamNumber + "' does not exist in the database.");
                    });
                    return null;
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    // NEW: Handle actual internet/server crashes gracefully
                    tablePlaceholder.setText("Connection Error. Please check your internet and try again.");
                });
                e.printStackTrace();
                return null;
            }

            // --- 3. Hand data back to the UI Thread ---
        }).thenAccept(skillsData -> {
            Platform.runLater(() -> {
                searchButton.setDisable(false);
                searchButton.setText("Search");

                if (skillsData != null && !skillsData.isEmpty()) {

                    skillsData.sort((rank1, rank2) -> {
                        int seasonCompare = Integer.compare(rank2.getSeasonId(), rank1.getSeasonId());
                        if (seasonCompare != 0) return seasonCompare;

                        int eventCompare = rank1.getEventName().compareTo(rank2.getEventName());
                        if (eventCompare != 0) return eventCompare;

                        return Integer.compare(rank2.getScore(), rank1.getScore());
                    });

                    skillsTable.getItems().setAll(skillsData);

                } else if (skillsData != null && skillsData.isEmpty()) {
                    // NEW: If the list is empty but the team IS real
                    tablePlaceholder.setText("No skills runs recorded for " + teamNumber + ".");
                }
            });
        });
    }
    private void setupSearchHistoryPopup() {
        searchHistoryPopup = new ContextMenu();
        searchHistoryPopup.getStyleClass().add("history-popup"); // For our CSS later

        // Trigger 1: When the user physically clicks inside the text box
        teamInput.setOnMouseClicked(event -> showRecentSearches());

        // Trigger 2: Hide the popup the moment they start typing something new
        teamInput.textProperty().addListener((observable, oldValue, newValue) -> {
            searchHistoryPopup.hide();
        });
    }

    private void showRecentSearches() {
        // If there is no history yet, don't show an empty floating box
        if (recentSearches.isEmpty()) return;

        searchHistoryPopup.getItems().clear();

        // Build a clickable menu item for every saved search
        for (String search : recentSearches) {
            MenuItem item = new MenuItem("🕒  " + search); // Added a nice clock icon
            item.setOnAction(event -> {
                // When clicked, fill the text box and instantly trigger the search!
                teamInput.setText(search);
                handleSearch();
            });
            searchHistoryPopup.getItems().add(item);
        }

        // Show the popup perfectly aligned underneath the text field
        searchHistoryPopup.show(teamInput, Side.BOTTOM, 0, 5);
    }
}