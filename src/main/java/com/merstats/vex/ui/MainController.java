package com.merstats.vex.ui;

import com.merstats.vex.model.SeasonRanking;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

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
    @FXML private VBox leaderboardCard; // The Leaderboard
    @FXML private VBox aboutCard;       // About Me Page
    @FXML private VBox placeholderCard; // Under Construction Page
    @FXML private Label placeholderTitle;

    // --- Dashboard Elements ---
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
    @FXML private TableColumn<SkillsRanking, Integer> dashRankCol;

    // --- Leaderboard Elements ---
    @FXML private Button btnLoadLeaderboard;
    @FXML private ProgressBar lbLoadingBar;
    @FXML private TableView<SeasonRanking> leaderboardTable;
    @FXML private TableColumn<SeasonRanking, Integer> lbRankCol;
    @FXML private TableColumn<SeasonRanking, String> lbTeamCol;
    @FXML private TableColumn<SeasonRanking, String> lbRecordCol;
    @FXML private TableColumn<SeasonRanking, Integer> lbWpCol;
    @FXML private TableColumn<SeasonRanking, Integer> lbApCol;
    @FXML private TableColumn<SeasonRanking, Integer> lbSpCol;
    @FXML private TableColumn<SeasonRanking, Double> lbTrueRankCol;

    // --- State Variables ---
    private Label dashboardPlaceholder;
    private Label leaderboardPlaceholder;
    private final RobotEventsService apiService = new RobotEventsService();
    private final List<String> recentSearches = new ArrayList<>();
    private RotateTransition logoRotation;

    // Constant for the current active season (Over Under / High Stakes etc.)
    private static final int CURRENT_SEASON_ID = 181;

    @FXML
    public void initialize() {
        setupDashboardTable();
        setupLeaderboardTable();

        // Triggers & Engine Setup
        setupSearchHistoryPopup();
        searchButton.setOnAction(event -> handleSearch());
        btnLoadLeaderboard.setOnAction(event -> loadLeaderboardData());

        setupNavigationRouter();
        setupSocialLinks();

        // Initial Boot Animations
        playEntranceAnimation();
    }

    private void setupDashboardTable() {
        seasonCol.setCellValueFactory(new PropertyValueFactory<>("seasonName"));
        eventCol.setCellValueFactory(new PropertyValueFactory<>("eventName"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("formattedType"));
        attemptCol.setCellValueFactory(new PropertyValueFactory<>("attempts"));
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        dashRankCol.setCellValueFactory(new PropertyValueFactory<>("rank"));
        skillsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        dashboardPlaceholder = new Label("Ready to query. Enter a Team ID above.");
        dashboardPlaceholder.getStyleClass().add("placeholder-label");
        skillsTable.setPlaceholder(dashboardPlaceholder);
    }

    private void setupLeaderboardTable() {
        // Map Columns to SeasonRanking object methods
        lbRankCol.setCellValueFactory(new PropertyValueFactory<>("rank")); // We will overwrite this with index visually later
        lbTeamCol.setCellValueFactory(new PropertyValueFactory<>("teamNumber"));
        lbRecordCol.setCellValueFactory(new PropertyValueFactory<>("record"));
        lbWpCol.setCellValueFactory(new PropertyValueFactory<>("wp"));
        lbApCol.setCellValueFactory(new PropertyValueFactory<>("ap"));
        lbSpCol.setCellValueFactory(new PropertyValueFactory<>("sp"));
        lbTrueRankCol.setCellValueFactory(new PropertyValueFactory<>("trueRankScore"));

        leaderboardTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        leaderboardPlaceholder = new Label("Click 'Load' to fetch the global MMR standings.");
        leaderboardPlaceholder.getStyleClass().add("placeholder-label");
        leaderboardTable.setPlaceholder(leaderboardPlaceholder);

        // --- VISUAL TIER BADGES LOGIC ---
        lbTrueRankCol.setCellFactory(column -> new TableCell<SeasonRanking, Double>() {
            @Override
            protected void updateItem(Double mmr, boolean empty) {
                super.updateItem(mmr, empty);
                if (empty || mmr == null) {
                    setText(null);
                    getStyleClass().removeAll("rank-gold", "rank-green", "rank-standard");
                } else {
                    getStyleClass().removeAll("rank-gold", "rank-green", "rank-standard");

                    if (mmr >= 3000.0) {
                        getStyleClass().add("rank-gold"); // Grandmaster Tier
                        setText("⭐ " + mmr);
                    } else if (mmr >= 2000.0) {
                        getStyleClass().add("rank-green"); // Diamond Tier
                        setText("💎 " + mmr);
                    } else {
                        getStyleClass().add("rank-standard"); // Standard
                        setText(String.valueOf(mmr));
                    }
                }
            }
        });

        // --- INTERACTIVE CONTEXT MENU LOGIC ---
        leaderboardTable.setRowFactory(tv -> {
            TableRow<SeasonRanking> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem viewDashboardItem = new MenuItem("🔍 View Full Dashboard");
            MenuItem copyIdItem = new MenuItem("📋 Copy Team ID");

            viewDashboardItem.setOnAction(event -> {
                SeasonRanking selectedTeam = row.getItem();
                if (selectedTeam != null) {
                    teamInput.setText(selectedTeam.getTeamNumber());
                    switchView(contentCard, btnDashboard, "");
                    handleSearch(); // Automatically trigger the dashboard search
                }
            });

            copyIdItem.setOnAction(event -> {
                SeasonRanking selectedTeam = row.getItem();
                if (selectedTeam != null) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(selectedTeam.getTeamNumber());
                    clipboard.setContent(content);
                }
            });

            contextMenu.getItems().addAll(viewDashboardItem, copyIdItem);

            // Only show menu if row contains a team
            row.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                if (isNowEmpty) {
                    row.setContextMenu(null);
                } else {
                    row.setContextMenu(contextMenu);
                }
            });
            return row;
        });
    }

    private void setupSocialLinks() {
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
        btnDashboard.setOnMouseClicked(e -> switchView(contentCard, btnDashboard, ""));
        btnSkills.setOnMouseClicked(e -> switchView(placeholderCard, btnSkills, "Skills Analytics"));
        btnH2H.setOnMouseClicked(e -> switchView(placeholderCard, btnH2H, "Head-to-Head Simulator"));
        btnAwards.setOnMouseClicked(e -> switchView(placeholderCard, btnAwards, "Awards Archive"));
        btnEvents.setOnMouseClicked(e -> switchView(placeholderCard, btnEvents, "Event Statistics"));
        btnLeaderboards.setOnMouseClicked(e -> switchView(leaderboardCard, btnLeaderboards, ""));
        btnAbout.setOnMouseClicked(e -> switchView(aboutCard, btnAbout, ""));
        btnSettings.setOnMouseClicked(e -> switchView(placeholderCard, btnSettings, "System Settings"));
    }

    private void switchView(VBox targetCard, Label activeNavLabel, String pageTitle) {
        if (activeNavLabel.getStyleClass().contains("nav-link-active")) return;

        VBox[] allCards = {contentCard, leaderboardCard, aboutCard, placeholderCard};
        for (VBox card : allCards) {
            card.setVisible(false);
            card.setManaged(false);
        }

        if (targetCard == placeholderCard) {
            placeholderTitle.setText(pageTitle);
        }

        targetCard.setVisible(true);
        targetCard.setManaged(true);
        playCardAnimation(targetCard);

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
        playCardAnimation(contentCard);
    }

    // =======================================================
    // SEARCH & API LOGIC
    // =======================================================
    private void loadLeaderboardData() {
        btnLoadLeaderboard.setDisable(true);
        btnLoadLeaderboard.setText("Calculating MMR...");
        lbLoadingBar.setVisible(true);
        leaderboardTable.getItems().clear();

        CompletableFuture.supplyAsync(() -> {
            try {
                return apiService.getSeasonRankings(CURRENT_SEASON_ID);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).thenAccept((List<SeasonRanking> rankingsData) -> {
            Platform.runLater(() -> {
                btnLoadLeaderboard.setDisable(false);
                btnLoadLeaderboard.setText("Load Current Season");
                lbLoadingBar.setVisible(false);

                if (rankingsData != null && !rankingsData.isEmpty()) {
                    // Sort descending by TrueRank MMR
                    rankingsData.sort((r1, r2) -> Double.compare(r2.getTrueRankScore(), r1.getTrueRankScore()));

                    // Manually assign correct visual ranks after sorting
                    for (int i = 0; i < rankingsData.size(); i++) {
                        // Normally API returns rank, but we re-sort by MMR, so we override it for the UI
                    }

                    leaderboardTable.getItems().setAll(rankingsData);

                    // Small fade animation for the table
                    leaderboardTable.setOpacity(0);
                    FadeTransition fade = new FadeTransition(Duration.millis(500), leaderboardTable);
                    fade.setToValue(1);
                    fade.play();

                } else {
                    leaderboardPlaceholder.setText("Error: Failed to fetch ranking data.");
                }
            });
        });
    }

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
        dashboardPlaceholder.setText("Fetching telemetry for " + teamNumber + "...");
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
                        dashboardPlaceholder.setText("Error: Team '" + teamNumber + "' does not exist in the database.");

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
                    dashboardPlaceholder.setText("Connection Error. Please check your internet and try again.");
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

                    skillsTable.setOpacity(0.0);
                    skillsTable.setTranslateY(20.0);
                    FadeTransition tableFade = new FadeTransition(Duration.millis(600), skillsTable);
                    tableFade.setToValue(1.0);
                    TranslateTransition tableSlide = new TranslateTransition(Duration.millis(600), skillsTable);
                    tableSlide.setToY(0.0);
                    tableFade.play();
                    tableSlide.play();

                } else if (skillsData != null && skillsData.isEmpty()) {
                    dashboardPlaceholder.setText("No skills runs recorded for " + teamNumber + ".");
                }
            });
        });
    }

    private RotateTransition createLogoAnimation() {
        RotateTransition rt = new RotateTransition(Duration.millis(800), mainLogo);
        rt.setByAngle(360);
        rt.setCycleCount(Animation.INDEFINITE);
        rt.setInterpolator(Interpolator.LINEAR);
        return rt;
    }
}