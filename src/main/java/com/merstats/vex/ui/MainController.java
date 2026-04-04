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

        searchButton.setOnAction(event -> handleSearch());
    }

    private void handleSearch() {
        // Grab the text from the UI and remove any accidental spaces
        String teamNumber = teamInput.getText().trim();

        // Prevent searching if the user accidentally clicks the button while the box is empty
        if (teamNumber.isEmpty()) return;

        // --- NEW: THE MEMORY SYSTEM ---
        // If this is a new search, add it to our recent searches list
        if (!recentSearches.contains(teamNumber)) {
            recentSearches.add(0, teamNumber); // Push it to the very top of the list
            if (recentSearches.size() > 5) {
                recentSearches.remove(5); // Keep the list clean (only remember the last 5)
            }
        }

        // --- 1. Visual Feedback (UI Thread) ---
        // Disable the button and change the text so the user knows the app is working
        searchButton.setDisable(true);
        searchButton.setText("Searching...");
        skillsTable.getItems().clear(); // Clear any old data from a previous search

        // --- 2. The Background Thread (Prevents UI Freezing) ---
        CompletableFuture.supplyAsync(() -> {
            try {
                // Call 1: Get the Team to find their internal RobotEvents ID
                VexTeam team = apiService.getTeamByNumber(teamNumber);

                if (team != null) {
                    // Call 2: Use the ID to fetch their entire skills history
                    return apiService.getSkillsByTeamId(team.getId());
                }
                return null; // Team not found

            } catch (Exception e) {
                System.err.println("Failed to fetch data from RobotEvents.");
                e.printStackTrace();
                return null;
            }

            // --- 3. Hand data back to the UI Thread ---
        }).thenAccept(skillsData -> {
            // Platform.runLater is required anytime we want to change the visual window
            // from a background thread
            Platform.runLater(() -> {
                // Reset the button back to its normal state
                searchButton.setDisable(false);
                searchButton.setText("Search");

                // Check if we actually received data
                if (skillsData != null && !skillsData.isEmpty()) {

                    // --- 4. The Sorting Engine ---
                    // This groups all runs by Season first, then groups them by Event Name,
                    // and finally sorts by highest score at that specific event!
                    skillsData.sort((rank1, rank2) -> {
                        // Priority 1: Compare Seasons (Descending so newest seasons are at the top)
                        int seasonCompare = Integer.compare(rank2.getSeasonId(), rank1.getSeasonId());
                        if (seasonCompare != 0) return seasonCompare;

                        // Priority 2: Compare Events if they are in the exact same season
                        int eventCompare = rank1.getEventName().compareTo(rank2.getEventName());
                        if (eventCompare != 0) return eventCompare;

                        // Priority 3: Compare Scores if they are at the exact same event (Highest score first)
                        return Integer.compare(rank2.getScore(), rank1.getScore());
                    });

                    // Inject the beautifully sorted and grouped data into the table
                    skillsTable.getItems().setAll(skillsData);

                } else {
                    // If no data comes back, log it
                    System.out.println("No skills records found for team: " + teamNumber);
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