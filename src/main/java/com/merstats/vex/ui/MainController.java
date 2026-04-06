package com.merstats.vex.ui;

import com.merstats.vex.service.RobotEventsService;
import com.merstats.vex.model.SkillsRanking;
import com.merstats.vex.model.VexTeam;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainController {

    // --- FXML Navigation Links ---
    @FXML private Label btnDashboard;
    @FXML private Label btnSkills;
    @FXML private Label btnH2H;
    @FXML private Label btnAwards;
    @FXML private Label btnEvents;
    @FXML private Label btnLeaderboards;
    @FXML private Label btnAbout;
    @FXML private Label btnSettings;

    // --- FXML View Cards ---
    @FXML private VBox sidebar;
    @FXML private VBox contentCard;     // The Dashboard
    @FXML private VBox aboutCard;       // About Me Page
    @FXML private VBox placeholderCard; // Under Construction Page
    @FXML private Label placeholderTitle;

    // --- About Me & Dashboard Elements ---
    @FXML private Button btnGithub;
    @FXML private ImageView mainLogo;
    @FXML private Label dashboardTitle;
    @FXML private Label dashboardSubtitle;
    @FXML private TextField teamInput;
    @FXML private Button searchButton;
    @FXML private ProgressBar loadingBar;

    @FXML private TableView<SkillsRanking> skillsTable;
    @FXML private TableColumn<SkillsRanking, String> seasonCol;
    @FXML private TableColumn<SkillsRanking, String> eventCol;
    @FXML private TableColumn<SkillsRanking, String> typeCol;
    @FXML private TableColumn<SkillsRanking, Integer> attemptCol;
    @FXML private TableColumn<SkillsRanking, Integer> scoreCol;
    @FXML private TableColumn<SkillsRanking, Integer> rankCol;

    // --- State Variables ---
    private Label tablePlaceholder;
    private final RobotEventsService apiService = new RobotEventsService();
    private final List<String> recentSearches = new ArrayList<>();
    private RotateTransition logoRotation;

    @FXML
    public void initialize() {
        // 1. Map Table Columns
        seasonCol.setCellValueFactory(new PropertyValueFactory<>("seasonName"));
        eventCol.setCellValueFactory(new PropertyValueFactory<>("eventName"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("formattedType"));
        attemptCol.setCellValueFactory(new PropertyValueFactory<>("attempts"));
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        rankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        skillsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // 2. Set up Empty State Placeholder
        tablePlaceholder = new Label("Ready to query. Enter a Team ID above.");
        tablePlaceholder.getStyleClass().add("placeholder-label");
        skillsTable.setPlaceholder(tablePlaceholder);

        // 3. Conditional Formatting for Global Rank
        rankCol.setCellFactory(column -> new TableCell<SkillsRanking, Integer>() {
            @Override
            protected void updateItem(Integer rank, boolean empty) {
                super.updateItem(rank, empty);
                if (empty || rank == null) {
                    setText(null);
                    getStyleClass().removeAll("rank-gold", "rank-green", "rank-standard");
                } else {
                    getStyleClass().removeAll("rank-gold", "rank-green", "rank-standard");
                    if (rank == 1) {
                        getStyleClass().add("rank-gold");
                        setText("🏆 " + rank);
                    } else if (rank <= 50) {
                        getStyleClass().add("rank-green");
                        setText("🔥 " + rank);
                    } else {
                        getStyleClass().add("rank-standard");
                        setText(String.valueOf(rank));
                    }
                }
            }
        });

        // 4. Triggers & Engine Setup
        setupSearchHistoryPopup();
        searchButton.setOnAction(event -> handleSearch());
        setupNavigationRouter();
        setupSocialLinks();

        // Initial Boot Animations
        playEntranceAnimation();
    }

    private void setupSocialLinks() {
        // Automatically opens the default web browser when clicked
        btnGithub.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI("https://github.com/Meeerrr"));
                }
            } catch (Exception ex) {
                System.err.println("Could not open GitHub link.");
                ex.printStackTrace();
            }
        });
    }

    // =======================================================
    // THE VIEW ROUTER ENGINE
    // =======================================================
    private void setupNavigationRouter() {
        // Map clicks to the router, passing the Card to show, the Label to highlight, and Title
        btnDashboard.setOnMouseClicked(e -> switchView(contentCard, btnDashboard, ""));
        btnSkills.setOnMouseClicked(e -> switchView(placeholderCard, btnSkills, "Skills Analytics"));
        btnH2H.setOnMouseClicked(e -> switchView(placeholderCard, btnH2H, "Head-to-Head Simulator"));
        btnAwards.setOnMouseClicked(e -> switchView(placeholderCard, btnAwards, "Awards Archive"));
        btnEvents.setOnMouseClicked(e -> switchView(placeholderCard, btnEvents, "Event Statistics"));
        btnLeaderboards.setOnMouseClicked(e -> switchView(placeholderCard, btnLeaderboards, "Global Leaderboards"));
        btnAbout.setOnMouseClicked(e -> switchView(aboutCard, btnAbout, ""));
        btnSettings.setOnMouseClicked(e -> switchView(placeholderCard, btnSettings, "System Settings"));
    }

    private void switchView(VBox targetCard, Label activeNavLabel, String pageTitle) {
        // PREVENT REDUNDANT CLICKS: If the clicked label is already active, do nothing
        if (activeNavLabel.getStyleClass().contains("nav-link-active")) {
            return;
        }

        // 1. Hide all cards and remove them from layout processing
        VBox[] allCards = {contentCard, aboutCard, placeholderCard};
        for (VBox card : allCards) {
            card.setVisible(false);
            card.setManaged(false);
        }

        // 2. Set dynamic title if it's a placeholder
        if (targetCard == placeholderCard) {
            placeholderTitle.setText(pageTitle);
        }

        // 3. Show target card and animate it in
        targetCard.setVisible(true);
        targetCard.setManaged(true);
        playCardAnimation(targetCard);

        // 4. Update Sidebar CSS (Remove active from all, add to clicked)
        Label[] allNavs = {btnDashboard, btnSkills, btnH2H, btnAwards, btnEvents, btnLeaderboards, btnAbout, btnSettings};
        for (Label nav : allNavs) {
            nav.getStyleClass().remove("nav-link-active");
            if (!nav.getStyleClass().contains("nav-link")) {
                nav.getStyleClass().add("nav-link");
            }
        }
        activeNavLabel.getStyleClass().remove("nav-link");
        activeNavLabel.getStyleClass().add("nav-link-active");
    }

    // =======================================================
    // ANIMATIONS
    // =======================================================
    private void playCardAnimation(VBox card) {
        card.setOpacity(0.0);
        card.setTranslateY(20.0);

        FadeTransition fade = new FadeTransition(Duration.millis(500), card);
        fade.setToValue(1.0);

        TranslateTransition slide = new TranslateTransition(Duration.millis(500), card);
        slide.setToY(0.0);

        fade.play();
        slide.play();
    }

    private void playEntranceAnimation() {
        sidebar.setOpacity(0.0);
        FadeTransition sidebarFade = new FadeTransition(Duration.millis(800), sidebar);
        sidebarFade.setFromValue(0.0);
        sidebarFade.setToValue(1.0);
        sidebarFade.play();

        // Start by animating the Dashboard card
        playCardAnimation(contentCard);
    }

    private void animateDataArrival() {
        skillsTable.setOpacity(0.0);
        skillsTable.setTranslateY(20.0);
        FadeTransition tableFade = new FadeTransition(Duration.millis(600), skillsTable);
        tableFade.setToValue(1.0);
        TranslateTransition tableSlide = new TranslateTransition(Duration.millis(600), skillsTable);
        tableSlide.setToY(0.0);
        tableFade.play();
        tableSlide.play();
    }

    private RotateTransition createLogoAnimation() {
        RotateTransition rt = new RotateTransition(Duration.millis(800), mainLogo);
        rt.setByAngle(360);
        rt.setCycleCount(Animation.INDEFINITE);
        rt.setInterpolator(Interpolator.LINEAR);
        return rt;
    }

    // =======================================================
    // SEARCH & API LOGIC
    // =======================================================
    private void setupSearchHistoryPopup() {
        ContextMenu historyMenu = new ContextMenu();
        teamInput.setOnMouseClicked(event -> {
            if (!recentSearches.isEmpty()) {
                historyMenu.getItems().clear();
                for (String search : recentSearches) {
                    MenuItem item = new MenuItem(search);
                    item.setOnAction(e -> {
                        teamInput.setText(search);
                        handleSearch();
                    });
                    historyMenu.getItems().add(item);
                }
                historyMenu.show(teamInput, javafx.geometry.Side.BOTTOM, 0, 0);
            }
        });
    }

    private void handleSearch() {
        String teamNumber = teamInput.getText().trim();
        if (teamNumber.isEmpty()) return;

        if (!recentSearches.contains(teamNumber)) {
            recentSearches.add(0, teamNumber);
            if (recentSearches.size() > 5) {
                recentSearches.remove(5);
            }
        }

        searchButton.setDisable(true);
        searchButton.setText("Searching...");
        skillsTable.getItems().clear();
        tablePlaceholder.setText("Fetching telemetry for " + teamNumber + "...");
        loadingBar.setVisible(true);

        if (logoRotation == null) {
            logoRotation = createLogoAnimation();
        }
        logoRotation.play();
        contentCard.getStyleClass().add("logo-active-pulse");

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
                        tablePlaceholder.setText("Error: Team '" + teamNumber + "' does not exist in the database.");

                        loadingBar.setVisible(false);
                        if (logoRotation != null) { logoRotation.stop(); mainLogo.setRotate(0); }
                        contentCard.getStyleClass().remove("logo-active-pulse");
                        searchButton.setDisable(false);
                        searchButton.setText("Search");
                    });
                    return null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    tablePlaceholder.setText("Connection Error. Please check your internet and try again.");
                    loadingBar.setVisible(false);
                    if (logoRotation != null) { logoRotation.stop(); mainLogo.setRotate(0); }
                    contentCard.getStyleClass().remove("logo-active-pulse");
                    searchButton.setDisable(false);
                    searchButton.setText("Search");
                });
                return null;
            }
        }).thenAccept((List<SkillsRanking> skillsData) -> {
            Platform.runLater(() -> {
                loadingBar.setVisible(false);
                if (logoRotation != null) { logoRotation.stop(); mainLogo.setRotate(0); }
                contentCard.getStyleClass().remove("logo-active-pulse");
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
                    animateDataArrival();
                } else if (skillsData != null && skillsData.isEmpty()) {
                    tablePlaceholder.setText("No skills runs recorded for " + teamNumber + ".");
                }
            });
        });
    }
}