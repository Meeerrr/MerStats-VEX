package com.merstats.vex.ui;

import com.merstats.vex.model.SkillsRanking;
import com.merstats.vex.model.VexTeam;
import com.merstats.vex.service.RobotEventsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.layout.VBox;

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

    @FXML private ProgressBar loadingBar;
    @FXML private ImageView mainLogo;
    @FXML private VBox contentCard;

    @FXML private VBox sidebar;



    // The dynamic message that shows when the table is empty
    private Label tablePlaceholder;

    // Remembers our animation so we can stop it later
    private RotateTransition logoRotation;

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
        // --- 1. Map Table Columns to Model Properties ---
        // Using PropertyValueFactory to link our FXML columns to the Java Model getters
        seasonCol.setCellValueFactory(new PropertyValueFactory<>("seasonName"));
        eventCol.setCellValueFactory(new PropertyValueFactory<>("eventName"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("formattedType"));
        attemptCol.setCellValueFactory(new PropertyValueFactory<>("attempts"));
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));

        // --- 2. Polish the Table Layout ---
        // Forces columns to perfectly fill the width of the table without generating an empty gray filler column
        skillsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // --- 3. Set up the Empty State Placeholder ---
        // Creates a beautiful, dashed UI card instead of a blank screen when there is no data
        tablePlaceholder = new Label("Ready to query. Enter a Team ID above.");
        tablePlaceholder.getStyleClass().add("placeholder-label");
        skillsTable.setPlaceholder(tablePlaceholder);

        // --- 4. Custom Cell Factory for Conditional Formatting ---
        // This engine dynamically recolors the Global Rank text and adds emojis based on the score
        rankCol.setCellFactory(column -> new TableCell<SkillsRanking, Integer>() {
            @Override
            protected void updateItem(Integer rank, boolean empty) {
                super.updateItem(rank, empty);

                // Erase everything if the row is empty (prevents CSS ghosting during scrolling)
                if (empty || rank == null) {
                    setText(null);
                    getStyleClass().removeAll("rank-gold", "rank-green", "rank-standard");
                } else {
                    // Strip old CSS classes before applying new ones to ensure clean transitions
                    getStyleClass().removeAll("rank-gold", "rank-green", "rank-standard");

                    // Apply the analytical logic
                    if (rank == 1) {
                        getStyleClass().add("rank-gold");
                        setText("🏆 " + rank); // World Champion styling
                    } else if (rank <= 50) {
                        getStyleClass().add("rank-green");
                        setText("🔥 " + rank); // Top 50 styling
                    } else {
                        getStyleClass().add("rank-standard");
                        setText(String.valueOf(rank)); // Standard styling
                    }
                }
            }
        });

        // --- 5. Initialize the Memory System ---
        // Wires up the ContextMenu that remembers recent searches
        setupSearchHistoryPopup();

        // --- 6. Wire the Search Button ---
        // Connects the visual button to the background threading engine
        searchButton.setOnAction(event -> handleSearch());

        // --- 7. Trigger the Entrance Animation ---
        // Fires the smooth fade and slide transitions when the app boots up
        playEntranceAnimation();
    }

    private void handleSearch() {
        // Grab the text from the UI and remove any accidental spaces
        String teamNumber = teamInput.getText().trim();

        // Prevent searching if the user accidentally clicks the button while the box is empty
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
        skillsTable.getItems().clear();
        tablePlaceholder.setText("Fetching telemetry for " + teamNumber + "...");

        // Turn on the loading bar
        loadingBar.setVisible(true);

        // --- NEW: START LOGO ANIMATION & GLOW ---
        if (logoRotation == null) {
            logoRotation = createLogoAnimation();
        }
        logoRotation.play();
        contentCard.getStyleClass().add("logo-active-pulse"); // Triggers the CSS glow!


        // --- 2. The Background Thread (Prevents UI Freezing) ---
        CompletableFuture.supplyAsync(() -> {
            try {
                // Call 1: Get the Team to find their internal RobotEvents ID
                VexTeam team = apiService.getTeamByNumber(teamNumber);

                if (team != null) {
                    // Update the Header UI immediately!
                    Platform.runLater(() -> {
                        dashboardTitle.setText("Team " + team.getNumber() + " - " + team.getTeam_name());
                        dashboardSubtitle.setText("Grade Level: " + team.getGrade());
                    });

                    // Call 2: Use the ID to fetch their entire skills history
                    return apiService.getSkillsByTeamId(team.getId());

                } else {
                    // IF TEAM DOES NOT EXIST: Gracefully reset labels and STOP ANIMATIONS
                    Platform.runLater(() -> {
                        dashboardTitle.setText("Team Not Found");
                        dashboardSubtitle.setText("Please verify the ID and try again.");
                        tablePlaceholder.setText("Error: Team '" + teamNumber + "' does not exist in the database.");

                        // Stop Animations
                        loadingBar.setVisible(false);
                        if (logoRotation != null) {
                            logoRotation.stop();
                            mainLogo.setRotate(0); // Snap it back to upright
                        }
                        contentCard.getStyleClass().remove("logo-active-pulse"); // Turn off glow

                        searchButton.setDisable(false);
                        searchButton.setText("Search");
                    });
                    return null; // Stop the background thread
                }

            } catch (Exception e) {
                System.err.println("Failed to fetch data from RobotEvents.");
                e.printStackTrace();

                // IF NETWORK ERROR: Handle crash gracefully and STOP ANIMATIONS
                Platform.runLater(() -> {
                    tablePlaceholder.setText("Connection Error. Please check your internet and try again.");

                    // Stop Animations
                    loadingBar.setVisible(false);
                    if (logoRotation != null) {
                        logoRotation.stop();
                        mainLogo.setRotate(0);
                    }
                    contentCard.getStyleClass().remove("logo-active-pulse");

                    searchButton.setDisable(false);
                    searchButton.setText("Search");
                });
                return null;
            }

            // --- 3. Hand data back to the UI Thread (SUCCESS BLOCK) ---
        }).thenAccept(skillsData -> {
            Platform.runLater(() -> {

                // --- NEW: STOP ANIMATIONS ON SUCCESS ---
                loadingBar.setVisible(false);
                if (logoRotation != null) {
                    logoRotation.stop();
                    mainLogo.setRotate(0); // Snap it back to upright
                }
                contentCard.getStyleClass().remove("logo-active-pulse"); // Turn off the glow

                // Reset the button back to its normal state
                searchButton.setDisable(false);
                searchButton.setText("Search");

                // Check if we actually received data
                if (skillsData != null && !skillsData.isEmpty()) {

                    // --- 4. The Sorting Engine ---
                    skillsData.sort((rank1, rank2) -> {
                        int seasonCompare = Integer.compare(rank2.getSeasonId(), rank1.getSeasonId());
                        if (seasonCompare != 0) return seasonCompare;

                        int eventCompare = rank1.getEventName().compareTo(rank2.getEventName());
                        if (eventCompare != 0) return eventCompare;

                        return Integer.compare(rank2.getScore(), rank1.getScore());
                    });

                    // Inject the data
                    skillsTable.getItems().setAll(skillsData);

                    // --- Fire the arrival animation so the data slides in smoothly! ---
                    animateDataArrival();

                } else if (skillsData != null && skillsData.isEmpty()) {
                    // Team exists, but has never run skills
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
    private void playEntranceAnimation() {
        // 1. Initial State: Make everything invisible and push the card down 30 pixels
        sidebar.setOpacity(0.0);
        contentCard.setOpacity(0.0);
        contentCard.setTranslateY(30.0);

        // 2. Sidebar Fade-In (Takes 800 milliseconds)
        FadeTransition sidebarFade = new FadeTransition(Duration.millis(800), sidebar);
        sidebarFade.setFromValue(0.0);
        sidebarFade.setToValue(1.0);

        // 3. Content Card Fade-In
        FadeTransition cardFade = new FadeTransition(Duration.millis(800), contentCard);
        cardFade.setFromValue(0.0);
        cardFade.setToValue(1.0);
        cardFade.setDelay(Duration.millis(300)); // Starts slightly after the sidebar

        // 4. Content Card Slide-Up (Runs at the exact same time as the card fade)
        TranslateTransition cardSlide = new TranslateTransition(Duration.millis(800), contentCard);
        cardSlide.setFromY(30.0);
        cardSlide.setToY(0.0); // Slides up to its original 0 position
        cardSlide.setDelay(Duration.millis(300));

        // 5. Fire the animations!
        sidebarFade.play();
        cardFade.play();
        cardSlide.play();
    }
    private void animateDataArrival() {
        // 1. Reset the table's starting position (invisible and pushed down 20 pixels)
        skillsTable.setOpacity(0.0);
        skillsTable.setTranslateY(20.0);

        // 2. Create the Fade-In effect
        FadeTransition tableFade = new FadeTransition(Duration.millis(600), skillsTable);
        tableFade.setToValue(1.0);

        // 3. Create the Slide-Up effect
        TranslateTransition tableSlide = new TranslateTransition(Duration.millis(600), skillsTable);
        tableSlide.setToY(0.0);

        // 4. Play them simultaneously!
        tableFade.play();
        tableSlide.play();
    }
    private RotateTransition createLogoAnimation() {
        // Create an 800ms rotation applied to the mainLogo
        RotateTransition rt = new RotateTransition(Duration.millis(800), mainLogo);
        rt.setByAngle(360);
        rt.setCycleCount(Animation.INDEFINITE); // Loop forever until told to stop
        rt.setInterpolator(Interpolator.LINEAR); // Keeps the spinning speed perfectly smooth and constant
        return rt;
    }
}