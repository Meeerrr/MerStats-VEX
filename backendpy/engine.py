import math

def calculate_truerank(teams, matches):
    """
    The Core Math Engine (Single-Pass Chronological).
    Now features a 10% Rolling OPR Synergy Bonus.
    """
    print("      -> Initializing TrueRank Math Engine...")

    # 1. Initialize the global roster with new Rolling OPR tracking stats
    team_data = {}
    for t in teams:
        team_name = t["name"]
        team_data[team_name] = {
            "elo": 1500.0,
            "wins": 0,
            "losses": 0,
            "ties": 0,
            "total_points": 0,       # Tracks raw points scored chronologically
            "offensive_matches": 0   # Tracks matches played for the OPR average
        }

    base_k = 32.0

    for match in matches:
        red_alliance = match["alliances"][0] if match["alliances"][0]["color"] == "red" else match["alliances"][1]
        blue_alliance = match["alliances"][0] if match["alliances"][0]["color"] == "blue" else match["alliances"][1]

        red_score = red_alliance["score"]
        blue_score = blue_alliance["score"]

        red_teams = [t["team"]["name"] for t in red_alliance["teams"]]
        blue_teams = [t["team"]["name"] for t in blue_alliance["teams"]]

        if not red_teams or not blue_teams:
            continue

        # Calculate Alliance Averages
        red_avg_elo = sum([team_data[t]["elo"] for t in red_teams]) / len(red_teams)
        blue_avg_elo = sum([team_data[t]["elo"] for t in blue_teams]) / len(blue_teams)

        # ---------------------------------------------------------
        # 📊 NEW: CALCULATE ROLLING OPR
        # Calculate the historical average points scored by these teams before this match happens
        # ---------------------------------------------------------
        red_total_pts = sum([team_data[t]["total_points"] for t in red_teams])
        red_total_matches = sum([team_data[t]["offensive_matches"] for t in red_teams])
        red_rolling_opr = (red_total_pts / red_total_matches) if red_total_matches > 0 else 0

        blue_total_pts = sum([team_data[t]["total_points"] for t in blue_teams])
        blue_total_matches = sum([team_data[t]["offensive_matches"] for t in blue_teams])
        blue_rolling_opr = (blue_total_pts / blue_total_matches) if blue_total_matches > 0 else 0

        # Calculate Expected Win Probability
        red_expected = 1.0 / (1.0 + math.pow(10, (blue_avg_elo - red_avg_elo) / 400.0))
        blue_expected = 1.0 / (1.0 + math.pow(10, (red_avg_elo - blue_avg_elo) / 400.0))

        # Determine actual outcome & Log W-L-T
        if red_score > blue_score:
            red_actual = 1.0
            blue_actual = 0.0
            for t in red_teams: team_data[t]["wins"] += 1
            for t in blue_teams: team_data[t]["losses"] += 1
        elif blue_score > red_score:
            red_actual = 0.0
            blue_actual = 1.0
            for t in red_teams: team_data[t]["losses"] += 1
            for t in blue_teams: team_data[t]["wins"] += 1
        else:
            red_actual = 0.5
            blue_actual = 0.5
            for t in red_teams: team_data[t]["ties"] += 1
            for t in blue_teams: team_data[t]["ties"] += 1

        # 1. THE "SMART" MOV MULTIPLIER (Capped at 1.20x)
        point_diff = abs(red_score - blue_score)
        mov_multiplier = 1.0 + (math.log10(point_diff + 1) * 0.10)
        mov_multiplier = min(mov_multiplier, 1.20)

        # ---------------------------------------------------------
        # 💥 2. THE NEW 10% OPR SYNERGY BONUS
        # If a team wins, AND they have a higher historical offensive output,
        # they get a micro-boost for executing their strategy.
        # ---------------------------------------------------------
        opr_multiplier = 1.0
        if red_actual == 1.0 and red_rolling_opr > blue_rolling_opr:
            opr_advantage = red_rolling_opr - blue_rolling_opr
            # 0.005 scaling means a 20-point OPR advantage hits the 10% max cap
            opr_multiplier = 1.0 + min((opr_advantage * 0.005), 0.10)
        elif blue_actual == 1.0 and blue_rolling_opr > red_rolling_opr:
            opr_advantage = blue_rolling_opr - red_rolling_opr
            opr_multiplier = 1.0 + min((opr_advantage * 0.005), 0.10)

        # 3. ASYMMETRIC EVENT MULTIPLIER (The Worlds Shield)
        event_level = match.get("level", "Local")
        if "World" in event_level or "Championship" in event_level:
            reward_mult = 2.0
            penalty_mult = 0.5
        elif "Signature" in event_level or "National" in event_level:
            reward_mult = 1.5
            penalty_mult = 0.75
        elif "State" in event_level or "Region" in event_level:
            reward_mult = 1.2
            penalty_mult = 0.9
        else:
            reward_mult = 1.0
            penalty_mult = 1.0

            # ---------------------------------------------------------
        # COMBINE ALL THE MATH
        # Notice we multiply the OPR synergy in here!
        # ---------------------------------------------------------
        base_red_shift = base_k * mov_multiplier * opr_multiplier * (red_actual - red_expected)
        base_blue_shift = base_k * mov_multiplier * opr_multiplier * (blue_actual - blue_expected)

        red_shift = base_red_shift * (reward_mult if base_red_shift > 0 else penalty_mult)
        blue_shift = base_blue_shift * (reward_mult if base_blue_shift > 0 else penalty_mult)

        for t in red_teams:
            team_data[t]["elo"] += red_shift
            # Update points for the next match's Rolling OPR calculation!
            team_data[t]["total_points"] += red_score
            team_data[t]["offensive_matches"] += 1

        for t in blue_teams:
            team_data[t]["elo"] += blue_shift
            team_data[t]["total_points"] += blue_score
            team_data[t]["offensive_matches"] += 1

    # Package the final data for Supabase
    print("      -> Math complete. Packaging leaderboards...")
    final_leaderboard = []

    for team_name, stats in team_data.items():
        total_matches = stats["wins"] + stats["losses"] + stats["ties"]
        if total_matches > 0:
            final_leaderboard.append({
                "team_id": team_name,
                "elo_score": round(stats["elo"], 2),
                "wins": stats["wins"],
                "losses": stats["losses"],
                "ties": stats["ties"]
            })

    return final_leaderboard