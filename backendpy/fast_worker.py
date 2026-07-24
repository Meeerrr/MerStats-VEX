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
    """Pulls matches from Supabase using strict URL pagination to prevent timeouts"""
    matches = []
    offset = 0
    limit = 500

    print(f"📡 Pulling Season {season_id} matches from Data Lake...", end=" ", flush=True)

    while True:
        # Strict pagination using URL parameters
        url = f"{SUPABASE_URL}/rest/v1/raw_matches?season_id=eq.{season_id}&select=match_data&order=id.asc&limit={limit}&offset={offset}"

        try:
            # Added a 20-second timeout so it never hangs indefinitely
            res = requests.get(url, headers=supabase_headers, timeout=20)

            if res.status_code != 200:
                print(f"\n❌ Database Error: {res.text}")
                break

            data = res.json()
            if not data:
                break # No more rows returned

            matches.extend([row["match_data"] for row in data])
            offset += limit

            # If we received less than the limit, we hit the end of the table
            if len(data) < limit:
                break

        except requests.exceptions.Timeout:
            print("\n❌ Error: Supabase connection timed out. Ensure your SQL Index is created!")
            break
        except Exception as e:
            print(f"\n❌ Unexpected Error: {e}")
            break

    print(f"✅ Downloaded {len(matches)} matches.")
    return matches


# 🔥 The Lightning Compute Engine
def run_fast_compute(seasons_to_process):
    print("\n🚀 Starting Lightning Compute Engine...")
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

        # 1. Parse Data
        for m in micro_matches:
            unique_teams.update(m.get("rt", []))
            unique_teams.update(m.get("bt", []))
            inflated_matches.append(inflate_match(m))

        all_teams = [{"name": t} for t in unique_teams]
        print(f"🤖 Found {len(all_teams)} unique active teams.")

        # 2. Sync Roster with Supabase
        print("🛡️ Verifying Team Roster in Database (Fixing Foreign Keys)...")
        team_payload = [{"id": t, "team_name": "Unknown"} for t in unique_teams]
        team_headers = {**supabase_headers, "Prefer": "resolution=ignore-duplicates"}

        for i in range(0, len(team_payload), 1000):
            batch = team_payload[i:i+1000]
            try:
                res = requests.post(f"{SUPABASE_URL}/rest/v1/teams?on_conflict=id", json=batch, headers=team_headers, timeout=15)
                if res.status_code not in [200, 201]:
                    print(f"   ❌ CRITICAL ROSTER ERROR: {res.text}")
            except Exception as e:
                print(f"   ❌ Network error syncing roster: {e}")

        # 3. Math Engine Execution
        print("🧠 Crunching TrueRank and OPR matrices...")
        engine_start = time.time()
        final_elo_data = calculate_truerank(all_teams, inflated_matches)
        print(f"⏱️ Math finished in {round(time.time() - engine_start, 2)} seconds.")

        # Attach the season ID to the calculated data
        for data in final_elo_data:
            data["season_id"] = season_id

        # 4. Upload Leaderboards
        print("☁️ Pushing updated leaderboards to Supabase...")
        for i in range(0, len(final_elo_data), 1000):
            batch = final_elo_data[i:i+1000]
            try:
                res = requests.post(f"{SUPABASE_URL}/rest/v1/global_truerank", json=batch, headers=supabase_headers, timeout=20)
                if res.status_code in [200, 201]:
                    print(f"   ✅ Upserted batch {i} to {i + len(batch)}")
                else:
                    print(f"   ❌ Upload Error: {res.text}")
            except Exception as e:
                print(f"   ❌ Network error uploading ranks: {e}")

    total_time = round(time.time() - start_time, 2)
    print(f"\n🏁 ENGINE COMPLETED in {total_time} seconds!")


if __name__ == "__main__":
    # If you run fast_worker.py manually, it defaults to calculating everything.
    TARGET_SEASONS = [181, 190, 197, 240, 173, 154, 139, 130, 125, 119, 115, 110, 102, 92, 85, 73]
    run_fast_compute(TARGET_SEASONS)