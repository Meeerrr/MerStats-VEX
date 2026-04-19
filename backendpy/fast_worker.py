import requests
import os
from dotenv import load_dotenv
from engine import calculate_truerank

load_dotenv()
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")
supabase_headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "resolution=merge-duplicates"
}

TARGET_SEASONS = [197, 190, 204]

def fetch_db_matches(season_id):
    """Pulls thousands of raw matches from Supabase in ~2 seconds"""
    matches = []
    offset = 0
    limit = 1000

    print(f"📡 Pulling Season {season_id} matches from Supabase...", end=" ")
    while True:
        # Supabase limits queries to 1000 rows, so we paginate using the Range header
        headers = {**supabase_headers, "Range": f"{offset}-{offset+limit-1}"}
        res = requests.get(f"{SUPABASE_URL}/rest/v1/raw_matches?season_id=eq.{season_id}&select=match_data", headers=headers)

        if res.status_code != 200:
            break

        data = res.json()
        if not data:
            break

        matches.extend([row["match_data"] for row in data])
        offset += limit
        if len(data) < limit:
            break

    print(f"✅ Downloaded {len(matches)} matches.")
    return matches

def run_fast_compute():
    for season_id in TARGET_SEASONS:
        print(f"\n⚡ RECALCULATING SEASON: {season_id}")

        # 1. Fetch from your own DB instead of RobotEvents
        all_matches = fetch_db_matches(season_id)
        if not all_matches:
            continue

        # 2. Extract unique teams directly from the match data
        all_teams = []
        seen_teams = set()
        for m in all_matches:
            for alliance in m.get("alliances", []):
                for team_wrapper in alliance.get("teams", []):
                    team_name = team_wrapper["team"]["name"]
                    if team_name not in seen_teams:
                        seen_teams.add(team_name)
                        all_teams.append(team_wrapper["team"])

        # 3. Run the math engine
        print("🧠 Crunching TrueRank and OPR matrices...")
        final_elo_data = calculate_truerank(all_teams, all_matches)
        for data in final_elo_data:
            data["season_id"] = season_id

        # 4. Push updated ranks back to Supabase
        print("☁️ Pushing updated leaderboards to Supabase...")
        for i in range(0, len(final_elo_data), 1000):
            batch = final_elo_data[i:i+1000]
            requests.post(f"{SUPABASE_URL}/rest/v1/global_truerank", json=batch, headers=supabase_headers)

        print("✅ Done!")

if __name__ == "__main__":
    run_fast_compute()