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

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainController {

    // --- 1. Link the UI Elements ---
    @FXML private TextField teamInput;
    @FXML private Button searchButton;


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
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        attemptCol.setCellValueFactory(new PropertyValueFactory<>("attempts"));
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));

        searchButton.setOnAction(event -> handleSearch());
    }

    private void handleSearch() {
        // Grab the text from the UI and remove any accidental spaces
        String teamNumber = teamInput.getText().trim();

        // Prevent searching if the user accidentally clicks the button while the box is empty
        if (teamNumber.isEmpty()) return;

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
                        // Compares the numeric IDs so the highest ID (newest season) is always on top
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
                    // If no data comes back, log it (we can add a visual alert for this later)
                    System.out.println("No skills records found for team: " + teamNumber);
                }
            });
        });
    }
}