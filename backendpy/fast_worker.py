import requests
import os
import time
from dotenv import load_dotenv
from engine import calculate_truerank

load_dotenv()

# ==========================================
# API KEYS & HEADERS
# ==========================================
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

supabase_headers = {
    "apikey": SUPABASE_KEY,
    "Authorization": f"Bearer {SUPABASE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "resolution=merge-duplicates"
}

def inflate_match(micro_match):
    """Inflates the Micro-JSON and passes the Event Tier to the math engine"""
    return {
        "level": micro_match.get("lvl", "Local"), # Passes the tier string
        "alliances": [
            {
                "color": "red",
                "score": micro_match.get("rs", 0),
                "teams": [{"team": {"name": t}} for t in micro_match.get("rt", [])]
            },
            {
                "color": "blue",
                "score": micro_match.get("bs", 0),
                "teams": [{"team": {"name": t}} for t in micro_match.get("bt", [])]
            }
        ]
    }

def fetch_db_matches(season_id):
    matches = []
    offset = 0
    limit = 1000

    print(f"📡 Pulling Season {season_id} matches from Data Lake...", end=" ")

    while True:
        # Use Range headers to paginate through the database
        headers = {**supabase_headers, "Range": f"{offset}-{offset+limit-1}"}

        # Added &order=id.asc to force chronological sorting!
        res = requests.get(f"{SUPABASE_URL}/rest/v1/raw_matches?season_id=eq.{season_id}&select=match_data&order=id.asc", headers=headers)

        if res.status_code != 200:
            print(f"❌ Error: {res.text}")
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

# 🔥 UPDATE: The function now takes a list of seasons to process!
def run_fast_compute(seasons_to_process):
    print("🚀 Starting Lightning Compute Engine...")
    start_time = time.time()

    for season_id in seasons_to_process:
        print(f"\n==================================================")
        print(f"⚡ RECALCULATING SEASON: {season_id}")
        print(f"==================================================")

        micro_matches = fetch_db_matches(season_id)
        if not micro_matches:
            print("⚠️ No matches found for this season. Skipping.")
            continue

        unique_teams = set()
        inflated_matches = []

        for m in micro_matches:
            unique_teams.update(m.get("rt", []))
            unique_teams.update(m.get("bt", []))
            inflated_matches.append(inflate_match(m))

        all_teams = [{"name": t} for t in unique_teams]
        print(f"🤖 Found {len(all_teams)} unique active teams.")

        print("🛡️ Verifying Team Roster in Database (Fixing Foreign Keys)...")
        team_payload = [{"id": t, "team_name": "Unknown"} for t in unique_teams]
        team_headers = {**supabase_headers, "Prefer": "resolution=ignore-duplicates"}

        for i in range(0, len(team_payload), 1000):
            batch = team_payload[i:i+1000]
            res = requests.post(f"{SUPABASE_URL}/rest/v1/teams?on_conflict=id", json=batch, headers=team_headers)
            if res.status_code not in [200, 201]:
                print(f"   ❌ CRITICAL ROSTER ERROR: {res.text}")

        print("🧠 Crunching TrueRank and OPR matrices...")
        engine_start = time.time()
        final_elo_data = calculate_truerank(all_teams, inflated_matches)
        print(f"⏱️ Math finished in {round(time.time() - engine_start, 2)} seconds.")

        for data in final_elo_data:
            data["season_id"] = season_id

        print("☁️ Pushing updated leaderboards to Supabase...")
        for i in range(0, len(final_elo_data), 1000):
            batch = final_elo_data[i:i+1000]
            res = requests.post(f"{SUPABASE_URL}/rest/v1/global_truerank", json=batch, headers=supabase_headers)
            if res.status_code in [200, 201]:
                print(f"   ✅ Upserted batch {i} to {i + len(batch)}")
            else:
                print(f"   ❌ Upload Error: {res.text}")

    total_time = round(time.time() - start_time, 2)
    print(f"\n🏁 ENGINE COMPLETED in {total_time} seconds!")

if __name__ == "__main__":
    # If you run fast_worker.py manually, it defaults to calculating everything.
    TARGET_SEASONS = [181,190,197, 240, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92, 85, 73]
    run_fast_compute(TARGET_SEASONS)