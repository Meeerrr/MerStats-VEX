import pytest
import sys
import os

# Ensure pytest can find the engine.py file in the parent directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from engine import calculate_truerank

def get_base_teams():
    """Helper function to reset team stats before each test."""
    return [
        {"name": "11017Y"},
        {"name": "229V"},
        {"name": "99999A"},
        {"name": "99999B"}
    ]

def test_standard_match_elo_shift():
    """
    TEST 1: Basic Elo functionality.
    If both teams start at 1500 Elo and Red wins a normal match,
    Red should gain points and Blue should lose the exact same amount.
    """
    teams = get_base_teams()

    # A standard local match where Red wins 60 to 40
    matches = [{
        "level": "Local",
        "alliances": [
            {"color": "red", "score": 60, "teams": [{"team": {"name": "11017Y"}}, {"team": {"name": "229V"}}]},
            {"color": "blue", "score": 40, "teams": [{"team": {"name": "99999A"}}, {"team": {"name": "99999B"}}]}
        ]
    }]

    leaderboard = calculate_truerank(teams, matches)

    # Extract the new Elos
    red_elo = next(t["elo_score"] for t in leaderboard if t["team_id"] == "11017Y")
    blue_elo = next(t["elo_score"] for t in leaderboard if t["team_id"] == "99999A")

    # Assertions: Did the math work?
    assert red_elo > 1500.0, "Winning team did not gain Elo."
    assert blue_elo < 1500.0, "Losing team did not lose Elo."
    assert round((red_elo - 1500.0) + (blue_elo - 1500.0), 2) == 0.0, "Elo transfer was not perfectly symmetrical."

def test_margin_of_victory_cap():
    """
    TEST 2: The Logarithmic MOV Cap.
    Tests if a massive 200-point blowout is successfully capped at the 1.20x limit
    to prevent Elo inflation.
    """
    teams = get_base_teams()

    # A massive blowout: 200 to 0
    matches = [{
        "level": "Local",
        "alliances": [
            {"color": "red", "score": 200, "teams": [{"team": {"name": "11017Y"}}, {"team": {"name": "229V"}}]},
            {"color": "blue", "score": 0, "teams": [{"team": {"name": "99999A"}}, {"team": {"name": "99999B"}}]}
        ]
    }]

    leaderboard = calculate_truerank(teams, matches)
    red_elo = next(t["elo_score"] for t in leaderboard if t["team_id"] == "11017Y")

    # Base shift for equal Elo is 16. The absolute max with a 1.20x MOV cap is 19.2.
    # It should never exceed this number, even with a 200 point difference.
    points_gained = red_elo - 1500.0
    assert round(points_gained, 2) <= 19.2, f"MOV Cap failed! Team gained {points_gained} points..."

def test_worlds_shield_asymmetric_multiplier():
    """
    TEST 3: The Event Level Multiplier.
    Tests the v2.3.0 feature where Worlds matches give 2.0x for a win, but only 0.5x for a loss.
    """
    teams = get_base_teams()

    matches = [{
        "level": "World Championship",
        "alliances": [
            {"color": "red", "score": 50, "teams": [{"team": {"name": "11017Y"}}, {"team": {"name": "229V"}}]},
            {"color": "blue", "score": 40, "teams": [{"team": {"name": "99999A"}}, {"team": {"name": "99999B"}}]}
        ]
    }]

    leaderboard = calculate_truerank(teams, matches)

    red_elo_gained = next(t["elo_score"] for t in leaderboard if t["team_id"] == "11017Y") - 1500.0
    blue_elo_lost = 1500.0 - next(t["elo_score"] for t in leaderboard if t["team_id"] == "99999A")

    # Because of the Worlds Shield, the winners get double, and the losers get half.
    # They should no longer be symmetrical!
    assert red_elo_gained > blue_elo_lost, "The Worlds Shield failed to protect the losing team."